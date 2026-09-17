import fs from 'fs'
import os from 'os'
import path from 'path'
import { InstanceManager } from './InstanceManager'
import { JarReader } from '../util/jarReader'
import { logger } from '../logs/Logger'
import { crystalPath, isPlainFileName } from '../paths'

export type FixKind =
  | 'disable-mod'
  | 'install-fabric-api'
  | 'lower-ram'
  | 'raise-ram'

export interface DetectedProblem {
  /** Stable id so the renderer can key on it. */
  id: string
  /** What went wrong, in the user's words. */
  title: string
  /** Why this happened / what the fix does. */
  detail: string
  /** null = detected but not automatically fixable; the UI then only explains it. */
  fix: { kind: FixKind; label: string; modFile?: string; ram?: number } | null
}

/**
 * Turns a failed launch's log output into concrete, applicable fixes.
 *
 * Deliberately narrow: it only reports a problem when the log states it
 * outright (Fabric's own dependency/incompatibility lines, the JVM's heap
 * messages), and only offers a fix that is actually carried out here. A guess
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

  /** Maps Fabric mod ids (e.g. "sodium") to the jar file that declares them. */
  private modIdToFile(instanceId: string): Map<string, string> {
    const dir = this.modsDir(instanceId)
    const map = new Map<string, string>()
    if (!fs.existsSync(dir)) return map

    for (const fileName of fs.readdirSync(dir)) {
      if (!fileName.endsWith('.jar')) continue
      try {
        const reader = new JarReader(path.join(dir, fileName))
        const raw = reader.readText('fabric.mod.json')
        if (!raw) continue
        const id = JSON.parse(raw)?.id
        if (typeof id === 'string') map.set(id.toLowerCase(), fileName)
      } catch {
        // An unreadable jar is itself a possible cause, but it's reported by
        // the zip-error rule below rather than by failing the whole scan.
      }
    }
    return map
  }

  analyze(instanceId: string, log: string, currentRam: number): DetectedProblem[] {
    const problems: DetectedProblem[] = []
    const mods = this.modIdToFile(instanceId)
    const seen = new Set<string>()

    const push = (p: DetectedProblem) => {
      if (seen.has(p.id)) return
      seen.add(p.id)
      problems.push(p)
    }

    // "Mod 'X' (x) 1.0 is incompatible with version ... of mod 'Y' (y) ..."
    // Fabric names both sides; the one that declared the incompatibility is
    // usually the newer one, so the conflicting partner is what we offer to
    // disable — but both are listed so the choice stays with the user.
    const incompatible = /Mod '([^']+)' \(([^)]+)\)[^\n]*?is incompatible with[^\n]*?mod '([^']+)' \(([^)]+)\)/gi
    for (const m of log.matchAll(incompatible)) {
      const [, nameA, idA, nameB, idB] = m
      for (const [name, id] of [[nameA, idA], [nameB, idB]] as const) {
        const file = mods.get(id.toLowerCase())
        if (!file) continue
        push({
          id: `incompat-${id}`,
          title: `${nameA} und ${nameB} vertragen sich nicht`,
          detail: `Diese beiden Mods laufen nicht gleichzeitig. Deaktiviere eine davon (${name} ist ${file}).`,
          fix: { kind: 'disable-mod', label: `${name} deaktivieren`, modFile: file },
        })
      }
    }

    // "Mod 'X' (x) requires version ... of 'Java ...' (java), but only the wrong version is present: 8!"
    const javaMismatch = /Mod '([^']+)' \(([^)]+)\)[^\n]*?requires version (\d+)[^\n]*?of 'Java[^\n]*?present: (\d+)/i
    const javaMatch = log.match(javaMismatch)
    if (javaMatch) {
      push({
        id: 'java-version',
        title: `Falsche Java-Version (${javaMatch[4]} statt ${javaMatch[3]})`,
        detail: 'Crystal lädt Java 21 beim nächsten Start automatisch herunter und benutzt es, '
          + 'statt eine ältere Java-Installation vom System zu nehmen. Starte einfach erneut.',
        fix: null,
      })
    }

    // "requires ... of mod 'Fabric API' (fabric-api), which is missing!"
    const missing = /requires[^\n]*?of mod '([^']+)' \(([^)]+)\)[^\n]*?which is missing/gi
    for (const m of log.matchAll(missing)) {
      const [, name, id] = m
      const isFabricApi = id.toLowerCase() === 'fabric-api' || id.toLowerCase() === 'fabricloader'
      push({
        id: `missing-${id}`,
        title: `Fehlende Abhängigkeit: ${name}`,
        detail: isFabricApi
          ? 'Fabric API fehlt. Crystal kann sie direkt von Modrinth nachinstallieren.'
          : `Eine Mod verlangt ${name}, die nicht installiert ist. Installiere sie über den Modrinth-Tab.`,
        fix: isFabricApi
          ? { kind: 'install-fabric-api', label: 'Fabric API installieren' }
          : null,
      })
    }

    if (/Could not reserve enough space for.*object heap/i.test(log)) {
      const lowered = Math.max(1024, Math.floor(currentRam / 2))
      push({
        id: 'ram-too-high',
        title: 'Zu viel RAM eingestellt',
        detail: `Java konnte ${currentRam} MB nicht reservieren. Das ist mehr, als dein System gerade frei hat.`,
        fix: { kind: 'lower-ram', label: `Auf ${lowered} MB reduzieren`, ram: lowered },
      })
    }

    // The JVM itself couldn't get memory from Windows (hs_err report): the
    // whole system ran out, usually a small page file plus other programs.
    if (/insufficient memory for the Java Runtime|Native memory allocation \((?:malloc|mmap)\) failed/i.test(log)) {
      const lowered = Math.max(2048, Math.min(currentRam - 1024, Math.floor(currentRam * 0.75)))
      push({
        id: 'system-out-of-memory',
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
        title: 'Minecraft ist der Speicher ausgegangen',
        detail: raised > currentRam
          ? `Der Start lief mit ${currentRam} MB. Mehr RAM behebt das in den meisten Fällen.`
          : `Der Start lief mit ${currentRam} MB, mehr gibt dein PC nicht sicher her. Entferne speicherhungrige Mods oder senke die Sichtweite.`,
        fix: raised > currentRam ? { kind: 'raise-ram', label: `Auf ${raised} MB erhöhen`, ram: raised } : null,
      })
    }

    // A truncated download leaves a jar that Fabric can't open at all.
    const corrupt = log.match(/(?:zip END header not found|Invalid or corrupt jarfile|error in opening zip file)[^\n]*/i)
    if (corrupt) {
      push({
        id: 'corrupt-jar',
        title: 'Beschädigte Mod-Datei',
        detail: 'Mindestens eine .jar im mods-Ordner lässt sich nicht öffnen, meist nach einem abgebrochenen Download. '
          + 'Lösche sie im Mods-Tab und installiere sie neu.',
        fix: null,
      })
    }

    return problems
  }

  /** Applies one fix. Returns a message describing what actually happened. */
  applyFix(instanceId: string, fix: NonNullable<DetectedProblem['fix']>): { ok: boolean; message: string } {
    switch (fix.kind) {
      case 'disable-mod': {
        if (!fix.modFile) return { ok: false, message: 'Keine Datei angegeben.' }
        if (!isPlainFileName(fix.modFile)) return { ok: false, message: 'Ungültiger Dateiname.' }
        const filePath = path.join(this.modsDir(instanceId), fix.modFile)
        if (!fs.existsSync(filePath)) return { ok: false, message: `${fix.modFile} nicht gefunden.` }
        // Renamed, never deleted — the user can re-enable it in the Mods tab.
        fs.renameSync(filePath, `${filePath}.disabled`)
        logger.info('client', `Autofix: ${fix.modFile} deaktiviert`)
        return { ok: true, message: `${fix.modFile} deaktiviert.` }
      }
      default:
        return { ok: false, message: 'Dieser Fix wird an anderer Stelle ausgeführt.' }
    }
  }
}
