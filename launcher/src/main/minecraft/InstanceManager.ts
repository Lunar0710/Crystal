import Store from 'electron-store'
import { randomUUID } from 'crypto'
import { dialog, BrowserWindow } from 'electron'
import path from 'path'
import { logger } from '../logs/Logger'
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
  /** Launch with Nexora's own mod (HUD + modules) or as a plain modded game. */
  useCrystalClient: boolean
  /** The account this instance always starts with; unset means the active one. */
  accountUuid?: string
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

  /**
   * A copy of an instance under a new name: mods, settings, packs and the
   * Nexora config. Logs, crash reports, world backups and screenshots stay
   * behind; worlds only when asked for (they can be large).
   */
  async duplicate(id: string, withWorlds: boolean): Promise<Instance | null> {
    const source = this.get(id)
    if (!source) return null
    const { id: _id, createdAt: _c, gameDir: from, imported: _i, ...rest } = source
    const copy = this.create({ ...rest, name: `${source.name} (Kopie)`, gameDir: '' } as Omit<Instance, 'id' | 'createdAt'>)
    // Game files the launcher shares between instances: an instance imported
    // from .minecraft has them in its folder, several GB that the copy doesn't need.
    const skip = new Set(['logs', 'crash-reports', 'backups', 'screenshots', 'versions', 'libraries', 'assets', 'runtime',
      'webcache2', ...(withWorlds ? [] : ['saves'])])
    // Recorded fights belong to the original; copied, every fight would count twice on the Fights page.
    const fights = path.join(from, '.crystal', 'fights')
    try {
      for (const entry of fs.readdirSync(from)) {
        if (skip.has(entry) || entry === 'session.lock' || /^hs_err_pid\d+\.log$/.test(entry)) continue
        await fs.promises.cp(path.join(from, entry), path.join(copy.gameDir, entry), {
          recursive: true,
          // The boot cache (.jsa) is rebuilt on the copy's first exit; no need to copy it.
          filter: src => path.basename(src) !== 'session.lock' && !src.endsWith('.jsa') && src !== fights && !src.startsWith(fights + path.sep),
        })
      }
    } catch (err) {
      // A half copy (disk full, a locked file) is taken out again rather than left in the list.
      logger.warn('launcher', `Kopie von ${source.name} fehlgeschlagen`, String(err))
      this.store.set('instances', this.list().filter(i => i.id !== copy.id))
      await fs.promises.rm(copy.gameDir, { recursive: true, force: true }).catch(() => {})
      return null
    }
    return copy
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
   * Removes an instance from Nexora. Its folder is never destroyed: it holds
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
