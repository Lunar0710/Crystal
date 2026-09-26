import { contextBridge, ipcRenderer } from 'electron'

/**
 * The splash window gets only these three calls, not Node: it shows startup
 * status and the update prompt, nothing more.
 */
contextBridge.exposeInMainWorld('splash', {
  onStatus: (cb: (data: { text: string; percent?: number }) => void) => ipcRenderer.on('splash:status', (_e, data) => cb(data)),
  onUpdate: (cb: (data: { current: string; version: string; manual?: boolean }) => void) => ipcRenderer.on('splash:update', (_e, data) => cb(data)),
  choose: (choice: 'update' | 'later') => ipcRenderer.send('splash:choice', choice === 'update' ? 'update' : 'later'),
})
