import fs from 'fs'
import path from 'path'
import os from 'os'
import { createHash } from 'crypto'
import { InstanceManager, Instance } from './InstanceManager'
import { JarReader } from '../util/jarReader'
import { logger } from '../logs/Logger'
import { crystalPath } from '../paths'

// Electron 31 ships a Node runtime with a native global fetch (Node 18+),
// but this tsconfig's "lib" is ES2020-only (no DOM), so it isn't typed —
// declared here instead of pulling in a DOM lib for one global.
declare function fetch(url: string, init?: { headers?: Record<string, string> }): Promise<{
  ok: boolean
  status: number
  json(): Promise<any>
  arrayBuffer(): Promise<ArrayBuffer>
}>

export interface ModrinthHit {
  project_id: string
  slug: string
  title: string
  description: string
  icon_url: string | null
  downloads: number
  categories: string[]
}

export interface ModrinthVersionFile {
  url: string
  filename: string
  primary: boolean
}

export interface ModrinthVersion {
  id: string
  version_number: string
  game_versions: string[]
  loaders: string[]
  files: ModrinthVersionFile[]
}

export interface ModInstallResult {
  success: boolean
  fileName?: string
  error?: string
}

export interface ModpackInstallResult {
  success: boolean
  name?: string
  filesInstalled?: number
  error?: string
}

/** One entry from a .mrpack's modrinth.index.json. */
interface MrpackFile {
  path: string
  downloads: string[]
  env?: { client?: 'required' | 'optional' | 'unsupported' }
}

interface MrpackIndex {
  name: string
  files: MrpackFile[]
  dependencies?: Record<string, string>
}

const API_BASE = 'https://api.modrinth.com/v2'
const HEADERS = { 'User-Agent': 'crystal-client/1.0.0 (github.com/crystal-client)' }

export type ContentType = 'mod' | 'resourcepack' | 'shader'

/**
 * Well-established client-side performance mods, each checked to exist for
 * 1.21.11 on Fabric. None of them change gameplay, so they are safe on any
 * server. (ModernFix was considered but has no 1.21.11 build.)
 */
export const PERFORMANCE_PACK: { slug: string; title: string; purpose: string }[] = [
  { slug: 'sodium',          title: 'Sodium',          purpose: 'Schnellere Chunk-Darstellung, meist der größte FPS-Gewinn' },
  { slug: 'lithium',         title: 'Lithium',         purpose: 'Effizientere Spiellogik, weniger Laggs in Einzelspieler-Welten' },
  { slug: 'ferrite-core',    title: 'FerriteCore',     purpose: 'Deutlich weniger Arbeitsspeicher-Verbrauch' },
  { slug: 'entityculling',   title: 'EntityCulling',   purpose: 'Unsichtbare Mobs und Blöcke werden nicht gezeichnet' },
  { slug: 'immediatelyfast', title: 'ImmediatelyFast', purpose: 'Schnelleres HUD, Text und Partikel' },
]

// Resourcepacks and shaders aren't loader-specific, so the loader facet is
// only applied to actual mods — adding it elsewhere returns zero results.
const LOADER_FILTERED: ContentType[] = ['mod']

const TARGET_FOLDER: Record<ContentType, string> = {
  mod: 'mods',
  resourcepack: 'resourcepacks',
  shader: 'shaderpacks',
}

export class ModrinthService {
  constructor(private instances?: InstanceManager) {}

  /** Imported instances live outside ~/.crystal, so the registered gameDir is the source of truth when we have one. */
  private gameDir(instanceId: string): string {
    const registered = this.instances?.get(instanceId)?.gameDir
    return registered || crystalPath('instances', instanceId)
  }

  async search(query: string, gameVersion: string, loader: string, type: ContentType = 'mod', offset = 0): Promise<{ hits: ModrinthHit[]; totalHits: number }> {
    const facets: string[][] = [
      [`project_type:${type}`],
      [`versions:${gameVersion}`],
    ]
    if (LOADER_FILTERED.includes(type)) facets.push([`categories:${loader}`])

    const url = `${API_BASE}/search?query=${encodeURIComponent(query)}&facets=${encodeURIComponent(JSON.stringify(facets))}&limit=40&offset=${offset}`

    try {
      const res = await fetch(url, { headers: HEADERS })
      if (!res.ok) return { hits: [], totalHits: 0 }
      const data = await res.json()
      return { hits: data.hits || [], totalHits: data.total_hits || 0 }
    } catch {
      return { hits: [], totalHits: 0 }
    }
  }

  async getVersions(projectId: string, gameVersion: string, loader: string, type: ContentType = 'mod'): Promise<ModrinthVersion[]> {
    let url = `${API_BASE}/project/${projectId}/version?game_versions=${encodeURIComponent(JSON.stringify([gameVersion]))}`
    if (LOADER_FILTERED.includes(type)) {
      url += `&loaders=${encodeURIComponent(JSON.stringify([loader]))}`
    }
    try {
      const res = await fetch(url, { headers: HEADERS })
      if (!res.ok) return []
      return await res.json()
    } catch {
      return []
    }
  }

  async install(
    instanceId: string,
    projectId: string,
    gameVersion: string,
    loader: string,
    type: ContentType = 'mod',
    versionId?: string
  ): Promise<ModInstallResult> {
    let versions = await this.getVersions(projectId, gameVersion, loader, type)

    // Resourcepacks/shaders often tag only a broad version range, so fall back
    // to the project's newest release rather than reporting "not available".
    if (versions.length === 0 && !LOADER_FILTERED.includes(type)) {
      versions = await this.getVersions(projectId, gameVersion, loader, type).catch(() => [])
      if (versions.length === 0) {
        try {
          const res = await fetch(`${API_BASE}/project/${projectId}/version`, { headers: HEADERS })
          if (res.ok) versions = await res.json()
        } catch { /* fall through to the error below */ }
      }
    }

    if (versions.length === 0) {
      return { success: false, error: `Keine passende Version für Minecraft ${gameVersion} gefunden` }
    }

    // Modrinth returns versions newest-first; an explicit versionId (from the
    // version picker) overrides that default so the player can pick an older
    // release instead of always getting latest.
    const version = (versionId && versions.find(v => v.id === versionId)) || versions[0]
    const file = version.files.find(f => f.primary) || version.files[0]
    if (!file) return { success: false, error: 'Keine Datei zum Download gefunden' }

    try {
      const res = await fetch(file.url)
      if (!res.ok) return { success: false, error: `Download fehlgeschlagen (HTTP ${res.status})` }

      const targetDir = path.join(this.gameDir(instanceId), TARGET_FOLDER[type])
      fs.mkdirSync(targetDir, { recursive: true })

      const buffer = Buffer.from(await res.arrayBuffer())
      fs.writeFileSync(path.join(targetDir, file.filename), buffer)

      return { success: true, fileName: file.filename }
    } catch (err) {
      return { success: false, error: err instanceof Error ? err.message : 'Unbekannter Fehler' }
    }
  }

  /**
   * Works out which Modrinth project an already-installed jar belongs to by
   * its SHA-1, which is how Modrinth indexes files. Returns null for anything
   * it doesn't know (hand-built jars, files from elsewhere) — those simply
   * can't offer a version switch.
   */
  async identifyFile(instanceId: string, type: ContentType, fileName: string): Promise<{
    projectId: string
    versionId: string
    versionNumber: string
  } | null> {
    const filePath = path.join(this.gameDir(instanceId), TARGET_FOLDER[type], fileName)
    if (!fs.existsSync(filePath)) return null

    const sha1 = createHash('sha1').update(fs.readFileSync(filePath)).digest('hex')

    try {
      const res = await fetch(`${API_BASE}/version_file/${sha1}?algorithm=sha1`, { headers: HEADERS })
      if (!res.ok) return null
      const version = await res.json() as ModrinthVersion & { project_id: string }
      return {
        projectId: version.project_id,
        versionId: version.id,
        versionNumber: version.version_number,
      }
    } catch {
      return null
    }
  }

  /**
   * Installs the performance pack into one instance, skipping every project
   * that is already there (matched by Modrinth project, not by file name, so a
   * manually installed Sodium isn't downloaded a second time).
   */
  async installPerformancePack(instanceId: string, gameVersion: string): Promise<{
    installed: string[]
    skipped: string[]
    failed: { title: string; error: string }[]
  }> {
    const present = new Set(Object.values(await this.identifyFolder(instanceId, 'mod')).map(i => i.projectId))
    const installed: string[] = []
    const skipped: string[] = []
    const failed: { title: string; error: string }[] = []

    for (const mod of PERFORMANCE_PACK) {
      let projectId = mod.slug
      try {
        const res = await fetch(`${API_BASE}/project/${mod.slug}`, { headers: HEADERS })
        if (res.ok) projectId = (await res.json()).id
      } catch { /* fall back to the slug, which the version endpoint also accepts */ }

      if (present.has(projectId)) {
        skipped.push(mod.title)
        continue
      }
      const result = await this.install(instanceId, mod.slug, gameVersion, 'fabric', 'mod')
      if (result.success) installed.push(mod.title)
      else failed.push({ title: mod.title, error: result.error || 'unbekannter Fehler' })
    }

    logger.info('client', `Performance-Paket: ${installed.length} installiert, ${skipped.length} vorhanden, ${failed.length} fehlgeschlagen`)
    return { installed, skipped, failed }
  }

  /** Which pack members are already in the instance, for the UI. */
  async performancePackStatus(instanceId: string): Promise<{ slug: string; title: string; purpose: string; installed: boolean }[]> {
    const present = new Set(Object.values(await this.identifyFolder(instanceId, 'mod')).map(i => i.projectId))
    const ids = await Promise.all(PERFORMANCE_PACK.map(async mod => {
      try {
        const res = await fetch(`${API_BASE}/project/${mod.slug}`, { headers: HEADERS })
        return res.ok ? (await res.json()).id as string : mod.slug
      } catch {
        return mod.slug
      }
    }))
    return PERFORMANCE_PACK.map((mod, i) => ({ ...mod, installed: present.has(ids[i]) }))
  }

  /** sha1 per file, keyed by path + size + mtime so unchanged jars are never re-hashed. */
  private hashCache = new Map<string, string>()

  private sha1Of(filePath: string): string | null {
    try {
      const stat = fs.statSync(filePath)
      const key = `${filePath}|${stat.size}|${stat.mtimeMs}`
      const cached = this.hashCache.get(key)
      if (cached) return cached
      const hash = createHash('sha1').update(fs.readFileSync(filePath)).digest('hex')
      this.hashCache.set(key, hash)
      return hash
    } catch {
      return null
    }
  }

  /**
   * Identifies every file in one content folder with a single request
   * (Modrinth's bulk version_files endpoint), instead of one request per jar.
   * Returns fileName -> { projectId, versionId } for the files Modrinth knows.
   */
  async identifyFolder(instanceId: string, type: ContentType): Promise<Record<string, { projectId: string; versionId: string }>> {
    const dir = path.join(this.gameDir(instanceId), TARGET_FOLDER[type])
    if (!fs.existsSync(dir)) return {}

    const byHash = new Map<string, string>()
    for (const fileName of fs.readdirSync(dir)) {
      const hash = this.sha1Of(path.join(dir, fileName))
      if (hash) byHash.set(hash, fileName)
    }
    if (byHash.size === 0) return {}

    try {
      const res = await (globalThis as any).fetch(`${API_BASE}/version_files`, {
        method: 'POST',
        headers: { ...HEADERS, 'Content-Type': 'application/json' },
        body: JSON.stringify({ hashes: [...byHash.keys()], algorithm: 'sha1' }),
      })
      if (!res.ok) return {}
      const data = await res.json() as Record<string, { id: string; project_id: string }>
      const result: Record<string, { projectId: string; versionId: string }> = {}
      for (const [hash, version] of Object.entries(data)) {
        const fileName = byHash.get(hash)
        if (fileName) result[fileName] = { projectId: version.project_id, versionId: version.id }
      }
      return result
    } catch (err) {
      logger.warn('client', 'Modrinth-Sammelabfrage fehlgeschlagen', String(err))
      return {}
    }
  }

  /**
   * Replaces an installed file with a different version of the same project.
   * The old file is only deleted once the new one is on disk, so a failed
   * download can't leave the instance with no mod at all.
   */
  async switchVersion(
    instanceId: string,
    type: ContentType,
    fileName: string,
    versionId: string
  ): Promise<ModInstallResult> {
    try {
      const res = await fetch(`${API_BASE}/version/${versionId}`, { headers: HEADERS })
      if (!res.ok) return { success: false, error: `Version konnte nicht geladen werden (HTTP ${res.status})` }

      const version = await res.json() as ModrinthVersion
      const file = version.files.find(f => f.primary) || version.files[0]
      if (!file) return { success: false, error: 'Diese Version hat keine herunterladbare Datei' }

      const download = await fetch(file.url)
      if (!download.ok) return { success: false, error: `Download fehlgeschlagen (HTTP ${download.status})` }

      const targetDir = path.join(this.gameDir(instanceId), TARGET_FOLDER[type])
      fs.mkdirSync(targetDir, { recursive: true })
      fs.writeFileSync(path.join(targetDir, file.filename), Buffer.from(await download.arrayBuffer()))

      if (file.filename !== fileName) {
        const old = path.join(targetDir, fileName)
        if (fs.existsSync(old)) fs.unlinkSync(old)
      }

      return { success: true, fileName: file.filename }
    } catch (err) {
      return { success: false, error: err instanceof Error ? err.message : 'Unbekannter Fehler' }
    }
  }

  /** Browsable "preset" list for Create Instance — finished Modrinth modpacks, not individual mods. */
  async searchModpacks(query: string, gameVersion?: string): Promise<ModrinthHit[]> {
    const facets: string[][] = [['project_type:modpack']]
    if (gameVersion) facets.push([`versions:${gameVersion}`])

    const url = `${API_BASE}/search?query=${encodeURIComponent(query)}&facets=${encodeURIComponent(JSON.stringify(facets))}&limit=40`

    try {
      const res = await fetch(url, { headers: HEADERS })
      if (!res.ok) return []
      const data = await res.json()
      return data.hits || []
    } catch (err) {
      logger.warn('client', 'Modrinth-Modpack-Suche fehlgeschlagen', String(err))
      return []
    }
  }

  /**
   * Installs a Modrinth modpack (.mrpack) into an instance: downloads every
   * client-required file the pack lists, then unpacks its "overrides" folder
   * (configs, resource packs, whatever the pack author bundled) on top.
   *
   * The instance must already exist and be otherwise empty-ish — this adds
   * files, it never removes what's already in the mods folder.
   */
  async installModpack(instanceId: string, projectId: string): Promise<ModpackInstallResult> {
    const instance = this.instances?.get(instanceId)
    if (!instance) return { success: false, error: 'Instanz nicht gefunden.' }

    let versions: ModrinthVersion[]
    try {
      const res = await fetch(`${API_BASE}/project/${projectId}/version`, { headers: HEADERS })
      if (!res.ok) return { success: false, error: `Modrinth antwortete mit HTTP ${res.status}` }
      versions = await res.json()
    } catch (err) {
      return { success: false, error: err instanceof Error ? err.message : 'Modpack-Versionen konnten nicht geladen werden' }
    }

    if (versions.length === 0) return { success: false, error: 'Dieses Modpack hat keine veröffentlichten Versionen.' }

    const version = versions[0]
    const file = version.files.find(f => f.primary) || version.files[0]
    if (!file) return { success: false, error: 'Keine .mrpack-Datei in dieser Version gefunden.' }

    const tempPath = path.join(os.tmpdir(), `crystal-modpack-${Date.now()}.mrpack`)
    try {
      const res = await fetch(file.url)
      if (!res.ok) return { success: false, error: `Download fehlgeschlagen (HTTP ${res.status})` }
      fs.writeFileSync(tempPath, Buffer.from(await res.arrayBuffer()))

      return await this.installMrpackFile(instance, tempPath)
    } catch (err) {
      logger.error('client', 'Modpack-Installation fehlgeschlagen', err)
      return { success: false, error: err instanceof Error ? err.message : 'Unbekannter Fehler' }
    } finally {
      fs.rm(tempPath, { force: true }, () => {})
    }
  }

  /** Same install as {@link installModpack}, but from a .mrpack file already on disk — used by the "Datei importieren" picker. */
  async installModpackFromFile(instanceId: string, filePath: string): Promise<ModpackInstallResult> {
    const instance = this.instances?.get(instanceId)
    if (!instance) return { success: false, error: 'Instanz nicht gefunden.' }
    if (!filePath.toLowerCase().endsWith('.mrpack')) {
      return { success: false, error: 'Das ist keine .mrpack-Datei.' }
    }
    if (!fs.existsSync(filePath)) {
      return { success: false, error: 'Die Datei existiert nicht (mehr).' }
    }

    try {
      return await this.installMrpackFile(instance, filePath)
    } catch (err) {
      logger.error('client', 'Modpack-Import aus Datei fehlgeschlagen', err)
      return { success: false, error: err instanceof Error ? err.message : 'Unbekannter Fehler' }
    }
  }

  /**
   * Shared core: reads a .mrpack file's index, downloads every client-required
   * mod it lists, then unpacks its "overrides" folder (configs, resource
   * packs, whatever the pack author bundled) on top.
   *
   * The instance must already exist and be otherwise empty-ish — this adds
   * files, it never removes what's already in the mods folder.
   */
  private async installMrpackFile(instance: Instance, mrpackPath: string): Promise<ModpackInstallResult> {
    let pack: JarReader
    try {
      pack = new JarReader(mrpackPath)
    } catch (err) {
      return { success: false, error: err instanceof Error ? err.message : 'Die .mrpack-Datei ist beschädigt.' }
    }

    const index = pack.readJson<MrpackIndex>('modrinth.index.json')
    if (!index) return { success: false, error: 'In diesem Modpack fehlt modrinth.index.json, die Datei ist beschädigt.' }

    const gameDir = this.gameDir(instance.id)
    fs.mkdirSync(gameDir, { recursive: true })

    let installed = 0
    for (const entry of index.files) {
      if (entry.env?.client === 'unsupported') continue

      const destination = path.join(gameDir, entry.path)
      fs.mkdirSync(path.dirname(destination), { recursive: true })

      const downloaded = await this.downloadFirstWorking(entry.downloads)
      if (!downloaded) {
        logger.warn('client', `Modpack-Datei "${entry.path}" konnte von keiner Quelle geladen werden`)
        continue
      }
      fs.writeFileSync(destination, downloaded)
      installed++
    }

    // Bundled configs/resourcepacks/etc. — "overrides" always, "client-overrides" takes priority when present.
    this.extractOverrides(pack, 'overrides/', gameDir)
    this.extractOverrides(pack, 'client-overrides/', gameDir)

    // A preset picks the Minecraft version and loader for you.
    const mcVersion = index.dependencies?.minecraft
    const loader = index.dependencies?.['fabric-loader'] ? 'fabric'
      : index.dependencies?.['forge'] ? 'forge'
      : index.dependencies?.['quilt-loader'] ? 'fabric'
      : instance.loader
    this.instances?.update(instance.id, {
      version: mcVersion || instance.version,
      loader,
    })

    logger.info('client', `Modpack "${index.name}" in "${instance.name}" installiert (${installed} Dateien)`)
    return { success: true, name: index.name, filesInstalled: installed }
  }

  private async downloadFirstWorking(urls: string[]): Promise<Buffer | null> {
    for (const url of urls) {
      try {
        const res = await fetch(url)
        if (res.ok) return Buffer.from(await res.arrayBuffer())
      } catch {
        // try the next mirror
      }
    }
    return null
  }

  private extractOverrides(pack: JarReader, prefix: string, gameDir: string): void {
    for (const name of pack.names()) {
      if (!name.startsWith(prefix) || name.endsWith('/')) continue

      const relative = name.slice(prefix.length)
      if (!relative) continue

      const destination = path.join(gameDir, relative)
      const data = pack.read(name)
      if (!data) continue

      fs.mkdirSync(path.dirname(destination), { recursive: true })
      fs.writeFileSync(destination, data)
    }
  }
}
