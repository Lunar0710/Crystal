import { ipcMain, BrowserWindow, dialog, shell } from 'electron'
import path from 'path'
import fs from 'fs'
import os from 'os'
import Store from 'electron-store'
import { MinecraftManager } from './minecraft/MinecraftManager'
import { InstanceManager } from './minecraft/InstanceManager'
import { ContentManager, ContentType } from './minecraft/ContentManager'
import { UpdateManager } from './updater/UpdateManager'
import { AuthManager } from './auth/AuthManager'
import { syncThemeToClient } from './theme/ThemeSync'
import { ExternalClientManager } from './minecraft/ExternalClientManager'
import { BrandingManager } from './branding/BrandingManager'
import { ModrinthService } from './minecraft/ModrinthService'
import { CapeManager } from './cosmetics/CapeManager'
import { SkinService } from './cosmetics/SkinService'
import { LogManager, setInstanceDirResolver, instanceDir } from './logs/LogManager'
import { ClaudeService } from './claude/ClaudeService'
import { FriendManager } from './friends/FriendManager'
import { logger, LogCategory } from './logs/Logger'
import { TryCrystalService } from './minecraft/TryCrystalService'
import { CustomClientInstaller } from './minecraft/CustomClientInstaller'

export function registerIpcHandlers(store: Store) {
  const minecraft = new MinecraftManager(store)
  const instances = new InstanceManager(store)
  const content = new ContentManager(instances)
  const updater = new UpdateManager(store)
  const auth = new AuthManager(store)
  const externalClients = new ExternalClientManager(store)
  const branding = new BrandingManager(store)
  const modrinth = new ModrinthService(instances)
  const capes = new CapeManager(store)
  const skins = new SkinService()
  const logs = new LogManager()
  setInstanceDirResolver(id => instances.get(id)?.gameDir ?? null)
  const claude = new ClaudeService(store)
  const friends = new FriendManager(store)
  const tryCrystal = new TryCrystalService(store, instances, minecraft, auth)
  const clientInstaller = new CustomClientInstaller(instances)

  // A snapshot left over from a previous run means that attempt never finished.
  tryCrystal.recoverInterrupted()

  // Window
  ipcMain.handle('window:minimize', () => BrowserWindow.getFocusedWindow()?.minimize())
  ipcMain.handle('window:maximize', () => {
    const win = BrowserWindow.getFocusedWindow()
    win?.isMaximized() ? win.unmaximize() : win?.maximize()
  })
  ipcMain.handle('window:close', () => BrowserWindow.getFocusedWindow()?.close())

  // Settings
  ipcMain.handle('settings:get', (_e, key: string) => store.get(key))
  ipcMain.handle('settings:set', (_e, key: string, value: unknown) => {
    store.set(key, value)
    if (key === 'theme' && typeof value === 'string') {
      syncThemeToClient(value, store.get('customTheme') as any)
    }
    if (key === 'customTheme' && store.get('theme') === 'custom') {
      syncThemeToClient('custom', value as any)
    }
  })

  // Auth
  ipcMain.handle('auth:loginMicrosoft', async () => {
    const win = BrowserWindow.getFocusedWindow()
    if (!win) return null
    return auth.loginMicrosoft(win)
  })
  ipcMain.handle('auth:loginOffline', (_e, username: string) => auth.loginOffline(username))
  ipcMain.handle('auth:autoLogin', () => auth.tryAutoLogin())
  ipcMain.handle('auth:getProfile', () => auth.getStoredProfile())
  ipcMain.handle('auth:logout', () => auth.logout())
  // Read-only by design: a rank is something the Crystal team grants, so the
  // renderer can look it up but never assign one to itself.
  ipcMain.handle('auth:getRank', () => auth.getRank())

  // Rank management — every handler re-checks the CALLER's own current rank
  // server-side (never trusts a flag the renderer sends), so a compromised
  // renderer can't just claim to be the owner.
  ipcMain.handle('ranks:list', () => {
    if (auth.getRank() !== 'owner') return []
    return Object.values(auth.getGrants())
  })
  ipcMain.handle('ranks:grant', (_e, username: string, rank: string) => {
    if (auth.getRank() !== 'owner') return false
    if (!username?.trim()) return false
    auth.grantRank(username.trim(), rank as any)
    return true
  })
  ipcMain.handle('ranks:revoke', (_e, username: string) => {
    if (auth.getRank() !== 'owner') return false
    auth.revokeGrant(username)
    return true
  })

  // Minecraft
  ipcMain.handle('minecraft:getVersions', () => minecraft.fetchAllVersions())
  ipcMain.handle('minecraft:launch', async (_e, opts) => {
    const win = BrowserWindow.getFocusedWindow()
    const profile = auth.getStoredProfile()

    // A launch never waits on this — an update becomes a dismissible banner
    // (see UpdateBanner.tsx), never a blocker standing between the user and Play.
    updater.check()
      .then(info => {
        if (info.available) win?.webContents.send('update:available', info)
      })
      .catch(err => logger.warn('updater', 'Update-Pruefung vor dem Start fehlgeschlagen', String(err)))

    return minecraft.launch({ ...opts, profile }, (event, data) => win?.webContents.send(event, data))
  })
  ipcMain.handle('minecraft:selectDir', async () => {
    const result = await dialog.showOpenDialog({ properties: ['openDirectory'] })
    return result.canceled ? null : result.filePaths[0]
  })

  // Instances
  ipcMain.handle('instances:list', () => instances.list())
  ipcMain.handle('instances:create', (_e, data) => instances.create(data))
  ipcMain.handle('instances:update', (_e, id: string, patch) => instances.update(id, patch))
  ipcMain.handle('instances:import', (_e, version: string) => instances.importFromDisk(version))
  ipcMain.handle('instances:delete', (_e, id: string) => instances.delete(id))

  // One-shot Crystal attempt with automatic revert on failure
  ipcMain.handle('tryCrystal:run', async (_e, instanceId: string) => {
    const win = BrowserWindow.getFocusedWindow()
    return tryCrystal.run(instanceId, (event, data) => win?.webContents.send(event, data))
  })

  // Installing a custom client jar into one specific instance
  ipcMain.handle('clientInstall:target', (_e, instanceId: string) => clientInstaller.describeTarget(instanceId))
  ipcMain.handle('clientInstall:inspect', (_e, filePath: string) => clientInstaller.inspect(filePath))
  ipcMain.handle('clientInstall:pick', (_e, instanceId: string) => clientInstaller.installFromPicker(instanceId))
  ipcMain.handle('clientInstall:install', (_e, instanceId: string, filePath: string) =>
    clientInstaller.install(instanceId, filePath))
  ipcMain.handle('clientInstall:uninstall', (_e, instanceId: string, fileName: string) =>
    clientInstaller.uninstall(instanceId, fileName))

  // Friends — local list, verified against Mojang so typos don't stick
  ipcMain.handle('friends:list', () => friends.list())
  ipcMain.handle('friends:add', (_e, username: string) => friends.add(username))
  ipcMain.handle('friends:remove', (_e, id: string) => friends.remove(id))

  // Instance content (mods / resourcepacks / shaderpacks) — read straight off disk
  ipcMain.handle('content:list', (_e, instanceId: string, type: ContentType) => content.list(instanceId, type))
  ipcMain.handle('content:installFromDisk', (_e, instanceId: string, type: ContentType) => content.installFromDisk(instanceId, type))
  ipcMain.handle('content:remove', (_e, instanceId: string, type: ContentType, fileName: string) => content.remove(instanceId, type, fileName))
  ipcMain.handle('content:toggle', (_e, instanceId: string, type: ContentType, fileName: string) => content.toggle(instanceId, type, fileName))
  ipcMain.handle('content:openFolder', (_e, instanceId: string, type: ContentType) => {
    const dir = content.contentDir(instanceId, type)
    require('fs').mkdirSync(dir, { recursive: true })
    shell.openPath(dir)
  })

  // Modrinth (search + one-click install into the right instance folder)
  ipcMain.handle('modrinth:search', (_e, query: string, gameVersion: string, loader: string, type: ContentType) =>
    modrinth.search(query, gameVersion, loader, type))
  ipcMain.handle('modrinth:install', (_e, instanceId: string, projectId: string, gameVersion: string, loader: string, type: ContentType) =>
    modrinth.install(instanceId, projectId, gameVersion, loader, type))

  // Modpack presets for Create Instance — browse finished Modrinth modpacks and install one wholesale.
  ipcMain.handle('modrinth:searchModpacks', (_e, query: string, gameVersion?: string) =>
    modrinth.searchModpacks(query, gameVersion))
  ipcMain.handle('modrinth:installModpack', (_e, instanceId: string, projectId: string) =>
    modrinth.installModpack(instanceId, projectId))
  ipcMain.handle('modrinth:pickAndInstallModpackFile', async (_e, instanceId: string) => {
    const win = BrowserWindow.getFocusedWindow()
    const result = await dialog.showOpenDialog(win!, {
      title: '.mrpack-Datei auswählen',
      filters: [{ name: 'Modrinth Modpack', extensions: ['mrpack'] }],
      properties: ['openFile'],
    })
    if (result.canceled || result.filePaths.length === 0) return null
    return modrinth.installModpackFromFile(instanceId, result.filePaths[0])
  })

  // External clients (Lunar/Badlion/custom jars or launchers)
  ipcMain.handle('externalClients:list', () => externalClients.list())
  ipcMain.handle('externalClients:add', () => externalClients.addViaFilePicker())
  ipcMain.handle('externalClients:remove', (_e, id: string) => externalClients.remove(id))
  ipcMain.handle('externalClients:rename', (_e, id: string, name: string) => externalClients.rename(id, name))
  ipcMain.handle('externalClients:launch', (_e, id: string) => externalClients.launch(id))

  // Branding / app icon — gated to staff-tier ranks (owner, co_owner, admin,
  // staff, developer). Not Crystal+, that's a perk tier, not a staff privilege.
  const STAFF_RANKS = ['owner', 'co_owner', 'admin', 'staff', 'developer']
  ipcMain.handle('branding:list', () => branding.list())
  ipcMain.handle('branding:getCurrent', () => branding.getCurrentId())
  ipcMain.handle('branding:setCurrent', (_e, id: string) => {
    if (!STAFF_RANKS.includes(auth.getRank())) return false
    const win = BrowserWindow.getFocusedWindow()
    return branding.setCurrent(win, id)
  })

  // Skin preview (Mojang lookup happens in main to dodge CORS/CSP)
  ipcMain.handle('skin:fetch', (_e, username: string) => skins.fetchSkin(username))

  // Equipped cosmetics loadout (cape + headband + aura + wings)
  ipcMain.handle('cosmetics:getLoadout', () => store.get('cosmetics.loadout', {
    cape: null, headband: null, aura: null, wings: null,
  }))
  ipcMain.handle('cosmetics:setLoadout', (_e, loadout: unknown) => store.set('cosmetics.loadout', loadout))

  // Cosmetics / capes
  ipcMain.handle('capes:listCustom', () => capes.listCustom())
  ipcMain.handle('capes:upload', () => capes.upload())
  ipcMain.handle('capes:remove', (_e, id: string) => capes.remove(id))
  ipcMain.handle('capes:getDataUrl', (_e, id: string) => capes.getCapeDataUrl(id))
  ipcMain.handle('capes:getSelected', () => capes.getSelected())
  ipcMain.handle('capes:setSelected', (_e, id: string) => capes.setSelected(id))

  // Pushes whichever cape is actually equipped (as a real PNG) to a fixed path
  // the Java client reads — capes.ts's built-in designs only ever exist as
  // canvas data URLs inside the renderer, so this is the one place they
  // become a real file the in-game mod can load.
  ipcMain.handle('cosmetics:syncCape', (_e, dataUrl: string | null) => {
    const dir = path.join(os.homedir(), '.crystal', 'cosmetics')
    fs.mkdirSync(dir, { recursive: true })
    const target = path.join(dir, 'equipped_cape.png')

    if (!dataUrl) {
      fs.rm(target, { force: true }, () => {})
      return true
    }
    const base64 = dataUrl.replace(/^data:image\/\w+;base64,/, '')
    fs.writeFileSync(target, Buffer.from(base64, 'base64'))
    return true
  })

  // Launcher's own logs (separate from per-instance game logs)
  ipcMain.handle('launcherLog:read', (_e, category: LogCategory) => logger.read(category))
  ipcMain.handle('launcherLog:clear', (_e, category?: LogCategory) => logger.clear(category))
  ipcMain.handle('launcherLog:openFolder', () => {
    require('fs').mkdirSync(logger.logDir(), { recursive: true })
    return shell.openPath(logger.logDir())
  })
  ipcMain.handle('launcherLog:setDebug', (_e, enabled: boolean) => {
    logger.setDebug(enabled)
    store.set('debugMode', enabled)
    logger.info('launcher', `Debug-Modus ${enabled ? 'aktiviert' : 'deaktiviert'}`)
  })
  ipcMain.handle('launcherLog:isDebug', () => logger.isDebug())

  // Reveal a specific instance log/crash file in the file manager
  ipcMain.handle('logs:openFolder', (_e, instanceId: string, kind: 'logs' | 'crash-reports') => {
    const dir = require('path').join(instanceDir(instanceId), kind)
    require('fs').mkdirSync(dir, { recursive: true })
    return shell.openPath(dir)
  })

  // Logs / crash reports
  ipcMain.handle('logs:list', (_e, instanceId: string) => logs.listLogs(instanceId))
  ipcMain.handle('logs:read', (_e, instanceId: string, fileName: string) => logs.readLog(instanceId, fileName))
  ipcMain.handle('crashes:list', (_e, instanceId: string) => logs.listCrashReports(instanceId))
  ipcMain.handle('crashes:read', (_e, instanceId: string, fileName: string) => logs.readCrashReport(instanceId, fileName))
  ipcMain.handle('crashes:analyze', async (_e, instanceId: string, fileName: string) => {
    const text = logs.readCrashReport(instanceId, fileName)
    return claude.analyzeCrash(text)
  })

  // Claude API key (used only for crash analysis, stored locally)
  ipcMain.handle('claude:getApiKey', () => claude.getApiKey())
  ipcMain.handle('claude:setApiKey', (_e, key: string) => claude.setApiKey(key))
  ipcMain.handle('claude:hasApiKey', () => claude.hasApiKey())

  // Updates
  ipcMain.handle('update:check', () => updater.check())
  ipcMain.handle('update:install', async (_e) => {
    const win = BrowserWindow.getFocusedWindow()
    return updater.install((event, data) => win?.webContents.send(event, data))
  })
}
