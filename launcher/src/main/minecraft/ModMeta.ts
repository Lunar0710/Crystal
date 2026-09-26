import fs from 'fs'
import path from 'path'
import { JarReader } from '../util/jarReader'

/**
 * Name, authors and icon of each mod, read from the fabric.mod.json inside
 * its jar (the icon is a png in the jar too), for the mod grid. Offline and
 * for every mod, Modrinth or not. Cached per file and modification time, so
 * opening the tab again reads nothing.
 */

export interface ModMeta {
  name: string
  authors: string[]
  icon: string | null
}

const cache = new Map<string, { mtime: number; meta: ModMeta | null }>()

function authorName(a: unknown): string | null {
  if (typeof a === 'string') return a
  if (a && typeof a === 'object' && typeof (a as any).name === 'string') return (a as any).name
  return null
}

function read(file: string): ModMeta | null {
  try {
    const jar = new JarReader(file)
    const text = jar.readText('fabric.mod.json') ?? jar.readText('quilt.mod.json')
    if (!text) return null
    // Some mods put raw newlines inside strings; JSON.parse would refuse the whole file.
    const json = JSON.parse(text.replace(/[\r\n\t]+/g, ' '))
    const info = json.quilt_loader?.metadata ?? json
    let iconPath: unknown = info.icon
    // An icon given per size: take the largest.
    if (iconPath && typeof iconPath === 'object') {
      const sizes = Object.keys(iconPath as object).map(Number).filter(n => !Number.isNaN(n)).sort((a, b) => b - a)
      iconPath = sizes.length ? (iconPath as any)[String(sizes[0])] : null
    }
    let icon: string | null = null
    if (typeof iconPath === 'string') {
      const png = jar.read(iconPath.replace(/^\//, ''))
      if (png && png.length < 512 * 1024) icon = `data:image/png;base64,${png.toString('base64')}`
    }
    const authors = (Array.isArray(info.authors) ? info.authors : Object.keys(info.contributors ?? {}))
      .map(authorName).filter((a: string | null): a is string => !!a)
    return { name: String(info.name || json.id || path.basename(file)), authors, icon }
  } catch {
    return null
  }
}

/** Metadata for every jar in the folder, by file name (disabled ".jar.disabled" files included). */
export function modMetaIn(dir: string): Record<string, ModMeta> {
  const out: Record<string, ModMeta> = {}
  let files: string[] = []
  try { files = fs.readdirSync(dir) } catch { return out }
  for (const fileName of files) {
    if (!/\.jar(\.disabled)?$/i.test(fileName)) continue
    const file = path.join(dir, fileName)
    let mtime = 0
    try { mtime = fs.statSync(file).mtimeMs } catch { continue }
    const hit = cache.get(file)
    const meta = hit && hit.mtime === mtime ? hit.meta : read(file)
    if (!hit || hit.mtime !== mtime) cache.set(file, { mtime, meta })
    if (meta) out[fileName] = meta
  }
  return out
}
