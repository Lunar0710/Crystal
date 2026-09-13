import Store from 'electron-store'
import { randomUUID } from 'crypto'
import { dialog, BrowserWindow } from 'electron'
import path from 'path'
import os from 'os'
import fs from 'fs'
import { crystalPath } from '../paths'

export interface Instance {
  id: string
  name: string
  version: string
  loader: string
  gameDir: string
  createdAt: number
  /** Launch with Crystal's own mod (HUD + modules) or as a plain modded game. */
  useCrystalClient: boolean
  /** Set when the folder lives outside ~/.crystal and must not be deleted. */
  imported?: boolean
  icon?: string
}

// A fresh instance starts empty; these are created so the folders exist for
// drag-and-drop and for the content browser to write into.
const CONTENT_FOLDERS = ['mods', 'resourcepacks', 'shaderpacks']

export class InstanceManager {
  private store: Store

  constructor(store: Store) {
    this.store = store
  }

  list(): Instance[] {
    return (this.store.get('instances', []) as Instance[]).map(i => ({
      ...i,
      // Older entries predate these fields.
      useCrystalClient: i.useCrystalClient ?? true,
      loader: i.loader ?? 'fabric',
    }))
  }

  get(id: string): Instance | null {
    return this.list().find(i => i.id === id) ?? null
  }

  create(data: Omit<Instance, 'id' | 'createdAt'>): Instance {
    const instance: Instance = {
      id: randomUUID(),
      createdAt: Date.now(),
      ...data,
    }

    const gameDir = crystalPath('instances', instance.id)
    fs.mkdirSync(gameDir, { recursive: true })
    for (const folder of CONTENT_FOLDERS) {
      fs.mkdirSync(path.join(gameDir, folder), { recursive: true })
    }
    instance.gameDir = gameDir

    const instances = this.list()
    instances.push(instance)
    this.store.set('instances', instances)
    return instance
  }

  /**
   * Registers an existing Minecraft folder (MultiMC, Prism, vanilla .minecraft
   * or any manual folder) without copying it — the files stay where they are.
   */
  async importFromDisk(version: string): Promise<Instance | null> {
    const win = BrowserWindow.getFocusedWindow()
    const result = await dialog.showOpenDialog(win!, {
      title: 'Instance-Ordner auswählen (der Ordner, der "mods" enthält)',
      properties: ['openDirectory'],
    })
    if (result.canceled || result.filePaths.length === 0) return null

    let gameDir = result.filePaths[0]

    // MultiMC/Prism keep the actual game files in a ".minecraft" subfolder.
    const nested = path.join(gameDir, '.minecraft')
    if (fs.existsSync(nested) && fs.statSync(nested).isDirectory()) {
      gameDir = nested
    }

    for (const folder of CONTENT_FOLDERS) {
      fs.mkdirSync(path.join(gameDir, folder), { recursive: true })
    }

    const instance: Instance = {
      id: randomUUID(),
      name: path.basename(result.filePaths[0]),
      version,
      loader: 'fabric',
      gameDir,
      createdAt: Date.now(),
      useCrystalClient: false,
      imported: true,
    }

    const instances = this.list()
    instances.push(instance)
    this.store.set('instances', instances)
    return instance
  }

  update(id: string, patch: Partial<Instance>): Instance | null {
    const instances = this.list()
    const index = instances.findIndex(i => i.id === id)
    if (index === -1) return null

    instances[index] = { ...instances[index], ...patch, id: instances[index].id }
    this.store.set('instances', instances)
    return instances[index]
  }

  /**
   * Removes an instance from Crystal. Its folder is never destroyed: it holds
   * the player's worlds, and a single mis-click used to rm -rf all of them.
   * The folder is moved into <data root>/trash instead, where it can be
   * restored by hand or cleared deliberately. Imported folders belong to the
   * user and are only unregistered.
   */
  delete(id: string): { movedTo: string | null } {
    const instance = this.get(id)
    let movedTo: string | null = null

    if (instance && !instance.imported && instance.gameDir && fs.existsSync(instance.gameDir)) {
      const safeName = instance.name.replace(/[^\w.-]+/g, '_').slice(0, 40) || 'instanz'
      const stamp = new Date().toISOString().replace(/[:.]/g, '-')
      const target = crystalPath('trash', `${safeName}-${stamp}`)
      fs.mkdirSync(path.dirname(target), { recursive: true })

      try {
        fs.renameSync(instance.gameDir, target)
      } catch (err) {
        // rename can't cross drives (EXDEV) once the data root lives elsewhere;
        // copy first and only remove the original after the copy succeeded.
        if ((err as NodeJS.ErrnoException).code !== 'EXDEV') throw err
        fs.cpSync(instance.gameDir, target, { recursive: true })
        fs.rmSync(instance.gameDir, { recursive: true, force: true })
      }
      movedTo = target
    }

    this.store.set('instances', this.list().filter(i => i.id !== id))
    return { movedTo }
  }
}
