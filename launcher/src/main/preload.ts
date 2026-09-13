import { contextBridge, ipcRenderer } from 'electron'

contextBridge.exposeInMainWorld('crystal', {
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

  // Rank management (server-side gated to the owner rank, see ipc.ts)
  listRankGrants:  () => ipcRenderer.invoke('ranks:list'),
  grantRank:       (username: string, rank: string) => ipcRenderer.invoke('ranks:grant', username, rank),
  revokeRankGrant: (username: string) => ipcRenderer.invoke('ranks:revoke', username),

  // Minecraft
  getVersions:      () => ipcRenderer.invoke('minecraft:getVersions'),
  launchGame:       (opts: object) => ipcRenderer.invoke('minecraft:launch', opts),
  tryWithCrystal:   (instanceId: string) => ipcRenderer.invoke('tryCrystal:run', instanceId),
  selectGameDir:    () => ipcRenderer.invoke('minecraft:selectDir'),
  getInstances:     () => ipcRenderer.invoke('instances:list'),
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

  // Cosmetics / capes
  listCustomCapes: () => ipcRenderer.invoke('capes:listCustom'),
  uploadCape:      () => ipcRenderer.invoke('capes:upload'),
  removeCape:      (id: string) => ipcRenderer.invoke('capes:remove', id),
  getCapeDataUrl:  (id: string) => ipcRenderer.invoke('capes:getDataUrl', id),
  getSelectedCape: () => ipcRenderer.invoke('capes:getSelected'),
  setSelectedCape: (id: string) => ipcRenderer.invoke('capes:setSelected', id),

  // Launcher's own logs
  readLauncherLog:  (category: string) => ipcRenderer.invoke('launcherLog:read', category),
  clearLauncherLog: (category?: string) => ipcRenderer.invoke('launcherLog:clear', category),
  openLogFolder:    () => ipcRenderer.invoke('launcherLog:openFolder'),
  setDebugMode:     (enabled: boolean) => ipcRenderer.invoke('launcherLog:setDebug', enabled),
  isDebugMode:      () => ipcRenderer.invoke('launcherLog:isDebug'),
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
  searchModrinth:  (query: string, gameVersion: string, loader: string, type: string) =>
    ipcRenderer.invoke('modrinth:search', query, gameVersion, loader, type),
  installFromModrinth: (instanceId: string, projectId: string, gameVersion: string, loader: string, type: string) =>
    ipcRenderer.invoke('modrinth:install', instanceId, projectId, gameVersion, loader, type),

  // Modpack presets (Create Instance)
  searchModpacks:  (query: string, gameVersion?: string) => ipcRenderer.invoke('modrinth:searchModpacks', query, gameVersion),
  installModpack:  (instanceId: string, projectId: string) => ipcRenderer.invoke('modrinth:installModpack', instanceId, projectId),
  pickAndInstallModpackFile: (instanceId: string) => ipcRenderer.invoke('modrinth:pickAndInstallModpackFile', instanceId),

  // Events from main
  on: (channel: string, cb: (...args: unknown[]) => void) => {
    const valid = [
      'launch:progress', 'launch:error', 'launch:started',
      'update:available', 'update:progress', 'update:error',
      'tryCrystal:status',
    ]
    if (valid.includes(channel)) ipcRenderer.on(channel, (_e, ...args) => cb(...args))
  },
  off: (channel: string, cb: (...args: unknown[]) => void) => {
    ipcRenderer.removeListener(channel, cb)
  },
})
