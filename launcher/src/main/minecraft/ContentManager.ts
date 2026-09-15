import fs from 'fs'
import path from 'path'
import os from 'os'
import { dialog, BrowserWindow } from 'electron'
import { InstanceManager } from './InstanceManager'
import { crystalPath, isPlainFileName } from '../paths'

export type ContentType = 'mod' | 'resourcepack' | 'shader'

export interface ContentFile {
  fileName: string
  sizeBytes: number
  enabled: boolean
  installedAt: number
}

const FOLDERS: Record<ContentType, string> = {
  mod: 'mods',
  resourcepack: 'resourcepacks',
  shader: 'shaderpacks',
}

const EXTENSIONS: Record<ContentType, string[]> = {
  mod: ['.jar'],
  resourcepack: ['.zip'],
  shader: ['.zip'],
}

const DISABLED_SUFFIX = '.disabled'

/**
 * Reads and writes an instance's content straight off disk — the mods,
 * resourcepacks and shaderpacks folders are the source of truth, so files
 * dropped in manually show up too and nothing can drift out of sync.
 */
export class ContentManager {
  constructor(private instances?: InstanceManager) {}

  // Imported instances live wherever the user keeps them, so the registered
  // gameDir is the only reliable answer. The homedir path is just the fallback
  // for instances Crystal created itself.
  private instanceDir(instanceId: string): string {
    const gameDir = this.instances?.get(instanceId)?.gameDir
    if (gameDir) return gameDir
    return crystalPath('instances', instanceId)
  }

  contentDir(instanceId: string, type: ContentType): string {
    return path.join(this.instanceDir(instanceId), FOLDERS[type])
  }

  list(instanceId: string, type: ContentType): ContentFile[] {
    const dir = this.contentDir(instanceId, type)
    if (!fs.existsSync(dir)) return []

    return fs.readdirSync(dir)
      .filter(name => {
        const base = name.endsWith(DISABLED_SUFFIX) ? name.slice(0, -DISABLED_SUFFIX.length) : name
        return EXTENSIONS[type].some(ext => base.toLowerCase().endsWith(ext))
      })
      .map(name => {
        const stat = fs.statSync(path.join(dir, name))
        return {
          fileName: name,
          sizeBytes: stat.size,
          enabled: !name.endsWith(DISABLED_SUFFIX),
          installedAt: stat.mtimeMs,
        }
      })
      .sort((a, b) => b.installedAt - a.installedAt)
  }

  async installFromDisk(instanceId: string, type: ContentType): Promise<ContentFile | null> {
    const win = BrowserWindow.getFocusedWindow()
    const result = await dialog.showOpenDialog(win!, {
      title: type === 'mod' ? 'Mod-JAR auswählen' : 'ZIP-Datei auswählen',
      properties: ['openFile', 'multiSelections'],
      filters: [{ name: type === 'mod' ? 'Fabric Mod' : 'Archiv', extensions: EXTENSIONS[type].map(e => e.slice(1)) }],
    })
    if (result.canceled || result.filePaths.length === 0) return null

    const dir = this.contentDir(instanceId, type)
    fs.mkdirSync(dir, { recursive: true })

    let last: ContentFile | null = null
    for (const src of result.filePaths) {
      const fileName = path.basename(src)
      fs.copyFileSync(src, path.join(dir, fileName))
      const stat = fs.statSync(path.join(dir, fileName))
      last = { fileName, sizeBytes: stat.size, enabled: true, installedAt: stat.mtimeMs }
    }
    return last
  }

  remove(instanceId: string, type: ContentType, fileName: string): boolean {
    if (!isPlainFileName(fileName)) return false
    const target = path.join(this.contentDir(instanceId, type), fileName)
    if (!fs.existsSync(target)) return false
    fs.unlinkSync(target)
    return true
  }

  /** Enable/disable by renaming — the same convention every Minecraft launcher uses. */
  toggle(instanceId: string, type: ContentType, fileName: string): string | null {
    if (!isPlainFileName(fileName)) return null
    const dir = this.contentDir(instanceId, type)
    const current = path.join(dir, fileName)
    if (!fs.existsSync(current)) return null

    const next = fileName.endsWith(DISABLED_SUFFIX)
      ? fileName.slice(0, -DISABLED_SUFFIX.length)
      : fileName + DISABLED_SUFFIX

    fs.renameSync(current, path.join(dir, next))
    return next
  }
}
