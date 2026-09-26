import { spawn, execFile, ChildProcessWithoutNullStreams } from 'child_process'
import os from 'os'
import { logger } from '../logs/Logger'

/** A game the launcher started and that is still running. */
export interface RunningGame {
  instanceId: string
  instanceName: string
  version: string
  username: string
  accountUuid: string
  pid: number
  startedAt: number
  /** The heap the game was started with (-Xmx), in MB. */
  maxRamMb: number
  /**
   * Found running when the launcher started (a game outlives the launcher).
   * There is no exit event for it, so it leaves the list when its process is gone.
   */
  adopted?: boolean
}

/** ps's etime ("[[dd-]hh:]mm:ss") in seconds; 0 when it can't be read. */
export function elapsedSeconds(etime: string): number {
  const m = etime.match(/^(?:(\d+)-)?(?:(\d+):)?(\d+):(\d+)$/)
  if (!m) return 0
  const [, d, h, min, sec] = m
  return ((Number(d || 0) * 24 + Number(h || 0)) * 60 + Number(min)) * 60 + Number(sec)
}

/**
 * Marks the launcher puts on the game's command line, so a launcher started
 * later can tell its games apart from any other Java program.
 */
export function gameMarkers(instanceId: string, accountUuid: string, username: string, maxRamMb: number): string[] {
  return [`-Dnexora.instance=${instanceId}`, `-Dnexora.account=${accountUuid}`, `-Dnexora.user=${username}`, `-Dnexora.ram=${maxRamMb}`]
}

/** What a game is using right now. */
export interface GameUsage {
  /** Share of the whole machine, like the Task Manager shows it (0-100). */
  cpuPercent: number
  /** Memory the process holds (working set), in MB. */
  ramMb: number
}

export type RunningGameWithUsage = RunningGame & { usage: GameUsage | null }

const SAMPLE_MS = 2000

/** One reading: CPU as total milliseconds (Windows) or as a ready share (ps), and memory. */
type Sample = { cpuMs?: number; cpuPercent?: number; ramBytes: number }

/**
 * Every game the launcher has running, and what each one uses.
 *
 * Usage is sampled every two seconds while at least one game runs. Windows
 * has no cheap per-process API reachable from Node, and starting PowerShell
 * every two seconds costs more than it measures, so one PowerShell process is
 * kept open for as long as games run: it gets the process ids on a line and
 * answers with CPU time and memory for each. Elsewhere `ps` does the job.
 */
export class RunningGames {
  private games = new Map<string, RunningGame>()
  private usage = new Map<number, GameUsage>()
  /** Running sums over the whole session, for the performance history. */
  private totals = new Map<number, { cpu: number; ram: number; peak: number; n: number }>()
  private lastCpu = new Map<number, { ms: number; at: number }>()
  private timer: NodeJS.Timeout | null = null
  private shell: ChildProcessWithoutNullStreams | null = null
  private pendingReply: ((line: string) => void) | null = null
  private shellBuffer = ''

  constructor(private readonly onChange: (games: RunningGameWithUsage[]) => void) {}

  list(): RunningGameWithUsage[] {
    return [...this.games.values()].map(g => ({ ...g, usage: this.usage.get(g.pid) ?? null }))
  }

  /** Average CPU and RAM over the session so far, and the most RAM it held. */
  sessionUsage(instanceId: string): { cpuAvg: number; ramAvgMb: number; ramPeakMb: number } | null {
    const game = this.games.get(instanceId)
    const t = game && this.totals.get(game.pid)
    if (!t || t.n === 0) return null
    return { cpuAvg: Math.round((t.cpu / t.n) * 10) / 10, ramAvgMb: Math.round(t.ram / t.n), ramPeakMb: t.peak }
  }

  isInstanceRunning(instanceId: string): boolean {
    return this.games.has(instanceId)
  }

  /** The running game that plays on this account, if any. */
  gameForAccount(accountUuid: string): RunningGame | undefined {
    return [...this.games.values()].find(g => g.accountUuid === accountUuid)
  }

  /** Games a close was sent to, by pid. */
  private closing = new Set<number>()

  add(game: RunningGame) {
    this.games.set(game.instanceId, game)
    this.start()
    this.onChange(this.list())
  }

  remove(instanceId: string) {
    const game = this.games.get(instanceId)
    if (!game) return
    this.games.delete(instanceId)
    this.usage.delete(game.pid)
    this.totals.delete(game.pid)
    this.lastCpu.delete(game.pid)
    if (this.games.size === 0) this.stop()
    this.onChange(this.list())
  }

  /**
   * Asks a game to close the way the window's X button does, so Minecraft
   * saves the world first. Not a kill: that would lose what was not saved.
   */
  close(instanceId: string): boolean {
    const game = this.games.get(instanceId)
    if (!game) return false
    if (process.platform === 'win32') {
      // One close at a time: repeated clicks only add log noise.
      if (this.closing.has(game.pid)) return true
      this.closing.add(game.pid)
      const force = () => execFile('taskkill', ['/F', '/T', '/PID', String(game.pid)], err => {
        this.closing.delete(game.pid)
        if (err && this.games.has(instanceId)) logger.warn('launcher', `Spiel ${game.instanceName} ließ sich nicht beenden`, String(err))
      })
      execFile('taskkill', ['/PID', String(game.pid)], err => {
        // A game still starting up (no window yet) refuses a friendly close:
        // then it has nothing to save and is simply ended.
        if (err) { force(); return }
        // Asked nicely; if it is still there after 20 seconds (hung on saving), end it.
        setTimeout(() => { if (this.games.has(instanceId)) force(); else this.closing.delete(game.pid) }, 20000)
      })
    } else {
      try { process.kill(game.pid, 'SIGTERM') } catch { return false }
    }
    return true
  }

  private start() {
    if (this.timer) return
    this.timer = setInterval(() => { void this.sample() }, SAMPLE_MS)
    void this.sample()
  }

  private stop() {
    if (this.timer) clearInterval(this.timer)
    this.timer = null
    this.shell?.stdin.end()
    this.shell?.kill()
    this.shell = null
    this.pendingReply = null
  }

  private sampling = false

  /**
   * Takes over games that were started by an earlier launcher and are still
   * running, found by the marks gameMarkers put on their command line.
   * {@code lookup} gives an instance's name and version, or null for an
   * instance that no longer exists (that game is left alone).
   */
  async adoptRunning(lookup: (instanceId: string) => { name: string; version: string } | null): Promise<number> {
    let found: { pid: number; cmd: string; started: number }[] = []
    try {
      found = process.platform === 'win32' ? await this.javaProcessesWindows() : await this.javaProcessesPs()
    } catch (err) {
      logger.debug('launcher', 'Laufende Spiele nicht auffindbar', String(err))
      return 0
    }
    let adopted = 0
    for (const { pid, cmd, started } of found) {
      const mark = (key: string) => cmd.match(new RegExp(`-Dnexora\\.${key}=(\\S+)`))?.[1]
      const instanceId = mark('instance')
      if (!instanceId || this.games.has(instanceId)) continue
      const instance = lookup(instanceId)
      if (!instance) continue
      this.games.set(instanceId, {
        instanceId,
        instanceName: instance.name,
        version: instance.version,
        username: mark('user') ?? '?',
        accountUuid: mark('account') ?? '',
        pid,
        startedAt: started || Date.now(),
        maxRamMb: Number(mark('ram')) || 0,
        adopted: true,
      })
      adopted++
    }
    if (adopted > 0) {
      logger.info('launcher', `${adopted} laufende(s) Spiel(e) vom letzten Launcher übernommen`)
      this.start()
      this.onChange(this.list())
    }
    return adopted
  }

  private javaProcessesWindows(): Promise<{ pid: number; cmd: string; started: number }[]> {
    const script =
      "Get-CimInstance Win32_Process -Filter \"name='java.exe' or name='javaw.exe'\" | " +
      "Where-Object { $_.CommandLine -match '-Dnexora.instance=' } | " +
      'ForEach-Object { [pscustomobject]@{ pid = $_.ProcessId; cmd = $_.CommandLine; started = ([DateTimeOffset]$_.CreationDate).ToUnixTimeMilliseconds() } } | ' +
      'ConvertTo-Json -Compress'
    return new Promise((resolve, reject) => {
      execFile('powershell.exe', ['-NoLogo', '-NoProfile', '-NonInteractive', '-Command', script], { windowsHide: true, maxBuffer: 8 * 1024 * 1024 }, (err, stdout) => {
        if (err) return reject(err)
        const text = stdout.trim()
        if (!text) return resolve([])
        const parsed = JSON.parse(text)
        resolve((Array.isArray(parsed) ? parsed : [parsed]).map((p: any) => ({ pid: Number(p.pid), cmd: String(p.cmd ?? ''), started: Number(p.started) || 0 })))
      })
    })
  }

  private javaProcessesPs(): Promise<{ pid: number; cmd: string; started: number }[]> {
    return new Promise((resolve, reject) => {
      // etime ("[[dd-]hh:]mm:ss") rather than etimes: macOS's BSD ps only knows etime.
      execFile('ps', ['-eo', 'pid=,etime=,args='], { maxBuffer: 8 * 1024 * 1024 }, (err, stdout) => {
        if (err && !stdout) return reject(err)
        const now = Date.now()
        resolve(stdout.split('\n').flatMap(line => {
          const m = line.trim().match(/^(\d+)\s+(\S+)\s+(.*)$/)
          if (!m || !m[3].includes('-Dnexora.instance=')) return []
          return [{ pid: Number(m[1]), cmd: m[3], started: now - elapsedSeconds(m[2]) * 1000 }]
        }))
      })
    })
  }

  private async sample() {
    if (this.sampling || this.games.size === 0) return
    this.sampling = true
    try {
      const pids = [...this.games.values()].map(g => g.pid)
      const raw = process.platform === 'win32' ? await this.sampleWindows(pids) : await this.samplePs(pids)
      const cores = Math.max(1, os.cpus().length)
      const now = Date.now()
      for (const [pid, { cpuMs, ramBytes, cpuPercent }] of raw) {
        let percent = cpuPercent ?? 0
        // Windows gives CPU time; the first reading has nothing to compare with yet.
        let firstReading = false
        if (cpuMs !== undefined) {
          const last = this.lastCpu.get(pid)
          firstReading = !last
          this.lastCpu.set(pid, { ms: cpuMs, at: now })
          percent = last && now > last.at ? ((cpuMs - last.ms) / (now - last.at) / cores) * 100 : 0
        }
        const usage = {
          cpuPercent: Math.max(0, Math.min(100, Math.round(percent * 10) / 10)),
          ramMb: Math.round(ramBytes / (1024 * 1024)),
        }
        this.usage.set(pid, usage)
        if (!firstReading) {
          const t = this.totals.get(pid) ?? { cpu: 0, ram: 0, peak: 0, n: 0 }
          this.totals.set(pid, { cpu: t.cpu + usage.cpuPercent, ram: t.ram + usage.ramMb, peak: Math.max(t.peak, usage.ramMb), n: t.n + 1 })
        }
      }
      // Adopted games send no exit event: they leave once their process is gone.
      for (const game of [...this.games.values()]) {
        if (game.adopted && !raw.has(game.pid)) this.remove(game.instanceId)
      }
      this.onChange(this.list())
    } catch (err) {
      logger.debug('launcher', 'Ressourcen der laufenden Spiele nicht lesbar', String(err))
    } finally {
      this.sampling = false
    }
  }

  private sampleWindows(pids: number[]): Promise<Map<number, Sample>> {
    if (!this.shell) {
      // Reads a line of comma separated ids, answers "id:cpuMs:bytes;..." on one line.
      const script =
        '$ErrorActionPreference = "SilentlyContinue"; ' +
        'while ($null -ne ($line = [Console]::In.ReadLine())) { ' +
        '$out = foreach ($id in $line.Split(",")) { $p = Get-Process -Id ([int]$id); ' +
        'if ($p) { "{0}:{1}:{2}" -f $p.Id, [long]$p.TotalProcessorTime.TotalMilliseconds, $p.WorkingSet64 } }; ' +
        '[Console]::Out.WriteLine(($out -join ";")); [Console]::Out.Flush() }'
      this.shell = spawn('powershell.exe', ['-NoLogo', '-NoProfile', '-NonInteractive', '-Command', script], { windowsHide: true })
      this.shell.stdout.setEncoding('utf8')
      this.shell.stdout.on('data', (chunk: string) => {
        this.shellBuffer += chunk
        let nl: number
        while ((nl = this.shellBuffer.indexOf('\n')) >= 0) {
          const line = this.shellBuffer.slice(0, nl).trim()
          this.shellBuffer = this.shellBuffer.slice(nl + 1)
          const reply = this.pendingReply
          this.pendingReply = null
          reply?.(line)
        }
      })
      this.shell.on('exit', () => { this.shell = null })
    }
    const shell = this.shell
    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => { this.pendingReply = null; reject(new Error('timeout')) }, 5000)
      this.pendingReply = line => {
        clearTimeout(timeout)
        const result = new Map<number, Sample>()
        for (const part of line.split(';')) {
          const [id, cpu, bytes] = part.split(':').map(Number)
          if (id && Number.isFinite(cpu) && Number.isFinite(bytes)) result.set(id, { cpuMs: cpu, ramBytes: bytes })
        }
        resolve(result)
      }
      shell.stdin.write(pids.join(',') + '\n')
    })
  }

  private samplePs(pids: number[]): Promise<Map<number, Sample>> {
    return new Promise((resolve, reject) => {
      execFile('ps', ['-o', 'pid=,%cpu=,rss=', '-p', pids.join(',')], (err, stdout) => {
        // Exit code 1 with no output: none of the processes exists any more,
        // which is an answer (they ended), not a failure.
        if (err && !stdout) return (err as { code?: unknown }).code === 1 ? resolve(new Map()) : reject(err)
        const cores = Math.max(1, os.cpus().length)
        const result = new Map<number, Sample>()
        for (const line of stdout.split('\n')) {
          const [pid, cpu, rssKb] = line.trim().split(/\s+/).map(Number)
          // ps counts one full core as 100%; the launcher shows the whole machine.
          if (pid) result.set(pid, { cpuPercent: cpu / cores, ramBytes: rssKb * 1024 })
        }
        resolve(result)
      })
    })
  }
}
