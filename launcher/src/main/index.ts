import { app, BrowserWindow, ipcMain, dialog, shell } from 'electron'
import path from 'path'
import Store from 'electron-store'
import { MinecraftManager } from './minecraft/MinecraftManager'
import { UpdateManager, type UpdateInfo } from './updater/UpdateManager'
import { UpdateGuard } from './updater/UpdateGuard'
import { registerIpcHandlers } from './ipc'
import { syncThemeToClient } from './theme/ThemeSync'
import { BrandingManager } from './branding/BrandingManager'
import { logger } from './logs/Logger'
import { setCrystalRoot } from './paths'

const store = new Store()
// Applied before anything touches the data folder (logs are written from the first lines below).
setCrystalRoot(store.get('dataRoot') as string | undefined)
const isDev = process.env.NODE_ENV === 'development'

let mainWindow: BrowserWindow | null = null
let splashWindow: BrowserWindow | null = null

function createSplash(): BrowserWindow {
  const splashPath = app.isPackaged
    ? path.join(process.resourcesPath, 'splash.html')
    : path.join(__dirname, '../../src/main/splash.html')

  const win = new BrowserWindow({
    width: 340,
    height: 300,
    frame: false,
    resizable: false,
    transparent: false,
    backgroundColor: '#0d0f14',
    show: true,
    webPreferences: {
      nodeIntegration: true,
      contextIsolation: false,
    },
  })
  win.loadFile(splashPath)
  return win
}

function setSplashStatus(text: string, percent?: number) {
  splashWindow?.webContents.send('splash:status', { text, percent })
}

function createMainWindow() {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 800,
    minWidth: 1100,
    minHeight: 700,
    // macOS keeps its native traffic lights, inset into our own title bar;
    // Windows and Linux get a frameless window with the custom caption buttons.
    ...(process.platform === 'darwin'
      ? { titleBarStyle: 'hiddenInset' as const, trafficLightPosition: { x: 14, y: 11 } }
      : { frame: false }),
    transparent: false,
    backgroundColor: '#0d0f14',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      nodeIntegration: false,
      contextIsolation: true,
    },
    icon: path.join(__dirname, '../../src/renderer/assets/icons/crystal.png'),
    show: false,
  })

  // The window only ever shows the launcher's own page. A link dropped onto it
  // or a stray window.open would otherwise load a foreign site with the
  // preload bridge (window.crystal) attached.
  mainWindow.webContents.setWindowOpenHandler(() => ({ action: 'deny' }))
  mainWindow.webContents.on('will-navigate', (event, url) => {
    const ownPage = isDev ? url.startsWith('http://localhost:5173') : url.startsWith('file://')
    if (!ownPage) event.preventDefault()
  })

  if (isDev) {
    mainWindow.loadURL('http://localhost:5173')
    mainWindow.webContents.openDevTools()
  } else {
    mainWindow.loadFile(path.join(__dirname, '../renderer/index.html'))
  }

  mainWindow.once('ready-to-show', () => {
    splashWindow?.close()
    splashWindow = null
    new BrandingManager(store).apply(mainWindow)
    mainWindow?.show()
    // Made it to a real, rendering window — this build is confirmed good.
    new UpdateGuard(store).confirmStartupSuccess()
  })

  mainWindow.on('closed', () => {
    mainWindow = null
  })
}

// The update check usually resolves in a few hundred milliseconds (and fails
// instantly when no release channel is configured), which would make the splash
// flash by unseen. Hold it long enough to actually read.
const MIN_SPLASH_MS = 2600

// Anything that escapes a handler still ends up on disk instead of vanishing.
process.on('uncaughtException', err => logger.error('launcher', 'Uncaught exception', err))
process.on('unhandledRejection', reason => logger.error('launcher', 'Unhandled rejection', reason))

app.whenReady().then(async () => {
  logger.info('launcher', `Crystal Launcher ${app.getVersion()} gestartet`, {
    electron: process.versions.electron,
    node: process.versions.node,
    platform: process.platform,
    packaged: app.isPackaged,
  })

  // Before anything else: was this build installed by an update that never
  // made it to a working window last time? If so, this run doesn't get to try
  // again — put the last known-good version back instead.
  const updateGuard = new UpdateGuard(store)
  const updateCheck = updateGuard.checkForBrokenUpdate()
  if (updateCheck.broken) {
    dialog.showMessageBoxSync({
      type: 'error',
      title: 'Crystal Update fehlgeschlagen',
      message: `Das Update auf Version ${updateCheck.pendingVersion} konnte nicht gestartet werden.`,
      detail: updateCheck.lastGoodVersion
        ? `Version ${updateCheck.lastGoodVersion} wird jetzt wiederhergestellt. Crystal startet danach neu.`
        : 'Es ist keine vorherige funktionierende Version bekannt. Lade Crystal bitte neu herunter.',
    })
    const rolledBack = await updateGuard.rollback(updateCheck.lastGoodVersion)
    if (rolledBack) {
      app.quit()
      return
    }
    // Rollback itself failed (offline, no repo configured, …) — fall through
    // and let the user use whatever is currently installed rather than
    // leaving them with nothing.
    logger.error('launcher', 'Rollback fehlgeschlagen — Start wird trotzdem versucht')
  }

  const splashShownAt = Date.now()
  splashWindow = createSplash()

  registerIpcHandlers(store)
  syncThemeToClient((store.get('theme') as string) || 'crystal-blue')

  setSplashStatus('Suche nach Updates...', 40)

  // The update check runs while the splash is up. When an update exists the
  // splash stays open and asks: install now (downloads, installs silently and
  // restarts) or later (the launcher opens and shows the update banner).
  const updater = new UpdateManager(store)
  const checkTimeout = new Promise<UpdateInfo>(resolve =>
    setTimeout(() => resolve({ available: false, version: app.getVersion() }), 6000))
  const updateInfo = await Promise.race([updater.check(), checkTimeout])

  const elapsed = Date.now() - splashShownAt
  if (elapsed < MIN_SPLASH_MS) {
    await new Promise(resolve => setTimeout(resolve, MIN_SPLASH_MS - elapsed))
  }

  if (updateInfo.available && splashWindow) {
    splashWindow.webContents.send('splash:update', {
      current: app.getVersion(),
      version: updateInfo.version,
      manual: process.platform === 'darwin',
    })
    const choice = await new Promise<string>(resolve => {
      ipcMain.once('splash:choice', (_e, value: string) => resolve(value))
      splashWindow?.once('closed', () => resolve('later'))
    })
    if (choice === 'update') {
      const installed = await updater.install((event, data) => {
        if (event === 'update:progress') {
          const p = data as { step: string; percent: number }
          setSplashStatus(p.percent >= 95 ? 'Update wird installiert...' : `Update wird geladen... ${p.percent}%`, p.percent)
        } else if (event === 'update:error') {
          setSplashStatus(String(data), 100)
        }
      })
      // On success the app is already quitting into the installer.
      if (installed) return
      await new Promise(resolve => setTimeout(resolve, 2500))
    }
  }

  setSplashStatus('Crystal wird gestartet...', 100)
  createMainWindow()

  // A skipped update still shows as the dismissible banner inside the launcher.
  // Sent only once the page has loaded and subscribed: a result that arrived
  // before the renderer was listening used to vanish.
  const rendererReady = new Promise<void>(resolve => {
    const wc = mainWindow?.webContents
    if (!wc || !wc.isLoading()) resolve()
    else wc.once('did-finish-load', () => resolve())
  })
  rendererReady.then(() => {
    if (updateInfo.available) mainWindow?.webContents.send('update:available', updateInfo)
  })

  ipcMain.handle('update:downloadAndRestart', async () => {
    return updater.install((event, data) => mainWindow?.webContents.send(event, data))
  })

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createMainWindow()
  })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})
