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
        detail: 'Crystal lädt die passende Version von Modrinth und ersetzt die alte Datei.',
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
        detail: `Crystal lädt die neueste passende Version von ${name} und ersetzt die alte Datei.`,
        fix: { kind: 'update-mod', label: 'Ersetzen', modFile: installed.file, modName: name },
        currentVersion: installed.version,
      })
    }

    // A mod built for another game version often passes Fabric's checks and
    // then breaks while loading: its mixins no longer fit, or its start-up code
    // calls something that is gone. The log names the mod; a matching version
    // from Modrinth usually fixes it. Crystal's own jar comes with the launcher.
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
          detail: 'Meist passt die Mod nicht zu dieser Minecraft-Version. Crystal lädt die passende Version von Modrinth.',
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
          ? 'Fabric API fehlt. Crystal installiert sie direkt von Modrinth.'
          : `Eine Mod braucht ${name}. Crystal sucht sie auf Modrinth und installiert sie.`,
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
        detail: 'Crystal lädt die passende Java-Version beim nächsten Start automatisch herunter. Starte einfach erneut.',
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
