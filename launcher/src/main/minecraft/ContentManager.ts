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
const PROFILES_FILE = 'crystal-mod-profiles.json'
/** The launcher adds and removes the Crystal jar itself; profiles leave it alone. */
const MANAGED_MOD = /^crystal-client-/

export interface ModProfiles {
  active: string | null
  profiles: { name: string; mods: number }[]
}

interface ProfilesFile {
  active: string | null
  profiles: Record<string, string[]>
}

export function isValidProfileName(name: unknown): name is string {
  return typeof name === 'string' && name.trim().length > 0 && name.trim().length <= 32
}

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

  // ---- Mod profiles: named sets of enabled mods, switched by enabling/disabling files.

  private readProfiles(instanceId: string): ProfilesFile {
    try {
      const raw = JSON.parse(fs.readFileSync(path.join(this.instanceDir(instanceId), PROFILES_FILE), 'utf8'))
      const profiles: Record<string, string[]> = {}
      for (const [name, mods] of Object.entries(raw?.profiles ?? {})) {
        if (isValidProfileName(name) && Array.isArray(mods)) profiles[name] = mods.filter((m): m is string => typeof m === 'string')
      }
      return { active: typeof raw?.active === 'string' && profiles[raw.active] ? raw.active : null, profiles }
    } catch {
      return { active: null, profiles: {} }
    }
  }

  private writeProfiles(instanceId: string, data: ProfilesFile): void {
    const dir = this.instanceDir(instanceId)
    fs.mkdirSync(dir, { recursive: true })
    fs.writeFileSync(path.join(dir, PROFILES_FILE), JSON.stringify(data, null, 2))
  }

  listProfiles(instanceId: string): ModProfiles {
    const data = this.readProfiles(instanceId)
    return {
      active: data.active,
      profiles: Object.entries(data.profiles)
        .map(([name, mods]) => ({ name, mods: mods.length }))
        .sort((a, b) => a.name.localeCompare(b.name, 'de')),
    }
  }

  /** Saves the mods that are enabled right now under this name, and makes it the active profile. */
  saveProfile(instanceId: string, name: string): ModProfiles | null {
    if (!isValidProfileName(name)) return null
    const enabled = this.list(instanceId, 'mod')
      .filter(f => f.enabled && !MANAGED_MOD.test(f.fileName))
      .map(f => f.fileName)
    const data = this.readProfiles(instanceId)
    data.profiles[name.trim()] = enabled
    data.active = name.trim()
    this.writeProfiles(instanceId, data)
    return this.listProfiles(instanceId)
  }

  /** Enables exactly the profile's mods and disables every other one. */
  applyProfile(instanceId: string, name: string): ModProfiles | null {
    const data = this.readProfiles(instanceId)
    const wanted = data.profiles[name]
    if (!wanted) return null
    const keep = new Set(wanted)
    for (const file of this.list(instanceId, 'mod')) {
      if (MANAGED_MOD.test(file.fileName)) continue
      const base = file.enabled ? file.fileName : file.fileName.slice(0, -DISABLED_SUFFIX.length)
      if (keep.has(base) !== file.enabled) this.toggle(instanceId, 'mod', file.fileName)
    }
    data.active = name
    this.writeProfiles(instanceId, data)
    return this.listProfiles(instanceId)
  }

  deleteProfile(instanceId: string, name: string): ModProfiles {
    const data = this.readProfiles(instanceId)
    delete data.profiles[name]
    if (data.active === name) data.active = null
    this.writeProfiles(instanceId, data)
    return this.listProfiles(instanceId)
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
