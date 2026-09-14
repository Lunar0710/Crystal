import fs from 'fs'
import path from 'path'
import os from 'os'
import https from 'https'
import { spawn } from 'child_process'
import extract from 'extract-zip'
import { AuthProfile } from '../auth/AuthManager'
import { logger } from '../logs/Logger'
import { crystalRoot } from '../paths'
import { nativesSuffix, platformJvmArgs, rulesAllow, OsRule } from './platform'

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
  profile: AuthProfile
}

type Rule = OsRule
interface LibraryDownload { path: string; url: string; sha1?: string }
interface LibraryArtifact {
  downloads?: { artifact?: LibraryDownload; classifiers?: Record<string, LibraryDownload> }
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
]

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

    fs.mkdirSync(opts.gameDir, { recursive: true })
    fs.mkdirSync(VERSIONS_DIR(), { recursive: true })
    fs.mkdirSync(LIBRARIES_DIR(), { recursive: true })
    fs.mkdirSync(ASSETS_DIR(), { recursive: true })

    emit('launch:progress', { step: 'Resolving version metadata...', percent: 5 })
    const manifest = await this.getJson<{ versions: { id: string; url: string }[] }>(
      'https://launchermeta.mojang.com/mc/game/version_manifest_v2.json'
    )
    const entry = manifest.versions.find(v => v.id === opts.version)
    if (!entry) {
      emit('launch:error', `Unknown Minecraft version: ${opts.version}`)
      return false
    }
    const versionJson = await this.getJson<any>(entry.url)

    let mainClass = versionJson.mainClass
    let extraLibraries: LibraryArtifact[] = []
    let extraClasspathJars: string[] = []

    if (opts.loader === 'fabric') {
      emit('launch:progress', { step: 'Resolving Fabric loader...', percent: 12 })
      const loaders = await this.getJson<{ loader: { version: string } }[]>(
        `https://meta.fabricmc.net/v2/versions/loader/${opts.version}`
      )
      if (loaders.length === 0) {
        emit('launch:error', `No Fabric loader available for ${opts.version}`)
        return false
      }
      const loaderVersion = loaders[0].loader.version
      const profile = await this.getJson<any>(
        `https://meta.fabricmc.net/v2/versions/loader/${opts.version}/${loaderVersion}/profile/json`
      )
      mainClass = profile.mainClass
      extraLibraries = profile.libraries || []
    }

    emit('launch:progress', { step: 'Downloading client jar...', percent: 18 })
    const versionDir = path.join(VERSIONS_DIR(), opts.version)
    fs.mkdirSync(versionDir, { recursive: true })
    const clientJarPath = path.join(versionDir, `${opts.version}.jar`)
    await this.downloadIfMissing(versionJson.downloads.client.url, clientJarPath)

    emit('launch:progress', { step: 'Downloading libraries...', percent: 30 })
    const { classpath: libPaths, natives: nativeJars } = await this.downloadLibraries(
      [...(versionJson.libraries as LibraryArtifact[]), ...extraLibraries],
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

    emit('launch:progress', { step: 'Launching Minecraft...', percent: 92 })

    const classpath = [clientJarPath, ...libPaths, ...extraClasspathJars].join(path.delimiter)
    const jvmArgs = [
      // Start with half the heap (1-4 GB). High render distances load chunks
      // faster than a small heap can grow, and every resize step is a GC pause.
      `-Xmx${opts.maxRam}M`, `-Xms${Math.min(opts.maxRam, Math.max(1024, Math.min(4096, Math.floor(opts.maxRam / 2))))}M`,
      // Tuned for smooth frames rather than raw throughput: the old 200 ms
      // pause target let the collector freeze the game for visible stutters.
      // A short target with a larger young generation spreads that work out.
      '-XX:+UseG1GC', '-XX:+ParallelRefProcEnabled', '-XX:MaxGCPauseMillis=40',
      '-XX:+UnlockExperimentalVMOptions', '-XX:G1NewSizePercent=20', '-XX:G1ReservePercent=20',
      '-XX:G1HeapRegionSize=16M', '-XX:+DisableExplicitGC', '-XX:+PerfDisableSharedMem',
      ...platformJvmArgs(),
      `-Djava.library.path=${nativesDir}`,
      // Tells the in-game client where the launcher keeps cosmetics/theme files,
      // so a moved data folder doesn't silently break cape and theme sync.
      `-Dcrystal.root=${crystalRoot()}`,
      '-cp', classpath,
    ]

    const gameArgs = this.buildGameArgs(versionJson, opts)

    const logPath = path.join(opts.gameDir, 'crystal-launch.log')
    const logStream = fs.createWriteStream(logPath, { flags: 'w' })
    const header =
      `# Crystal launch ${new Date().toISOString()}\n` +
      `# java:      ${opts.javaPath}\n` +
      `# version:   ${opts.version}\n` +
      `# loader:    ${opts.loader}\n` +
      `# mainClass: ${mainClass}\n` +
      `# gameDir:   ${opts.gameDir}\n` +
      `# jvmArgs:   ${jvmArgs.filter(a => a !== '-cp' && !a.includes(path.delimiter)).join(' ')}\n` +
      `# classpath: ${libPaths.length + 1} entries\n\n`
    logStream.write(header)

    logger.info('client', `Starte Minecraft ${opts.version} (${opts.loader})`, {
      gameDir: opts.gameDir,
      mainClass,
      java: opts.javaPath,
      maxRam: opts.maxRam,
      classpathEntries: libPaths.length + 1,
    })

    const proc = spawn(opts.javaPath, [...jvmArgs, mainClass, ...gameArgs], { cwd: opts.gameDir })

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
        : `Minecraft hat sich beim Start beendet (Exit-Code ${outcome.code}).`

      logger.error('client', reason, tail.slice(-4000))
      emit('launch:error', `${reason}\n\nLog: ${logPath}\n\n${tail.slice(-1200)}`)
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
      const fatal = findFatalReason(tail) ?? `Exit-Code ${code}`
      logger.error('client', `Minecraft ist nach dem Start abgestürzt: ${fatal}`, tail.slice(-4000))
      emit('launch:error', `Minecraft ist abgestürzt:\n${fatal}\n\nLog: ${logPath}\n\n${tail.slice(-1200)}`)
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
      try {
        await extract(jar, { dir: nativesDir })
      } catch (err) {
        // A single unreadable natives jar shouldn't abort the whole launch —
        // the game will report a clearer error if something is genuinely missing.
        continue
      }
    }
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
    let done = 0

    for (const lib of applicable) {
      // Modern versions ship natives as their own library entries
      // (name ending in ":natives-windows", ":natives-macos-arm64", ...);
      // older ones use a classifiers map keyed the same way.
      const isNative = lib.name?.includes(nativesSuffix())
      const classifier = lib.downloads?.classifiers?.[nativesSuffix()]

      const artifact = lib.downloads?.artifact
      if (artifact?.url && artifact.path) {
        const dest = path.join(LIBRARIES_DIR(), artifact.path)
        fs.mkdirSync(path.dirname(dest), { recursive: true })
        await this.downloadIfMissing(artifact.url, dest)
        if (isNative) natives.push(dest)
        else classpath.push(dest)
      } else if (lib.name && lib.url) {
        // Fabric's meta API describes libraries as Maven coordinates plus a
        // repository root instead of a prebuilt download entry — without this
        // branch the loader itself never lands on the classpath.
        const relative = this.mavenToPath(lib.name)
        if (relative) {
          const dest = path.join(LIBRARIES_DIR(), relative)
          fs.mkdirSync(path.dirname(dest), { recursive: true })
          const base = lib.url.endsWith('/') ? lib.url : `${lib.url}/`
          await this.downloadIfMissing(base + relative.replace(/\\/g, '/'), dest)
          classpath.push(dest)
        }
      }

      if (classifier?.url && classifier.path) {
        const dest = path.join(LIBRARIES_DIR(), classifier.path)
        fs.mkdirSync(path.dirname(dest), { recursive: true })
        await this.downloadIfMissing(classifier.url, dest)
        natives.push(dest)
      }

      done++
      onProgress(done, applicable.length)
    }
    return { classpath, natives }
  }

  /** "group:artifact:version" → "group/path/artifact/version/artifact-version.jar" */
  private mavenToPath(coordinate: string): string | null {
    const [group, artifact, version] = coordinate.split(':')
    if (!group || !artifact || !version) return null
    return path.join(...group.split('.'), artifact, version, `${artifact}-${version}.jar`)
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

    let done = 0
    for (const obj of entries) {
      const sub = obj.hash.slice(0, 2)
      const dest = path.join(objectsDir, sub, obj.hash)
      if (!fs.existsSync(dest)) {
        fs.mkdirSync(path.dirname(dest), { recursive: true })
        await this.downloadIfMissing(`https://resources.download.minecraft.net/${sub}/${obj.hash}`, dest)
      }
      done++
      if (done % 25 === 0 || done === entries.length) onProgress(done, entries.length)
    }
  }

  private downloadIfMissing(url: string, dest: string): Promise<void> {
    if (fs.existsSync(dest) && fs.statSync(dest).size > 0) return Promise.resolve()

    return new Promise((resolve, reject) => {
      const file = fs.createWriteStream(dest)
      https.get(url, res => {
        if (res.statusCode !== 200) {
          file.close()
          fs.unlinkSync(dest)
          reject(new Error(`HTTP ${res.statusCode} for ${url}`))
          return
        }
        res.pipe(file)
        file.on('finish', () => file.close(() => resolve()))
      }).on('error', err => {
        file.close()
        if (fs.existsSync(dest)) fs.unlinkSync(dest)
        reject(err)
      })
    })
  }

  private getJson<T>(url: string): Promise<T> {
    return new Promise((resolve, reject) => {
      https.get(url, { headers: { 'User-Agent': 'crystal-launcher' } }, res => {
        if (res.statusCode !== 200) {
          reject(new Error(`HTTP ${res.statusCode} for ${url}`))
          return
        }
        let data = ''
        res.on('data', c => (data += c))
        res.on('end', () => {
          try { resolve(JSON.parse(data)) } catch (e) { reject(e) }
        })
      }).on('error', reject)
    })
  }
}
