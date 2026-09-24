import fs from 'fs'
import path from 'path'
import Store from 'electron-store'

/** One game session, as the launcher saw it. */
export interface SessionRecord {
  start: number
  end: number
  version: string
  instanceId: string
  instanceName: string
  crystal: boolean
  /** Time on each server during the session, in ms ("Einzelspieler" for local worlds). */
  servers: Record<string, number>
  /** How the game ran, when it was measured (Nexora instances, launcher 1.5 and later). */
  perf?: SessionPerf
}

export interface SessionPerf {
  /** Frame rate in a world: average and 1% low, from the client's perf-session.json. */
  avgFps?: number
  lowFps?: number
  /** Nexora version the session ran on, so a drop after an update shows. */
  nexora?: string
  cpuAvg?: number
  ramAvgMb?: number
  ramPeakMb?: number
}

/** One session in the performance history. */
export interface PerfPoint {
  start: number
  instanceName: string
  version: string
  perf: SessionPerf
}

export interface StatsSummary {
  playtimeMs: number
  sessions: number
  longestSessionMs: number
  /** Days in a row, up to today, with at least one session. */
  streakDays: number
  /** The last 30 days, oldest first: local date (YYYY-MM-DD) and playtime. */
  days: { date: string; ms: number }[]
  byVersion: { name: string; ms: number }[]
  byServer: { name: string; ms: number }[]
  byInstance: { name: string; ms: number }[]
  /** Sessions recorded per session (not just totals) — older launchers only kept totals. */
  trackedSince: number | null
  /** The last sessions with frame rate data, oldest first. */
  perf: PerfPoint[]
}

const MAX_RECORDS = 2000
const SINGLEPLAYER = 'Einzelspieler'

function localDate(ms: number): string {
  const d = new Date(ms)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/**
 * Playtime statistics: every session with its version, instance and the
 * servers played on. Servers come from the game's own log ("Connecting to
 * host, port"); time is split between connects, so a session that hopped
 * from Hypixel to a friend's server counts for both.
 */
export class StatsService {
  constructor(private store: Store) {}

  private records(): SessionRecord[] {
    const raw = this.store.get('stats.log')
    return Array.isArray(raw) ? raw as SessionRecord[] : []
  }

  record(session: Omit<SessionRecord, 'servers' | 'perf'>, gameDir: string, usage?: { cpuAvg: number; ramAvgMb: number; ramPeakMb: number } | null): void {
    const servers = this.serversFromLog(path.join(gameDir, 'crystal-launch.log'), session.start, session.end)
    const fps = this.fpsFromClient(gameDir, session.start)
    const perf: SessionPerf | undefined = fps || usage ? { ...fps, ...(usage ?? {}) } : undefined
    const next = [...this.records(), { ...session, servers, ...(perf ? { perf } : {}) }].slice(-MAX_RECORDS)
    this.store.set('stats.log', next)
    this.store.set('stats.playtimeMs', (Number(this.store.get('stats.playtimeMs')) || 0) + (session.end - session.start))
    this.store.set('stats.sessions', (Number(this.store.get('stats.sessions')) || 0) + 1)
  }

  /**
   * The frame rate the Nexora client wrote for this session. The file is
   * rewritten each session, so one that started before this launch is left
   * over from an earlier one and ignored.
   */
  private fpsFromClient(gameDir: string, start: number): Pick<SessionPerf, 'avgFps' | 'lowFps' | 'nexora'> | null {
    try {
      const json = JSON.parse(fs.readFileSync(path.join(gameDir, '.crystal', 'perf-session.json'), 'utf8'))
      if (typeof json?.startedAt !== 'number' || json.startedAt < start - 60_000) return null
      if (typeof json.avgFps !== 'number') return null
      return { avgFps: json.avgFps, lowFps: typeof json.lowFps === 'number' ? json.lowFps : undefined, nexora: typeof json.nexora === 'string' ? json.nexora : undefined }
    } catch {
      return null
    }
  }

  /** Splits [start, end] by the server connects the game logged. */
  private serversFromLog(logPath: string, start: number, end: number): Record<string, number> {
    const joins: { at: number; name: string }[] = []
    try {
      const text = fs.readFileSync(logPath, 'utf8')
      // XML log events: <log4j:Event ... timestamp="123"> ... <![CDATA[Connecting to host, 25565]]>
      const event = /timestamp="(\d+)"[\s\S]*?<!\[CDATA\[([^\]]*)\]\]>/g
      for (const m of text.matchAll(event)) {
        const at = Number(m[1])
        const connect = m[2].match(/^Connecting to ([^,\s]+), \d+/)
        if (connect) joins.push({ at, name: connect[1].toLowerCase() })
        else if (/^Starting integrated minecraft server/.test(m[2])) joins.push({ at, name: SINGLEPLAYER })
      }
    } catch {
      // No log (external client, deleted instance): the session just has no servers.
    }
    const servers: Record<string, number> = {}
    joins.sort((a, b) => a.at - b.at)
    joins.forEach((join, i) => {
      const from = Math.max(start, join.at)
      const to = Math.min(end, joins[i + 1]?.at ?? end)
      if (to > from) servers[join.name] = (servers[join.name] ?? 0) + (to - from)
    })
    return servers
  }

  summary(now = Date.now()): StatsSummary {
    const records = this.records()
    const add = (map: Map<string, number>, key: string, ms: number) => map.set(key, (map.get(key) ?? 0) + ms)
    const perDay = new Map<string, number>()
    const perVersion = new Map<string, number>()
    const perServer = new Map<string, number>()
    const perInstance = new Map<string, number>()
    let longest = 0

    for (const r of records) {
      const ms = Math.max(0, r.end - r.start)
      longest = Math.max(longest, ms)
      add(perDay, localDate(r.start), ms)
      add(perVersion, r.version, ms)
      add(perInstance, r.instanceName || r.instanceId, ms)
      for (const [name, t] of Object.entries(r.servers ?? {})) add(perServer, name, t)
    }

    const days: StatsSummary['days'] = []
    for (let i = 29; i >= 0; i--) {
      const date = localDate(now - i * 86_400_000)
      days.push({ date, ms: perDay.get(date) ?? 0 })
    }

    let streak = 0
    for (let i = 0; ; i++) {
      if (!perDay.has(localDate(now - i * 86_400_000))) {
        // Today without a session yet doesn't break yesterday's streak.
        if (i === 0) continue
        break
      }
      streak++
    }

    const top = (map: Map<string, number>, n: number) =>
      [...map.entries()].map(([name, ms]) => ({ name, ms })).sort((a, b) => b.ms - a.ms).slice(0, n)

    return {
      playtimeMs: Number(this.store.get('stats.playtimeMs')) || 0,
      sessions: Number(this.store.get('stats.sessions')) || 0,
      longestSessionMs: longest,
      streakDays: streak,
      days,
      byVersion: top(perVersion, 6),
      byServer: top(perServer, 8),
      byInstance: top(perInstance, 6),
      trackedSince: records[0]?.start ?? null,
      perf: records
        .filter(r => r.perf?.avgFps !== undefined)
        .slice(-40)
        .map(r => ({ start: r.start, instanceName: r.instanceName, version: r.version, perf: r.perf! })),
    }
  }
}
