import path from 'path'
import os from 'os'
import fs from 'fs'

/**
 * Where Crystal keeps everything that isn't the app itself: instances, the
 * downloaded JDK, caches, logs, cosmetics.
 *
 * Configurable because the default sits on the system drive, and a large
 * modpack collection is exactly the thing people want on another disk. It is
 * read from a module-level variable rather than the store on every call, since
 * this is used deep inside path helpers that have no store reference.
 */
const DEFAULT_ROOT = path.join(os.homedir(), '.crystal')

let dataRoot = DEFAULT_ROOT

export function crystalRoot(): string {
  return dataRoot
}

export function defaultCrystalRoot(): string {
  return DEFAULT_ROOT
}

/** Called once at startup with the stored value, and again whenever the user changes it. */
export function setCrystalRoot(root: string | undefined | null): void {
  dataRoot = root && root.trim() ? root : DEFAULT_ROOT
}

/** crystalRoot()/<segments>, with the directory created. */
export function crystalPath(...segments: string[]): string {
  return path.join(dataRoot, ...segments)
}

/** True if the folder can be used as a data root — i.e. it exists (or can be made) and is writable. */
export function canUseAsRoot(root: string): { ok: boolean; error?: string } {
  try {
    fs.mkdirSync(root, { recursive: true })
    fs.accessSync(root, fs.constants.W_OK)
    return { ok: true }
  } catch (err) {
    return { ok: false, error: err instanceof Error ? err.message : 'Ordner nicht beschreibbar' }
  }
}
