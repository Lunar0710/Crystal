import fs from 'fs'
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
    const incompatible = /Mod '([^']+)' \(([^)]+)\)[^\n]*?is incompatible with[^\n]*?mod '([^']+)' \(([^)]+)\)/gi
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

    // "Mod 'X' (x) 1.0 requires version 1.21.5 of 'Minecraft' (minecraft), but only the wrong version is present: 1.21.11!"
    const wrongMinecraft = /Mod '([^']+)' \(([^)]+)\)[^\n]*?requires [^\n]*?of '?Minecraft'? \(minecraft\), but only the wrong version is present/gi
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
    const wrongDependency = /Mod '([^']+)' \(([^)]+)\)[^\n]*?requires [^\n]*?of mod '([^']+)' \(([^)]+)\), but only the wrong version is present/gi
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
    const missing = /requires[^\n]*?of mod '([^']+)' \(([^)]+)\)[^\n]*?which is missing/gi
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
    const javaMatch = log.match(/Mod '([^']+)' \(([^)]+)\)[^\n]*?requires version (\d+)[^\n]*?of 'Java[^\n]*?present: (\d+)/i)
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

  disableMod(instanceId: string, modFile: string | undefined): { ok: boolean; message: string } {
    if (!modFile) return { ok: false, message: 'Keine Datei angegeben.' }
    if (!isPlainFileName(modFile)) return { ok: false, message: 'Ungültiger Dateiname.' }
    const filePath = path.join(this.modsDir(instanceId), modFile)
    if (!fs.existsSync(filePath)) return { ok: false, message: `${modFile} nicht gefunden.` }
    fs.renameSync(filePath, `${filePath}.disabled`)
    logger.info('client', `Autofix: ${modFile} deaktiviert`)
    return { ok: true, message: `${modFile} deaktiviert.` }
  }

  /** The launch log with anything personal or secret taken out, for sharing. */
  shareableLog(instanceId: string): string | null {
    const gameDir = this.instances.get(instanceId)?.gameDir || crystalPath('instances', instanceId)
    const logPath = path.join(gameDir, 'crystal-launch.log')
    if (!fs.existsSync(logPath)) return null
    const lines = fs.readFileSync(logPath, 'utf8').split('\n')
    // mclo.gs takes at most 25,000 lines; the end of the log is where crashes are.
    return lines.slice(-25000).join('\n')
      .replace(/(accessToken|access_token|session|token)(["'=:\s]+)[\w.\-]{20,}/gi, '$1$2<entfernt>')
      .replace(/eyJ[\w-]+\.[\w-]+\.[\w-]+/g, '<entfernt>')
      .replace(/([A-Za-z]:[\\/]+Users[\\/]+)[^\\/\s]+/gi, '$1<user>')
      .replace(/(\/(?:home|Users)\/)[^/\s]+/g, '$1<user>')
  }
}
