import fs from 'fs'
import path from 'path'
import Store from 'electron-store'
import { InstanceManager } from './InstanceManager'
import { MinecraftManager, LaunchOptions } from './MinecraftManager'
import { AuthManager } from '../auth/AuthManager'
import { logger } from '../logs/Logger'

interface Snapshot {
  instanceId: string
  useCrystalClient: boolean
  /** Mod filenames present before the attempt, so anything added can be undone. */
  modsBefore: string[]
  takenAt: number
}

export interface TryResult {
  success: boolean
  reverted: boolean
  message: string
}

/**
 * One-shot "does Nexora work here?" launch.
 *
 * Takes a snapshot first, gives Nexora exactly one attempt, and puts the
 * instance back exactly how it was if the game dies on startup. Never retries
 * on its own — a failed attempt ends in a revert, not another launch.
 */
export class TryCrystalService {
  constructor(
    private store: Store,
    private instances: InstanceManager,
    private minecraft: MinecraftManager,
    private auth: AuthManager
  ) {}

  private snapshotKey(instanceId: string) {
    return `tryCrystal.snapshot.${instanceId}`
  }

  private modsDir(gameDir: string) {
    return path.join(gameDir, 'mods')
  }

  private listMods(gameDir: string): string[] {
    const dir = this.modsDir(gameDir)
    if (!fs.existsSync(dir)) return []
    return fs.readdirSync(dir)
  }

  async run(
    instanceId: string,
    emit: (event: string, data: unknown) => void
  ): Promise<TryResult> {
    const instance = this.instances.get(instanceId)
    if (!instance) {
      const message = 'Instanz nicht gefunden.'
      logger.error('client', `Try with Nexora abgebrochen: ${message}`)
      return { success: false, reverted: false, message }
    }

    // Without a profile the launch would fail for an unrelated reason and get
    // blamed on Nexora, so bail out before touching anything.
    // Fresh token, same as a normal launch: a stale one breaks multiplayer joins.
    const { profile, error: sessionError } = await this.auth.ensureFreshProfile()
    if (!profile) {
      const message = sessionError || 'Nicht angemeldet. Melde dich zuerst an.'
      logger.warn('client', `Try with Nexora abgebrochen: ${message}`)
      return { success: false, reverted: false, message }
    }

    const snapshot: Snapshot = {
      instanceId,
      useCrystalClient: instance.useCrystalClient,
      modsBefore: this.listMods(instance.gameDir),
      takenAt: Date.now(),
    }
    this.store.set(this.snapshotKey(instanceId), snapshot)

    logger.info('client', `Try with Nexora: Snapshot für "${instance.name}" erstellt`, {
      useCrystalClient: snapshot.useCrystalClient,
      mods: snapshot.modsBefore.length,
    })

    emit('tryCrystal:status', { state: 'launching', instance: instance.name })

    // Nexora's mod is Fabric-only, so the attempt always runs on Fabric.
    const opts: LaunchOptions = {
      version: instance.version,
      instanceId,
      gameDir: instance.gameDir,
      username: profile.username,
      profile,
      maxRam: (this.store.get('maxRam') as number) || 4096,
      loader: 'fabric',
      injectCrystal: true,
    }

    let launched = false
    try {
      launched = await this.minecraft.launch(opts, emit)
    } catch (err) {
      logger.error('client', 'Try with Nexora: Start warf eine Exception', err)
      launched = false
    }

    if (launched) {
      this.instances.update(instanceId, { useCrystalClient: true, loader: 'fabric' })
      this.store.delete(this.snapshotKey(instanceId))

      logger.info('client', `Try with Nexora erfolgreich — "${instance.name}" bleibt auf Nexora`)
      emit('tryCrystal:status', { state: 'success', instance: instance.name })

      return {
        success: true,
        reverted: false,
        message: 'Nexora läuft. Die Instanz bleibt auf Nexora Client.',
      }
    }

    const restored = this.revert(instanceId, snapshot)
    emit('tryCrystal:status', { state: 'reverted', instance: instance.name })

    return {
      success: false,
      reverted: restored,
      message: 'Nexora konnte nicht gestartet werden. Deine vorherige Konfiguration wurde wiederhergestellt.',
    }
  }

  /** Puts the instance back to exactly the snapshot state. */
  private revert(instanceId: string, snapshot: Snapshot): boolean {
    const instance = this.instances.get(instanceId)
    if (!instance) return false

    try {
      // Remove only files the attempt added — never touch the user's own mods.
      const before = new Set(snapshot.modsBefore)
      const dir = this.modsDir(instance.gameDir)

      if (fs.existsSync(dir)) {
        for (const file of fs.readdirSync(dir)) {
          if (!before.has(file)) {
            fs.unlinkSync(path.join(dir, file))
            logger.info('client', `Try with Nexora: "${file}" wieder entfernt`)
          }
        }
      }

      this.instances.update(instanceId, { useCrystalClient: snapshot.useCrystalClient })
      this.store.delete(this.snapshotKey(instanceId))

      logger.info('client', `Try with Nexora: "${instance.name}" auf vorherigen Stand zurückgesetzt`, {
        useCrystalClient: snapshot.useCrystalClient,
      })
      return true
    } catch (err) {
      logger.error('client', 'Try with Nexora: Wiederherstellung fehlgeschlagen', err)
      return false
    }
  }

  /**
   * Recovers from a launcher crash mid-attempt: any snapshot still on disk at
   * startup means the previous attempt never finished, so undo it.
   */
  recoverInterrupted(): void {
    for (const instance of this.instances.list()) {
      const snapshot = this.store.get(this.snapshotKey(instance.id)) as Snapshot | undefined
      if (!snapshot) continue

      logger.warn('client', `Unterbrochener Try-with-Nexora-Versuch für "${instance.name}" gefunden — wird zurückgesetzt`)
      this.revert(instance.id, snapshot)
    }
  }
}
