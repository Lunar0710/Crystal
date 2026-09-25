import fs from 'fs'
import path from 'path'
import { isPlainFileName } from '../paths'
import { logger } from '../logs/Logger'

export interface WorldInfo {
  name: string
  sizeMb: number
  lastPlayed: number
  backups: { stamp: string; at: number; sizeMb: number }[]
}

/** Backups kept per world; the oldest goes when a new one would exceed this. */
const KEEP = 5
/** Worlds bigger than this are left out of the automatic backup before a start (it would hold up the launch). */
const AUTO_LIMIT_MB = 1024

/**
 * Copies of an instance's singleplayer worlds, in <instance>/backups/<world>/<stamp>.
 * Plain folder copies rather than zips: nothing to unpack on restore, and
 * the launcher ships no zip writer.
 */
export class WorldBackups {
  constructor(private gameDir: (instanceId: string) => string | null) {}

  private dirs(instanceId: string) {
    const root = this.gameDir(instanceId)
    if (!root) return null
    return { saves: path.join(root, 'saves'), backups: path.join(root, 'backups') }
  }

  private static sizeMb(dir: string): number {
    let bytes = 0
    const walk = (d: string) => {
      for (const entry of fs.readdirSync(d, { withFileTypes: true })) {
        const full = path.join(d, entry.name)
        if (entry.isDirectory()) walk(full)
        else try { bytes += fs.statSync(full).size } catch { /* vanished */ }
      }
    }
    try { walk(dir) } catch { /* unreadable */ }
    return Math.round(bytes / (1024 * 1024))
  }

  private static stamp(at = new Date()): string {
    const p = (n: number) => String(n).padStart(2, '0')
    return `${at.getFullYear()}-${p(at.getMonth() + 1)}-${p(at.getDate())}_${p(at.getHours())}-${p(at.getMinutes())}-${p(at.getSeconds())}`
  }

  list(instanceId: string): WorldInfo[] {
    const d = this.dirs(instanceId)
    if (!d || !fs.existsSync(d.saves)) return []
    return fs.readdirSync(d.saves, { withFileTypes: true })
      .filter(e => e.isDirectory() && fs.existsSync(path.join(d.saves, e.name, 'level.dat')))
      .map(e => {
        const world = path.join(d.saves, e.name)
        const backupDir = path.join(d.backups, e.name)
        const backups = fs.existsSync(backupDir)
          ? fs.readdirSync(backupDir).sort().reverse().map(stamp => {
              const full = path.join(backupDir, stamp)
              return { stamp, at: fs.statSync(full).mtimeMs, sizeMb: WorldBackups.sizeMb(full) }
            })
          : []
        return { name: e.name, sizeMb: WorldBackups.sizeMb(world), lastPlayed: fs.statSync(path.join(world, 'level.dat')).mtimeMs, backups }
      })
      .sort((a, b) => b.lastPlayed - a.lastPlayed)
  }

  async backup(instanceId: string, world: string): Promise<{ ok: boolean; message: string }> {
    const d = this.dirs(instanceId)
    if (!d || !isPlainFileName(world)) return { ok: false, message: 'Welt nicht gefunden.' }
    const source = path.join(d.saves, world)
    if (!fs.existsSync(path.join(source, 'level.dat'))) return { ok: false, message: 'Welt nicht gefunden.' }
    const target = path.join(d.backups, world, WorldBackups.stamp())
    try {
      fs.mkdirSync(path.dirname(target), { recursive: true })
      // session.lock is held by a running game and means nothing in a copy.
      await fs.promises.cp(source, target, { recursive: true, filter: src => path.basename(src) !== 'session.lock' })
    } catch (err) {
      logger.warn('launcher', `Backup von ${world} fehlgeschlagen`, String(err))
      return { ok: false, message: 'Das Backup ist fehlgeschlagen. Ist genug Speicherplatz frei?' }
    }
    this.prune(path.join(d.backups, world))
    logger.info('launcher', `Welt ${world} gesichert`)
    return { ok: true, message: `„${world}“ ist gesichert.` }
  }

  private prune(worldBackups: string) {
    const all = fs.readdirSync(worldBackups).sort()
    for (const old of all.slice(0, Math.max(0, all.length - KEEP))) {
      fs.rmSync(path.join(worldBackups, old), { recursive: true, force: true })
    }
  }

  /**
   * Puts a backup back. The world as it is now is backed up first, so a
   * restore can always be undone with the next-newest backup.
   */
  async restore(instanceId: string, world: string, stamp: string): Promise<{ ok: boolean; message: string }> {
    const d = this.dirs(instanceId)
    if (!d || !isPlainFileName(world) || !isPlainFileName(stamp)) return { ok: false, message: 'Backup nicht gefunden.' }
    const backup = path.join(d.backups, world, stamp)
    if (!fs.existsSync(path.join(backup, 'level.dat'))) return { ok: false, message: 'Backup nicht gefunden.' }
    const current = path.join(d.saves, world)
    if (fs.existsSync(path.join(current, 'level.dat'))) {
      const safety = await this.backup(instanceId, world)
      if (!safety.ok) return { ok: false, message: 'Der jetzige Stand ließ sich nicht sichern, deshalb wurde nichts ersetzt.' }
    }
    try {
      await fs.promises.rm(current, { recursive: true, force: true })
      await fs.promises.cp(backup, current, { recursive: true })
    } catch (err) {
      logger.warn('launcher', `Wiederherstellen von ${world} fehlgeschlagen`, String(err))
      return { ok: false, message: 'Wiederherstellen fehlgeschlagen. Der Stand davor liegt als neuestes Backup bereit.' }
    }
    logger.info('launcher', `Welt ${world} auf ${stamp} zurückgesetzt`)
    return { ok: true, message: `„${world}“ ist auf den Stand vom ${stamp.replace('_', ' um ').replace(/-(\d\d)-(\d\d)$/, ':$1:$2')} zurückgesetzt.` }
  }

  /** Before a start: backs up every world played since its newest backup, up to AUTO_LIMIT_MB each. */
  async autoBackup(instanceId: string): Promise<number> {
    let done = 0
    for (const world of this.list(instanceId)) {
      const newest = world.backups[0]?.at ?? 0
      if (world.lastPlayed <= newest || world.sizeMb > AUTO_LIMIT_MB) continue
      if ((await this.backup(instanceId, world.name)).ok) done++
    }
    return done
  }
}
