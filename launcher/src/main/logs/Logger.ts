import fs from 'fs'
import path from 'path'
import os from 'os'

export type LogCategory = 'launcher' | 'client' | 'updater' | 'crash'
export type LogLevel = 'INFO' | 'WARN' | 'ERROR' | 'DEBUG'

const LOG_ROOT = path.join(os.homedir(), '.crystal', 'logs')
const MAX_BYTES = 2 * 1024 * 1024
const KEEP_ROTATIONS = 3

/**
 * File logger for everything the launcher itself does. Each category gets its
 * own file so a launch problem never has to be dug out of updater chatter, and
 * every entry is timestamped.
 *
 * Deliberately never throws: logging failing must not take down the operation
 * it was reporting on.
 */
class Logger {
  private debugEnabled = false

  setDebug(enabled: boolean) {
    this.debugEnabled = enabled
  }

  isDebug(): boolean {
    return this.debugEnabled
  }

  fileFor(category: LogCategory): string {
    return path.join(LOG_ROOT, `${category}.log`)
  }

  logDir(): string {
    return LOG_ROOT
  }

  info(category: LogCategory, message: string, detail?: unknown) {
    this.write(category, 'INFO', message, detail)
  }

  warn(category: LogCategory, message: string, detail?: unknown) {
    this.write(category, 'WARN', message, detail)
  }

  debug(category: LogCategory, message: string, detail?: unknown) {
    if (this.debugEnabled) this.write(category, 'DEBUG', message, detail)
  }

  /** Logs the real error — never collapse this into a generic message. */
  error(category: LogCategory, message: string, error?: unknown) {
    const detail = error instanceof Error
      ? `${error.name}: ${error.message}\n${error.stack ?? ''}`
      : error
    this.write(category, 'ERROR', message, detail)
  }

  read(category: LogCategory, maxChars = 200_000): string {
    const file = this.fileFor(category)
    if (!fs.existsSync(file)) return ''
    try {
      const content = fs.readFileSync(file, 'utf-8')
      return content.length > maxChars ? content.slice(-maxChars) : content
    } catch {
      return ''
    }
  }

  clear(category?: LogCategory): void {
    try {
      const categories: LogCategory[] = category
        ? [category]
        : ['launcher', 'client', 'updater', 'crash']

      for (const c of categories) {
        const file = this.fileFor(c)
        if (fs.existsSync(file)) fs.writeFileSync(file, '')
      }
      this.info('launcher', `Logs geleert (${category ?? 'alle'})`)
    } catch (err) {
      // Nothing sensible left to do — clearing logs must not crash the app.
    }
  }

  private write(category: LogCategory, level: LogLevel, message: string, detail?: unknown) {
    try {
      fs.mkdirSync(LOG_ROOT, { recursive: true })
      const file = this.fileFor(category)
      this.rotateIfNeeded(file)

      const time = new Date().toISOString().replace('T', ' ').slice(0, 23)
      let line = `[${time}] [${level}] ${message}\n`

      if (detail !== undefined && detail !== null) {
        const text = typeof detail === 'string' ? detail : JSON.stringify(detail, null, 2)
        line += text.split('\n').map(l => `    ${l}`).join('\n') + '\n'
      }

      fs.appendFileSync(file, line)

      // Mirror to stdout so `npm start` shows the same stream.
      if (level === 'ERROR') console.error(line.trimEnd())
      else console.log(line.trimEnd())
    } catch {
      // Swallowed on purpose: this is the logger itself failing.
    }
  }

  private rotateIfNeeded(file: string) {
    if (!fs.existsSync(file)) return
    if (fs.statSync(file).size < MAX_BYTES) return

    for (let i = KEEP_ROTATIONS - 1; i >= 1; i--) {
      const from = `${file}.${i}`
      const to = `${file}.${i + 1}`
      if (fs.existsSync(from)) fs.renameSync(from, to)
    }
    fs.renameSync(file, `${file}.1`)
  }
}

export const logger = new Logger()
