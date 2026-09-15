import { execFile, spawnSync, ChildProcess } from 'child_process'
import extract from 'extract-zip'
import { app } from 'electron'
import path from 'path'
import fs from 'fs'
import os from 'os'
import https from 'https'
import Store from 'electron-store'
import { AuthProfile } from '../auth/AuthManager'
import { LaunchPipeline } from './LaunchPipeline'
import { logger } from '../logs/Logger'
import { crystalPath, isPlainFileName } from '../paths'
import { adoptiumJdk, extractTarGz, javaCandidates, javaInJdk } from './platform'
import { compareVersions, downloadJavaMajor, fabricMetaFor, isSupportedVersion, javaFits, needsX64JavaOnArmMac } from './versions'

// Electron/Node 18+ ships a global fetch; not covered by this tsconfig's
// ES2020-only lib, so declared locally instead of pulling in a DOM lib.
declare function fetch(url: string, init?: { headers?: Record<string, string> }): Promise<{
  ok: boolean
  status: number
  json(): Promise<any>
  arrayBuffer(): Promise<ArrayBuffer>
}>

const CRYSTAL_MOD_MARKER = 'crystal-client-'
const FABRIC_API_MARKER = 'fabric-api-'
const MODRINTH_HEADERS = { 'User-Agent': 'crystal-client/1.0.0 (github.com/crystal-client)' }

export interface LaunchOptions {
  version: string
  instanceId: string
  gameDir: string
  username: string
  maxRam: number
  loader?: 'vanilla' | 'fabric' | 'forge'
  profile?: AuthProfile | null
  // Whether to inject Crystal's own Fabric mod (HUD + QoL modules) into this
  // instance's mods folder before launching. false = plain "Vanilla + Mods"
  // launch using only whatever the user installed themselves (Modrinth/manual).
  injectCrystal?: boolean
  /** Test runs only, see LaunchPipelineOptions. */
  extraJvmArgs?: string[]
  extraGameArgs?: string[]
}

export interface VersionInfo {
  id: string
  type: 'release' | 'snapshot' | 'old_beta' | 'old_alpha'
  releaseTime: string
  url: string
}

const VERSION_MANIFEST_URL = 'https://launchermeta.mojang.com/mc/game/version_manifest_v2.json'
/** The one Minecraft version the unsuffixed client jar (crystal-client-X.jar) is built for. */
const LEGACY_JAR_MC_VERSION = '1.21.11'

export class MinecraftManager {
  private store: Store
  private runningProcesses: Map<string, ChildProcess> = new Map()
  private versionCache: VersionInfo[] | null = null
  private pipeline = new LaunchPipeline()

  constructor(store: Store) {
    this.store = store
  }

  // Crystal's client mod is built against exactly one Minecraft version, so
  // the launcher only offers that one — anything else would load a mod jar
  // compiled against mismatched mappings.
  // Only ever returns versions Mojang actually publishes — if the manifest
  // can't be reached we return nothing rather than inventing an entry the
  // launcher would then fail to download.
  async fetchAllVersions(): Promise<VersionInfo[]> {
    if (this.versionCache) return this.versionCache

    const manifest = await this.httpGetJson<{ versions: VersionInfo[] }>(VERSION_MANIFEST_URL)
    // Every release from 1.8.9 to 26.2.
    const supported = manifest.versions.filter(v => v.type === 'release' && isSupportedVersion(v.id))

    this.versionCache = supported
    return supported
  }

  // Mod loaders that exist for a version: Fabric from 1.14, Legacy Fabric on
  // 1.8.9, 1.9.4, 1.10.2, 1.11.2, 1.12.2 and 1.13.2. Forge isn't launched yet.
  getSupportedLoaders(version: string): Array<'vanilla' | 'fabric' | 'forge'> {
    const loaders: Array<'vanilla' | 'fabric' | 'forge'> = ['vanilla']
    if (fabricMetaFor(version)) loaders.push('fabric')
    return loaders
  }

  /** Whether a Crystal client build exists for this Minecraft version. */
  hasCrystalFor(version: string): boolean {
    return this.findBundledCrystalJar(version) !== null
  }

  private compareVersions(a: string, b: string): number {
    return compareVersions(a, b)
  }

  private httpGetJson<T>(url: string): Promise<T> {
    return new Promise((resolve, reject) => {
      https.get(url, res => {
        if (res.statusCode !== 200) {
          reject(new Error(`HTTP ${res.statusCode}`))
          return
        }
        let data = ''
        res.on('data', chunk => (data += chunk))
        res.on('end', () => {
          try {
            resolve(JSON.parse(data))
          } catch (e) {
            reject(e)
          }
        })
      }).on('error', reject)
    })
  }

  async launch(opts: LaunchOptions, emit: (event: string, data: unknown) => void): Promise<boolean> {
    const javaPath = await this.ensureJava(opts.version, emit)
    if (!javaPath) {
      emit('launch:error',
        `Es konnte kein Java ${downloadJavaMajor(opts.version)} für Minecraft ${opts.version} gefunden oder heruntergeladen werden.\n\n` +
        `Prüfe deine Internetverbindung, oder installiere Java ${downloadJavaMajor(opts.version)} manuell (adoptium.net).`)
      return false
    }

    if (!opts.profile) {
      emit('launch:error', 'Not logged in. Please sign in with Microsoft or use offline mode.')
      return false
    }

    const gameDir = opts.gameDir || crystalPath('instances', opts.instanceId)
    fs.mkdirSync(gameDir, { recursive: true })

    const modsDir = path.join(gameDir, 'mods')
    fs.mkdirSync(modsDir, { recursive: true })

    if (opts.injectCrystal) {
      if (!this.hasCrystalFor(opts.version) && this.findBundledCrystalJar(LEGACY_JAR_MC_VERSION)) {
        emit('launch:error',
          `Crystal gibt es für Minecraft ${opts.version} noch nicht. Die Crystal-Module kommen Version für Version dazu.\n\n` +
          'Starte diese Instanz so lange ohne Crystal ("Vanilla + Mods"), oder nimm eine Version mit Crystal.')
        return false
      }
      if (!this.injectCrystalMod(modsDir, opts.version)) {
        emit('launch:error',
          'Crystal-Client-Mod nicht gefunden.\n\n' +
          `Gesucht in: ${this.resolveCrystalModSourceDir()}\n\n` +
          'Client bauen mit "gradlew build" im client-Ordner, oder "Vanilla + Mods" wählen.')
        return false
      }

      // Crystal's fabric.mod.json hard-requires fabric-api — without it Fabric
      // Loader refuses to start at all, so this has to succeed before we even
      // try to launch, not be left for the user to figure out from a crash log.
      if (!(await this.ensureFabricApi(modsDir, opts.version, emit))) {
        emit('launch:error',
          'Fabric API (von Crystal benötigt) konnte nicht installiert werden.\n\n' +
          'Prüfe deine Internetverbindung oder installiere Fabric API manuell in den mods-Ordner.')
        return false
      }
    } else {
      // "Vanilla + Mods" launch: strip any previously injected Crystal jar so
      // switching modes on the same instance doesn't silently keep it active.
      this.removeCrystalMod(modsDir)
    }

    try {
      return await this.pipeline.launch({
        version: opts.version,
        loader: opts.injectCrystal ? 'fabric' : this.resolveLoaderFor(opts),
        gameDir,
        javaPath,
        maxRam: opts.maxRam,
        profile: opts.profile,
        extraJvmArgs: opts.extraJvmArgs,
        extraGameArgs: opts.extraGameArgs,
      }, emit)
    } catch (err) {
      emit('launch:error', err instanceof Error ? (err.message || err.stack || err.name) : 'Unbekannter Fehler beim Starten')
      return false
    }
  }

  private resolveLoaderFor(opts: LaunchOptions): 'vanilla' | 'fabric' | 'forge' {
    return opts.loader || 'vanilla'
  }

  // Locates the compiled Crystal Client Fabric jar, wherever this build ships it.
  // Anchored to the app root rather than __dirname: this file compiles into a
  // nested folder, so relative climbing silently resolves to the wrong place.
  private resolveCrystalModSourceDir(): string {
    return app.isPackaged
      ? path.join(process.resourcesPath, 'crystal-mod')
      : path.join(app.getAppPath(), '..', 'client', 'build', 'libs')
  }

  /** Version of the client mod jar that ships with this launcher, or null if none is bundled. */
  getBundledClientVersion(): string | null {
    const jar = this.findBundledCrystalJar(LEGACY_JAR_MC_VERSION)
    return jar ? this.parseJarName(path.basename(jar))?.mod ?? null : null
  }

  /**
   * The newest client jar built for this Minecraft version. Jars are named
   * crystal-client-<mod>+<mc>.jar (one per Minecraft version); the older
   * crystal-client-<mod>.jar without "+<mc>" is the 1.21.11 build.
   */
  private findBundledCrystalJar(mcVersion: string): string | null {
    const dir = this.resolveCrystalModSourceDir()
    if (!fs.existsSync(dir)) return null

    // A stale build (old gradle output, or a partial clean) can leave more than
    // one version sitting here — directory order isn't guaranteed, so always
    // pick the newest by version number rather than whichever readdir returns first.
    const jars = fs.readdirSync(dir)
      .map(f => ({ f, info: this.parseJarName(f) }))
      .filter((j): j is { f: string; info: { mod: string; mc: string } } => j.info !== null && j.info.mc === mcVersion)
      .sort((a, b) => this.compareVersions(a.info.mod, b.info.mod))

    const jar = jars[jars.length - 1]
    return jar ? path.join(dir, jar.f) : null
  }

  private parseJarName(fileName: string): { mod: string; mc: string } | null {
    if (!fileName.startsWith(CRYSTAL_MOD_MARKER) || !fileName.endsWith('.jar') || fileName.endsWith('-sources.jar')) return null
    const match = fileName.match(/^crystal-client-([\d.]+)(?:\+([\d.]+))?\.jar$/)
    return match ? { mod: match[1], mc: match[2] ?? LEGACY_JAR_MC_VERSION } : null
  }

  private injectCrystalMod(modsDir: string, mcVersion: string): boolean {
    const source = this.findBundledCrystalJar(mcVersion)
    if (!source) return false

    this.removeCrystalMod(modsDir)
    fs.copyFileSync(source, path.join(modsDir, path.basename(source)))
    return true
  }

  /** Downloads Fabric API from Modrinth into modsDir if it isn't already there. Cached under ~/.crystal so repeat launches don't re-download. */
  private async ensureFabricApi(modsDir: string, gameVersion: string, emit: (event: string, data: unknown) => void): Promise<boolean> {
    if (fs.readdirSync(modsDir).some(f => f.toLowerCase().startsWith(FABRIC_API_MARKER) && f.endsWith('.jar'))) {
      return true
    }

    const cacheDir = crystalPath('cache')
    fs.mkdirSync(cacheDir, { recursive: true })
    const cachedPath = path.join(cacheDir, `fabric-api-${gameVersion}.jar`)

    if (fs.existsSync(cachedPath)) {
      fs.copyFileSync(cachedPath, path.join(modsDir, path.basename(cachedPath)))
      return true
    }

    emit('launch:progress', { step: 'Installiere Fabric API (von Crystal benötigt)...', percent: 40 })

    try {
      const versionsUrl = `https://api.modrinth.com/v2/project/fabric-api/version` +
        `?game_versions=${encodeURIComponent(JSON.stringify([gameVersion]))}` +
        `&loaders=${encodeURIComponent(JSON.stringify(['fabric']))}`

      const versionsRes = await fetch(versionsUrl, { headers: MODRINTH_HEADERS })
      if (!versionsRes.ok) {
        logger.error('client', `Fabric-API-Versionsabfrage fehlgeschlagen: HTTP ${versionsRes.status}`)
        return false
      }

      const versions = await versionsRes.json() as { files: { url: string; filename: string; primary: boolean }[] }[]
      if (versions.length === 0) {
        logger.error('client', `Keine Fabric-API-Version für Minecraft ${gameVersion} gefunden`)
        return false
      }

      const file = versions[0].files.find(f => f.primary) || versions[0].files[0]
      if (!file || !isPlainFileName(file.filename)) {
        logger.error('client', 'Fabric-API-Version enthält keine gültige herunterladbare Datei')
        return false
      }

      const downloadRes = await fetch(file.url)
      if (!downloadRes.ok) {
        logger.error('client', `Fabric-API-Download fehlgeschlagen: HTTP ${downloadRes.status}`)
        return false
      }

      const buffer = Buffer.from(await downloadRes.arrayBuffer())
      fs.writeFileSync(cachedPath, buffer)
      fs.copyFileSync(cachedPath, path.join(modsDir, file.filename))
      logger.info('client', `Fabric API installiert: ${file.filename}`)
      return true
    } catch (err) {
      logger.error('client', 'Fabric-API-Installation fehlgeschlagen', err)
      return false
    }
  }

  private removeCrystalMod(modsDir: string): void {
    if (!fs.existsSync(modsDir)) return
    for (const file of fs.readdirSync(modsDir)) {
      if (file.startsWith(CRYSTAL_MOD_MARKER) && file.endsWith('.jar')) {
        fs.unlinkSync(path.join(modsDir, file))
      }
    }
  }

  /**
   * A friend's fresh Windows install almost never has Java, and telling them to
   * go install it themselves is exactly the kind of "fake installer" moment we
   * don't want. So: check what's already there first (fixed paths, JAVA_HOME,
   * PATH), and only if genuinely nothing is found, download a portable Temurin
   * JDK 21 (Eclipse Adoptium's public build API, no auth needed) into
   * ~/.crystal/jdk and use that from then on — cached, so this only ever
   * happens once per machine.
   */
  private async ensureJava(version: string, emit: (event: string, data: unknown) => void): Promise<string | null> {
    const major = downloadJavaMajor(version)
    const forceX64 = needsX64JavaOnArmMac(version)
    const bundledDir = crystalPath('jdk', forceX64 ? `${major}-x64` : String(major))
    // Check our own previously-downloaded copy first — no point re-validating
    // it every launch, and it can't be some unrelated stale Java 8 install.
    if (fs.existsSync(javaInJdk(bundledDir))) return javaInJdk(bundledDir)

    // An installed Java only counts on the matching architecture; on Apple
    // Silicon old versions need the x64 one, so those always use our own copy.
    const existing = forceX64 ? null : this.findJava(version)
    if (existing) return existing

    const download = adoptiumJdk(major, forceX64)
    if (!download) {
      logger.error('client', `Kein Java-Download für ${process.platform}/${process.arch} verfügbar`)
      return null
    }

    emit('launch:progress', { step: `Lade Java ${major} herunter (einmalig)...`, percent: 5 })

    try {
      const res = await fetch(download.url)
      if (!res.ok) {
        logger.error('client', `Java-Download fehlgeschlagen: HTTP ${res.status}`)
        return null
      }

      const cacheDir = crystalPath('cache')
      fs.mkdirSync(cacheDir, { recursive: true })
      const archivePath = path.join(cacheDir, `temurin-${major}-${process.platform}-${forceX64 ? 'x64' : process.arch}.${download.archive}`)
      fs.writeFileSync(archivePath, Buffer.from(await res.arrayBuffer()))

      emit('launch:progress', { step: `Installiere Java ${major}...`, percent: 15 })

      const extractDir = crystalPath('jdk', `${major}-extract`)
      fs.rmSync(extractDir, { recursive: true, force: true })
      fs.mkdirSync(extractDir, { recursive: true })
      if (download.archive === 'zip') await extract(archivePath, { dir: extractDir })
      else extractTarGz(archivePath, extractDir)

      // The archive contains one top-level "jdk-21.x.y+z" folder — move its
      // contents up so the final path is always <data>/jdk/21/bin/java
      // (…/Contents/Home/bin/java on macOS) whatever patch version Adoptium serves.
      const inner = fs.readdirSync(extractDir).find(f => fs.statSync(path.join(extractDir, f)).isDirectory())
      if (!inner) {
        logger.error('client', 'Java-Archiv hatte kein erwartetes JDK-Verzeichnis')
        return null
      }

      fs.rmSync(bundledDir, { recursive: true, force: true })
      fs.renameSync(path.join(extractDir, inner), bundledDir)
      fs.rmSync(extractDir, { recursive: true, force: true })
      fs.unlinkSync(archivePath)

      const bundledJava = javaInJdk(bundledDir)
      if (!fs.existsSync(bundledJava)) {
        logger.error('client', `Java-Installation unvollständig, erwartet: ${bundledJava}`)
        return null
      }

      logger.info('client', `Java ${major} automatisch installiert unter ${bundledDir}`)
      return bundledJava
    } catch (err) {
      logger.error('client', 'Automatische Java-Installation fehlgeschlagen', err)
      return null
    }
  }

  /**
   * Parses "java -version"'s stderr output for the major version.
   * Handles both formats: `java version "1.8.0_411"` (old, major = 8) and
   * `openjdk version "21.0.12" ...` (new, major = 21).
   */
  private getJavaMajorVersion(javaPath: string): number | null {
    // "java -version" writes its output to stderr, not stdout, and exits 0 —
    // execFileSync only returns stdout, so it looked like empty output no
    // matter what Java was actually installed. spawnSync captures both.
    const result = spawnSync(javaPath, ['-version'], { encoding: 'utf8' })
    const text = (result.stderr || '') + (result.stdout || '')
    const match = text.match(/version "(\d+)(?:\.(\d+))?/)
    if (!match) return null
    const first = parseInt(match[1], 10)
    // "1.8.0_411" -> major is the SECOND number; "21.0.12" -> major is the first.
    return first === 1 ? parseInt(match[2] || '0', 10) : first
  }

  private findJava(version: string): string | null {
    const candidates = javaCandidates()

    // Existence alone isn't enough — a stale Java 8 on JAVA_HOME/PATH would
    // otherwise "win" over actually downloading the version Minecraft needs,
    // which is exactly the bug that let a Java-8 machine slip through before.
    for (const candidate of candidates) {
      if (candidate !== 'java' && !fs.existsSync(candidate)) continue
      const major = this.getJavaMajorVersion(candidate)
      if (major !== null && javaFits(version, major)) return candidate
    }
    return null
  }
}
