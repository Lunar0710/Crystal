import { contextBridge, ipcRenderer } from 'electron'

contextBridge.exposeInMainWorld('crystal', {
  // 'win32' | 'darwin' | 'linux' — the title bar draws macOS traffic-light spacing instead of caption buttons.
  platform: process.platform,

  // Window controls
  minimize:  () => ipcRenderer.invoke('window:minimize'),
  maximize:  () => ipcRenderer.invoke('window:maximize'),
  close:     () => ipcRenderer.invoke('window:close'),

  // Settings
  getSetting: (key: string) => ipcRenderer.invoke('settings:get', key),
  setSetting: (key: string, value: unknown) => ipcRenderer.invoke('settings:set', key, value),

  // Auth
  loginMicrosoft: () => ipcRenderer.invoke('auth:loginMicrosoft'),
  loginOffline:   (username: string) => ipcRenderer.invoke('auth:loginOffline', username),
  autoLogin:      () => ipcRenderer.invoke('auth:autoLogin'),
  getProfile:     () => ipcRenderer.invoke('auth:getProfile'),
  logout:         () => ipcRenderer.invoke('auth:logout'),
  getRank:        () => ipcRenderer.invoke('auth:getRank'),
  listAccounts:   () => ipcRenderer.invoke('auth:listAccounts'),
  switchAccount:  (uuid: string) => ipcRenderer.invoke('auth:switchAccount', uuid),
  removeAccount:  (uuid: string) => ipcRenderer.invoke('auth:removeAccount', uuid),

  // News (GitHub releases)
  listNews:     () => ipcRenderer.invoke('news:list'),
  openExternal: (url: string) => ipcRenderer.invoke('shell:openExternal', url),

  // Data folder
  getDataRoot:   () => ipcRenderer.invoke('dataRoot:get'),
  pickDataRoot:  () => ipcRenderer.invoke('dataRoot:pick'),
  resetDataRoot: () => ipcRenderer.invoke('dataRoot:reset'),

  // Autofix
  analyzeFailure: (instanceId: string, log: string) => ipcRenderer.invoke('autofix:analyze', instanceId, log),
  applyFix:       (instanceId: string, fix: unknown) => ipcRenderer.invoke('autofix:apply', instanceId, fix),

  // Rank management (server-side gated to the owner rank, see ipc.ts)
  listRankGrants:  () => ipcRenderer.invoke('ranks:list'),
  grantRank:       (username: string, rank: string, durationMs?: number) => ipcRenderer.invoke('ranks:grant', username, rank, durationMs),
  revokeRankGrant: (username: string) => ipcRenderer.invoke('ranks:revoke', username),
  hasRankToken:    () => ipcRenderer.invoke('ranks:hasToken'),
  setRankToken:    (token: string) => ipcRenderer.invoke('ranks:setToken', token),
  publishRanks:    () => ipcRenderer.invoke('ranks:publishNow'),
  refreshRemoteRanks: () => ipcRenderer.invoke('ranks:refreshRemote'),
  getVersionInfo:  () => ipcRenderer.invoke('app:versions'),

  // Discord Rich Presence
  isDiscordEnabled:   () => ipcRenderer.invoke('discord:isEnabled'),
  isDiscordConnected: () => ipcRenderer.invoke('discord:isConnected'),
  isDiscordConfigured: () => ipcRenderer.invoke('discord:isConfigured'),
  setDiscordEnabled:  (enabled: boolean) => ipcRenderer.invoke('discord:setEnabled', enabled),

  // Minecraft
  getVersions:      () => ipcRenderer.invoke('minecraft:getVersions'),
  launchGame:       (opts: object) => ipcRenderer.invoke('minecraft:launch', opts),
  tryWithCrystal:   (instanceId: string) => ipcRenderer.invoke('tryCrystal:run', instanceId),
  selectGameDir:    () => ipcRenderer.invoke('minecraft:selectDir'),
  getInstances:     () => ipcRenderer.invoke('instances:list'),
  listScreenshots:  () => ipcRenderer.invoke('screenshots:list'),
  screenshotThumb:  (instanceId: string, fileName: string) => ipcRenderer.invoke('screenshots:thumb', instanceId, fileName),
  screenshotFull:   (instanceId: string, fileName: string) => ipcRenderer.invoke('screenshots:full', instanceId, fileName),
  copyScreenshot:   (instanceId: string, fileName: string) => ipcRenderer.invoke('screenshots:copy', instanceId, fileName),
  showScreenshot:   (instanceId: string, fileName: string) => ipcRenderer.invoke('screenshots:show', instanceId, fileName),
  removeScreenshot: (instanceId: string, fileName: string) => ipcRenderer.invoke('screenshots:remove', instanceId, fileName),
  createInstance:   (data: object) => ipcRenderer.invoke('instances:create', data),
  updateInstance:   (id: string, patch: object) => ipcRenderer.invoke('instances:update', id, patch),
  importInstance:   (version: string) => ipcRenderer.invoke('instances:import', version),
  deleteInstance:   (id: string) => ipcRenderer.invoke('instances:delete', id),

  // Custom client install (per instance)
  getInstallTarget:     (instanceId: string) => ipcRenderer.invoke('clientInstall:target', instanceId),
  inspectClientJar:     (filePath: string) => ipcRenderer.invoke('clientInstall:inspect', filePath),
  pickAndInstallClient: (instanceId: string) => ipcRenderer.invoke('clientInstall:pick', instanceId),
  installClientJar:     (instanceId: string, filePath: string) => ipcRenderer.invoke('clientInstall:install', instanceId, filePath),
  uninstallClientJar:   (instanceId: string, fileName: string) => ipcRenderer.invoke('clientInstall:uninstall', instanceId, fileName),

  // Friends (local list)
  listFriends:  () => ipcRenderer.invoke('friends:list'),
  addFriend:    (username: string) => ipcRenderer.invoke('friends:add', username),
  removeFriend: (id: string) => ipcRenderer.invoke('friends:remove', id),

  // External clients
  listExternalClients:   () => ipcRenderer.invoke('externalClients:list'),
  addExternalClient:     () => ipcRenderer.invoke('externalClients:add'),
  removeExternalClient:  (id: string) => ipcRenderer.invoke('externalClients:remove', id),
  renameExternalClient:  (id: string, name: string) => ipcRenderer.invoke('externalClients:rename', id, name),
  launchExternalClient:  (id: string) => ipcRenderer.invoke('externalClients:launch', id),

  // Branding (rank-gated app icon picker)
  listIcons:      () => ipcRenderer.invoke('branding:list'),
  getCurrentIcon: () => ipcRenderer.invoke('branding:getCurrent'),
  setCurrentIcon: (id: string) => ipcRenderer.invoke('branding:setCurrent', id),

  // Skin preview + cosmetics loadout
  fetchSkin:   (username: string) => ipcRenderer.invoke('skin:fetch', username),
  getLoadout:  () => ipcRenderer.invoke('cosmetics:getLoadout'),
  setLoadout:  (loadout: unknown) => ipcRenderer.invoke('cosmetics:setLoadout', loadout),
  syncEquippedCape: (dataUrl: string | null) => ipcRenderer.invoke('cosmetics:syncCape', dataUrl),
  syncLoadout: (items: unknown) => ipcRenderer.invoke('cosmetics:syncLoadout', items),

  // Cosmetics / capes
  listCustomCapes: () => ipcRenderer.invoke('capes:listCustom'),
  uploadCape:      () => ipcRenderer.invoke('capes:upload'),
  removeCape:      (id: string) => ipcRenderer.invoke('capes:remove', id),
  replaceCapeImage: (id: string, dataUrl: string) => ipcRenderer.invoke('capes:replaceImage', id, dataUrl),
  getCapeDataUrl:  (id: string) => ipcRenderer.invoke('capes:getDataUrl', id),
  getSelectedCape: () => ipcRenderer.invoke('capes:getSelected'),
  setSelectedCape: (id: string) => ipcRenderer.invoke('capes:setSelected', id),

  // Launcher's own logs
  readLauncherLog:  (category: string) => ipcRenderer.invoke('launcherLog:read', category),
  clearLauncherLog: (category?: string) => ipcRenderer.invoke('launcherLog:clear', category),
  openLogFolder:    () => ipcRenderer.invoke('launcherLog:openFolder'),
  setDebugMode:     (enabled: boolean) => ipcRenderer.invoke('launcherLog:setDebug', enabled),
  isDebugMode:      () => ipcRenderer.invoke('launcherLog:isDebug'),
  openTrashFolder: () => ipcRenderer.invoke('trash:open'),
  openInstanceLogFolder: (instanceId: string, kind: string) => ipcRenderer.invoke('logs:openFolder', instanceId, kind),

  // Logs / crashes
  listLogs:       (instanceId: string) => ipcRenderer.invoke('logs:list', instanceId),
  readLog:        (instanceId: string, fileName: string) => ipcRenderer.invoke('logs:read', instanceId, fileName),
  listCrashes:    (instanceId: string) => ipcRenderer.invoke('crashes:list', instanceId),
  readCrash:      (instanceId: string, fileName: string) => ipcRenderer.invoke('crashes:read', instanceId, fileName),
  analyzeCrash:   (instanceId: string, fileName: string) => ipcRenderer.invoke('crashes:analyze', instanceId, fileName),

  // Claude API key
  getClaudeApiKey: () => ipcRenderer.invoke('claude:getApiKey'),
  setClaudeApiKey: (key: string) => ipcRenderer.invoke('claude:setApiKey', key),
  hasClaudeApiKey: () => ipcRenderer.invoke('claude:hasApiKey'),

  // Updates
  checkUpdate:        () => ipcRenderer.invoke('update:check'),
  downloadAndRestart: () => ipcRenderer.invoke('update:downloadAndRestart'),
  installUpdate:  () => ipcRenderer.invoke('update:install'),

  // Instance content (mods / resourcepacks / shaderpacks)
  listContent:        (instanceId: string, type: string) => ipcRenderer.invoke('content:list', instanceId, type),
  installContentFile: (instanceId: string, type: string) => ipcRenderer.invoke('content:installFromDisk', instanceId, type),
  removeContent:      (instanceId: string, type: string, fileName: string) => ipcRenderer.invoke('content:remove', instanceId, type, fileName),
  toggleContent:      (instanceId: string, type: string, fileName: string) => ipcRenderer.invoke('content:toggle', instanceId, type, fileName),
  openContentFolder:  (instanceId: string, type: string) => ipcRenderer.invoke('content:openFolder', instanceId, type),

  // Modrinth
  searchModrinth:  (query: string, gameVersion: string, loader: string, type: string, offset?: number) =>
    ipcRenderer.invoke('modrinth:search', query, gameVersion, loader, type, offset),
  getModVersions: (projectId: string, gameVersion: string, loader: string, type: string) =>
    ipcRenderer.invoke('modrinth:getVersions', projectId, gameVersion, loader, type),
  installFromModrinth: (instanceId: string, projectId: string, gameVersion: string, loader: string, type: string, versionId?: string) =>
    ipcRenderer.invoke('modrinth:install', instanceId, projectId, gameVersion, loader, type, versionId),
  identifyModFile: (instanceId: string, type: string, fileName: string) =>
    ipcRenderer.invoke('modrinth:identifyFile', instanceId, type, fileName),
  performancePackStatus: (instanceId: string) => ipcRenderer.invoke('perfpack:status', instanceId),
  installPerformancePack: (instanceId: string) => ipcRenderer.invoke('perfpack:install', instanceId),
  getSystemMemory: () => ipcRenderer.invoke('system:memory'),
  identifyModFolder: (instanceId: string, type: string) =>
    ipcRenderer.invoke('modrinth:identifyFolder', instanceId, type),
  switchModVersion: (instanceId: string, type: string, fileName: string, versionId: string) =>
    ipcRenderer.invoke('modrinth:switchVersion', instanceId, type, fileName, versionId),

  // Modpack presets (Create Instance)
  searchModpacks:  (query: string, gameVersion?: string) => ipcRenderer.invoke('modrinth:searchModpacks', query, gameVersion),
  getModpackVersions: (projectId: string, gameVersion: string) => ipcRenderer.invoke('modrinth:getModpackVersions', projectId, gameVersion),
  installModpack:  (instanceId: string, projectId: string, versionId?: string) => ipcRenderer.invoke('modrinth:installModpack', instanceId, projectId, versionId),
  pickAndInstallModpackFile: (instanceId: string) => ipcRenderer.invoke('modrinth:pickAndInstallModpackFile', instanceId),

  // Events from main
  // Returns an unsubscribe function. The previous `off(channel, cb)` could
  // never work: `on` registers a wrapper, not `cb`, so removeListener(cb)
  // matched nothing and every subscription leaked for the life of the window.
  on: (channel: string, cb: (...args: unknown[]) => void) => {
    const valid = [
      'launch:progress', 'launch:error', 'launch:started', 'launch:exit',
      'update:available', 'update:progress', 'update:error',
      'tryCrystal:status',
    ]
    if (!valid.includes(channel)) return () => {}
    const listener = (_e: unknown, ...args: unknown[]) => cb(...args)
    ipcRenderer.on(channel, listener)
    return () => ipcRenderer.removeListener(channel, listener)
  },
})
