import { ipcMain, BrowserWindow, dialog, shell, nativeImage, app } from 'electron'
import path from 'path'
import fs from 'fs'
import os from 'os'
import Store from 'electron-store'
import { MinecraftManager } from './minecraft/MinecraftManager'
import { InstanceManager } from './minecraft/InstanceManager'
import { ContentManager, ContentType } from './minecraft/ContentManager'
import { UpdateManager } from './updater/UpdateManager'
import { AuthManager } from './auth/AuthManager'
import { RankSyncService } from './auth/RankSyncService'
import { DiscordPresence } from './discord/DiscordPresence'
import { syncThemeToClient, syncProfileToClient } from './theme/ThemeSync'
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
import { CrashDoctor } from './minecraft/CrashDoctor'
import { crystalPath, crystalRoot, defaultCrystalRoot, setCrystalRoot, canUseAsRoot } from './paths'

export function registerIpcHandlers(store: Store) {
  const minecraft = new MinecraftManager(store)
  const instances = new InstanceManager(store)
  const content = new ContentManager(instances)
  const updater = new UpdateManager(store)
  const auth = new AuthManager(store)
  const rankSync = new RankSyncService(store)
  const discord = new DiscordPresence(store)
  // Pull the published ranks once at startup so a rank granted elsewhere is
  // already in place by the time the UI asks for it.
  rankSync.fetchRemoteGrants()
  rankSync.startAutoRefresh()
  const syncProfile = () => {
    try { syncProfileToClient(auth.getRank()) } catch (err) { logger.warn('launcher', 'Rang konnte nicht an den Client übergeben werden', String(err)) }
  }
  rankSync.ready().then(syncProfile)
  setInterval(syncProfile, 3 * 60 * 1000).unref?.()
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
  const crashDoctor = new CrashDoctor(instances)

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
      syncThemeToClient(value)
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
  ipcMain.handle('auth:listAccounts', () => auth.listAccounts())
  ipcMain.handle('auth:switchAccount', async (_e, uuid: string) => {
    const result = await auth.switchAccount(uuid)
    syncProfile()
    return result
  })
  ipcMain.handle('auth:removeAccount', (_e, uuid: string) => {
    auth.removeAccount(uuid)
    syncProfile()
  })
  // Read-only by design: a rank is something the Crystal team grants, so the
  // renderer can look it up but never assign one to itself.
  // Awaits the startup rank fetch so a first-ever launch reports the published
  // rank straight away instead of "member" until the next restart.
  ipcMain.handle('auth:getRank', async () => {
    await rankSync.ready()
    syncProfile()
    return auth.getRank()
  })

  // Rank management — every handler re-checks the CALLER's own current rank
  // server-side (never trusts a flag the renderer sends), so a compromised
  // renderer can't just claim to be the owner.
  ipcMain.handle('ranks:list', () => {
    if (auth.getRank() !== 'owner') return []
    return Object.values(auth.getGrants())
  })
  ipcMain.handle('ranks:grant', async (_e, username: string, rank: string, durationMs?: number) => {
    if (auth.getRank() !== 'owner') return false
    if (!username?.trim()) return false
    auth.grantRank(username.trim(), rank as any, durationMs)
    // Publishing is what makes the rank visible on the other person's own
    // machine — without it the grant would only ever apply to this install.
    await rankSync.publish(auth.getGrants())
    return true
  })
  ipcMain.handle('ranks:revoke', async (_e, username: string) => {
    if (auth.getRank() !== 'owner') return false
    auth.revokeGrant(username)
    await rankSync.publish(auth.getGrants())
    return true
  })

  // GitHub-backed rank sync. The token lives only in this machine's local
  // store and is never bundled into the installer — a normal user's launcher
  // has no token and can therefore only ever read the published ranks.
  ipcMain.handle('ranks:hasToken', () => auth.getRank() === 'owner' && rankSync.hasToken())
  ipcMain.handle('ranks:setToken', (_e, token: string) => {
    if (auth.getRank() !== 'owner') return false
    rankSync.setToken(token)
    return true
  })
  ipcMain.handle('ranks:publishNow', async () => {
    if (auth.getRank() !== 'owner') return { ok: false, error: 'Nur der Owner kann veröffentlichen.' }
    return rankSync.publish(auth.getGrants())
  })
  ipcMain.handle('ranks:refreshRemote', () => rankSync.fetchRemoteGrants())

  // Discord Rich Presence
  discord.idle()
  ipcMain.handle('discord:isEnabled', () => discord.isEnabled())
  ipcMain.handle('discord:isConnected', () => discord.isConnected())
  ipcMain.handle('discord:isConfigured', () => discord.isConfigured())
  ipcMain.handle('discord:setEnabled', (_e, enabled: boolean) => {
    discord.setEnabled(enabled)
    return discord.isEnabled()
  })

  // Suggested RAM for this machine: half the physical memory, capped to 8 GB
  // and never below 2 GB. Used as the default instead of a fixed 4 GB that is
  // too much for an 8 GB laptop and too little for nothing.
  ipcMain.handle('system:memory', () => {
    const totalMb = Math.round(os.totalmem() / 1024 / 1024)
    const suggested = Math.max(2048, Math.min(8192, Math.floor(totalMb / 2 / 512) * 512))
    return { totalMb, suggestedMb: suggested }
  })

  // Real versions, read from the build rather than typed into the UI — the
  // hardcoded "1.0.0" strings they replaced were still showing long after
  // several releases had shipped.
  ipcMain.handle('app:versions', () => ({
    launcher: app.getVersion(),
    client: minecraft.getBundledClientVersion(),
  }))

  // News = the project's real GitHub releases. Public endpoint, no token; on
  // failure the UI shows an honest "couldn't load" instead of placeholder posts.
  ipcMain.handle('news:list', async () => {
    try {
      const res = await (globalThis as any).fetch('https://api.github.com/repos/Lunar0710/Crystal/releases?per_page=10', {
        headers: { Accept: 'application/vnd.github+json', 'User-Agent': 'crystal-launcher' },
      })
      if (!res.ok) return { ok: false, items: [] }
      const releases = await res.json() as any[]
      return {
        ok: true,
        items: releases
          .filter(r => !r.draft)
          .map(r => ({
            id: String(r.id),
            title: r.name || r.tag_name,
            tag: r.tag_name,
            body: r.body || '',
            publishedAt: r.published_at,
            url: r.html_url,
          })),
      }
    } catch (err) {
      logger.warn('launcher', 'News konnten nicht geladen werden', String(err))
      return { ok: false, items: [] }
    }
  })
  ipcMain.handle('shell:openExternal', (_e, url: string) => {
    // Only ever the project's own GitHub pages — never an arbitrary URL from the renderer.
    if (typeof url === 'string' && url.startsWith('https://github.com/Lunar0710/Crystal')) shell.openExternal(url)
  })

  // Data folder location. Existing instances keep their stored absolute path,
  // so switching never strands or moves anyone's worlds — only new instances,
  // downloads and caches go to the new folder.
  ipcMain.handle('dataRoot:get', () => ({ current: crystalRoot(), default: defaultCrystalRoot() }))
  ipcMain.handle('dataRoot:pick', async () => {
    const win = BrowserWindow.getFocusedWindow()
    const result = await dialog.showOpenDialog(win!, {
      title: 'Speicherort für Crystal-Dateien wählen',
      properties: ['openDirectory', 'createDirectory'],
    })
    if (result.canceled || result.filePaths.length === 0) return null

    const chosen = path.join(result.filePaths[0], 'Crystal')
    const check = canUseAsRoot(chosen)
    if (!check.ok) return { ok: false, error: check.error }

    store.set('dataRoot', chosen)
    setCrystalRoot(chosen)
    logger.info('launcher', `Datenordner geändert: ${chosen}`)
    return { ok: true, path: chosen }
  })
  ipcMain.handle('dataRoot:reset', () => {
    store.delete('dataRoot')
    setCrystalRoot(null)
    return crystalRoot()
  })

  // Autofix for a failed launch — analyses the log, then applies the one fix
  // the user picks. Nothing is changed without an explicit click.
  ipcMain.handle('autofix:analyze', (_e, instanceId: string, errorMessage: string) => {
    // The error message only carries the last ~1200 characters; Fabric prints
    // its dependency/incompatibility report well before that, so the full
    // launch log is what actually gets analysed.
    const gameDir = instances.get(instanceId)?.gameDir || crystalPath('instances', instanceId)
    const logPath = path.join(gameDir, 'crystal-launch.log')
    let fullLog = ''
    try {
      if (fs.existsSync(logPath)) fullLog = fs.readFileSync(logPath, 'utf8')
    } catch (err) {
      logger.warn('launcher', 'Launch-Log für Autofix nicht lesbar', String(err))
    }
    return crashDoctor.analyze(instanceId, `${fullLog}\n${errorMessage || ''}`, (store.get('maxRam') as number) || 4096)
  })

  ipcMain.handle('autofix:apply', async (_e, instanceId: string, fix: any) => {
    if (fix?.kind === 'lower-ram' || fix?.kind === 'raise-ram') {
      store.set('maxRam', fix.ram)
      return { ok: true, message: `RAM auf ${fix.ram} MB gesetzt.` }
    }
    if (fix?.kind === 'install-fabric-api') {
      const result = await modrinth.install(instanceId, 'fabric-api', '1.21.11', 'fabric', 'mod')
      return result.success
        ? { ok: true, message: `${result.fileName} installiert.` }
        : { ok: false, message: result.error || 'Installation fehlgeschlagen.' }
    }
    return crashDoctor.applyFix(instanceId, fix)
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

    const instanceName = instances.get(opts.instanceId)?.name || 'Minecraft'
    discord.playing(instanceName, opts.version)

    return minecraft.launch({ ...opts, profile }, (event, data) => {
      // Back to the idle line once the game is gone, however it ended.
      if (event === 'launch:exit' || event === 'launch:error') discord.idle()
      win?.webContents.send(event, data)
    })
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
  ipcMain.handle('trash:open', () => {
    const dir = crystalPath('trash')
    fs.mkdirSync(dir, { recursive: true })
    return shell.openPath(dir)
  })

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
  ipcMain.handle('modrinth:search', (_e, query: string, gameVersion: string, loader: string, type: ContentType, offset?: number) =>
    modrinth.search(query, gameVersion, loader, type, offset))
  ipcMain.handle('modrinth:getVersions', (_e, projectId: string, gameVersion: string, loader: string, type: ContentType) =>
    modrinth.getVersions(projectId, gameVersion, loader, type))
  ipcMain.handle('modrinth:install', (_e, instanceId: string, projectId: string, gameVersion: string, loader: string, type: ContentType, versionId?: string) =>
    modrinth.install(instanceId, projectId, gameVersion, loader, type, versionId))

  // Version switching for files that are already installed.
  ipcMain.handle('modrinth:identifyFile', (_e, instanceId: string, type: ContentType, fileName: string) =>
    modrinth.identifyFile(instanceId, type, fileName))
  ipcMain.handle('perfpack:status', (_e, instanceId: string) => modrinth.performancePackStatus(instanceId))
  ipcMain.handle('perfpack:install', (_e, instanceId: string) => modrinth.installPerformancePack(instanceId, '1.21.11'))
  ipcMain.handle('modrinth:identifyFolder', (_e, instanceId: string, type: ContentType) =>
    modrinth.identifyFolder(instanceId, type))
  ipcMain.handle('modrinth:switchVersion', (_e, instanceId: string, type: ContentType, fileName: string, versionId: string) =>
    modrinth.switchVersion(instanceId, type, fileName, versionId))

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
    const dir = crystalPath('cosmetics')
    fs.mkdirSync(dir, { recursive: true })
    const target = path.join(dir, 'equipped_cape.png')

    if (!dataUrl) {
      fs.rm(target, { force: true }, () => {})
      return true
    }

    // Always re-encode through nativeImage instead of dumping the data URL's
    // bytes straight to disk: an uploaded .jpg would otherwise land as JPEG
    // bytes in a .png file, and the Java client's NativeImage.read() only
    // decodes PNG — the cape then silently never appears. Minecraft also
    // requires a 64x32 cape texture, so anything else is scaled to fit.
    const image = nativeImage.createFromDataURL(dataUrl)
    if (image.isEmpty()) {
      logger.warn('launcher', 'Cape konnte nicht dekodiert werden — Datei nicht geschrieben')
      return false
    }

    const { width, height } = image.getSize()
    const normalized = (width === 64 && height === 32)
      ? image
      : image.resize({ width: 64, height: 32, quality: 'best' })

    fs.writeFileSync(target, normalized.toPNG())
    if (width !== 64 || height !== 32) {
      logger.info('launcher', `Cape von ${width}x${height} auf 64x32 skaliert`)
    }
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
