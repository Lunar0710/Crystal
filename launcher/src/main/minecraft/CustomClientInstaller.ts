import fs from 'fs'
import path from 'path'
import { dialog, BrowserWindow } from 'electron'
import { InstanceManager, Instance } from './InstanceManager'
import { JarReader, NotAZipError } from '../util/jarReader'
import { logger } from '../logs/Logger'

/** Metadata a Fabric mod declares about itself. */
interface FabricModJson {
  id?: string
  name?: string
  version?: string
  depends?: Record<string, string | string[]>
  environment?: string
}

export interface JarInspection {
  valid: boolean
  /** Why the jar was rejected — always filled in when valid is false. */
  reason?: string
  fileName: string
  modId?: string
  name?: string
  version?: string
  loader: 'fabric' | 'forge' | 'unknown'
  /** Raw version predicate the mod declares for Minecraft, e.g. ">=1.21". */
  minecraftDepends?: string
  fabricLoaderDepends?: string
}

export interface InstalledClient {
  fileName: string
  modId?: string
  name?: string
  version?: string
  installedAt: number
  sizeBytes: number
}

export interface InstallTarget {
  instanceId: string
  instanceName: string
  minecraftVersion: string
  loader: string
  /** Latest Fabric loader Fabric publishes for this Minecraft version. */
  fabricLoaderVersion: string | null
  fabricAvailable: boolean
  modsDir: string
  modsDirWritable: boolean
  installed: InstalledClient[]
  /** Set when the target is unusable — the UI shows this instead of the install button. */
  blocker?: string
}

export interface InstallResult {
  success: boolean
  message: string
  fileName?: string
  /** Name of the backup created when an older copy was replaced. */
  replacedBackup?: string
}

const FABRIC_META = 'https://meta.fabricmc.net/v2/versions/loader'
const BACKUP_DIR = '.crystal-backups'

/**
 * Installs a user-supplied client mod (Nexora's own jar or any other Fabric
 * client) into one specific instance.
 *
 * Everything is validated before a single byte is written, and an existing copy
 * is moved into a backup folder rather than overwritten, so a bad jar can never
 * cost the user the version that was working.
 */
export class CustomClientInstaller {
  constructor(private instances: InstanceManager) {}

  private modsDir(instance: Instance): string {
    return path.join(instance.gameDir, 'mods')
  }

  private backupDir(instance: Instance): string {
    return path.join(instance.gameDir, BACKUP_DIR)
  }

  // ---------------------------------------------------------------- inspect

  /** Reads a jar's metadata without installing it. Never throws. */
  inspect(filePath: string): JarInspection {
    const fileName = path.basename(filePath)
    const reject = (reason: string): JarInspection =>
      ({ valid: false, reason, fileName, loader: 'unknown' })

    if (!fs.existsSync(filePath)) {
      return reject('Die Datei existiert nicht (mehr).')
    }
    if (!fs.statSync(filePath).isFile()) {
      return reject('Der gewählte Pfad ist keine Datei.')
    }
    if (!fileName.toLowerCase().endsWith('.jar')) {
      return reject('Nur .jar-Dateien können als Client installiert werden.')
    }

    let jar: JarReader
    try {
      jar = new JarReader(filePath)
    } catch (err) {
      if (err instanceof NotAZipError) return reject(err.message)
      logger.error('client', `Konnte "${fileName}" nicht lesen`, err)
      return reject(`Die Datei konnte nicht gelesen werden: ${err instanceof Error ? err.message : String(err)}`)
    }

    if (!jar.has('fabric.mod.json')) {
      // Distinguishing the loader matters: "wrong loader" is a very different
      // problem for the user than "this isn't a mod at all".
      const isForge = jar.has('META-INF/mods.toml') || jar.has('META-INF/neoforge.mods.toml')
      if (isForge) {
        return {
          ...reject('Das ist ein Forge/NeoForge-Mod. Nexora-Instanzen laufen auf Fabric.'),
          loader: 'forge',
        }
      }
      return reject('Die Datei enthält keine fabric.mod.json, das ist kein Fabric-Mod.')
    }

    let meta: FabricModJson | null
    try {
      meta = jar.readJson<FabricModJson>('fabric.mod.json')
    } catch (err) {
      return reject(`fabric.mod.json ist kein gültiges JSON: ${err instanceof Error ? err.message : String(err)}`)
    }

    if (!meta?.id) {
      return reject('fabric.mod.json enthält keine Mod-ID, die Datei ist unvollständig.')
    }

    const asText = (value: string | string[] | undefined) =>
      Array.isArray(value) ? value.join(' oder ') : value

    return {
      valid: true,
      fileName,
      loader: 'fabric',
      modId: meta.id,
      name: meta.name || meta.id,
      version: meta.version,
      minecraftDepends: asText(meta.depends?.minecraft),
      fabricLoaderDepends: asText(meta.depends?.fabricloader),
    }
  }

  // ----------------------------------------------------------------- target

  /** Everything the install screen needs to show about the selected instance. */
  async describeTarget(instanceId: string): Promise<InstallTarget | { blocker: string }> {
    const instance = this.instances.get(instanceId)
    if (!instance) {
      return { blocker: 'Instanz nicht gefunden. Wähle eine andere Instanz.' }
    }

    const modsDir = this.modsDir(instance)
    let modsDirWritable = false
    try {
      fs.mkdirSync(modsDir, { recursive: true })
      fs.accessSync(modsDir, fs.constants.W_OK)
      modsDirWritable = true
    } catch (err) {
      logger.error('client', `mods-Ordner von "${instance.name}" nicht beschreibbar`, err)
    }

    const fabricLoaderVersion = await this.latestFabricLoader(instance.version)

    const target: InstallTarget = {
      instanceId,
      instanceName: instance.name,
      minecraftVersion: instance.version,
      loader: instance.loader,
      fabricLoaderVersion,
      fabricAvailable: fabricLoaderVersion !== null,
      modsDir,
      modsDirWritable,
      installed: this.listInstalled(instance),
    }

    if (!modsDirWritable) {
      target.blocker = `Auf den mods-Ordner kann nicht geschrieben werden:\n${modsDir}\nLäuft Minecraft noch, oder fehlen Schreibrechte?`
    } else if (!target.fabricAvailable) {
      target.blocker = `Für Minecraft ${instance.version} gibt es keinen Fabric-Loader. Client-Mods brauchen Fabric.`
    }

    return target
  }

  private async latestFabricLoader(minecraftVersion: string): Promise<string | null> {
    try {
      const response = await fetch(`${FABRIC_META}/${minecraftVersion}`)
      if (!response.ok) {
        logger.warn('client', `Fabric-Meta antwortete mit ${response.status} für ${minecraftVersion}`)
        return null
      }
      const loaders = await response.json() as { loader: { version: string } }[]
      return loaders[0]?.loader.version ?? null
    } catch (err) {
      logger.error('client', `Fabric-Loader für ${minecraftVersion} konnte nicht abgefragt werden`, err)
      return null
    }
  }

  private listInstalled(instance: Instance): InstalledClient[] {
    const dir = this.modsDir(instance)
    if (!fs.existsSync(dir)) return []

    const found: InstalledClient[] = []
    for (const fileName of fs.readdirSync(dir)) {
      if (!fileName.toLowerCase().endsWith('.jar')) continue

      const full = path.join(dir, fileName)
      const stat = fs.statSync(full)
      const entry: InstalledClient = { fileName, installedAt: stat.mtimeMs, sizeBytes: stat.size }

      // Best effort: a jar we can't parse still gets listed, just without names.
      try {
        const meta = new JarReader(full).readJson<FabricModJson>('fabric.mod.json')
        if (meta) {
          entry.modId = meta.id
          entry.name = meta.name || meta.id
          entry.version = meta.version
        }
      } catch {
        logger.debug('client', `Metadaten von "${fileName}" nicht lesbar — wird ohne Details gelistet`)
      }

      found.push(entry)
    }
    return found.sort((a, b) => b.installedAt - a.installedAt)
  }

  // ---------------------------------------------------------------- install

  async installFromPicker(instanceId: string): Promise<InstallResult | null> {
    const win = BrowserWindow.getFocusedWindow()
    const result = await dialog.showOpenDialog(win!, {
      title: 'Client-Mod (.jar) auswählen',
      filters: [{ name: 'Fabric Mod', extensions: ['jar'] }],
      properties: ['openFile'],
    })
    if (result.canceled || result.filePaths.length === 0) return null

    return this.install(instanceId, result.filePaths[0])
  }

  async install(instanceId: string, filePath: string): Promise<InstallResult> {
    const instance = this.instances.get(instanceId)
    if (!instance) {
      return { success: false, message: 'Instanz nicht gefunden. Wurde sie gerade entfernt?' }
    }

    const inspection = this.inspect(filePath)
    if (!inspection.valid) {
      logger.warn('client', `Installation abgelehnt: ${inspection.reason}`, { file: inspection.fileName })
      return { success: false, message: inspection.reason! }
    }

    const mismatch = this.versionMismatch(inspection, instance.version)
    if (mismatch) {
      logger.warn('client', `Installation abgelehnt: ${mismatch}`)
      return { success: false, message: mismatch }
    }

    const modsDir = this.modsDir(instance)
    try {
      fs.mkdirSync(modsDir, { recursive: true })
      fs.accessSync(modsDir, fs.constants.W_OK)
    } catch (err) {
      logger.error('client', `mods-Ordner nicht beschreibbar: ${modsDir}`, err)
      return {
        success: false,
        message: `Auf den mods-Ordner kann nicht geschrieben werden:\n${modsDir}\n${err instanceof Error ? err.message : String(err)}`,
      }
    }

    // Back up any copy of the same mod before replacing it, so a broken new
    // build never takes the working one down with it.
    let replacedBackup: string | undefined
    try {
      replacedBackup = this.backupExisting(instance, inspection.modId!)
    } catch (err) {
      logger.error('client', 'Backup der vorhandenen Version fehlgeschlagen', err)
      return {
        success: false,
        message: `Die vorhandene Version konnte nicht gesichert werden, deshalb wurde nichts verändert.\n${err instanceof Error ? err.message : String(err)}`,
      }
    }

    const targetPath = path.join(modsDir, inspection.fileName)
    try {
      fs.copyFileSync(filePath, targetPath)
    } catch (err) {
      logger.error('client', `Kopieren nach ${targetPath} fehlgeschlagen`, err)
      this.restoreBackup(instance, replacedBackup)
      return {
        success: false,
        message: `Die Datei konnte nicht kopiert werden: ${err instanceof Error ? err.message : String(err)}`,
      }
    }

    // Verify what landed on disk reads back as the same mod, not a truncated copy.
    const verified = this.inspect(targetPath)
    if (!verified.valid || verified.modId !== inspection.modId) {
      fs.rmSync(targetPath, { force: true })
      this.restoreBackup(instance, replacedBackup)
      const detail = verified.reason ?? `Erwartet "${inspection.modId}", gefunden "${verified.modId}"`
      logger.error('client', `Verifizierung nach dem Kopieren fehlgeschlagen: ${detail}`)
      return {
        success: false,
        message: `Die kopierte Datei war beschädigt (${detail}). Der vorherige Stand wurde wiederhergestellt.`,
      }
    }

    logger.info('client', `"${inspection.name}" ${inspection.version ?? ''} in "${instance.name}" installiert`, {
      modsDir,
      replacedBackup,
    })

    return {
      success: true,
      fileName: inspection.fileName,
      replacedBackup,
      message: replacedBackup
        ? `${inspection.name} ${inspection.version ?? ''} installiert. Die vorherige Version liegt als Backup unter ${BACKUP_DIR}/.`
        : `${inspection.name} ${inspection.version ?? ''} in "${instance.name}" installiert.`,
    }
  }

  /**
   * Compares the mod's declared Minecraft dependency against the instance.
   * Returns an explanation when they clearly don't match, otherwise null.
   */
  private versionMismatch(inspection: JarInspection, instanceVersion: string): string | null {
    const predicate = inspection.minecraftDepends
    if (!predicate) return null
    if (this.satisfies(instanceVersion, predicate)) return null

    return `${inspection.name} verlangt Minecraft ${predicate}, die Instanz läuft auf ${instanceVersion}.`
  }

  /**
   * Small subset of Fabric's version predicates: wildcards, ranges with
   * >= / > / <= / <, ~ and ^, and exact matches. Anything it can't parse counts
   * as compatible — refusing an install over an unparsed predicate would be
   * worse than letting Fabric itself reject it at startup.
   */
  private satisfies(version: string, predicate: string): boolean {
    const alternatives = predicate.split(/\s+or\s+|\s*\|\|\s*/)
    return alternatives.some(alternative =>
      alternative.split(/\s+/).every(clause => this.satisfiesClause(version, clause.trim()))
    )
  }

  private satisfiesClause(version: string, clause: string): boolean {
    if (!clause || clause === '*') return true

    const match = clause.match(/^(>=|<=|>|<|\^|~|=)?\s*(.+)$/)
    if (!match) return true

    const operator = match[1] ?? '='
    const target = match[2].trim()

    if (target.includes('x') || target.includes('*')) {
      return version.startsWith(target.split(/[x*]/)[0])
    }

    const comparison = this.compare(version, target)
    switch (operator) {
      case '>=': return comparison >= 0
      case '>':  return comparison > 0
      case '<=': return comparison <= 0
      case '<':  return comparison < 0
      // ~1.21 allows 1.21.x; ^1.21 allows anything below 2.0.
      case '~':  return comparison >= 0 &&
        version.split('.').slice(0, 2).join('.') === target.split('.').slice(0, 2).join('.')
      case '^':  return comparison >= 0 && version.split('.')[0] === target.split('.')[0]
      default:   return comparison === 0
    }
  }

  private compare(a: string, b: string): number {
    const left = a.split(/[.-]/).map(Number)
    const right = b.split(/[.-]/).map(Number)

    for (let i = 0; i < Math.max(left.length, right.length); i++) {
      const l = left[i] ?? 0
      const r = right[i] ?? 0
      // Snapshot / pre-release segments aren't numbers; treat them as equal
      // rather than inventing an ordering.
      if (Number.isNaN(l) || Number.isNaN(r)) return 0
      if (l !== r) return l > r ? 1 : -1
    }
    return 0
  }

  // ----------------------------------------------------------------- backup

  /** Moves any installed jar with the same mod id into the backup folder. */
  private backupExisting(instance: Instance, modId: string): string | undefined {
    const modsDir = this.modsDir(instance)
    if (!fs.existsSync(modsDir)) return undefined

    for (const fileName of fs.readdirSync(modsDir)) {
      if (!fileName.toLowerCase().endsWith('.jar')) continue

      let existingId: string | undefined
      try {
        existingId = new JarReader(path.join(modsDir, fileName)).readJson<FabricModJson>('fabric.mod.json')?.id
      } catch {
        continue
      }
      if (existingId !== modId) continue

      const backupDir = this.backupDir(instance)
      fs.mkdirSync(backupDir, { recursive: true })

      const backupName = `${Date.now()}-${fileName}`
      fs.renameSync(path.join(modsDir, fileName), path.join(backupDir, backupName))
      logger.info('client', `Vorherige Version gesichert: ${BACKUP_DIR}/${backupName}`)
      return backupName
    }
    return undefined
  }

  private restoreBackup(instance: Instance, backupName?: string): void {
    if (!backupName) return
    try {
      const source = path.join(this.backupDir(instance), backupName)
      if (!fs.existsSync(source)) return

      // The prefix is the timestamp added when backing it up.
      const originalName = backupName.slice(backupName.indexOf('-') + 1)
      fs.renameSync(source, path.join(this.modsDir(instance), originalName))
      logger.info('client', `Backup "${backupName}" wiederhergestellt`)
    } catch (err) {
      logger.error('client', `Backup "${backupName}" konnte nicht wiederhergestellt werden`, err)
    }
  }

  /** Removes an installed client mod, keeping a backup copy. */
  uninstall(instanceId: string, fileName: string): InstallResult {
    const instance = this.instances.get(instanceId)
    if (!instance) return { success: false, message: 'Instanz nicht gefunden.' }

    // Guard against a crafted name escaping the mods folder.
    if (path.basename(fileName) !== fileName) {
      return { success: false, message: 'Ungültiger Dateiname.' }
    }

    const source = path.join(this.modsDir(instance), fileName)
    if (!fs.existsSync(source)) {
      return { success: false, message: `"${fileName}" liegt nicht (mehr) im mods-Ordner.` }
    }

    try {
      const backupDir = this.backupDir(instance)
      fs.mkdirSync(backupDir, { recursive: true })
      const backupName = `${Date.now()}-${fileName}`
      fs.renameSync(source, path.join(backupDir, backupName))

      logger.info('client', `"${fileName}" aus "${instance.name}" entfernt (Backup: ${backupName})`)
      return {
        success: true,
        replacedBackup: backupName,
        message: `"${fileName}" entfernt. Eine Kopie liegt unter ${BACKUP_DIR}/ falls du sie zurück brauchst.`,
      }
    } catch (err) {
      logger.error('client', `"${fileName}" konnte nicht entfernt werden`, err)
      return { success: false, message: `Entfernen fehlgeschlagen: ${err instanceof Error ? err.message : String(err)}` }
    }
  }
}
