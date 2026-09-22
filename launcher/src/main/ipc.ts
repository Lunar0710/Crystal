import { ipcMain, BrowserWindow, dialog, shell, nativeImage, app, clipboard } from 'electron'
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
import { crystalServerAddress, writeEquippedCapeId } from './cosmetics/CrystalServer'
import { SkinService } from './cosmetics/SkinService'
import { LogManager, setInstanceDirResolver, instanceDir } from './logs/LogManager'
import { ClaudeService } from './claude/ClaudeService'
import { FriendManager } from './friends/FriendManager'
import { logger, LogCategory } from './logs/Logger'
import { TryCrystalService } from './minecraft/TryCrystalService'
import { CustomClientInstaller } from './minecraft/CustomClientInstaller'
import { CrashDoctor } from './minecraft/CrashDoctor'
import { ScreenshotService } from './screenshots/ScreenshotService'
import { ServerListService, isValidServerAddress } from './servers/ServerListService'
import { StatsService } from './stats/StatsService'
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
    try { syncProfileToClient(auth.getRank(), auth.isTester()) } catch (err) { logger.warn('launcher', 'Rang konnte nicht an den Client übergeben werden', String(err)) }
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
  const stats = new StatsService(store)
  const screenshots = new ScreenshotService(instances)
  const servers = new ServerListService(store)

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
  // Saved here either way; the renderer says so when it didn't reach the others.
  const publishResult = (r: { ok: boolean; error?: string }) =>
    r.ok || !rankSync.hasToken() ? true : { saved: true, publishError: r.error || 'Veröffentlichen fehlgeschlagen' }
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
    return publishResult(await rankSync.publish(auth.getGrants()))
  })
  ipcMain.handle('ranks:setTester', async (_e, username: string, tester: boolean) => {
    if (auth.getRank() !== 'owner') return false
    if (!username?.trim()) return false
    auth.setTester(username.trim(), !!tester)
    return publishResult(await rankSync.publish(auth.getGrants()))
  })
  ipcMain.handle('ranks:revoke', async (_e, username: string) => {
    if (auth.getRank() !== 'owner') return false
    auth.revokeGrant(username)
    return publishResult(await rankSync.publish(auth.getGrants()))
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
    // Only the project's own GitHub pages and uploaded crash logs — never an arbitrary URL from the renderer.
    if (typeof url !== 'string') return
    if (url.startsWith('https://github.com/Lunar0710/Crystal') || /^https:\/\/mclo\.gs\/[A-Za-z0-9]+$/.test(url)) shell.openExternal(url)
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

  // The newest Modrinth version of an installed mod that fits this instance, or why there is none.
  const MODRINTH_PROJECT = /^[A-Za-z0-9_-]{1,64}$/
  async function replacementFor(instanceId: string, modFile: string) {
    const known = await modrinth.identifyFile(instanceId, 'mod', modFile)
    if (!known) return { error: 'Nicht auf Modrinth gefunden' }
    const gameVersion = instances.get(instanceId)?.version ?? '1.21.11'
    const versions = await modrinth.getVersions(known.projectId, gameVersion, 'fabric', 'mod')
    const newest = versions[0]
    if (!newest) return { error: `Keine Version für ${gameVersion}` }
    if (newest.id === known.versionId) return { error: 'Schon die neueste passende Version' }
    return { versionId: newest.id, label: newest.version_number }
  }

  ipcMain.handle('autofix:preview', async (_e, instanceId: string, fix: any) => {
    if (fix?.kind !== 'update-mod' || typeof fix.modFile !== 'string') return null
    return replacementFor(instanceId, fix.modFile)
  })

  ipcMain.handle('autofix:apply', async (_e, instanceId: string, fix: any) => {
    const gameVersion = instances.get(instanceId)?.version ?? '1.21.11'
    switch (fix?.kind) {
      case 'lower-ram':
      case 'raise-ram': {
        const ram = Math.round(Number(fix.ram))
        if (!Number.isFinite(ram) || ram < 512 || ram > 65536) return { ok: false, message: 'Ungültige RAM-Angabe.' }
        store.set('maxRam', ram)
        return { ok: true, message: `RAM auf ${ram} MB gesetzt.` }
      }
      case 'install-fabric-api':
      case 'install-mod': {
        const project = fix.kind === 'install-fabric-api' ? 'fabric-api' : fix.project
        if (typeof project !== 'string' || !MODRINTH_PROJECT.test(project)) return { ok: false, message: 'Ungültiges Projekt.' }
        const result = await modrinth.install(instanceId, project, gameVersion, 'fabric', 'mod')
        return result.success
          ? { ok: true, message: `${result.fileName} installiert.` }
          : { ok: false, message: result.error || 'Installation fehlgeschlagen.' }
      }
      case 'update-mod': {
        if (typeof fix.modFile !== 'string') return { ok: false, message: 'Keine Datei angegeben.' }
        const target = await replacementFor(instanceId, fix.modFile)
        if (!target.versionId) return { ok: false, message: target.error }
        const result = await modrinth.switchVersion(instanceId, 'mod', fix.modFile, target.versionId)
        return result.success
          ? { ok: true, message: `Ersetzt durch ${result.fileName}.` }
          : { ok: false, message: result.error || 'Ersetzen fehlgeschlagen.' }
      }
      case 'disable-mod':
        return crashDoctor.disableMod(instanceId, fix.modFile)
      default:
        return { ok: false, message: 'Unbekannter Fix.' }
    }
  })

  // Shares the launch log on mclo.gs (the usual Minecraft log paste site), only
  // when the player clicks it. Tokens and the Windows user name are removed first.
  ipcMain.handle('autofix:uploadLog', async (_e, instanceId: string) => {
    const content = crashDoctor.shareableLog(instanceId)
    if (!content) return { ok: false, message: 'Kein Log gefunden.' }
    try {
      const res = await fetch('https://api.mclo.gs/1/log', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ content }).toString(),
      })
      const json = await res.json() as { success?: boolean; url?: string; error?: string }
      if (!json.success || !json.url) return { ok: false, message: json.error || `Upload fehlgeschlagen (HTTP ${res.status})` }
      clipboard.writeText(json.url)
      return { ok: true, url: json.url }
    } catch (err) {
      return { ok: false, message: err instanceof Error ? err.message : 'Upload fehlgeschlagen.' }
    }
  })

  // Minecraft
  ipcMain.handle('minecraft:getVersions', () => minecraft.fetchAllVersions())
  // Every supported release, newest first, with what exists for it.
  ipcMain.handle('minecraft:versionOptions', async () => {
    const list = await minecraft.fetchAllVersions().catch(() => [])
    return list.map(v => ({
      id: v.id,
      fabric: minecraft.getSupportedLoaders(v.id).includes('fabric'),
      crystal: minecraft.hasCrystalFor(v.id),
    }))
  })
  ipcMain.handle('minecraft:launch', async (_e, rawOpts) => {
    const win = BrowserWindow.getFocusedWindow()
    // Extra JVM/game arguments are for the automated tests only; a page must
    // never be able to pass its own. Joining a server is the one supported
    // extra, and only with an address that is a plain host[:port].
    const { extraJvmArgs: _jvm, extraGameArgs: _game, joinServer, ...opts } = rawOpts ?? {}
    if (isValidServerAddress(joinServer)) opts.extraGameArgs = ['--quickPlayMultiplayer', joinServer]
    const crystalServer = crystalServerAddress(store)
    if (crystalServer) opts.extraJvmArgs = [`-Dcrystal.server=${crystalServer}`]
    // Renews an expired Microsoft token first; a stale one gets every
    // multiplayer join rejected with "Invalid session".
    const { profile, error: sessionError } = await auth.ensureFreshProfile()
    if (sessionError) {
      win?.webContents.send('launch:error', sessionError)
      return false
    }

    // A launch never waits on this — an update becomes a dismissible banner
    // (see UpdateBanner.tsx), never a blocker standing between the user and Play.
    updater.check()
      .then(info => {
        if (info.available) win?.webContents.send('update:available', info)
      })
      .catch(err => logger.warn('updater', 'Update-Pruefung vor dem Start fehlgeschlagen', String(err)))

    // Crystal alone keeps vanilla's chunk renderer, which falls far behind at
    // high render distance (worst underground, where vanilla's cave culling is
    // weak). Sodium and EntityCulling close that gap, so a Crystal instance gets
    // the performance pack once. Only once: a mod the player removes afterwards
    // stays removed. A failed download never blocks the launch.
    const perfKey = `perfPackAuto.${opts.instanceId}`
    if (opts.injectCrystal && typeof opts.version === 'string' && opts.version.startsWith('1.21') && opts.instanceId
        && store.get('autoPerformancePack') !== false && !store.get(perfKey)) {
      win?.webContents.send('launch:progress', { step: 'Performance-Mods werden installiert...', percent: 2 })
      try {
        const result = await modrinth.installPerformancePack(opts.instanceId, opts.version)
        if (result.failed.length === 0) store.set(perfKey, true)
      } catch (err) {
        logger.warn('client', 'Performance-Paket beim Start fehlgeschlagen', String(err))
      }
    }

    const instanceName = instances.get(opts.instanceId)?.name || 'Minecraft'
    discord.playing(instanceName, opts.version)

    // Minimised windows are throttled by Chromium, which leaves more CPU/GPU for
    // the game. Only a window we minimised gets brought back afterwards.
    let minimizedByLaunch = false
    let playStartedAt = 0
    return minecraft.launch({ ...opts, profile }, (event, data) => {
      if (event === 'launch:started') playStartedAt = Date.now()
      if (event === 'launch:started' && win && store.get('minimizeOnLaunch') !== false && !win.isMinimized()) {
        win.minimize()
        minimizedByLaunch = true
      }
      // Back to the idle line once the game is gone, however it ended.
      if (event === 'launch:exit' || event === 'launch:error') {
        // Playtime statistics: only sessions that actually started count.
        if (playStartedAt > 0) {
          const instance = instances.get(opts.instanceId)
          try {
            stats.record({
              start: playStartedAt,
              end: Date.now(),
              version: String(opts.version ?? '?'),
              instanceId: String(opts.instanceId ?? ''),
              instanceName: instance?.name ?? '',
              crystal: !!opts.injectCrystal,
            }, instance?.gameDir || crystalPath('instances', String(opts.instanceId ?? '')))
          } catch (err) {
            logger.warn('launcher', 'Spielzeit konnte nicht gespeichert werden', String(err))
          }
          playStartedAt = 0
        }
        discord.idle()
        // After a crash this also puts the auto-fix panel in front of the user.
        if (minimizedByLaunch && win && !win.isDestroyed() && win.isMinimized()) win.restore()
        minimizedByLaunch = false
      }
      if (win && !win.isDestroyed()) win.webContents.send(event, data)
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
  ipcMain.handle('modProfiles:list', (_e, instanceId: string) => content.listProfiles(instanceId))
  ipcMain.handle('modProfiles:save', (_e, instanceId: string, name: string) => content.saveProfile(instanceId, name))
  ipcMain.handle('modProfiles:apply', (_e, instanceId: string, name: string) => content.applyProfile(instanceId, name))
  ipcMain.handle('modProfiles:delete', (_e, instanceId: string, name: string) => content.deleteProfile(instanceId, name))
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
  ipcMain.handle('perfpack:install', (_e, instanceId: string) => modrinth.installPerformancePack(instanceId, instances.get(instanceId)?.version ?? '1.21.11'))
  ipcMain.handle('modrinth:identifyFolder', (_e, instanceId: string, type: ContentType) =>
    modrinth.identifyFolder(instanceId, type))
  ipcMain.handle('modrinth:switchVersion', (_e, instanceId: string, type: ContentType, fileName: string, versionId: string) =>
    modrinth.switchVersion(instanceId, type, fileName, versionId))

  // Modpack presets for Create Instance — browse finished Modrinth modpacks and install one wholesale.
  ipcMain.handle('modrinth:searchModpacks', (_e, query: string, gameVersion?: string) =>
    modrinth.searchModpacks(query, gameVersion))
  ipcMain.handle('modrinth:getModpackVersions', (_e, projectId: string, gameVersion: string) =>
    modrinth.getModpackVersions(projectId, gameVersion))
  ipcMain.handle('modrinth:installModpack', (_e, instanceId: string, projectId: string, versionId?: string) =>
    modrinth.installModpack(instanceId, projectId, versionId))
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

  // Branding: app icon and own logo, for every rank except Member.
  const canBrand = () => { const rank = auth.getRank(); return !!rank && rank !== 'member' }
  ipcMain.handle('branding:list', () => branding.list())
  ipcMain.handle('branding:getCurrent', () => branding.getCurrentId())
  ipcMain.handle('branding:setCurrent', (_e, id: string) => {
    if (!canBrand()) return false
    const win = BrowserWindow.getFocusedWindow()
    return branding.setCurrent(win, id)
  })
  // Without a rank the default wordmark shows, even if a logo was saved earlier.
  ipcMain.handle('branding:getLogo', () => (canBrand() ? branding.getCustomLogo() : null))
  ipcMain.handle('branding:pickLogo', async () => {
    if (!canBrand()) return { ok: false, error: 'Ein eigenes Logo gibt es ab Crystal+ und für das Team.' }
    const win = BrowserWindow.getFocusedWindow()
    const result = await dialog.showOpenDialog(win!, {
      title: 'Logo auswählen',
      properties: ['openFile'],
      filters: [{ name: 'Bild', extensions: ['png', 'jpg', 'jpeg'] }],
    })
    if (result.canceled || !result.filePaths[0]) return { ok: false }
    if (!branding.setCustomLogo(win, result.filePaths[0])) return { ok: false, error: 'Das Bild konnte nicht gelesen werden.' }
    return { ok: true, logo: branding.getCustomLogo() }
  })
  ipcMain.handle('branding:resetLogo', () => {
    branding.clearCustomLogo(BrowserWindow.getFocusedWindow())
    return true
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
  ipcMain.handle('capes:replaceImage', (_e, id: string, dataUrl: string) => capes.replaceImage(id, dataUrl))
  ipcMain.handle('capes:getDataUrl', (_e, id: string) => capes.getCapeDataUrl(id))
  ipcMain.handle('capes:getSelected', () => capes.getSelected())
  ipcMain.handle('capes:setSelected', (_e, id: string) => capes.setSelected(id))

  // Hats, masks, wings… for the in-game client (CosmeticLoadout.java). Only the
  // fields the Java renderer needs, and only content-changing writes, since the
  // client re-reads the file whenever its modification time changes.
  ipcMain.handle('cosmetics:syncLoadout', (_e, items: Record<string, { color: string; secondary?: string; variant?: string; plusOnly?: boolean; anchor?: string; boxes?: unknown[] } | null>) => {
    const dir = crystalPath('cosmetics')
    fs.mkdirSync(dir, { recursive: true })
    const target = path.join(dir, 'loadout.json')
    const isHex = (v: unknown): v is string => typeof v === 'string' && /^#[0-9a-fA-F]{6}$/.test(v)
    const num = (v: unknown, limit: number) => typeof v === 'number' && Number.isFinite(v) ? Math.max(-limit, Math.min(limit, v)) : 0
    const clean: Record<string, unknown> = {}
    for (const slot of ['hat', 'bandana', 'mask', 'wings', 'backpack', 'aura']) {
      const item = items?.[slot]
      if (!item || !isHex(item.color)) continue
      // Shape boxes, bounded: a few dozen boxes of sane size around the player.
      const boxes = (Array.isArray(item.boxes) ? item.boxes : []).slice(0, 64).flatMap((raw: any) => {
        if (!raw || !isHex(raw.color)) return []
        return [{
          x: num(raw.x, 32), y: num(raw.y, 32), z: num(raw.z, 32),
          w: Math.abs(num(raw.w, 32)), h: Math.abs(num(raw.h, 32)), d: Math.abs(num(raw.d, 32)),
          rz: num(raw.rz, 7), color: raw.color, glow: !!raw.glow,
        }]
      })
      clean[slot] = {
        color: item.color,
        secondary: isHex(item.secondary) ? item.secondary : null,
        variant: typeof item.variant === 'string' ? item.variant : null,
        plusOnly: !!item.plusOnly,
        anchor: ['head', 'body', 'wing'].includes(item.anchor as string) ? item.anchor : null,
        boxes,
      }
    }
    const json = JSON.stringify(clean, null, 2)
    try {
      if (fs.existsSync(target) && fs.readFileSync(target, 'utf8') === json) return true
      fs.writeFileSync(target, json)
      return true
    } catch (err) {
      logger.warn('launcher', 'Cosmetics-Loadout konnte nicht geschrieben werden', String(err))
      return false
    }
  })

  // Pushes whichever cape is actually equipped (as a real PNG) to a fixed path
  // the Java client reads — capes.ts's built-in designs only ever exist as
  // canvas data URLs inside the renderer, so this is the one place they
  // become a real file the in-game mod can load.
  ipcMain.handle('cosmetics:syncCape', (_e, dataUrl: string | null, capeId?: string | null) => {
    const dir = crystalPath('cosmetics')
    fs.mkdirSync(dir, { recursive: true })
    writeEquippedCapeId(dir, capeId)
    const target = path.join(dir, 'equipped_cape.png')
    // A still cape: drop the animation description of a previous animated one.
    fs.rmSync(path.join(dir, 'equipped_cape.json'), { force: true })

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

    // The cape layout is 64x32; HD capes use the same layout at 2x to 16x
    // (up to 1024x512). Minecraft's cape model uses relative texture
    // coordinates, so any of those sizes shows correctly in game.
    const { width, height } = image.getSize()
    const factor = Math.min(16, Math.max(1, 2 ** Math.round(Math.log2(Math.max(1, width / 64)))))
    const targetW = 64 * factor, targetH = 32 * factor
    const normalized = (width === targetW && height === targetH)
      ? image
      : image.resize({ width: targetW, height: targetH, quality: 'best' })

    fs.writeFileSync(target, normalized.toPNG())
    if (width !== targetW || height !== targetH) {
      logger.info('launcher', `Cape von ${width}x${height} auf ${targetW}x${targetH} skaliert`)
    }
    return true
  })

  // Animated cape: a vertical strip of cape textures plus how to play it.
  // The game reads equipped_cape.json to know it is a strip (CosmeticCapeLoader).
  ipcMain.handle('cosmetics:syncCapeAnimation', (_e, dataUrl: string, frames: number, fps: number, capeId?: string | null) => {
    if (typeof dataUrl !== 'string' || !dataUrl.startsWith('data:image/png;base64,')) return false
    const n = Math.floor(Number(frames)), speed = Math.floor(Number(fps))
    if (!(n > 1 && n <= 64 && speed >= 1 && speed <= 30)) return false
    const image = nativeImage.createFromDataURL(dataUrl)
    if (image.isEmpty()) return false
    const { width, height } = image.getSize()
    if (height % n !== 0 || width !== (height / n) * 2 || width > 1024) return false
    const dir = crystalPath('cosmetics')
    fs.mkdirSync(dir, { recursive: true })
    fs.writeFileSync(path.join(dir, 'equipped_cape.png'), image.toPNG())
    fs.writeFileSync(path.join(dir, 'equipped_cape.json'), JSON.stringify({ frames: n, fps: speed }))
    writeEquippedCapeId(dir, capeId)
    return true
  })

  // Pictures of every built-in cape, so the game can show other Crystal
  // players' capes from just an id (PeerCapes.java, capeCache.ts).
  const capeCacheDir = () => crystalPath('cosmetics', 'cape-cache')
  const capeCacheVersion = (count: number) => `${app.getVersion()}:${Math.floor(Number(count)) || 0}`
  ipcMain.handle('cosmetics:syncCapeId', (_e, capeId: string | null) => {
    const dir = crystalPath('cosmetics')
    fs.mkdirSync(dir, { recursive: true })
    writeEquippedCapeId(dir, capeId)
    return true
  })
  ipcMain.handle('cosmetics:capeCacheStatus', (_e, count: number) => {
    let version = ''
    try { version = fs.readFileSync(path.join(capeCacheDir(), '.version'), 'utf8') } catch { /* not filled yet */ }
    return { version, current: capeCacheVersion(count) }
  })
  ipcMain.handle('cosmetics:cacheCape', (_e, id: string, dataUrl: string) => {
    if (typeof id !== 'string' || !/^[a-z]+-\d{1,4}$/.test(id)) return false
    if (typeof dataUrl !== 'string' || !dataUrl.startsWith('data:image/png;base64,')) return false
    const image = nativeImage.createFromDataURL(dataUrl)
    const { width, height } = image.getSize()
    if (image.isEmpty() || width > 1024 || width !== height * 2) return false
    fs.mkdirSync(capeCacheDir(), { recursive: true })
    fs.writeFileSync(path.join(capeCacheDir(), `${id}.png`), image.toPNG())
    return true
  })
  ipcMain.handle('cosmetics:capeCacheDone', (_e, version: string) => {
    if (typeof version !== 'string' || version.length > 64) return false
    fs.writeFileSync(path.join(capeCacheDir(), '.version'), version)
    return true
  })

  // Launcher's own logs (separate from per-instance game logs)
  // Profile card
  ipcMain.handle('stats:summary', () => stats.summary())
  ipcMain.handle('stats:get', () => ({
    playtimeMs: Number(store.get('stats.playtimeMs')) || 0,
    sessions: Number(store.get('stats.sessions')) || 0,
  }))
  ipcMain.handle('profileCard:copy', (_e, dataUrl: string) => {
    if (typeof dataUrl !== 'string' || !dataUrl.startsWith('data:image/png;base64,')) return false
    const image = nativeImage.createFromDataURL(dataUrl)
    if (image.isEmpty()) return false
    require('electron').clipboard.writeImage(image)
    return true
  })
  ipcMain.handle('profileCard:save', async (_e, dataUrl: string) => {
    if (typeof dataUrl !== 'string' || !dataUrl.startsWith('data:image/png;base64,')) return false
    const win = BrowserWindow.getFocusedWindow()
    const result = await dialog.showSaveDialog(win!, {
      title: 'Profil-Karte speichern',
      defaultPath: 'crystal-profil.png',
      filters: [{ name: 'PNG', extensions: ['png'] }],
    })
    if (result.canceled || !result.filePath) return false
    fs.writeFileSync(result.filePath, Buffer.from(dataUrl.split(',')[1], 'base64'))
    return true
  })

  // Favourite servers
  ipcMain.handle('servers:list', () => servers.list())
  ipcMain.handle('servers:add', (_e, name: string, address: string) => servers.add(name, address))
  ipcMain.handle('servers:remove', (_e, id: string) => servers.remove(id))
  ipcMain.handle('servers:ping', (_e, address: string) => servers.ping(address))

  // Screenshots from every instance
  ipcMain.handle('screenshots:list', () => screenshots.list())
  ipcMain.handle('screenshots:thumb', (_e, instanceId: string, fileName: string) => screenshots.thumbnail(instanceId, fileName))
  ipcMain.handle('screenshots:full', (_e, instanceId: string, fileName: string) => screenshots.full(instanceId, fileName))
  ipcMain.handle('screenshots:copy', (_e, instanceId: string, fileName: string) => screenshots.copy(instanceId, fileName))
  ipcMain.handle('screenshots:show', (_e, instanceId: string, fileName: string) => screenshots.showInFolder(instanceId, fileName))
  ipcMain.handle('screenshots:remove', (_e, instanceId: string, fileName: string) => screenshots.remove(instanceId, fileName))

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
