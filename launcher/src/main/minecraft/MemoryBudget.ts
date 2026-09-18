import { execFile } from 'child_process'
import { logger } from '../logs/Logger'

/**
 * How much heap this launch can safely get.
 *
 * The heap setting is a ceiling the game grows into while playing. If Windows
 * cannot back that growth (other programs, a build, a small page file), the
 * JVM dies with no message or an "insufficient memory" report, often minutes
 * in. Checking the free commit charge first and lowering the ceiling for this
 * one launch turns that crash into a notice.
 */

/** Memory the JVM uses beyond the heap: class metadata, JIT code, threads, the GL driver. */
const NATIVE_OVERHEAD_MB = 1536
/** Left for Windows and whatever else is open. */
const SYSTEM_RESERVE_MB = 1024
/** Below this Minecraft with mods does not load at all. */
const MIN_HEAP_MB = 2048

export interface HeapDecision {
  heapMb: number
  /** Shown to the player when the setting could not be used as is. */
  notice?: string
}

/** Pure: the heap for a requested setting and the currently free commit charge (MB). */
export function fitHeap(requestedMb: number, freeCommitMb: number | null): HeapDecision {
  if (freeCommitMb === null) return { heapMb: requestedMb }
  const budget = freeCommitMb - NATIVE_OVERHEAD_MB - SYSTEM_RESERVE_MB
  if (requestedMb <= budget) return { heapMb: requestedMb }

  const lowered = Math.max(MIN_HEAP_MB, Math.floor(budget / 512) * 512)
  if (lowered >= requestedMb) return { heapMb: requestedMb }
  const freeGb = (freeCommitMb / 1024).toFixed(1).replace('.', ',')
  if (budget < MIN_HEAP_MB) {
    return {
      heapMb: lowered,
      notice: `Windows hat gerade nur ${freeGb} GB Speicher frei. Minecraft startet mit ${lowered} MB, kann aber trotzdem abstürzen. `
        + 'Schließe andere Programme (Browser, Discord, weitere Spiele) und starte dann neu.',
    }
  }
  return {
    heapMb: lowered,
    notice: `Windows hat gerade nur ${freeGb} GB Speicher frei, deshalb startet Minecraft diesmal mit ${lowered} MB statt ${requestedMb} MB. `
      + 'Deine Einstellung bleibt unverändert.',
  }
}

/**
 * Free commit charge in MB (what Windows can still hand out, RAM plus page
 * file), or null when it cannot be read. Only Windows: Linux and macOS
 * overcommit, so the number would not predict a crash there.
 */
export function freeCommitMb(): Promise<number | null> {
  if (process.platform !== 'win32') return Promise.resolve(null)
  return new Promise(resolve => {
    execFile(
      'powershell.exe',
      ['-NoProfile', '-NonInteractive', '-Command', '(Get-CimInstance Win32_OperatingSystem).FreeVirtualMemory'],
      { timeout: 5000, windowsHide: true },
      (err, stdout) => {
        const kb = Number(String(stdout).trim())
        if (err || !Number.isFinite(kb) || kb <= 0) {
          logger.warn('client', 'Freier Arbeitsspeicher konnte nicht gelesen werden', err ? String(err) : stdout)
          resolve(null)
          return
        }
        resolve(Math.floor(kb / 1024))
      }
    )
  })
}
