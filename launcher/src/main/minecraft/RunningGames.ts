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
      execFile('taskkill', ['/PID', String(game.pid)], err => {
        if (err) logger.warn('launcher', `Spiel ${game.instanceName} ließ sich nicht schließen`, String(err))
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
        if (err && !stdout) return reject(err)
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
