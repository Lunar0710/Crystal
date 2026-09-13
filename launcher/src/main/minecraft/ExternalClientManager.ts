import { spawn } from 'child_process'
import { dialog, BrowserWindow } from 'electron'
import path from 'path'
import fs from 'fs'
import Store from 'electron-store'

export interface ExternalClientProfile {
  id: string
  name: string
  executablePath: string
  args: string
  addedAt: number
}

export class ExternalClientManager {
  private store: Store

  constructor(store: Store) {
    this.store = store
  }

  list(): ExternalClientProfile[] {
    return (this.store.get('externalClients', []) as ExternalClientProfile[])
  }

  async addViaFilePicker(): Promise<ExternalClientProfile | null> {
    const win = BrowserWindow.getFocusedWindow()
    const result = await dialog.showOpenDialog(win!, {
      title: 'Select client executable or launcher (.exe, .jar, .bat)',
      properties: ['openFile'],
      filters: [
        { name: 'Executables', extensions: ['exe', 'jar', 'bat', 'cmd'] },
        { name: 'All Files', extensions: ['*'] },
      ],
    })
    if (result.canceled || result.filePaths.length === 0) return null

    const execPath = result.filePaths[0]
    const profile: ExternalClientProfile = {
      id: Date.now().toString(),
      name: path.basename(execPath).replace(/\.(exe|jar|bat|cmd)$/i, ''),
      executablePath: execPath,
      args: '',
      addedAt: Date.now(),
    }

    const list = this.list()
    list.push(profile)
    this.store.set('externalClients', list)
    return profile
  }

  remove(id: string) {
    this.store.set('externalClients', this.list().filter(c => c.id !== id))
  }

  rename(id: string, name: string) {
    const list = this.list()
    const profile = list.find(c => c.id === id)
    if (profile) {
      profile.name = name
      this.store.set('externalClients', list)
    }
  }

  launch(id: string): boolean {
    const profile = this.list().find(c => c.id === id)
    if (!profile || !fs.existsSync(profile.executablePath)) return false

    const args = profile.args.split(' ').filter(Boolean)
    const ext = path.extname(profile.executablePath).toLowerCase()

    if (ext === '.jar') {
      spawn('java', ['-jar', profile.executablePath, ...args], { detached: true, stdio: 'ignore' }).unref()
    } else {
      spawn(profile.executablePath, args, { detached: true, stdio: 'ignore', cwd: path.dirname(profile.executablePath) }).unref()
    }
    return true
  }
}
