import { autoUpdater, UpdateDownloadedEvent } from 'electron-updater'
import { app, shell } from 'electron'
import Store from 'electron-store'
import { logger } from '../logs/Logger'
import { UpdateGuard } from './UpdateGuard'

/** True when version a (like "1.1.8") is higher than b; a newer local build is never offered a "downgrade". */
function isNewer(a: string, b: string): boolean {
  const pa = a.split(/[.-]/).map(n => parseInt(n, 10) || 0)
  const pb = b.split(/[.-]/).map(n => parseInt(n, 10) || 0)
  for (let i = 0; i < 3; i++) {
    if ((pa[i] ?? 0) !== (pb[i] ?? 0)) return (pa[i] ?? 0) > (pb[i] ?? 0)
  }
  return false
}

export interface UpdateInfo {
  available: boolean
  version?: string
}

// Update delivery runs on electron-updater's GitHub provider (see the
// "publish" block in package.json) — once REPLACE_WITH_GITHUB_OWNER/REPLACE_WITH_GITHUB_REPO
// point at a real repo and a release is published there, this becomes a real,
// working auto-updater with zero custom backend needed.
//
// Pipeline: Download -> Verify -> Backup -> Install -> Verify -> Start, with a
// rollback if the new build never makes it to a working window. The
// "Download/Verify" and "Install" steps are electron-updater's own job (it
// refuses to fire update-downloaded on a hash/signature mismatch); the
// "Backup"/"Verify after start"/"Rollback" steps are UpdateGuard's, since
// electron-updater has no concept of "this build turned out to be broken".
export class UpdateManager {
  private store: Store
  private guard: UpdateGuard

  constructor(store: Store) {
    this.store = store
    this.guard = new UpdateGuard(store)
    autoUpdater.autoDownload = false
    autoUpdater.autoInstallOnAppQuit = false
  }

  async check(): Promise<UpdateInfo> {
    try {
      const result = await autoUpdater.checkForUpdates()
      const remoteVersion = result?.updateInfo?.version
      const current = app.getVersion()
      if (remoteVersion) this.store.set('updater.candidateVersion', remoteVersion)
      return { available: !!remoteVersion && isNewer(remoteVersion, current), version: remoteVersion || current }
    } catch (err) {
      // No repo configured yet, or offline — not fatal, but still recorded.
      logger.warn('updater', 'Update-Pruefung fehlgeschlagen', String(err))
      return { available: false, version: app.getVersion() }
    }
  }

  async install(emit: (event: string, data: unknown) => void): Promise<boolean> {
    // macOS only installs updates into apps signed with an Apple Developer ID,
    // which Nexora isn't. Rather than fail midway, send the user to the release
    // page to download the new .dmg themselves.
    if (process.platform === 'darwin') {
      const version = this.store.get('updater.candidateVersion') as string | undefined
      await shell.openExternal(version
        ? `https://github.com/Lunar0710/Nexora/releases/tag/v${version}`
        : 'https://github.com/Lunar0710/Nexora/releases/latest')
      emit('update:error', 'Auf dem Mac wird das Update von Hand installiert. Die Download-Seite ist jetzt offen.')
      return false
    }

    // A retry after a failed download must not stack a second set of listeners
    // on top of the first; each event would then fire twice.
    autoUpdater.removeAllListeners('download-progress')
    autoUpdater.removeAllListeners('update-downloaded')
    autoUpdater.removeAllListeners('error')

    return new Promise(resolve => {
      autoUpdater.on('download-progress', p => {
        emit('update:progress', { step: 'Downloading update...', percent: Math.round(p.percent) })
      })

      // electron-updater only fires this once the download's hash/signature has
      // already been verified against the published release metadata — it
      // emits 'error' instead if that check fails, so reaching this handler at
      // all *is* the "Verify" step for the downloaded package.
      autoUpdater.on('update-downloaded', (info: UpdateDownloadedEvent) => {
        logger.info('updater', `Update ${info.version} heruntergeladen und verifiziert`)
        emit('update:progress', { step: 'Verified — installing...', percent: 95 })

        // "Backup": record what's currently running as the fallback before
        // committing to the restart, so a build that never starts can be
        // rolled back to it automatically.
        this.guard.recordPendingUpdate(info.version)

        emit('update:progress', { step: 'Update ready — restarting...', percent: 100 })
        // Silent: an update installs over the existing install without showing the
        // setup wizard again (that only appears for the very first install), then
        // starts Nexora straight back up.
        autoUpdater.quitAndInstall(true, true)
        resolve(true)
      })
      autoUpdater.on('error', err => {
        logger.error('updater', 'Update-Download/Verifizierung fehlgeschlagen', err)
        emit('update:error', err?.message || 'Update failed')
        resolve(false)
      })
      // Failures also arrive through the 'error' event above; this only keeps the
      // rejected promise from being reported as unhandled.
      autoUpdater.downloadUpdate().catch(() => {})
    })
  }
}
