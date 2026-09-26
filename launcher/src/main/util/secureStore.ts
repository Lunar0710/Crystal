import fs from 'fs'
import path from 'path'
import { safeStorage } from 'electron'
import { logger } from '../logs/Logger'

/**
 * Account secrets (Microsoft refresh tokens, Minecraft access tokens) are
 * never kept as plain text. They are sealed with the system's own key store:
 * DPAPI on Windows, the Keychain on macOS, libsecret on Linux. A copy of the
 * launcher's config file taken by a token stealer is then useless on any other
 * PC or user account, which is how launcher accounts got stolen in bulk before
 * (Lunar Client, 2023).
 */
const PREFIX = 'nexora-sealed:v1:'

export function canSeal(): boolean {
  try { return safeStorage.isEncryptionAvailable() } catch { return false }
}

export function isSealed(raw: unknown): boolean {
  return typeof raw === 'string' && raw.startsWith(PREFIX)
}

/** The value sealed for storage; unchanged where the system offers no key store. */
export function seal(value: unknown): unknown {
  if (value === undefined || value === null || !canSeal()) return value
  return PREFIX + safeStorage.encryptString(JSON.stringify(value)).toString('base64')
}

/** A stored value opened again; undefined when it can't be (another PC, another user). */
export function unseal<T>(raw: unknown): T | undefined {
  if (!isSealed(raw)) return raw as T | undefined
  try {
    return JSON.parse(safeStorage.decryptString(Buffer.from((raw as string).slice(PREFIX.length), 'base64'))) as T
  } catch {
    logger.warn('launcher', 'Gespeicherte Anmeldung ließ sich nicht entschlüsseln (anderer PC oder Benutzer?). Bitte neu anmelden.')
    return undefined
  }
}

// ------------------------------------------------------------ tokens in files

/** Minecraft access tokens (JWTs) and the --accessToken argument, wherever they appear in text. */
const TOKEN_PATTERNS: RegExp[] = [
  /(--accessToken[\s=]+)[^\s"]+/g,
  /("accessToken"\s*:\s*")[^"]+/g,
  /\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}/g,
]

export function scrubText(text: string): string {
  let out = text
  out = out.replace(TOKEN_PATTERNS[0], '$1[entfernt]')
  out = out.replace(TOKEN_PATTERNS[1], '$1[entfernt]')
  out = out.replace(TOKEN_PATTERNS[2], '[Token entfernt]')
  return out
}

/**
 * Takes access tokens out of the files a game leaves behind: Java crash
 * reports (hs_err_pid*.log, which copy the whole command line), Minecraft's
 * crash reports and the launch log. Anyone who reads or is sent these files
 * (a crash-log paste, a stealer) would otherwise get a working login.
 */
export function scrubGameDir(gameDir: string): number {
  let cleaned = 0
  const candidates: string[] = []
  const add = (dir: string, match: (f: string) => boolean) => {
    try {
      for (const f of fs.readdirSync(dir)) if (match(f)) candidates.push(path.join(dir, f))
    } catch { /* folder missing */ }
  }
  add(gameDir, f => /^hs_err_pid\d+\.log$/.test(f))
  add(path.join(gameDir, 'crash-reports'), f => f.endsWith('.txt'))
  add(path.join(gameDir, 'logs'), f => f === 'latest.log' || f === 'debug.log')
  for (const file of candidates) {
    try {
      if (fs.statSync(file).size > 64 * 1024 * 1024) continue
      const text = fs.readFileSync(file, 'utf8')
      const clean = scrubText(text)
      if (clean !== text) { fs.writeFileSync(file, clean); cleaned++ }
    } catch { /* in use or unreadable: next time */ }
  }
  if (cleaned) logger.info('launcher', `Zugangs-Token aus ${cleaned} Datei(en) in ${path.basename(gameDir)} entfernt`)
  return cleaned
}
