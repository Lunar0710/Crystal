import fs from 'fs'
import crypto from 'crypto'
import os from 'os'
import path from 'path'
import { InstanceManager } from './InstanceManager'
import { JarReader } from '../util/jarReader'
import { logger } from '../logs/Logger'
import { crystalPath, isPlainFileName } from '../paths'

export type FixKind =
  | 'disable-mod'
  | 'update-mod'
  | 'install-mod'
  | 'install-fabric-api'
  | 'lower-ram'
  | 'raise-ram'
  | 'disable-zgc'

export interface ProblemFix {
  kind: FixKind
  label: string
  /** The jar to disable or replace. */
  modFile?: string
  /** Display name of the mod the fix is about. */
  modName?: string
  /** Modrinth project (slug or id) to install. */
  project?: string
  /** update-mod: exactly this version number (Fabric named it), instead of the newest. */
  version?: string
  ram?: number
}

/**
 * How the crash window groups a problem:
 * conflict = two mods that can't run together, the player keeps one;
 * replace  = a mod in the wrong version, swapped for a matching one;
 * install  = a required mod that is missing;
 * other    = everything else (Java, RAM, broken files).
 */
export type ProblemGroup = 'conflict' | 'replace' | 'install' | 'other'

export interface ConflictSide {
  name: string
  version: string | null
  modFile: string
}

export interface DetectedProblem {
  /** Stable id so the renderer can key on it. */
  id: string
  group: ProblemGroup
  /** What went wrong, in the user's words. */
  title: string
  /** Why this happened / what the fix does. */
  detail: string
  /** null = detected but not automatically fixable; the UI then only explains it. */
  fix: ProblemFix | null
  /** conflict only: the two mods; keeping one disables the other. */
  sides?: [ConflictSide, ConflictSide]
  /** replace only: the installed version. */
  currentVersion?: string | null
  /** A warning the player can dismiss for this mod ("Ich vertraue dieser Mod"). */
  trust?: { modFile: string; modName: string }
}

interface InstalledMod {
  file: string
  version: string | null
}

/**
 * Turns a failed launch's log output into concrete, applicable fixes.
 *
 * Deliberately narrow: it only reports a problem when the log states it
 * outright (Fabric's own dependency/incompatibility lines, the JVM's heap
 * messages), and only offers a fix that is actually carried out. A guess
 * dressed up as a "Fix" button would be worse than no button at all — anything
 * it can't identify is left to the raw log, which is still shown.
 */
export class CrashDoctor {
  constructor(private instances: InstanceManager) {}

  private modsDir(instanceId: string): string {
    const gameDir = this.instances.get(instanceId)?.gameDir
      || crystalPath('instances', instanceId)
    return path.join(gameDir, 'mods')
  }

  /** Maps Fabric mod ids (e.g. "sodium") to the enabled jar that declares them. */
  private installedMods(instanceId: string): Map<string, InstalledMod> {
    const dir = this.modsDir(instanceId)
    const map = new Map<string, InstalledMod>()
    if (!fs.existsSync(dir)) return map

    for (const fileName of fs.readdirSync(dir)) {
      if (!fileName.endsWith('.jar')) continue
      try {
        const raw = new JarReader(path.join(dir, fileName)).readText('fabric.mod.json')
        if (!raw) continue
        const json = JSON.parse(raw)
        if (typeof json?.id === 'string') {
          map.set(json.id.toLowerCase(), { file: fileName, version: typeof json.version === 'string' ? json.version : null })
        }
      } catch {
        // An unreadable jar is itself a possible cause, but it's reported by
        // the zip-error rule below rather than by failing the whole scan.
      }
    }
    return map
  }

  analyze(instanceId: string, log: string, currentRam: number): DetectedProblem[] {
    const problems: DetectedProblem[] = []
    const mods = this.installedMods(instanceId)
    const seen = new Set<string>()
    const push = (p: DetectedProblem) => {
      if (seen.has(p.id)) return
      seen.add(p.id)
      problems.push(p)
    }

    // "Mod 'X' (x) 1.0 is incompatible with any version of mod 'Y' (y) ..."
    // Neither side is "the wrong one", so the player picks which to keep.
    const incompatible = /Mod '(.+?)' \(([^)]+)\)[^\n]*?is incompatible with[^\n]*?mod '(.+?)' \(([^)]+)\)/gi
    for (const m of log.matchAll(incompatible)) {
      const [, nameA, idA, nameB, idB] = m
      const a = mods.get(idA.toLowerCase())
      const b = mods.get(idB.toLowerCase())
      if (!a || !b) continue
      const key = [idA.toLowerCase(), idB.toLowerCase()].sort().join('+')
      push({
        id: `conflict-${key}`,
        group: 'conflict',
        title: `${nameA} und ${nameB} vertragen sich nicht`,
        detail: 'Diese beiden Mods laufen nicht gleichzeitig. Wähle, welche du behalten willst; die andere wird deaktiviert.',
        fix: null,
        sides: [
          { name: nameA, version: a.version, modFile: a.file },
          { name: nameB, version: b.version, modFile: b.file },
        ],
      })
    }

    // Fabric's own advice under "A potential solution has been determined":
    // "Replace mod 'Sodium' (sodium) 0.8.15 with version 0.8.14." names the exact
    // version that fits; "Remove mod 'X' (x)." one that has to go.
    for (const m of log.matchAll(/Replace mod '(.+?)' \(([^)]+)\) \S+ with version ([^\s,]+?)\.?(?:\s|$)/g)) {
      const [, name, id, version] = m
      const installed = mods.get(id.toLowerCase())
      if (!installed) continue
      push({
        id: `replace-${id.toLowerCase()}`,
        group: 'replace',
        title: `${name} braucht Version ${version}`,
        detail: `Eine andere Mod verlangt genau diese Version. Nexora lädt ${name} ${version} von Modrinth und ersetzt die jetzige Datei.`,
        fix: { kind: 'update-mod', label: `Auf ${version}`, modFile: installed.file, modName: name, version },
        currentVersion: installed.version,
      })
    }
    for (const m of log.matchAll(/Remove mod '(.+?)' \(([^)]+)\)/g)) {
      const [, name, id] = m
      const installed = mods.get(id.toLowerCase())
      if (!installed) continue
      push({
        id: `remove-${id.toLowerCase()}`,
        group: 'other',
        title: `${name} muss raus`,
        detail: 'Fabric kann das Spiel mit dieser Mod nicht starten. Deaktiviere sie; im Mods-Tab kannst du sie wieder einschalten.',
        fix: { kind: 'disable-mod', label: 'Mod deaktivieren', modFile: installed.file, modName: name },
      })
    }

    // "Mod 'X' (x) 1.0 requires version 1.21.5 of 'Minecraft' (minecraft), but only the wrong version is present: 1.21.11!"
    const wrongMinecraft = /Mod '(.+?)' \(([^)]+)\)[^\n]*?requires [^\n]*?of '?Minecraft'? \(minecraft\), but only the wrong version is present/gi
    for (const m of log.matchAll(wrongMinecraft)) {
      const [, name, id] = m
      const installed = mods.get(id.toLowerCase())
      if (!installed) continue
      push({
        id: `replace-${id.toLowerCase()}`,
        group: 'replace',
        title: `${name} ist für eine andere Minecraft-Version`,
        detail: 'Nexora lädt die passende Version von Modrinth und ersetzt die alte Datei.',
        fix: { kind: 'update-mod', label: 'Ersetzen', modFile: installed.file, modName: name },
        currentVersion: installed.version,
      })
    }

    // "Mod 'X' (x) requires version 0.6.x of mod 'Y' (y), but only the wrong version is present: 0.5.8!"
    const wrongDependency = /Mod '(.+?)' \(([^)]+)\)[^\n]*?requires [^\n]*?of mod '(.+?)' \(([^)]+)\), but only the wrong version is present/gi
    for (const m of log.matchAll(wrongDependency)) {
      const [, needer, , name, id] = m
      const installed = mods.get(id.toLowerCase())
      if (!installed) continue
      push({
        id: `replace-${id.toLowerCase()}`,
        group: 'replace',
        title: `${needer} braucht eine andere Version von ${name}`,
        detail: `Nexora lädt die neueste passende Version von ${name} und ersetzt die alte Datei.`,
        fix: { kind: 'update-mod', label: 'Ersetzen', modFile: installed.file, modName: name },
        currentVersion: installed.version,
      })
    }

    // A mod built for another game version often passes Fabric's checks and
    // then breaks while loading: its mixins no longer fit, or its start-up code
    // calls something that is gone. The log names the mod; a matching version
    // from Modrinth usually fixes it. Nexora's own jar comes with the launcher.
    const crashedBy = [
      /Mixin apply for mod ([\w-]+) failed/g,
      /from mod ([\w-]+)\]? .*?(?:InvalidInjectionException|failed)/g,
      /Could not execute entrypoint stage '\w+' due to errors, provided by '([\w-]+)'/g,
    ]
    for (const pattern of crashedBy) {
      for (const m of log.matchAll(pattern)) {
        const id = m[1].toLowerCase()
        const installed = mods.get(id)
        if (!installed || id === 'crystal' || id === 'fabricloader' || id === 'minecraft') continue
        push({
          id: `replace-${id}`,
          group: 'replace',
          title: `${id} ist beim Laden abgestürzt`,
          detail: 'Meist passt die Mod nicht zu dieser Minecraft-Version. Nexora lädt die passende Version von Modrinth.',
          fix: { kind: 'update-mod', label: 'Ersetzen', modFile: installed.file, modName: id },
          currentVersion: installed.version,
        })
      }
    }

    // "requires ... of mod 'Fabric API' (fabric-api), which is missing!"
    const missing = /requires[^\n]*?of mod '(.+?)' \(([^)]+)\)[^\n]*?which is missing/gi
    for (const m of log.matchAll(missing)) {
      const [, name, id] = m
      const lower = id.toLowerCase()
      if (lower === 'fabricloader' || lower === 'minecraft' || lower === 'java') continue
      push({
        id: `install-${lower}`,
        group: 'install',
        title: `${name} fehlt`,
        detail: lower === 'fabric-api'
          ? 'Fabric API fehlt. Nexora installiert sie direkt von Modrinth.'
          : `Eine Mod braucht ${name}. Nexora sucht sie auf Modrinth und installiert sie.`,
        fix: lower === 'fabric-api'
          ? { kind: 'install-fabric-api', label: 'Installieren', modName: name }
          : { kind: 'install-mod', label: 'Installieren', modName: name, project: lower },
      })
    }

    // "Mod 'X' (x) requires version (21,) of 'Java ...' (java), but only the wrong version is present: 8!"
    const javaMatch = log.match(/Mod '(.+?)' \(([^)]+)\)[^\n]*?requires version (\d+)[^\n]*?of 'Java[^\n]*?present: (\d+)/i)
    if (javaMatch) {
      push({
        id: 'java-version',
        group: 'other',
        title: `Falsche Java-Version (${javaMatch[4]} statt ${javaMatch[3]})`,
        detail: 'Nexora lädt die passende Java-Version beim nächsten Start automatisch herunter. Starte einfach erneut.',
        fix: null,
      })
    }

    this.nativeCrash(instanceId, push)
    this.javaCrash(instanceId, push)
    // Neither report explained it: find out from what the game did last.
    if (![...seen].some(id => id.startsWith('java-crash-') || id.startsWith('native-crash-'))) this.silentExit(instanceId, push)

    // The player's own Java arguments (instance page) that Java itself rejected.
    const ownArgs = this.instances.get(instanceId)?.jvmArgs ?? []
    if (ownArgs.length && /Unrecognized (?:VM )?option|Error: Could not create the Java Virtual Machine|Error occurred during initialization of (?:VM|boot layer)|Error opening zip file or JAR manifest missing|agent library failed to init|Conflicting collector combinations|multiple garbage collectors/i.test(log)) {
      push({
        id: 'own-jvm-args',
        group: 'other',
        title: 'Java lehnt deine eigenen Java-Argumente ab',
        detail: `Diese Instanz startet mit: ${ownArgs.join(' ').slice(0, 200)}. Java konnte damit nicht starten. `
          + 'Prüfe sie auf der Seite der Instanz unter "Java-Argumente" (Tippfehler, falscher Pfad zu einem -javaagent) oder leere das Feld.',
        fix: null,
      })
    }

    if (/Could not reserve enough space for.*object heap/i.test(log)) {
      const lowered = Math.max(1024, Math.floor(currentRam / 2))
      push({
        id: 'ram-too-high',
        group: 'other',
        title: 'Zu viel RAM eingestellt',
        detail: `Java konnte ${currentRam} MB nicht reservieren. Das ist mehr, als dein System gerade frei hat.`,
        fix: { kind: 'lower-ram', label: `Auf ${lowered} MB`, ram: lowered },
      })
    }

    // The JVM itself couldn't get memory from Windows (hs_err report): the
    // whole system ran out, usually a small page file plus other programs.
    if (/insufficient memory for the Java Runtime|Native memory allocation \((?:malloc|mmap)\) failed/i.test(log)) {
      const lowered = Math.max(2048, Math.min(currentRam - 1024, Math.floor(currentRam * 0.75)))
      push({
        id: 'system-out-of-memory',
        group: 'other',
        title: 'Windows hatte keinen freien Arbeitsspeicher mehr',
        detail: `Minecraft durfte bis zu ${currentRam} MB nutzen, aber Windows konnte keinen Speicher mehr vergeben. `
          + 'Schließe andere Programme (Browser, Discord, weitere Minecraft-Fenster) oder vergrößere die Auslagerungsdatei '
          + '(Windows-Einstellungen, "Erweiterte Systemeinstellungen", Leistung, Virtueller Arbeitsspeicher: automatisch verwalten).',
        fix: lowered < currentRam ? { kind: 'lower-ram', label: `Auf ${lowered} MB senken`, ram: lowered } : null,
      })
    }

    // Windows swaps to the page file on the system drive; with that drive nearly
    // full the page file can't grow, and Java is refused memory or ended without
    // a word. Checked on every crash, since it hides behind many kinds of them.
    const systemDrive = process.platform === 'win32' ? (process.env.SystemDrive || 'C:') + '\\' : '/'
    try {
      const stat = (fs as any).statfsSync?.(systemDrive) as { bavail: number; bsize: number } | undefined
      const freeGb = stat ? (stat.bavail * stat.bsize) / 1024 ** 3 : Infinity
      if (freeGb < 5) {
        push({
          id: 'system-drive-full',
          group: 'other',
          title: `Laufwerk ${systemDrive.replace('\\', '')} ist fast voll (${freeGb.toFixed(1)} GB frei)`,
          detail: 'Windows lagert Arbeitsspeicher auf dieses Laufwerk aus. Ist es fast voll, bekommt Minecraft keinen Speicher mehr '
            + 'und wird ohne Fehlermeldung beendet oder lädt ewig. Mach mindestens 10 GB frei (große Videos, alte Downloads, '
            + 'Spiele auf ein anderes Laufwerk) oder leg die Auslagerungsdatei auf ein anderes Laufwerk ("Erweiterte Systemeinstellungen", '
            + 'Leistung, Virtueller Arbeitsspeicher).',
          fix: null,
        })
      }
    } catch { /* no free-space reading on this system */ }

    // Minecraft's own heap was full. Only then can more RAM help, and never so
    // much that Windows itself runs short (at least 4 GB stay free for it).
    if (/java\.lang\.OutOfMemoryError/i.test(log) && !seen.has('system-out-of-memory')) {
      const systemLimit = Math.floor((os.totalmem() / 1048576 - 4096) / 512) * 512
      const raised = Math.min(16384, currentRam * 2, systemLimit)
      push({
        id: 'out-of-memory',
        group: 'other',
        title: 'Minecraft ist der Speicher ausgegangen',
        detail: raised > currentRam
          ? `Der Start lief mit ${currentRam} MB. Mehr RAM behebt das in den meisten Fällen.`
          : `Der Start lief mit ${currentRam} MB, mehr gibt dein PC nicht sicher her. Entferne speicherhungrige Mods oder senke die Sichtweite.`,
        fix: raised > currentRam ? { kind: 'raise-ram', label: `Auf ${raised} MB erhöhen`, ram: raised } : null,
      })
    }

    // A truncated download leaves a jar that Fabric can't open at all.
    if (/(?:zip END header not found|Invalid or corrupt jarfile|error in opening zip file)/i.test(log)) {
      push({
        id: 'corrupt-jar',
        group: 'other',
        title: 'Beschädigte Mod-Datei',
        detail: 'Mindestens eine .jar im mods-Ordner lässt sich nicht öffnen, meist nach einem abgebrochenen Download. '
          + 'Lösche sie im Mods-Tab und installiere sie neu.',
        fix: null,
      })
    }

    // Only a last guess: a mod with its own native program is named when
    // nothing above explains the crash, never next to a clear cause.
    // And only when Minecraft actually ran: a start the launcher itself refused
    // (client mod missing, no Java) is no reason to suspect a mod.
    if (problems.length === 0 && this.gameRanRecently(instanceId)) this.hiddenNativeCode(instanceId, push)

    return problems
  }

  /** Disables one mod by renaming it; the Mods tab can turn it back on. */
  /**
   * A hard crash of Java itself (an hs_err_pid*.log in the game folder from the
   * last few minutes). Java's own stack names the class that was running; if a
   * mod's jar holds that class, that mod crashed the game, and turning it off
   * is the fix. A crash in some library's own native code while ZGC was on
   * gets a second offer: start without ZGC, which such libraries often don't
   * cope with (KryptonPlus did exactly that).
   */
  private nativeCrash(instanceId: string, push: (p: DetectedProblem) => void): void {
    const gameDir = this.instances.get(instanceId)?.gameDir || crystalPath('instances', instanceId)
    let report: string | null = null
    try {
      const newest = fs.readdirSync(gameDir)
        .filter(f => /^hs_err_pid\d+\.log$/.test(f))
        .map(f => ({ f, t: fs.statSync(path.join(gameDir, f)).mtimeMs }))
        .sort((a, b) => b.t - a.t)[0]
      if (newest && Date.now() - newest.t < 10 * 60 * 1000) report = fs.readFileSync(path.join(gameDir, newest.f), 'utf8')
    } catch { return }
    if (!report) return

    const frame = report.match(/^# (?:C|j|J|V)\s+\[?([^\]\s+]+)/m)?.[1] ?? ''
    const zgc = /-XX:\+UseZGC/.test(report)
    // The first Java frame outside Java, Minecraft, Fabric and Mixin is the suspect.
    const skip = /^(?:java\.|jdk\.|sun\.|net\.minecraft\.|com\.mojang\.|net\.fabricmc\.|org\.spongepowered\.|com\.llamalad7\.|org\.lwjgl\.|org\.objectweb\.)/
    let suspectClass: string | null = null
    const javaFrames = report.split('Java frames:')[1] ?? ''
    for (const m of javaFrames.matchAll(/^[jJ]\s+(?:\d+\s+c\d\s+)?([\w$.\/]+)\.[\w$<>]+\(/gm)) {
      if (!skip.test(m[1])) { suspectClass = m[1]; break }
    }
    const suspectJar = suspectClass ? this.jarWithClass(instanceId, suspectClass) : null
    if (suspectJar && !/^(?:nexora|crystal-client)-/i.test(suspectJar)) {
      push({
        id: `native-crash-${suspectJar}`,
        group: 'other',
        title: `${suspectJar.replace(/\.jar$/, '')} hat Minecraft abstürzen lassen`,
        detail: 'Java ist hart abgestürzt, während diese Mod lief' + (frame ? ` (in ${frame})` : '') + '. '
          + 'Deaktiviere sie, oder hol dir eine neuere Version. Mods, die eigenen nativen Code nachladen, solltest du nur aus sicheren Quellen nehmen.',
        fix: { kind: 'disable-mod', label: 'Mod deaktivieren', modFile: suspectJar, modName: suspectJar },
      })
    }
    if (zgc && frame && !/^(?:jvm\.dll|libjvm)/i.test(frame)) {
      push({
        id: 'native-crash-zgc',
        group: 'other',
        title: 'Absturz mit "Weniger Ruckler" (ZGC)',
        detail: 'Der Absturz passierte in fremdem nativem Code, während ZGC an war. Manche Mods vertragen ZGC nicht. '
          + 'Starte ohne ZGC; einschalten kannst du es jederzeit wieder in den Einstellungen.',
        fix: { kind: 'disable-zgc', label: 'Ohne ZGC starten' },
      })
    }
  }

  /** Whether Minecraft wrote its log in the last ten minutes, i.e. the game really started. */
  private gameRanRecently(instanceId: string): boolean {
    const gameDir = this.instances.get(instanceId)?.gameDir || crystalPath('instances', instanceId)
    try { return Date.now() - fs.statSync(path.join(gameDir, 'logs', 'latest.log')).mtimeMs < 10 * 60 * 1000 } catch { return false }
  }

  /** The mod jar in this instance that holds the class, or null. */
  private jarWithClass(instanceId: string, className: string): string | null {
    const entry = className.replace(/\./g, '/') + '.class'
    try {
      for (const file of fs.readdirSync(this.modsDir(instanceId))) {
        if (!file.endsWith('.jar')) continue
        try {
          if (new JarReader(path.join(this.modsDir(instanceId), file)).has(entry)) return file
        } catch { /* unreadable jar: not the one */ }
      }
    } catch { /* no mods folder */ }
    return null
  }

  /**
   * A normal Minecraft crash report (crash-reports/ from the last few
   * minutes) whose stack runs through another mod before anything of
   * Minecraft's: that mod crashed the game (Essential's cosmetics, say), or
   * hung it while closing (the "Watchdog" reports). Nexora names it and offers
   * to turn it off. Without this the crash window had nothing to say about
   * most crashes, and they all looked like Nexora's.
   */
  private javaCrash(instanceId: string, push: (p: DetectedProblem) => void): void {
    const dir = path.join(this.instances.get(instanceId)?.gameDir || crystalPath('instances', instanceId), 'crash-reports')
    let report: string | null = null
    try {
      const newest = fs.readdirSync(dir)
        .filter(f => /^crash-.*-client\.txt$/.test(f))
        .map(f => ({ f, t: fs.statSync(path.join(dir, f)).mtimeMs }))
        .sort((a, b) => b.t - a.t)[0]
      if (newest && Date.now() - newest.t < 10 * 60 * 1000) report = fs.readFileSync(path.join(dir, newest.f), 'utf8')
    } catch { return }
    if (!report) return

    // Only the top trace: the thread dump below it lists every thread, busy or not.
    const top = report.split(/A detailed walkthrough/)[0]
    const description = top.match(/^Description: (.+)$/m)?.[1]?.trim() ?? ''
    const error = top.match(/^([\w.$]+(?:Exception|Error)[^\n]*)$/m)?.[1]?.trim() ?? ''
    const skip = /^(?:java\.|jdk\.|sun\.|kotlin\.|net\.minecraft\.|com\.mojang\.|net\.fabricmc\.|org\.spongepowered\.|com\.llamalad7\.|org\.lwjgl\.|org\.objectweb\.)/
    let suspectClass: string | null = null
    for (const m of top.matchAll(/^\s+at (?:[\w.@\/-]+\/\/?)?([\w$.]+)\.[\w$<>-]+\(/gm)) {
      const cls = m[1]
      if (skip.test(cls)) continue
      suspectClass = cls
      break
    }
    // Nexora's own crashes are ours to fix, not a mod to switch off.
    if (!suspectClass || suspectClass.startsWith('dev.crystal.')) return
    const jar = this.jarWithClass(instanceId, suspectClass.replace(/\$.*$/, ''))
    if (!jar || /^(?:nexora|crystal-client)-/i.test(jar)) return
    const name = jar.replace(/\.jar$/, '')
    const hang = /Watchdog/.test(error) || description === 'Client shutdown'
    push({
      id: `java-crash-${jar}`,
      group: 'other',
      title: hang ? `${name} hat Minecraft beim Beenden aufgehängt` : `${name} hat Minecraft abstürzen lassen`,
      detail: (hang
        ? 'Minecraft wartete beim Schließen auf diese Mod, bis es sich selbst beendet hat. '
        : `Der Absturz passierte in dieser Mod${error ? ` (${error.slice(0, 120)})` : ''}. `)
        + 'Deaktiviere sie oder hol dir eine neuere Version. Nexora selbst war nicht beteiligt.',
      fix: { kind: 'disable-mod', label: 'Mod deaktivieren', modFile: jar, modName: name },
    })
  }

  /**
   * The game ended without a crash report or a Java crash file: something
   * called exit itself, often a mod's own loader or check. The last lines of
   * latest.log still say what was loading at that moment; a mod's mixin
   * config ("... from mixins.foo.json into ...") leads to its jar.
   */
  private silentExit(instanceId: string, push: (p: DetectedProblem) => void): void {
    const gameDir = this.instances.get(instanceId)?.gameDir || crystalPath('instances', instanceId)
    const logFile = path.join(gameDir, 'logs', 'latest.log')
    let tail: string[] = []
    try {
      if (Date.now() - fs.statSync(logFile).mtimeMs > 10 * 60 * 1000) return
      tail = fs.readFileSync(logFile, 'utf8').split(/\r?\n/).filter(Boolean).slice(-40)
    } catch { return }
    // A game that reached the menu or a world didn't die while starting.
    if (tail.some(l => /Stopping!|Sound engine started|Connecting to/.test(l))) return
    let mixinConfig: string | null = null
    for (let i = tail.length - 1; i >= 0 && !mixinConfig; i--) {
      mixinConfig = tail[i].match(/from ([\w.-]+\.json) into /)?.[1] ?? null
    }
    if (!mixinConfig) return
    let jar: string | null = null
    try {
      for (const file of fs.readdirSync(this.modsDir(instanceId))) {
        if (!file.endsWith('.jar')) continue
        try { if (new JarReader(path.join(this.modsDir(instanceId), file)).has(mixinConfig)) { jar = file; break } } catch { /* unreadable */ }
      }
    } catch { return }
    if (!jar || /^(?:nexora|crystal-client)-/i.test(jar)) return
    const name = jar.replace(/\.jar$/, '')
    push({
      id: `silent-exit-${jar}`,
      group: 'other',
      title: `Minecraft hat sich beim Laden von ${name} beendet`,
      detail: 'Das Spiel hat sich ohne Fehlermeldung selbst beendet, während diese Mod als letzte geladen wurde. '
        + 'Deaktiviere sie und starte neu; startet es dann, war sie es.',
      fix: { kind: 'disable-mod', label: 'Mod deaktivieren', modFile: jar, modName: name },
    })
  }

  /**
   * Mods that bring a program of their own (a Windows .dll or similar) and
   * hide their Java code (a handful of classes, a licence key): loaders for
   * paid or cheat clients. They end the game without a word when their check
   * fails and can do anything on the PC. Voice chat and the like also ship
   * native libraries, but with hundreds of plain classes, so they aren't named.
   */
  private hiddenNativeCode(instanceId: string, push: (p: DetectedProblem) => void): void {
    let files: string[] = []
    try { files = fs.readdirSync(this.modsDir(instanceId)).filter(f => f.endsWith('.jar')) } catch { return }
    for (const file of files) {
      try {
        const names = new JarReader(path.join(this.modsDir(instanceId), file)).names()
        const natives = names.filter(n => /\.(dll|so|dylib|jnilib)$/i.test(n) || /(^|\/)natives?\/[^/]+\/[^/]+$/i.test(n))
        if (natives.length === 0) continue
        if (this.isTrusted(instanceId, file)) continue
        const classes = names.filter(n => n.endsWith('.class')).length
        const licensed = names.some(n => /licen[cs]e.?key/i.test(n))
        if (classes >= 25 && !licensed) continue
        const name = file.replace(/\.jar$/, '')
        const count = natives.length === 1 ? '1 Datei' : `${natives.length} Dateien`
        push({
          id: `hidden-native-${file}`,
          group: 'other',
          title: `${name} bringt versteckten Programmcode mit`,
          detail: `Diese Mod enthält ein eigenes Programm (${count}) und versteckt ihren Code${licensed ? ' hinter einem Lizenzschlüssel' : ''}. `
            + 'Solche Mods beenden Minecraft oft ohne Fehlermeldung und können auf dem PC tun, was sie wollen. Nimm sie nur aus einer Quelle, der du vertraust.',
          fix: { kind: 'disable-mod', label: 'Mod deaktivieren', modFile: file, modName: name },
          trust: { modFile: file, modName: name },
        })
      } catch { /* unreadable jar */ }
    }
  }

  /**
   * Mods the player trusts (the second button on the hidden-code warning), per
   * instance, stored with the jar's SHA-256: a replaced file is a different mod
   * and gets the warning once more.
   */
  private trustFile(instanceId: string): string {
    return path.join(this.instances.get(instanceId)?.gameDir || crystalPath('instances', instanceId), '.nexora-trusted-mods.json')
  }

  private readTrusted(instanceId: string): Record<string, string> {
    try { return JSON.parse(fs.readFileSync(this.trustFile(instanceId), 'utf8')) || {} } catch { return {} }
  }

  private hashOf(instanceId: string, modFile: string): string | null {
    try { return crypto.createHash('sha256').update(fs.readFileSync(path.join(this.modsDir(instanceId), modFile))).digest('hex') } catch { return null }
  }

  private isTrusted(instanceId: string, modFile: string): boolean {
    const saved = this.readTrusted(instanceId)[modFile]
    return !!saved && saved === this.hashOf(instanceId, modFile)
  }

  trustMod(instanceId: string, modFile: unknown): { ok: boolean; message: string } {
    if (typeof modFile !== 'string' || !isPlainFileName(modFile)) return { ok: false, message: 'Ungültiger Dateiname.' }
    const hash = this.hashOf(instanceId, modFile)
    if (!hash) return { ok: false, message: `${modFile} nicht gefunden.` }
    const trusted = this.readTrusted(instanceId)
    trusted[modFile] = hash
    fs.writeFileSync(this.trustFile(instanceId), JSON.stringify(trusted, null, 2))
    logger.info('client', `Mod als vertrauenswürdig markiert: ${modFile}`)
    return { ok: true, message: `Nexora warnt bei ${modFile} nicht mehr.` }
  }

  disableMod(instanceId: string, modFile: string | undefined): { ok: boolean; message: string } {
    if (!modFile) return { ok: false, message: 'Keine Datei angegeben.' }
    if (!isPlainFileName(modFile)) return { ok: false, message: 'Ungültiger Dateiname.' }
    const filePath = path.join(this.modsDir(instanceId), modFile)
    if (!fs.existsSync(filePath)) return { ok: false, message: `${modFile} nicht gefunden.` }
    fs.renameSync(filePath, `${filePath}.disabled`)
    logger.info('client', `Autofix: ${modFile} deaktiviert`)
    return { ok: true, message: `${modFile} deaktiviert.` }
  }

  /**
   * Keeps this crash's launch log as crash-logs/<id>.log, since the next start
   * overwrites crystal-launch.log: the short id the crash window shows then
   * still leads to the log. The newest 10 stay.
   */
  keepCrashLog(instanceId: string, id: string): void {
    if (!/^[0-9a-f]{12}$/.test(id)) return
    const gameDir = this.instances.get(instanceId)?.gameDir || crystalPath('instances', instanceId)
    const logPath = path.join(gameDir, 'crystal-launch.log')
    if (!fs.existsSync(logPath)) return
    try {
      const dir = path.join(gameDir, 'crash-logs')
      fs.mkdirSync(dir, { recursive: true })
      fs.copyFileSync(logPath, path.join(dir, `${id}.log`))
      const old = fs.readdirSync(dir).filter(f => f.endsWith('.log'))
        .map(f => ({ f, t: fs.statSync(path.join(dir, f)).mtimeMs }))
        .sort((a, b) => b.t - a.t).slice(10)
      for (const { f } of old) fs.rmSync(path.join(dir, f), { force: true })
      logger.info('client', `Absturz ${id}: Log gesichert unter ${path.join(dir, `${id}.log`)}`)
    } catch (err) {
      logger.warn('client', `Crash-Log ${id} konnte nicht gesichert werden`, String(err))
    }
  }

  /** The launch log with anything personal or secret taken out, for sharing. */
  shareableLog(instanceId: string, id?: string): string | null {
    const gameDir = this.instances.get(instanceId)?.gameDir || crystalPath('instances', instanceId)
    const logPath = path.join(gameDir, 'crystal-launch.log')
    if (!fs.existsSync(logPath)) return null
    // mclo.gs takes at most 25,000 lines; the end of the log is where crashes are.
    const lines = fs.readFileSync(logPath, 'utf8').split('\n').slice(-24999)
    if (id && /^[0-9a-f]{12}$/.test(id)) lines.unshift(`# Nexora crash id: ${id}`)
    return lines.join('\n')
      .replace(/(accessToken|access_token|session|token)(["'=:\s]+)[\w.\-]{20,}/gi, '$1$2<entfernt>')
      .replace(/eyJ[\w-]+\.[\w-]+\.[\w-]+/g, '<entfernt>')
      .replace(/([A-Za-z]:[\\/]+Users[\\/]+)[^\\/\s]+/gi, '$1<user>')
      .replace(/(\/(?:home|Users)\/)[^/\s]+/g, '$1<user>')
  }
}
