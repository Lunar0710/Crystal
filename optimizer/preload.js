// The page's only door to the system: a fixed list of calls, nothing else.
const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('lunar', {
  hardware: part => ipcRenderer.invoke('hw:static', part),
  hardwareParts: () => ipcRenderer.invoke('hw:parts'),
  live: want => ipcRenderer.invoke('hw:live', want),
  processes: () => ipcRenderer.invoke('proc:list'),
  kill: pids => ipcRenderer.invoke('proc:kill', pids),
  ping: (host, port) => ipcRenderer.invoke('net:ping', { host, port }),
  dnsBench: current => ipcRenderer.invoke('net:dns', current),
  netInfo: () => ipcRenderer.invoke('net:info'),
  checkUpdate: () => ipcRenderer.invoke('app:update'),
  onUpdate: fn => ipcRenderer.on('update:state', (_e, st) => fn(st)),
  installUpdate: () => ipcRenderer.invoke('update:install'),
  boostList: () => ipcRenderer.invoke('boost:list'),
  boostClose: exes => ipcRenderer.invoke('boost:close', exes),
  engine: (action, opts = {}) => ipcRenderer.invoke('engine', { action, ...opts }),
  info: () => ipcRenderer.invoke('app:info'),
  open: url => ipcRenderer.invoke('shell:open', url),
  taskManager: () => ipcRenderer.invoke('shell:taskmgr'),
  reboot: () => ipcRenderer.invoke('app:reboot'),
  window: {
    minimize: () => ipcRenderer.invoke('win:minimize'),
    maximize: () => ipcRenderer.invoke('win:maximize'),
    close: () => ipcRenderer.invoke('win:close'),
    onState: fn => ipcRenderer.on('win:state', (_e, s) => fn(s)),
  },
})
