import fs from 'fs'
import path from 'path'
import { clipboard, nativeImage, shell } from 'electron'
import { InstanceManager } from '../minecraft/InstanceManager'
import { isPlainFileName, resolveInside } from '../paths'
import { logger } from '../logs/Logger'

export interface Screenshot {
  instanceId: string
  instanceName: string
  fileName: string
  takenAt: number
  sizeBytes: number
}

const IMAGE = /\.(png|jpe?g)$/i

/**
 * Every screenshot from every instance in one place. Files are always
 * addressed by instance id + plain file name and resolved inside that
 * instance's screenshots folder, so the renderer can never point this at an
 * arbitrary path on disk.
 */
export class ScreenshotService {
  private thumbCache = new Map<string, string>()

  constructor(private instances: InstanceManager) {}

  list(): Screenshot[] {
    const out: Screenshot[] = []
    for (const instance of this.instances.list()) {
      const dir = path.join(instance.gameDir, 'screenshots')
      let names: string[]
      try {
        names = fs.readdirSync(dir)
      } catch {
        continue // no screenshots folder yet
      }
      for (const fileName of names) {
        if (!IMAGE.test(fileName)) continue
        try {
          const stat = fs.statSync(path.join(dir, fileName))
          if (!stat.isFile()) continue
          out.push({ instanceId: instance.id, instanceName: instance.name, fileName, takenAt: stat.mtimeMs, sizeBytes: stat.size })
        } catch { /* file vanished while listing */ }
      }
    }
    return out.sort((a, b) => b.takenAt - a.takenAt)
  }

  /** Absolute path of a screenshot, or null if the request doesn't name a real one. */
  private resolve(instanceId: string, fileName: string): string | null {
    const instance = this.instances.get(instanceId)
    if (!instance || !isPlainFileName(fileName) || !IMAGE.test(fileName)) return null
    const file = resolveInside(path.join(instance.gameDir, 'screenshots'), fileName)
    return file && fs.existsSync(file) ? file : null
  }

  /** Small preview as a data URL, cached per file and modification time. */
  thumbnail(instanceId: string, fileName: string): string | null {
    const file = this.resolve(instanceId, fileName)
    if (!file) return null
    const key = `${file}:${fs.statSync(file).mtimeMs}`
    const cached = this.thumbCache.get(key)
    if (cached) return cached
    const image = nativeImage.createFromPath(file)
    if (image.isEmpty()) return null
    const url = image.resize({ width: 360, quality: 'good' }).toDataURL()
    if (this.thumbCache.size > 400) this.thumbCache.clear()
    this.thumbCache.set(key, url)
    return url
  }

  /** Full-size image for the viewer. */
  full(instanceId: string, fileName: string): string | null {
    const file = this.resolve(instanceId, fileName)
    if (!file) return null
    const image = nativeImage.createFromPath(file)
    return image.isEmpty() ? null : image.toDataURL()
  }

  copy(instanceId: string, fileName: string): boolean {
    const file = this.resolve(instanceId, fileName)
    if (!file) return false
    const image = nativeImage.createFromPath(file)
    if (image.isEmpty()) return false
    clipboard.writeImage(image)
    return true
  }

  showInFolder(instanceId: string, fileName: string): boolean {
    const file = this.resolve(instanceId, fileName)
    if (!file) return false
    shell.showItemInFolder(file)
    return true
  }

  /** Moves the file to the system trash, so a mistake can still be undone. */
  async remove(instanceId: string, fileName: string): Promise<boolean> {
    const file = this.resolve(instanceId, fileName)
    if (!file) return false
    try {
      await shell.trashItem(file)
      return true
    } catch (err) {
      logger.warn('launcher', 'Screenshot konnte nicht gelöscht werden', String(err))
      return false
    }
  }
}
