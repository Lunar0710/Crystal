import { execFile, ChildProcess } from 'child_process'
import { app } from 'electron'
import path from 'path'
import fs from 'fs'
import os from 'os'
import https from 'https'
import Store from 'electron-store'
import { AuthProfile } from '../auth/AuthManager'
import { LaunchPipeline } from './LaunchPipeline'
import { logger } from '../logs/Logger'

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
}

export interface VersionInfo {
  id: string
  type: 'release' | 'snapshot' | 'old_beta' | 'old_alpha'
  releaseTime: string
  url: string
}

const VERSION_MANIFEST_URL = 'https://launchermeta.mojang.com/mc/game/version_manifest_v2.json'
// Matches the version the Crystal client mod is compiled against
// (see client/gradle.properties minecraft_version).
const SUPPORTED_VERSIONS = ['1.21.11']

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
    const supported = manifest.versions.filter(v => SUPPORTED_VERSIONS.includes(v.id))

    this.versionCache = supported
    return supported
  }

  // Determines which mod loader options apply to a given MC version.
  // Fabric supports 1.14+, Forge supports the whole 1.8.9+ range.
  getSupportedLoaders(version: string): Array<'vanilla' | 'fabric' | 'forge'> {
    const loaders: Array<'vanilla' | 'fabric' | 'forge'> = ['vanilla', 'forge']
    if (this.compareVersions(version, '1.14') >= 0) loaders.push('fabric')
    return loaders
  }

  private compareVersions(a: string, b: string): number {
    const pa = a.split('.').map(Number)
    const pb = b.split('.').map(Number)
    for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
      const diff = (pa[i] || 0) - (pb[i] || 0)
      if (diff !== 0) return diff
    }
    return 0
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
    const javaPath = this.findJava(opts.version)
    if (!javaPath) {
      emit('launch:error', 'Java not found. Please install a compatible Java version.')
      return false
    }

    if (!opts.profile) {
      emit('launch:error', 'Not logged in. Please sign in with Microsoft or use offline mode.')
      return false
    }

    const gameDir = opts.gameDir || path.join(os.homedir(), '.crystal', 'instances', opts.instanceId)
    fs.mkdirSync(gameDir, { recursive: true })

    const modsDir = path.join(gameDir, 'mods')
    fs.mkdirSync(modsDir, { recursive: true })

    if (opts.injectCrystal) {
      if (!this.injectCrystalMod(modsDir)) {
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
      }, emit)
    } catch (err) {
      emit('launch:error', err instanceof Error ? err.message : 'Unbekannter Fehler beim Starten')
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

  private findBundledCrystalJar(): string | null {
    const dir = this.resolveCrystalModSourceDir()
    if (!fs.existsSync(dir)) return null

    // A stale build (old gradle output, or a partial clean) can leave more than
    // one version sitting here — directory order isn't guaranteed, so always
    // pick the newest by version number rather than whichever readdir returns first.
    const jars = fs.readdirSync(dir)
      .filter(f => f.startsWith(CRYSTAL_MOD_MARKER) && f.endsWith('.jar') && !f.endsWith('-sources.jar'))
      .sort((a, b) => this.compareVersions(this.extractJarVersion(a), this.extractJarVersion(b)))

    const jar = jars[jars.length - 1]
    return jar ? path.join(dir, jar) : null
  }

  private extractJarVersion(fileName: string): string {
    const match = fileName.match(/^crystal-client-([\d.]+)\.jar$/)
    return match ? match[1] : '0'
  }

  private injectCrystalMod(modsDir: string): boolean {
    const source = this.findBundledCrystalJar()
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

    const cacheDir = path.join(os.homedir(), '.crystal', 'cache')
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
      if (!file) {
        logger.error('client', 'Fabric-API-Version enthält keine herunterladbare Datei')
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

  private findJava(version: string): string | null {
    // 1.17+ requires Java 16+, 1.18+ requires Java 17+, 1.20.5+ requires Java 21.
    // Pre-1.17 versions run best on Java 8. We probe for the closest match
    // and fall back to whatever JDK is available.
    const needsModernJava = this.compareVersions(version, '1.17') >= 0
    const homeCandidate = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', 'java.exe') : null

    const modernCandidates = [
      'C:\\Program Files\\Eclipse Adoptium\\jdk-21\\bin\\java.exe',
      'C:\\Program Files\\Java\\jdk-21\\bin\\java.exe',
      'C:\\Program Files\\Microsoft\\jdk-21\\bin\\java.exe',
      'C:\\Program Files\\Eclipse Adoptium\\jdk-17\\bin\\java.exe',
    ]

    const legacyCandidates = [
      'C:\\Program Files\\Eclipse Adoptium\\jdk-8\\bin\\java.exe',
      'C:\\Program Files (x86)\\Java\\jre8\\bin\\java.exe',
      'C:\\Program Files\\Java\\jre8\\bin\\java.exe',
    ]

    const ordered = needsModernJava
      ? [homeCandidate, ...modernCandidates, ...legacyCandidates]
      : [homeCandidate, ...legacyCandidates, ...modernCandidates]

    for (const candidate of ordered.filter(Boolean) as string[]) {
      if (fs.existsSync(candidate)) return candidate
    }
    return null
  }
}
