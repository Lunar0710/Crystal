import fs from 'fs'
import path from 'path'
import { isPlainFileName } from '../paths'

/** A recorded fight without its frames, for the list. */
export interface FightSummary {
  instanceId: string
  instanceName: string
  file: string
  start: number
  opponent: string
  won: boolean
  hits: number
  swings: number
  longestCombo: number
  hitsTaken: number
  durationMs: number
  server: string
}

type Instance = { id: string; name: string; gameDir: string }

/**
 * Fights the Nexora client recorded (CombatTracker writes
 * .crystal/fights/<start>.json in each instance). The list reads each file
 * once and remembers it by modification time, since a file holds a few
 * thousand frames the list does not need.
 */
export class FightService {
  private cache = new Map<string, { mtime: number; summary: FightSummary }>()

  constructor(private instances: () => Instance[]) {}

  private dir(instance: Instance): string {
    return path.join(instance.gameDir, '.crystal', 'fights')
  }

  list(): FightSummary[] {
    const out: FightSummary[] = []
    for (const instance of this.instances()) {
      const dir = this.dir(instance)
      let files: string[] = []
      try { files = fs.readdirSync(dir).filter(f => f.endsWith('.json')) } catch { continue }
      for (const file of files) {
        const full = path.join(dir, file)
        try {
          const mtime = fs.statSync(full).mtimeMs
          const cached = this.cache.get(full)
          if (cached && cached.mtime === mtime) { out.push(cached.summary); continue }
          const json = JSON.parse(fs.readFileSync(full, 'utf8'))
          const summary: FightSummary = {
            instanceId: instance.id,
            instanceName: instance.name,
            file,
            start: Number(json.start) || 0,
            opponent: String(json.opponent ?? '?'),
            won: !!json.won,
            hits: Number(json.hits) || 0,
            swings: Number(json.swings) || 0,
            longestCombo: Number(json.longestCombo) || 0,
            hitsTaken: Number(json.hitsTaken) || 0,
            durationMs: Number(json.durationMs) || 0,
            server: String(json.server ?? ''),
          }
          this.cache.set(full, { mtime, summary })
          out.push(summary)
        } catch { /* a half-written or broken file is left out */ }
      }
    }
    return out.sort((a, b) => b.start - a.start)
  }

  /** One fight with its frames, or null. The file name must be a plain name from the list. */
  read(instanceId: string, file: string): unknown {
    const instance = this.instances().find(i => i.id === instanceId)
    if (!instance || !isPlainFileName(file) || !file.endsWith('.json')) return null
    try {
      return JSON.parse(fs.readFileSync(path.join(this.dir(instance), file), 'utf8'))
    } catch {
      return null
    }
  }
}
