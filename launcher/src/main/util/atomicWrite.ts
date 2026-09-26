import fs from 'fs'

/**
 * Writes a file the game reads while it runs (cosmetics, rank, theme, its own
 * options.txt) in one step: into "<file>.tmp" first, then renamed over the
 * target. Written in place, the game could read it half-written, and a crash
 * or power cut mid-write left it broken for good.
 */
export function writeFileAtomic(file: string, data: string | Buffer): void {
  const temp = `${file}.tmp`
  fs.writeFileSync(temp, data)
  try {
    fs.renameSync(temp, file)
  } catch {
    // Windows refuses the rename now and then (a virus scanner holding the
    // file); writing in place as before is still better than not at all.
    fs.rmSync(temp, { force: true })
    fs.writeFileSync(file, data)
  }
}
