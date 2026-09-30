import type { DetectedProblem } from './CrashDoctor'

/**
 * Windows ran out of commit memory while Minecraft was still loading.
 *
 * When Windows cannot hand out more memory (RAM plus page file), Java often
 * dies without a word: exit code 1, no exception, no crash report, not even an
 * hs_err file, and the launch log simply stops mid-way (usually while the mods'
 * mixins load). A large heap setting on a PC with many mods is the usual cause;
 * the same mods start with less RAM. Pure so scripts/test-silent-memory-exit.cjs
 * can check it without a launcher.
 */
export interface SilentExitFacts {
  /** crystal-launch.log plus the launcher's error message. */
  log: string
  /** The RAM setting (MB), used when the log header names no -Xmx. */
  currentRam: number
  /** Physical memory of this PC in MB. */
  totalMemMb: number
  /** A fresh hs_err_pid*.log exists (Java's own crash file). */
  freshNativeCrash: boolean
  /** A fresh crash-reports/crash-*.txt exists (Minecraft's own report). */
  freshCrashReport: boolean
}

/** Exit codes that fit a silent death: 1, -1 and Windows' "commitment limit" status. */
const SILENT_CODES = new Set([1, -1, -1073741523, 3221225773])

/** Anything that means the game said why it ended, or got past loading. */
const EXPLAINED = /---- Minecraft Crash Report|A fatal error has been detected|Exception in thread|OutOfMemoryError|Could not reserve enough space|insufficient memory for the Java Runtime|Native memory allocation|Stopping!|Sound engine started|Connecting to|Could not create the Java Virtual Machine|Incompatible mods found|Mod resolution failed/i

/** A last line that is an error or part of a stack trace. */
const ERROR_LINE = /Exception|Error:|Caused by:|^\s+at [\w$.\/]+\.[\w$<>]+\(|\/(?:ERROR|FATAL)\]|^\s*\.\.\. \d+ more/

export function silentMemoryExit(f: SilentExitFacts): DetectedProblem | null {
  if (f.freshNativeCrash || f.freshCrashReport) return null

  const codeText = [...f.log.matchAll(/Exit-Code (-?\d+)/g)].pop()?.[1]
  if (codeText === undefined || !SILENT_CODES.has(Number(codeText))) return null
  if (EXPLAINED.test(f.log)) return null

  const lines = f.log.split(/\r?\n/).filter(l => l.trim() && !l.startsWith('#'))
  // Java really started and wrote the game's own log lines.
  if (!lines.some(l => /\[[^\]]*\/(?:INFO|WARN|DEBUG)\]|Loading \d+ mods/.test(l))) return null
  if (lines.slice(-40).some(l => ERROR_LINE.test(l))) return null

  const heap = Number(f.log.match(/-Xmx(\d+)M/i)?.[1]) || f.currentRam
  const halfPhysical = Math.floor(f.totalMemMb / 2 / 512) * 512
  const target = Math.max(2048, Math.min(4096, halfPhysical))
  // Already at or below a safe size: memory is an unlikely cause.
  if (target >= heap) return null

  const totalGb = Math.round(f.totalMemMb / 1024)
  return {
    id: 'silent-out-of-memory',
    group: 'other',
    title: 'Wahrscheinlich hatte Windows nicht genug Arbeitsspeicher',
    detail: `Minecraft hat sich beim Laden ohne Fehlermeldung beendet (kein Absturzbericht, kein Java-Crash). `
      + `Mit ${heap} MB für Minecraft auf einem PC mit ${totalGb} GB passiert das, wenn Windows keinen Speicher mehr vergeben kann, `
      + `vor allem mit vielen Mods. Mit ${target} MB startet es meist problemlos. `
      + 'Hilft das nicht, schließe andere Programme oder lass Windows die Auslagerungsdatei automatisch verwalten.',
    fix: { kind: 'lower-ram', label: `Auf ${target} MB senken`, ram: target },
  }
}
