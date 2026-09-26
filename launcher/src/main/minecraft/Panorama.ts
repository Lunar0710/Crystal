import fs from 'fs'
import path from 'path'
import { crystalRoot } from '../paths'
import { JarReader } from '../util/jarReader'

/**
 * The title-screen panorama of a Minecraft version, for the start banner:
 * all six faces of the cube around the camera (0 ahead, 1 right, 2 behind,
 * 3 left, 4 up, 5 down). Newer versions ship the real images as assets (the
 * jar only holds tiny placeholders), older ones inside the jar. Nothing is
 * downloaded: a version that was never started has no panorama yet.
 */

const FACES = [0, 1, 2, 3, 4, 5]
const ENTRY = (i: number) => `minecraft/textures/gui/title/background/panorama_${i}.png`
const cache = new Map<string, string[] | null>()

function fromAssets(versionId: string): Buffer[] | null {
  const root = crystalRoot()
  try {
    const versionJson = JSON.parse(fs.readFileSync(path.join(root, 'versions', versionId, `${versionId}.json`), 'utf8'))
    const indexId = versionJson.assetIndex?.id
    if (!indexId) return null
    const objects = JSON.parse(fs.readFileSync(path.join(root, 'assets', 'indexes', `${indexId}.json`), 'utf8')).objects || {}
    const faces = FACES.map(i => {
      const hash: string | undefined = objects[ENTRY(i)]?.hash
      if (!hash) return null
      const file = path.join(root, 'assets', 'objects', hash.slice(0, 2), hash)
      return fs.existsSync(file) ? fs.readFileSync(file) : null
    })
    return faces.every(Boolean) ? faces as Buffer[] : null
  } catch { return null }
}

function fromJar(versionId: string): Buffer[] | null {
  const jar = path.join(crystalRoot(), 'versions', versionId, `${versionId}.jar`)
  if (!fs.existsSync(jar)) return null
  try {
    const reader = new JarReader(jar)
    const faces = FACES.map(i => reader.read('assets/' + ENTRY(i)))
    // The placeholders in newer jars are a few dozen bytes.
    return faces.every(f => f && f.length > 1024) ? faces as Buffer[] : null
  } catch { return null }
}

/** Six data URLs (faces 0 to 5: ahead, right, behind, left, up, down), or null when the version has none on this PC. */
export function panoramaFor(versionId: string): string[] | null {
  if (!/^[\w.\-]+$/.test(versionId)) return null
  if (cache.has(versionId)) return cache.get(versionId)!
  const faces = fromAssets(versionId) || fromJar(versionId)
  const urls = faces ? faces.map(b => `data:image/png;base64,${b.toString('base64')}`) : null
  // Only a hit is kept: the version may be downloaded later in this session.
  if (urls) cache.set(versionId, urls)
  return urls
}
