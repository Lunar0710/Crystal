import fs from 'fs'
import path from 'path'
import { JarReader } from './jarReader'

/**
 * Unpacks a ZIP archive into `dir`, and only into it. Every entry's path is
 * resolved and must stay inside the target folder; anything that would land
 * elsewhere ("../", absolute paths, drive letters) is skipped. Entries are
 * always written as plain files, never as links, so a crafted archive can't
 * reach outside through a symlink either. Replaces extract-zip, which has
 * unfixed advisories for exactly that (GHSA-jmr9-qjv8-65gv, GHSA-7pqw-9j4j-h8q3).
 */
export async function safeExtract(zipPath: string, dir: string): Promise<number> {
  const root = path.resolve(dir)
  fs.mkdirSync(root, { recursive: true })
  const zip = new JarReader(zipPath)
  let written = 0
  for (const name of zip.names()) {
    if (name.endsWith('/')) continue
    const target = path.resolve(root, name)
    if (target !== root && !target.startsWith(root + path.sep)) continue
    const data = zip.read(name)
    if (!data) continue
    fs.mkdirSync(path.dirname(target), { recursive: true })
    fs.writeFileSync(target, data)
    written++
    // Let the event loop breathe on big archives (a Java runtime has hundreds of files).
    if (written % 200 === 0) await new Promise(resolve => setImmediate(resolve))
  }
  return written
}
