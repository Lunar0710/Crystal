import fs from 'fs'
import path from 'path'
import os from 'os'
import https from 'https'
import crypto from 'crypto'
import { spawn } from 'child_process'
import extract from 'extract-zip'
import { AuthProfile } from '../auth/AuthManager'
import { logger } from '../logs/Logger'
import { crystalRoot } from '../paths'
import { mojangOs, nativesSuffix, rulesAllow, OsRule } from './platform'
import { fabricMetaFor, downloadJavaMajor } from './versions'
import { fitHeap, freeCommitMb } from './MemoryBudget'

/**
 * Garbage collector flags. G1 is the default, tuned for short pauses. With the
 * player's "fewer stutters" switch on Java 21 or newer, generational ZGC
 * instead: it collects alongside the game, so there are hardly any pauses to
 * feel. Java 21 to 23 need it switched on; from 24 it is the only ZGC and the
 * flag would only print a warning. Java 17 knows no generational ZGC at all.
 */
function gcArgs(lowStutter: boolean, javaMajor: number): string[] {
  if (lowStutter && javaMajor >= 21) {
    return ['-XX:+UseZGC', ...(javaMajor < 24 ? ['-XX:+ZGenerational'] : [])]
  }
  return [
    '-XX:+UseG1GC', '-XX:+ParallelRefProcEnabled', '-XX:MaxGCPauseMillis=40',
    '-XX:+UnlockExperimentalVMOptions', '-XX:G1NewSizePercent=20', '-XX:G1ReservePercent=20',
    '-XX:G1HeapRegionSize=16M',
  ]
}

/**
 * Class-data-sharing cache: on the first exit the JVM writes the classes it
 * loaded into an archive in the instance folder, and every later start maps
 * that file instead of parsing and verifying those classes again. Vanilla
 * gains the most (its whole jar is on the class path); with Fabric it is the
 * JDK and the libraries. Java 19+ only. A stale archive (other Java, other
 * class path) is just ignored and rewritten, so the worst case is a normal
 * start. One file per collector, since the archive only fits the one it was
 * written with. -Xlog keeps the JVM's "skipping class" notes out of the log.
 */
export function bootCachePath(gameDir: string, lowStutter: boolean): string {
  return path.join(gameDir, '.crystal', lowStutter ? 'boot-zgc.jsa' : 'boot.jsa')
}

function bootCacheArgs(gameDir: string, lowStutter: boolean, javaMajor: number): string[] {
  if (javaMajor < 21) return []
  const file = bootCachePath(gameDir, lowStutter)
  try {
    fs.mkdirSync(path.dirname(file), { recursive: true })
    // The JVM writes the archive read-only; on Windows that would stop it from
    // replacing a stale one.
    if (fs.existsSync(file)) fs.chmodSync(file, 0o644)
  } catch {
    return []
  }
  return ['-XX:+AutoCreateSharedArchive', `-XX:SharedArchiveFile=${file}`, '-Xlog:cds=off', '-Xlog:cds+dynamic=off']
}

/** Drops the boot cache after a crash, so a damaged archive can never be the cause twice. */
function dropBootCache(gameDir: string, lowStutter: boolean): void {
  const file = bootCachePath(gameDir, lowStutter)
  try {
    if (fs.existsSync(file)) {
      fs.chmodSync(file, 0o644)
      fs.rmSync(file, { force: true })
    }
  } catch { /* only a cache */ }
}

// Electron/Node 18+ ships a global fetch; not covered by this tsconfig's
// ES2020-only lib, so declared locally instead of pulling in a DOM lib.
declare function fetch(url: string, init?: { headers?: Record<string, string> }): Promise<{
  ok: boolean
  status: number
  json(): Promise<any>
  arrayBuffer(): Promise<ArrayBuffer>
}>

// Getters, not constants: the user-configured data root is applied during
// startup, after this module has already been imported.
const VERSIONS_DIR = () => path.join(crystalRoot(), 'versions')
const LIBRARIES_DIR = () => path.join(crystalRoot(), 'libraries')
const ASSETS_DIR = () => path.join(crystalRoot(), 'assets')

export interface LaunchPipelineOptions {
  version: string
  loader: 'vanilla' | 'fabric' | 'forge'
  gameDir: string
  javaPath: string
  maxRam: number
  /** Lower the heap for this start when Windows is short on memory. Off = exactly maxRam. */
  autoRam?: boolean
  /** Generational ZGC instead of G1: hardly any collector pauses, a bit more memory. Java 21+ only. */
  lowStutterGc?: boolean
  profile: AuthProfile
  /** Test runs only (scripts/smoke-world.cjs); a normal launch never sets these. */
  extraJvmArgs?: string[]
  extraGameArgs?: string[]
}

type Rule = OsRule
interface LibraryDownload { path: string; url: string; sha1?: string; size?: number }
interface LibraryArtifact {
  downloads?: { artifact?: LibraryDownload; classifiers?: Record<string, LibraryDownload> }
  /** Old versions (before 1.19): which classifier holds this OS's natives, e.g. { osx: "natives-osx" }. */
  natives?: Record<string, string>
  name: string
  /** Maven repository root — present on Fabric's entries instead of `downloads`. */
  url?: string
  rules?: Rule[]
}

type Emit = (event: string, data: unknown) => void

type LaunchOutcome =
  | { kind: 'running' }
  | { kind: 'assumed-running' }
  | { kind: 'fatal'; reason: string }
  | { kind: 'exited'; code: number | null }
  | { kind: 'spawn-failed'; error: string }

// Fabric can spend a long time resolving mods before it gives up on an
// incompatible set — a launch that "succeeds" after only 8 seconds can still
// crash 20+ seconds later, so the grace period has to be generous rather than
// reacting to the first quiet moment.
const STARTUP_GRACE_MS = 45000

// Log lines that only appear once mod resolution and the game's own bootstrap
// have actually succeeded — proof of life beyond "the process is still alive".
const RUNNING_MARKERS = ['Setting user:', 'Backend library:', 'LWJGL Version']

// Known-fatal Fabric/JVM failure signatures, checked against the accumulated
// output so a message split across stdout chunks still matches.
const FATAL_PATTERNS: { pattern: RegExp; reason: string }[] = [
  { pattern: /Incompatible mods found/i, reason: 'Inkompatible Mods gefunden.' },
  { pattern: /FormattedException/, reason: 'Fabric Loader konnte die Mods nicht auflösen.' },
  { pattern: /Exception in thread "main"/, reason: 'Unbehandelte Exception beim Start.' },
  { pattern: /A fatal error has been detected by the Java Runtime Environment/, reason: 'JVM-Absturz (natives Modul).' },
  { pattern: /Could not reserve enough space for.*object heap/i, reason: 'Nicht genug Arbeitsspeicher für die gewählte RAM-Größe.' },
  { pattern: /insufficient memory for the Java Runtime|Native memory allocation \((?:malloc|mmap)\) failed/i, reason: 'Windows hatte keinen freien Arbeitsspeicher mehr.' },
]

/** Exit codes arrive unsigned on Windows; 4294967295 is -1. */
function exitCodeText(code: number | null): string {
  if (code === null) return 'unbekannt'
  return String(code > 0x7fffffff ? code - 0x100000000 : code)
}

/**
 * The JVM writes native crashes (out of memory, driver faults) to an
 * hs_err_pid*.log in the game folder instead of the normal log. Returns the
 * head of the newest one written since `since`, or ''.
 */
function nativeCrashReport(gameDir: string, since: number): string {
  try {
    const newest = fs.readdirSync(gameDir)
      .filter(f => /^hs_err_pid\d+\.log$/.test(f))
      .map(f => ({ f, t: fs.statSync(path.join(gameDir, f)).mtimeMs }))
      .filter(x => x.t >= since)
      .sort((a, b) => b.t - a.t)[0]
    if (!newest) return ''
    return fs.readFileSync(path.join(gameDir, newest.f), 'utf8').split('\n').slice(0, 30).join('\n')
  } catch {
    return ''
  }
}

function findFatalReason(output: string): string | null {
  for (const { pattern, reason } of FATAL_PATTERNS) {
    if (pattern.test(output)) return reason
  }
  return null
}

export class LaunchPipeline {
  async launch(opts: LaunchPipelineOptions, emit: Emit): Promise<boolean> {
    if (opts.loader === 'forge') {
      emit('launch:error', 'Forge wird noch nicht unterstützt (nur Vanilla und Fabric). Bitte Fabric oder Vanilla wählen.')
      return false
    }

    // Asked now, answered while the downloads run; read right before the start.
    const freeMemory = freeCommitMb()

    fs.mkdirSync(opts.gameDir, { recursive: true })
    fs.mkdirSync(VERSIONS_DIR(), { recursive: true })
    fs.mkdirSync(LIBRARIES_DIR(), { recursive: true })
    fs.mkdirSync(ASSETS_DIR(), { recursive: true })

    emit('launch:progress', { step: 'Resolving version metadata...', percent: 5 })
    // A release's version file never changes: once saved, a start needs no
    // round trip to Mojang (faster, and it works offline).
    const versionJsonPath = path.join(VERSIONS_DIR(), opts.version, `${opts.version}.json`)
    let versionJson: any = readJsonFile(versionJsonPath)
    if (!versionJson?.downloads?.client?.url) {
      const manifest = await this.getJson<{ versions: { id: string; url: string }[] }>(
        'https://launchermeta.mojang.com/mc/game/version_manifest_v2.json'
      )
      const entry = manifest.versions.find(v => v.id === opts.version)
      if (!entry) {
        emit('launch:error', `Unknown Minecraft version: ${opts.version}`)
        return false
      }
      versionJson = await this.getJson<any>(entry.url)
      writeJsonFile(versionJsonPath, versionJson)
    }

    let mainClass = versionJson.mainClass
    let extraLibraries: LibraryArtifact[] = []
    let extraClasspathJars: string[] = []
    let loaderProfile: any = null

    if (opts.loader === 'fabric') {
      emit('launch:progress', { step: 'Resolving Fabric loader...', percent: 12 })
      // Official Fabric from 1.14, Legacy Fabric for 1.8.9 to 1.13.2.
      const meta = fabricMetaFor(opts.version)
      // The profile last used for this version: the start still works when
      // Fabric's servers can't be reached.
      const cachedProfilePath = path.join(VERSIONS_DIR(), opts.version, 'fabric-profile.json')
      const cachedProfile = readJsonFile(cachedProfilePath)
      let loaders: { loader: { version: string } }[] = []
      let lookupFailed = false
      if (meta) {
        loaders = await this.getJson<{ loader: { version: string } }[]>(`${meta}/v2/versions/loader/${opts.version}`)
          .catch(() => { lookupFailed = true; return [] })
      }
      if (loaders.length > 0) {
        const loaderVersion = loaders[0].loader.version
        loaderProfile = await this.getJson<any>(`${meta}/v2/versions/loader/${opts.version}/${loaderVersion}/profile/json`)
          .catch(err => { if (cachedProfile) return cachedProfile; throw err })
        if (loaderProfile !== cachedProfile) writeJsonFile(cachedProfilePath, loaderProfile)
      } else if (lookupFailed && cachedProfile) {
        logger.warn('launcher', `Fabric nicht erreichbar, nutze das zuletzt verwendete Profil für ${opts.version}`)
        loaderProfile = cachedProfile
      } else {
        emit('launch:error', `Für Minecraft ${opts.version} gibt es kein Fabric. Wähle Vanilla oder eine andere Version.`)
        return false
      }
      mainClass = loaderProfile.mainClass
      extraLibraries = loaderProfile.libraries || []
    }

    emit('launch:progress', { step: 'Downloading client jar...', percent: 18 })
    const versionDir = path.join(VERSIONS_DIR(), opts.version)
    fs.mkdirSync(versionDir, { recursive: true })
    const clientJarPath = path.join(versionDir, `${opts.version}.jar`)
    await this.downloadIfMissing(versionJson.downloads.client.url, clientJarPath, versionJson.downloads.client.sha1, versionJson.downloads.client.size)

    emit('launch:progress', { step: 'Downloading libraries...', percent: 30 })
    // A loader library replaces the game's copy of the same one: Legacy Fabric
    // ships its own LWJGL 2 build, and two LWJGLs on the classpath crash the game.
    const libraryKey = (name: string) => name.split(':').slice(0, 2).join(':')
    const replacedByLoader = new Set(extraLibraries.map(l => libraryKey(l.name)))
    const gameLibraries = (versionJson.libraries as LibraryArtifact[]).filter(l => !replacedByLoader.has(libraryKey(l.name)))
    const { classpath: libPaths, natives: nativeJars } = await this.downloadLibraries(
      [...gameLibraries, ...extraLibraries],
      (done, total) => emit('launch:progress', { step: `Downloading libraries (${done}/${total})...`, percent: 30 + Math.round((done / Math.max(1, total)) * 25) })
    )

    // LWJGL loads its .dll files off java.library.path, so the natives jars
    // have to be unpacked to disk — without this Minecraft dies on startup.
    const nativesDir = path.join(VERSIONS_DIR(), opts.version, 'natives')
    emit('launch:progress', { step: 'Extracting natives...', percent: 56 })
    await this.extractNatives(nativeJars, nativesDir)

    emit('launch:progress', { step: 'Downloading assets...', percent: 58 })
    await this.downloadAssets(versionJson.assetIndex, (done, total) => {
      if (total > 0) emit('launch:progress', { step: `Downloading assets (${done}/${total})...`, percent: 58 + Math.round((done / total) * 27) })
    })

    // Mojang's logging config. For 1.8.9 to 1.18 it is also the Log4Shell fix:
    // without it a chat message on a server could run code on this PC.
    const loggingArgs: string[] = []
    const logging = versionJson.logging?.client
    if (logging?.file?.url && logging.file.id && typeof logging.argument === 'string') {
      const logConfig = path.join(ASSETS_DIR(), 'log_configs', path.basename(logging.file.id))
      fs.mkdirSync(path.dirname(logConfig), { recursive: true })
      await this.downloadIfMissing(logging.file.url, logConfig)
      loggingArgs.push(logging.argument.replace('${path}', logConfig))
    }

    emit('launch:progress', { step: 'Launching Minecraft...', percent: 92 })

    // Measured as late as possible: what is free right before the start counts.
    const free = await freeMemory
    const heap = opts.autoRam === false ? { heapMb: opts.maxRam, notice: undefined } : fitHeap(opts.maxRam, free)
    if (heap.notice) {
      logger.warn('client', 'RAM für diesen Start gesenkt', { requested: opts.maxRam, used: heap.heapMb, freeCommitMb: free })
      emit('launch:notice', heap.notice)
    }

    const lowStutter = opts.lowStutterGc === true
    const classpath = [clientJarPath, ...libPaths, ...extraClasspathJars].join(path.delimiter)
    const jvmArgs = [
      // Start with half the heap (1-4 GB). High render distances load chunks
      // faster than a small heap can grow, and every resize step is a GC pause.
      // A small start heap: the JVM grows it when needed. Reserving half the
      // maximum up front pushed systems with a small page file out of memory.
      `-Xmx${heap.heapMb}M`, `-Xms${Math.min(heap.heapMb, 1024)}M`,
      // Tuned for smooth frames rather than raw throughput: the old 200 ms
      // pause target let the collector freeze the game for visible stutters.
      // A short target with a larger young generation spreads that work out.
      ...gcArgs(lowStutter, downloadJavaMajor(opts.version)),
      '-XX:+DisableExplicitGC', '-XX:+PerfDisableSharedMem',
      ...bootCacheArgs(opts.gameDir, lowStutter, downloadJavaMajor(opts.version)),
      ...loggingArgs,
      // Tells the in-game client where the launcher keeps cosmetics/theme files,
      // so a moved data folder doesn't silently break cape and theme sync.
      `-Dcrystal.root=${crystalRoot()}`,
      ...(opts.extraJvmArgs ?? []),
      ...this.buildJvmArgs(versionJson, loaderProfile, nativesDir, classpath, opts.version),
    ]

    const gameArgs = [...this.buildGameArgs(versionJson, opts), ...this.loaderGameArgs(loaderProfile), ...(opts.extraGameArgs ?? [])]

    const logPath = path.join(opts.gameDir, 'crystal-launch.log')
    const launchedAt = Date.now()
    const logStream = fs.createWriteStream(logPath, { flags: 'w' })
    const header =
      `# Nexora launch ${new Date().toISOString()}\n` +
      `# java:      ${opts.javaPath}\n` +
      `# version:   ${opts.version}\n` +
      `# loader:    ${opts.loader}\n` +
      `# mainClass: ${mainClass}\n` +
      `# gameDir:   ${opts.gameDir}\n` +
      // Everything except the long classpath. Filtering by path.delimiter instead
      // hid every "-XX:..." flag on macOS/Linux, where the delimiter is ':'.
      `# jvmArgs:   ${jvmArgs.filter(a => a !== '-cp' && a !== classpath).join(' ')}\n` +
      `# classpath: ${libPaths.length + 1} entries\n\n`
    logStream.write(header)

    logger.info('client', `Starte Minecraft ${opts.version} (${opts.loader})`, {
      gameDir: opts.gameDir,
      mainClass,
      java: opts.javaPath,
      maxRam: heap.heapMb,
      freeCommitMb: free,
      classpathEntries: libPaths.length + 1,
    })

    // detached: without it, Node puts the game in a Windows job object that
    // ends it together with the launcher, so closing the launcher closed the
    // game. Detached, it keeps running; its output pipe then just breaks,
    // which Java ignores (the game's own log is logs/latest.log anyway).
    const proc = spawn(opts.javaPath, [...jvmArgs, mainClass, ...gameArgs], { cwd: opts.gameDir, detached: true })

    // Keep the tail of the game's own output so a startup crash can be
    // reported back to the user instead of silently "succeeding".
    let tail = ''
    const capture = (chunk: Buffer) => {
      const text = chunk.toString()
      logStream.write(text)
      tail = (tail + text).slice(-4000)
    }
    proc.stdout?.on('data', capture)
    proc.stderr?.on('data', capture)

    // A fixed "did it survive N seconds" check isn't enough: Fabric spends up to
    // half a minute preparing jars before it rejects an incompatible mod set, so
    // a short timer would report a crashed launch as a success. Wait for real
    // evidence instead — the game reporting it's up, a fatal line in the output,
    // or the process exiting.
    const outcome = await new Promise<LaunchOutcome>(resolve => {
      let settled = false
      const settle = (result: LaunchOutcome) => {
        if (settled) return
        settled = true
        clearTimeout(timer)
        resolve(result)
      }

      const timer = setTimeout(() => settle({ kind: 'assumed-running' }), STARTUP_GRACE_MS)

      const watch = (chunk: Buffer) => {
        const text = chunk.toString()
        const fatal = findFatalReason(tail + text)
        if (fatal) settle({ kind: 'fatal', reason: fatal })
        else if (RUNNING_MARKERS.some(marker => text.includes(marker))) settle({ kind: 'running' })
      }
      proc.stdout?.on('data', watch)
      proc.stderr?.on('data', watch)

      proc.once('exit', code => settle({ kind: 'exited', code }))
      proc.once('error', err => settle({ kind: 'spawn-failed', error: err.message }))
    })

    if (outcome.kind !== 'running' && outcome.kind !== 'assumed-running') {
      const reason =
        outcome.kind === 'spawn-failed' ? `Java konnte nicht gestartet werden: ${outcome.error}`
        : outcome.kind === 'fatal' ? `Minecraft konnte nicht starten:\n${outcome.reason}`
        : `Minecraft hat sich beim Start beendet (Exit-Code ${exitCodeText(outcome.code)}).`

      dropBootCache(opts.gameDir, lowStutter)
      const native = nativeCrashReport(opts.gameDir, launchedAt)
      const detail = native ? `${findFatalReason(native) ?? 'JVM-Absturz'}\n\n${native}` : tail.slice(-1200)
      logger.error('client', reason, (native || tail).slice(-4000))
      emit('launch:error', `${reason}\n\nLog: ${logPath}\n\n${detail}`)
      return false
    }

    // Even past the startup window a crash can still follow (a broken mod that
    // only blows up once a world loads), so keep watching rather than assuming
    // everything went fine from here on.
    proc.once('exit', code => {
      // Fires for every exit, clean or not — anything tracking "is the game
      // running" (Discord presence, UI state) needs the clean case too.
      emit('launch:exit', { code })

      if (code === 0 || code === null) {
        logger.info('client', `Minecraft beendet (Exit-Code ${code})`)
        return
      }
      dropBootCache(opts.gameDir, lowStutter)
      const native = nativeCrashReport(opts.gameDir, launchedAt)
      const fatal = findFatalReason(native) ?? findFatalReason(tail) ?? `Exit-Code ${exitCodeText(code)}`
      logger.error('client', `Minecraft ist nach dem Start abgestürzt: ${fatal}`, (native || tail).slice(-4000))
      emit('launch:error', `Minecraft ist abgestürzt:\n${fatal}\n\nLog: ${logPath}\n\n${native || tail.slice(-1200)}`)
    })

    logger.info('client', `Minecraft läuft (PID ${proc.pid})`)
    emit('launch:started', { version: opts.version, gameDir: opts.gameDir, username: opts.profile.username, pid: proc.pid })
    emit('launch:progress', { step: 'Game started!', percent: 100 })
    return true
  }

  private async extractNatives(nativeJars: string[], nativesDir: string): Promise<void> {
    if (nativeJars.length === 0) return
    fs.mkdirSync(nativesDir, { recursive: true })

    for (const jar of nativeJars) {
      // Each jar is unpacked once. Re-extracting over files a running game has
      // loaded (a second launch while playing) hung the launch at this step on
      // Windows, and it was wasted work on every start anyway.
      const marker = path.join(nativesDir, `.extracted-${path.basename(jar)}`)
      if (fs.existsSync(marker)) continue
      try {
        await extract(jar, { dir: nativesDir })
        fs.writeFileSync(marker, '')
      } catch (err) {
        // A single unreadable natives jar shouldn't abort the whole launch —
        // the game will report a clearer error if something is genuinely missing.
        continue
      }
    }
  }

  /**
   * The version's own JVM arguments. Newer versions list them (macOS window
   * thread, native access flags on 26.x, library path); versions before 1.13
   * list none and just need the library path and classpath. The library path
   * always points at the natives this launcher unpacked.
   */
  private buildJvmArgs(versionJson: any, loaderProfile: any, nativesDir: string, classpath: string, version: string): string[] {
    const values: Record<string, string> = {
      natives_directory: nativesDir,
      launcher_name: 'crystal-launcher',
      launcher_version: '1',
      classpath,
      classpath_separator: path.delimiter,
      library_directory: LIBRARIES_DIR(),
      version_name: version,
    }
    const fromLoader = this.flattenArgs(loaderProfile?.arguments?.jvm, values)
    const raw: unknown[] | undefined = versionJson.arguments?.jvm
    if (!Array.isArray(raw)) {
      return [...fromLoader, `-Djava.library.path=${nativesDir}`, '-cp', classpath]
    }
    const args = this.flattenArgs(raw, values).map(arg =>
      arg.startsWith('-Djava.library.path=') ? `-Djava.library.path=${nativesDir}` : arg)
    if (!args.includes('-cp') && !args.includes('-classpath')) args.push('-cp', classpath)
    // Loader flags go before the classpath pair so "-cp" keeps its value right after it.
    const cp = args.indexOf('-cp') >= 0 ? args.indexOf('-cp') : args.indexOf('-classpath')
    return [...args.slice(0, cp), ...fromLoader, ...args.slice(cp)]
  }

  /** Strings and rule-gated { rules, value } entries of an arguments list, placeholders filled in. */
  private flattenArgs(raw: unknown, values: Record<string, string>): string[] {
    if (!Array.isArray(raw)) return []
    const out: string[] = []
    for (const entry of raw) {
      if (typeof entry === 'string') {
        out.push(this.substitute(entry, values))
      } else if (entry && typeof entry === 'object') {
        const { rules, value } = entry as { rules?: Rule[]; value?: string | string[] }
        if (!this.rulesAllow(rules)) continue
        for (const v of Array.isArray(value) ? value : value ? [value] : []) out.push(this.substitute(v, values))
      }
    }
    return out
  }

  /** Extra game arguments a Fabric/Legacy Fabric profile adds (usually none). */
  private loaderGameArgs(loaderProfile: any): string[] {
    const raw = loaderProfile?.arguments?.game
    return Array.isArray(raw) ? raw.filter((a: unknown): a is string => typeof a === 'string') : []
  }

  private buildGameArgs(versionJson: any, opts: LaunchPipelineOptions): string[] {
    const placeholders: Record<string, string> = {
      auth_player_name: opts.profile.username,
      version_name: opts.version,
      game_directory: opts.gameDir,
      assets_root: ASSETS_DIR(),
      assets_index_name: versionJson.assetIndex.id,
      auth_uuid: opts.profile.uuid,
      auth_access_token: opts.profile.accessToken,
      user_type: opts.profile.type === 'microsoft' ? 'msa' : 'legacy',
      version_type: versionJson.type || 'release',
      clientid: '',
      auth_xuid: '',
      // 1.8.9 to 1.12 read this as JSON; an empty value makes them crash on start.
      user_properties: '{}',
      auth_session: opts.profile.accessToken,
    }

    // Modern versions describe game args as a structured list (with OS/feature
    // rules); legacy versions (pre-1.13) just have a flat "minecraftArguments"
    // string — support both.
    if (typeof versionJson.minecraftArguments === 'string') {
      return versionJson.minecraftArguments.split(' ').map((a: string) => this.substitute(a, placeholders))
    }

    const rawArgs: unknown[] = versionJson.arguments?.game || []
    const args: string[] = []
    for (const arg of rawArgs) {
      if (typeof arg === 'string') args.push(this.substitute(arg, placeholders))
      // Conditional (feature/OS-gated) args are skipped — none are required
      // for a standard offline/online launch.
    }
    return args
  }

  private substitute(str: string, values: Record<string, string>): string {
    return str.replace(/\$\{(\w+)\}/g, (_, key) => values[key] ?? '')
  }

  private async downloadLibraries(
    libraries: LibraryArtifact[],
    onProgress: (done: number, total: number) => void
  ): Promise<{ classpath: string[]; natives: string[] }> {
    const applicable = libraries.filter(lib => this.rulesAllow(lib.rules))
    const classpath: string[] = []
    const natives: string[] = []
    // The classpath is built in order right here; the downloads run afterwards, several at a time.
    const downloads: (() => Promise<void>)[] = []

    for (const lib of applicable) {
      // Modern versions ship natives as their own library entries
      // (name ending in ":natives-windows", ":natives-macos-arm64", ...);
      // older ones use a classifiers map keyed the same way.
      const isNative = lib.name?.includes(nativesSuffix())
      // Before 1.19 the natives sit in a classifier named by the "natives" map
      // ("natives-osx" on old versions, "natives-macos" later, sometimes with ${arch}).
      const classifierName = lib.natives?.[mojangOs()]?.replace('${arch}', process.arch === 'ia32' ? '32' : '64')
      const classifier = classifierName ? lib.downloads?.classifiers?.[classifierName] : undefined

      const artifact = lib.downloads?.artifact
      if (artifact?.url && artifact.path) {
        const dest = path.join(LIBRARIES_DIR(), artifact.path)
        fs.mkdirSync(path.dirname(dest), { recursive: true })
        downloads.push(() => this.downloadIfMissing(artifact.url, dest, artifact.sha1, artifact.size))
        // LWJGL 3.3+ can load its natives straight from the jar, so native jars
        // go on the classpath as well as being unpacked (26.x relies on that).
        if (isNative) natives.push(dest)
        classpath.push(dest)
      } else if (lib.name && lib.url && classifierName) {
        // Maven natives-only library (Legacy Fabric's LWJGL platform jar):
        // there is no main jar, just "<artifact>-<version>-natives-<os>.jar".
        const relative = this.mavenToPath(lib.name, classifierName)
        if (relative) {
          const dest = path.join(LIBRARIES_DIR(), relative)
          fs.mkdirSync(path.dirname(dest), { recursive: true })
          const base = lib.url.endsWith('/') ? lib.url : `${lib.url}/`
          downloads.push(() => this.downloadIfMissing(base + relative.replace(/\\/g, '/'), dest))
          natives.push(dest)
        }
      } else if (lib.name && lib.url) {
        // Fabric's meta API describes libraries as Maven coordinates plus a
        // repository root instead of a prebuilt download entry — without this
        // branch the loader itself never lands on the classpath.
        const relative = this.mavenToPath(lib.name)
        if (relative) {
          const dest = path.join(LIBRARIES_DIR(), relative)
          fs.mkdirSync(path.dirname(dest), { recursive: true })
          const base = lib.url.endsWith('/') ? lib.url : `${lib.url}/`
          downloads.push(() => this.downloadIfMissing(base + relative.replace(/\\/g, '/'), dest))
          classpath.push(dest)
        }
      }

      if (classifier?.url && classifier.path) {
        const dest = path.join(LIBRARIES_DIR(), classifier.path)
        fs.mkdirSync(path.dirname(dest), { recursive: true })
        downloads.push(() => this.downloadIfMissing(classifier.url, dest, classifier.sha1, classifier.size))
        natives.push(dest)
      }
    }
    let done = 0
    await runPool(downloads, LIBRARY_PARALLEL, async job => {
      await job()
      onProgress(++done, downloads.length)
    })
    return { classpath, natives }
  }

  /** "group:artifact:version" → "group/path/artifact/version/artifact-version.jar" */
  /** "group:artifact:version" (+ classifier) → "group/path/artifact/version/artifact-version[-classifier].jar" */
  private mavenToPath(coordinate: string, classifier?: string): string | null {
    const [group, artifact, version] = coordinate.split(':')
    if (!group || !artifact || !version) return null
    const suffix = classifier ? `-${classifier}` : ''
    return path.join(...group.split('.'), artifact, version, `${artifact}-${version}${suffix}.jar`)
  }

  private rulesAllow(rules?: Rule[]): boolean {
    return rulesAllow(rules)
  }

  private async downloadAssets(assetIndexRef: { id: string; url: string }, onProgress: (done: number, total: number) => void) {
    const indexDir = path.join(ASSETS_DIR(), 'indexes')
    fs.mkdirSync(indexDir, { recursive: true })
    const indexPath = path.join(indexDir, `${assetIndexRef.id}.json`)
    await this.downloadIfMissing(assetIndexRef.url, indexPath)

    const index = JSON.parse(fs.readFileSync(indexPath, 'utf-8')) as { objects: Record<string, { hash: string; size: number }> }
    const entries = Object.values(index.objects)
    const objectsDir = path.join(ASSETS_DIR(), 'objects')

    // Several names can share one object; each file is fetched once.
    const sizes = new Map(entries.map(o => [o.hash, o.size]))
    const missing = [...sizes.keys()].filter(hash => !hasSize(path.join(objectsDir, hash.slice(0, 2), hash), sizes.get(hash)))
    let done = entries.length - missing.length
    onProgress(done, entries.length)
    // One at a time, a first start fetched thousands of small files back to back.
    await runPool(missing, ASSET_PARALLEL, async hash => {
      const sub = hash.slice(0, 2)
      const dest = path.join(objectsDir, sub, hash)
      fs.mkdirSync(path.dirname(dest), { recursive: true })
      await this.downloadIfMissing(`https://resources.download.minecraft.net/${sub}/${hash}`, dest, hash, sizes.get(hash))
      done++
      if (done % 25 === 0 || done === entries.length) onProgress(Math.min(done, entries.length), entries.length)
    })
  }

  /**
   * Downloads a file unless it's already there. A dropped or timed-out
   * connection is retried twice before the launch gives up: a single network
   * hiccup among thousands of asset files shouldn't cost the player the launch.
   */
  /** Downloads in flight, by destination: a second request for the same file waits for the first. */
  private inFlight = new Map<string, Promise<void>>()

  /**
   * {@code size}, where known, also checks a file that is already there: one
   * cut short by an earlier launcher (before downloads went through .part
   * files) is fetched again instead of crashing every start.
   */
  private downloadIfMissing(url: string, dest: string, sha1?: string, size?: number): Promise<void> {
    if (hasSize(dest, size)) return Promise.resolve()
    const running = this.inFlight.get(dest)
    if (running) return running
    const job = this.downloadWithRetries(url, dest, sha1).finally(() => this.inFlight.delete(dest))
    this.inFlight.set(dest, job)
    return job
  }

  private async downloadWithRetries(url: string, dest: string, sha1?: string): Promise<void> {
    for (let attempt = 1; ; attempt++) {
      try {
        return await this.downloadOnce(url, dest, sha1)
      } catch (err) {
        const message = String((err as Error)?.message ?? err)
        if (attempt >= 3 || /HTTP 4\d\d/.test(message)) throw err
        await new Promise(resolve => setTimeout(resolve, 1500 * attempt))
      }
    }
  }

  /**
   * Written to "<dest>.part" and renamed once complete (and, where the
   * checksum is known, correct). Written straight to dest, a download cut off
   * by a dropped connection or a closed launcher left half a file that every
   * later start took as present: a broken jar and a crash on every launch.
   */
  private downloadOnce(url: string, dest: string, sha1?: string, redirects = 0): Promise<void> {
    const part = `${dest}.part`

    return new Promise((resolve, reject) => {
      let settled = false
      const fail = (err: Error) => {
        if (settled) return
        settled = true
        file.destroy()
        fs.rm(part, { force: true }, () => reject(err))
      }
      const file = fs.createWriteStream(part)
      file.on('error', fail)
      const req = https.get(url, { headers: { 'User-Agent': 'crystal-launcher' } }, res => {
        // Some Maven mirrors (Legacy Fabric's) answer with a redirect to the real file.
        const location = res.headers.location
        if (res.statusCode && res.statusCode >= 300 && res.statusCode < 400 && location && redirects < 5) {
          res.resume()
          settled = true
          file.destroy()
          fs.rm(part, { force: true }, () =>
            this.downloadOnce(new URL(location, url).toString(), dest, sha1, redirects + 1).then(resolve, reject))
          return
        }
        if (res.statusCode !== 200) {
          res.resume()
          fail(new Error(`HTTP ${res.statusCode} for ${url}`))
          return
        }
        const hash = sha1 ? crypto.createHash('sha1') : null
        if (hash) res.on('data', chunk => hash.update(chunk))
        res.on('aborted', () => fail(new Error(`Download abgebrochen: ${url}`)))
        res.on('error', err => fail(err))
        res.pipe(file)
        file.on('finish', () => file.close(() => {
          if (settled) return
          if (hash && hash.digest('hex') !== sha1!.toLowerCase()) return fail(new Error(`Prüfsumme falsch für ${url}`))
          settled = true
          fs.rename(part, dest, err => err ? reject(err) : resolve())
        }))
      })
      req.on('error', err => fail(err))
      // A connection that stops sending would otherwise hold the start forever.
      req.setTimeout(DOWNLOAD_STALL_MS, () => req.destroy(new Error(`Keine Antwort beim Download von ${url}`)))
    })
  }

  /**
   * JSON from Mojang/Fabric, tried up to three times: a single dropped
   * connection (a timeout, a reset) used to fail the whole start. A 4xx answer
   * won't change on retry and fails at once.
   */
  private async getJson<T>(url: string): Promise<T> {
    for (let attempt = 1; ; attempt++) {
      try {
        return await this.getJsonOnce<T>(url)
      } catch (err) {
        if (attempt >= 3 || /HTTP 4\d\d/.test(String(err))) throw err
        logger.warn('launcher', `Anfrage fehlgeschlagen, neuer Versuch (${attempt}/3)`, { url, error: String(err) })
        await new Promise(r => setTimeout(r, attempt * 1500))
      }
    }
  }

  private getJsonOnce<T>(url: string, redirects = 0): Promise<T> {
    return new Promise((resolve, reject) => {
      const req = https.get(url, { headers: { 'User-Agent': 'crystal-launcher' } }, res => {
        const location = res.headers.location
        if (res.statusCode && res.statusCode >= 300 && res.statusCode < 400 && location && redirects < 5) {
          res.resume()
          this.getJsonOnce<T>(new URL(location, url).toString(), redirects + 1).then(resolve, reject)
          return
        }
        if (res.statusCode !== 200) {
          reject(new Error(`HTTP ${res.statusCode} for ${url}`))
          return
        }
        let data = ''
        res.on('data', c => (data += c))
        res.on('end', () => {
          try { resolve(JSON.parse(data)) } catch (e) { reject(e) }
        })
        res.on('error', reject)
      })
      req.on('error', reject)
      req.setTimeout(DOWNLOAD_STALL_MS, () => req.destroy(new Error(`Keine Antwort von ${url}`)))
    })
  }
}

/** Parallel downloads: many small asset files, fewer but larger libraries. */
const ASSET_PARALLEL = 16
const LIBRARY_PARALLEL = 8
/** A download that sends nothing for this long is dropped and retried. */
const DOWNLOAD_STALL_MS = 20_000

/** Runs {@code fn} over the items, at most {@code limit} at once. */
async function runPool<T>(items: T[], limit: number, fn: (item: T) => Promise<void>): Promise<void> {
  let next = 0
  const worker = async () => {
    while (next < items.length) await fn(items[next++])
  }
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, worker))
}

/** True when the file exists with the expected size (any size above 0 when none is known). */
function hasSize(file: string, size?: number): boolean {
  try {
    const actual = fs.statSync(file).size
    return size ? actual === size : actual > 0
  } catch {
    return false
  }
}

function readJsonFile(file: string): any {
  try { return JSON.parse(fs.readFileSync(file, 'utf-8')) } catch { return null }
}

function writeJsonFile(file: string, data: unknown): void {
  try {
    fs.mkdirSync(path.dirname(file), { recursive: true })
    fs.writeFileSync(file, JSON.stringify(data))
  } catch (err) {
    logger.debug('launcher', `Konnte ${file} nicht speichern`, String(err))
  }
}
