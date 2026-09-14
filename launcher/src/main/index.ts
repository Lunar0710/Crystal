import { app, BrowserWindow, ipcMain, dialog, shell } from 'electron'
import path from 'path'
import Store from 'electron-store'
import { MinecraftManager } from './minecraft/MinecraftManager'
import { UpdateManager } from './updater/UpdateManager'
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

  setSplashStatus(`Crystal ${app.getVersion()}`, 70)

  const elapsed = Date.now() - splashShownAt
  if (elapsed < MIN_SPLASH_MS) {
    await new Promise(resolve => setTimeout(resolve, MIN_SPLASH_MS - elapsed))
  }

  setSplashStatus('Crystal wird gestartet...', 100)
  createMainWindow()

  // Updates are checked *after* the window is up and reported as a dismissible
  // banner — nothing blocks getting into the launcher.
  const updater = new UpdateManager(store)
  // Sent only once the page has loaded and subscribed: a result that arrived
  // before the renderer was listening used to vanish, so the banner never
  // appeared even though an update was available.
  const rendererReady = new Promise<void>(resolve => {
    const wc = mainWindow?.webContents
    if (!wc || !wc.isLoading()) resolve()
    else wc.once('did-finish-load', () => resolve())
  })
  Promise.all([updater.check(), rendererReady])
    .then(([info]) => {
      if (info.available) mainWindow?.webContents.send('update:available', info)
    })
    .catch(err => logger.warn('updater', 'Update-Prüfung beim Start fehlgeschlagen', String(err)))

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
