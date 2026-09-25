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

  /** Folder size, read asynchronously: big worlds would otherwise freeze the launcher. */
  private static async sizeMb(dir: string): Promise<number> {
    let bytes = 0
    const walk = async (d: string) => {
      for (const entry of await fs.promises.readdir(d, { withFileTypes: true })) {
        const full = path.join(d, entry.name)
        if (entry.isDirectory()) await walk(full)
        else try { bytes += (await fs.promises.stat(full)).size } catch { /* vanished */ }
      }
    }
    try { await walk(dir) } catch { /* unreadable */ }
    return Math.round(bytes / (1024 * 1024))
  }

  /** When the world was last saved, and its backups' stamps, newest first; cheap, no sizes. */
  private quick(d: { saves: string; backups: string }, name: string): { lastPlayed: number; stamps: { stamp: string; at: number }[] } {
    const backupDir = path.join(d.backups, name)
    const stamps = fs.existsSync(backupDir)
      ? fs.readdirSync(backupDir).sort().reverse().map(stamp => ({ stamp, at: fs.statSync(path.join(backupDir, stamp)).mtimeMs }))
      : []
    return { lastPlayed: fs.statSync(path.join(d.saves, name, 'level.dat')).mtimeMs, stamps }
  }

  private static stamp(at = new Date()): string {
    const p = (n: number) => String(n).padStart(2, '0')
    return `${at.getFullYear()}-${p(at.getMonth() + 1)}-${p(at.getDate())}_${p(at.getHours())}-${p(at.getMinutes())}-${p(at.getSeconds())}`
  }

  private worldNames(d: { saves: string }): string[] {
    if (!fs.existsSync(d.saves)) return []
    return fs.readdirSync(d.saves, { withFileTypes: true })
      .filter(e => e.isDirectory() && fs.existsSync(path.join(d.saves, e.name, 'level.dat')))
      .map(e => e.name)
  }

  async list(instanceId: string): Promise<WorldInfo[]> {
    const d = this.dirs(instanceId)
    if (!d) return []
    const worlds: WorldInfo[] = []
    for (const name of this.worldNames(d)) {
      const { lastPlayed, stamps } = this.quick(d, name)
      const backups = []
      for (const b of stamps) backups.push({ ...b, sizeMb: await WorldBackups.sizeMb(path.join(d.backups, name, b.stamp)) })
      worlds.push({ name, sizeMb: await WorldBackups.sizeMb(path.join(d.saves, name)), lastPlayed, backups })
    }
    return worlds.sort((a, b) => b.lastPlayed - a.lastPlayed)
  }

  /** {@code prune}: drop the oldest stamps beyond KEEP afterwards (not for a restore's safety copy, see restore). */
  async backup(instanceId: string, world: string, prune = true): Promise<{ ok: boolean; message: string }> {
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
    if (prune) this.prune(path.join(d.backups, world))
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
      // No pruning here: with KEEP backups already there it would delete the
      // oldest one, which may be the very one being restored.
      const safety = await this.backup(instanceId, world, false)
      if (!safety.ok) return { ok: false, message: 'Der jetzige Stand ließ sich nicht sichern, deshalb wurde nichts ersetzt.' }
    }
    // Copied next to the world first and swapped in by renaming, so the world
    // in saves is never gone while the copy runs or if it fails.
    const incoming = `${current}.restoring`
    const outgoing = `${current}.replaced`
    try {
      await fs.promises.rm(incoming, { recursive: true, force: true })
      await fs.promises.cp(backup, incoming, { recursive: true, preserveTimestamps: true })
      if (fs.existsSync(current)) await fs.promises.rename(current, outgoing)
      await fs.promises.rename(incoming, current)
      await fs.promises.rm(outgoing, { recursive: true, force: true })
    } catch (err) {
      // Put the world back if it had already been moved aside.
      if (!fs.existsSync(current) && fs.existsSync(outgoing)) await fs.promises.rename(outgoing, current).catch(() => {})
      await fs.promises.rm(incoming, { recursive: true, force: true }).catch(() => {})
      logger.warn('launcher', `Wiederherstellen von ${world} fehlgeschlagen`, String(err))
      return { ok: false, message: 'Wiederherstellen fehlgeschlagen, die Welt ist unverändert. Der jetzige Stand liegt zusätzlich als neuestes Backup bereit.' }
    }
    logger.info('launcher', `Welt ${world} auf ${stamp} zurückgesetzt`)
    return { ok: true, message: `„${world}“ ist auf den Stand vom ${stamp.replace('_', ' um ').replace(/-(\d\d)-(\d\d)$/, ':$1:$2')} zurückgesetzt.` }
  }

  /** Before a start: backs up every world played since its newest backup, up to AUTO_LIMIT_MB each. */
  async autoBackup(instanceId: string): Promise<number> {
    let done = 0
    const d = this.dirs(instanceId)
    if (!d) return 0
    for (const name of this.worldNames(d)) {
      const { lastPlayed, stamps } = this.quick(d, name)
      if (lastPlayed <= (stamps[0]?.at ?? 0)) continue
      if (await WorldBackups.sizeMb(path.join(d.saves, name)) > AUTO_LIMIT_MB) continue
      if ((await this.backup(instanceId, name)).ok) done++
    }
    return done
  }
}
