import fs from 'fs'
import path from 'path'
import os from 'os'
import { spawn } from 'child_process'
import { app } from 'electron'
import Store from 'electron-store'
import { logger } from '../logs/Logger'

// Electron/Node 18+ ships a global fetch; not covered by this tsconfig's
// ES2020-only lib, so declared locally instead of pulling in a DOM lib.
declare function fetch(url: string): Promise<{
  ok: boolean
  status: number
  arrayBuffer(): Promise<ArrayBuffer>
}>

/**
 * Detects an update that never finishes starting up and rolls back to the
 * last version that did. electron-updater/NSIS have no built-in concept of
 * "the new build is broken, put the old one back" — this fills that gap using
 * only what's available: a persisted attempt counter and a re-download of the
 * previous release's own installer.
 *
 * Flow: check() -> install() records the version being installed as pending ->
 * app restarts into the new build -> onStartupAttempt() runs before anything
 * risky, incrementing a counter -> confirmStartupSuccess() runs once the main
 * window is actually up, clearing the counter and promoting "pending" to
 * "last known good". If onStartupAttempt() ever sees a counter left over from
 * a run that never confirmed, that build never made it to a working window —
 * roll back.
 */
export class UpdateGuard {
  constructor(private store: Store) {}

  /**
   * Call once, as early as possible in startup. Returns whether this run
   * should abort into a rollback instead of starting normally.
   */
  checkForBrokenUpdate(): { broken: boolean; pendingVersion?: string; lastGoodVersion?: string } {
    const pendingVersion = this.store.get('updater.pendingVersion') as string | undefined
    const attempts = (this.store.get('updater.startupAttempts') as number) || 0
    const lastGoodVersion = this.store.get('updater.lastGoodVersion') as string | undefined

    // No pending update, or this is simply the first attempt at it — not broken (yet).
    if (!pendingVersion || pendingVersion !== app.getVersion() || attempts < 1) {
      this.store.set('updater.startupAttempts', attempts + 1)
      return { broken: false }
    }

    logger.error('updater', `Update auf ${pendingVersion} ist nicht gestartet (Versuch ${attempts + 1}) — Rollback wird vorbereitet`)
    return { broken: true, pendingVersion, lastGoodVersion }
  }

  /** Call once the main window is actually up and rendering. */
  confirmStartupSuccess(): void {
    const version = app.getVersion()
    this.store.set('updater.lastGoodVersion', version)
    this.store.set('updater.startupAttempts', 0)
    if (this.store.get('updater.pendingVersion') === version) {
      this.store.delete('updater.pendingVersion')
      logger.info('updater', `Update auf ${version} bestätigt`)
    }
  }

  /** Call right before quitAndInstall() so a failed start of the new version can be detected. */
  recordPendingUpdate(version: string): void {
    this.store.set('updater.pendingVersion', version)
    this.store.set('updater.startupAttempts', 0)
  }

  private getRepoConfig(): { owner: string; repo: string } | null {
    try {
      const pkgPath = path.join(app.getAppPath(), 'package.json')
      const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf8'))
      const publish = pkg?.build?.publish
      if (!publish?.owner || !publish?.repo || publish.owner.startsWith('REPLACE_WITH_')) return null
      return { owner: publish.owner, repo: publish.repo }
    } catch (err) {
      logger.error('updater', 'package.json für Rollback-Konfiguration konnte nicht gelesen werden', err)
      return null
    }
  }

  /**
   * Downloads the previous version's own installer from GitHub Releases and
   * runs it silently, then quits — the installer puts the old build back in
   * place exactly the way it would for a normal (forward) update.
   */
  async rollback(lastGoodVersion: string | undefined): Promise<boolean> {
    // Always clear the pending flag first: whatever happens next, we must
    // never re-enter this same broken-update branch on the next launch.
    this.store.delete('updater.pendingVersion')
    this.store.set('updater.startupAttempts', 0)

    if (!lastGoodVersion) {
      logger.error('updater', 'Rollback nicht möglich — keine vorherige funktionierende Version bekannt')
      return false
    }

    // The automatic rollback reinstalls via the silent NSIS installer, which
    // only exists on Windows. Elsewhere the user gets the dialog and a normal start.
    if (process.platform !== 'win32') {
      logger.warn('updater', `Automatischer Rollback gibt es nur unter Windows (${process.platform})`)
      return false
    }

    const repoConfig = this.getRepoConfig()
    if (!repoConfig) {
      logger.error('updater', 'Rollback nicht möglich — GitHub-Repo nicht konfiguriert')
      return false
    }

    const url = `https://github.com/${repoConfig.owner}/${repoConfig.repo}/releases/download/v${lastGoodVersion}/Crystal-Launcher-Setup-${lastGoodVersion}.exe`
    const installerPath = path.join(os.tmpdir(), `crystal-rollback-${lastGoodVersion}.exe`)

    try {
      logger.info('updater', `Lade vorherige Version ${lastGoodVersion} für Rollback: ${url}`)
      const response = await fetch(url)
      if (!response.ok) {
        logger.error('updater', `Rollback-Download fehlgeschlagen: HTTP ${response.status}`)
        return false
      }
      fs.writeFileSync(installerPath, Buffer.from(await response.arrayBuffer()))

      logger.info('updater', `Starte Rollback-Installer (still): ${installerPath}`)
      // /S = silent install, same NSIS installer the forward-update path already uses.
      spawn(installerPath, ['/S'], { detached: true, stdio: 'ignore' }).unref()
      return true
    } catch (err) {
      logger.error('updater', `Rollback fehlgeschlagen`, err)
      return false
    }
  }
}
