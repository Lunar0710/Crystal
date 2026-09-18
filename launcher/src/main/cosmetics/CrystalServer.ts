import fs from 'fs'
import path from 'path'
import Store from 'electron-store'
import { logger } from '../logs/Logger'

/**
 * The Crystal server the game connects to for other players' emotes and
 * cosmetics (server/ in the repo). Empty until one is running: then nothing
 * connects anywhere. Settings can point one launcher at another server.
 */
export const DEFAULT_CRYSTAL_SERVER = ''

/**
 * Only ws:// or wss:// with a plain host, port and path: the value ends up
 * as a JVM option, so anything else could smuggle in options of its own.
 */
const ADDRESS = /^wss?:\/\/[A-Za-z0-9.-]+(:\d{1,5})?(\/[A-Za-z0-9._~/-]*)?$/

export function isValidCrystalServer(value: unknown): value is string {
  return typeof value === 'string' && ADDRESS.test(value.trim())
}

export function crystalServerAddress(store: Store): string | null {
  const own = store.get('crystalServer')
  const value = typeof own === 'string' && own.trim() ? own.trim() : DEFAULT_CRYSTAL_SERVER
  return isValidCrystalServer(value) ? value : null
}

/**
 * cosmetics/equipped.json: which built-in cape is worn, as an id. The game
 * sends that id to the Crystal server; an uploaded cape is null and stays private.
 */
export function writeEquippedCapeId(dir: string, capeId: unknown): void {
  const id = typeof capeId === 'string' && /^[a-z]+-\d{1,4}$/.test(capeId) ? capeId : null
  const target = path.join(dir, 'equipped.json')
  const json = JSON.stringify({ cape: id })
  try {
    if (fs.existsSync(target) && fs.readFileSync(target, 'utf8') === json) return
    fs.writeFileSync(target, json)
  } catch (err) {
    logger.warn('launcher', 'equipped.json konnte nicht geschrieben werden', String(err))
  }
}
