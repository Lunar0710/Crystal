import fs from 'fs'
import os from 'os'
import path from 'path'
import { InstanceManager } from './InstanceManager'
import { JarReader } from '../util/jarReader'
import { crystalPath, isPlainFileName } from '../paths'
import { logger } from '../logs/Logger'

export type PerfFixKind = 'disable-mod' | 'disable-module' | 'install-perf-pack' | 'set-ram'

export interface PerfFix {
  kind: PerfFixKind
  label: string
  modFile?: string
  module?: string
  ram?: number
}

export interface PerfFinding {
  id: string
  /** high = costs clearly noticeable frames, info = worth knowing. */
  level: 'high' | 'medium' | 'info'
  title: string
  detail: string
  fix: PerfFix | null
}

/**
 * Mods that do the same job. Two of them only duplicate work, and some fight
 * over the same code. Keyed by Fabric mod id.
 */
const SAME_JOB: { job: string; ids: string[] }[] = [
  { job: 'Chunk-Renderer', ids: ['sodium', 'embeddium', 'rubidium', 'optifabric'] },
  { job: 'Licht-Engine', ids: ['starlight', 'scalablelux', 'phosphor'] },
  { job: 'Netzwerk', ids: ['krypton', 'krloader'] },
]

/** Mods known to cost frames or memory on their own, and why. */
const HEAVY: Record<string, string> = {
  'essential-container': 'Essential bringt eigene Menüs, Cosmetics, Freunde und einen Hintergrunddienst mit. Nexora hat das meiste davon schon.',
  essential: 'Essential bringt eigene Menüs, Cosmetics, Freunde und einen Hintergrunddienst mit. Nexora hat das meiste davon schon.',
  baritone: 'Baritone speichert laufend die Umgebung in einen Cache und schreibt ihn beim Verlassen auf die Platte.',
  'baritone-meteor': 'Baritone speichert laufend die Umgebung in einen Cache und schreibt ihn beim Verlassen auf die Platte.',
  replaymod: 'ReplayMod nimmt mit, solange die Aufnahme an ist, das kostet CPU und Platte.',
  noisium: 'Noisium beschleunigt nur das Erzeugen neuer Welten. Auf Servern bringt es nichts.',
}

/**
 * Nexora modules and what they cost per frame, measured with the world test's
 * frame rate run (1.21.11, 2026-09-24). Only the ones that show up clearly.
 */
const COSTLY_MODULES: Record<string, { ms: number; note: string }> = {
  '3D Skins': { ms: 0.4, note: 'zeichnet die äußere Skin-Schicht als kleine Würfel' },
  ColorSaturation: { ms: 0.3, note: 'legt einen Farbfilter über das ganze Bild' },
  MotionBlur: { ms: 0.3, note: 'mischt jedes Bild mit den vorigen' },
  MenuBlur: { ms: 0.1, note: 'zeichnet Menüs über einem weichgezeichneten Bild' },
}

export class PerfDoctor {
  constructor(private instances: InstanceManager) {}

  private gameDir(instanceId: string): string {
    return this.instances.get(instanceId)?.gameDir || crystalPath('instances', instanceId)
  }

  /** Enabled jars by Fabric mod id. */
  private mods(instanceId: string): Map<string, string> {
    const dir = path.join(this.gameDir(instanceId), 'mods')
    const map = new Map<string, string>()
    if (!fs.existsSync(dir)) return map
    for (const file of fs.readdirSync(dir)) {
      if (!file.endsWith('.jar')) continue
      try {
        const json = JSON.parse(new JarReader(path.join(dir, file)).readText('fabric.mod.json') || 'null')
        if (typeof json?.id === 'string') map.set(json.id.toLowerCase(), file)
      } catch { /* an unreadable jar says nothing about performance */ }
    }
    return map
  }

  /** The instance's Nexora module settings (.crystal/config/crystal.json), or null. */
  private nexoraConfig(instanceId: string): { path: string; json: any } | null {
    const file = path.join(this.gameDir(instanceId), '.crystal', 'config', 'crystal.json')
    try {
      return { path: file, json: JSON.parse(fs.readFileSync(file, 'utf8')) }
    } catch {
      return null
    }
  }

  analyze(instanceId: string, maxRamMb: number): PerfFinding[] {
    const instance = this.instances.get(instanceId)
    const findings: PerfFinding[] = []
    const mods = this.mods(instanceId)

    for (const { job, ids } of SAME_JOB) {
      const present = ids.filter(id => mods.has(id))
      if (present.length < 2) continue
      const [keep, ...rest] = present
      for (const id of rest) {
        findings.push({
          id: `same-${id}`,
          level: 'high',
          title: `Zwei Mods für ${job}`,
          detail: `${mods.get(keep)} und ${mods.get(id)} machen dasselbe. Doppelt rechnet nur doppelt, und manche kommen sich in die Quere.`,
          fix: { kind: 'disable-mod', label: `${mods.get(id)} ausschalten`, modFile: mods.get(id) },
        })
      }
    }

    const config = instance?.useCrystalClient ? this.nexoraConfig(instanceId) : null
    const enabled = (name: string) => !!config?.json?.modules?.[name]?.enabled

    if (mods.has('entityculling') && enabled('SmartCulling')) {
      findings.push({
        id: 'culling-twice',
        level: 'medium',
        title: 'Entity-Culling doppelt',
        detail: 'EntityCulling und Nexoras SmartCulling prüfen beide, welche Entities hinter Wänden sind. Eins davon reicht.',
        fix: { kind: 'disable-module', label: 'SmartCulling ausschalten', module: 'SmartCulling' },
      })
    }

    for (const [name, { ms, note }] of Object.entries(COSTLY_MODULES)) {
      if (!enabled(name)) continue
      findings.push({
        id: `module-${name}`,
        level: ms >= 0.3 ? 'medium' : 'info',
        title: `${name} kostet etwa ${ms.toLocaleString('de-DE')} ms pro Bild`,
        detail: `Das Modul ${note}. Bei 300 FPS sind das rund ${Math.round(300 - 1000 / (1000 / 300 + ms))} FPS.`,
        fix: { kind: 'disable-module', label: `${name} ausschalten`, module: name },
      })
    }

    for (const [id, why] of Object.entries(HEAVY)) {
      if (!mods.has(id)) continue
      findings.push({
        id: `heavy-${id}`,
        level: 'info',
        title: `${mods.get(id)} braucht selbst Leistung`,
        detail: why,
        fix: { kind: 'disable-mod', label: 'Ausschalten', modFile: mods.get(id) },
      })
    }

    if (instance?.useCrystalClient && instance.version.startsWith('1.21') && !mods.has('sodium') && !mods.has('embeddium')) {
      findings.push({
        id: 'no-sodium',
        level: 'high',
        title: 'Sodium fehlt',
        detail: 'Ohne Sodium zeichnet Minecraft Chunks mit dem langsamen Vanilla-Renderer. Das Performance-Paket (Sodium, Lithium, FerriteCore, EntityCulling, ImmediatelyFast) holt das meiste raus.',
        fix: { kind: 'install-perf-pack', label: 'Performance-Paket installieren' },
      })
    }

    const totalMb = Math.round(os.totalmem() / (1024 * 1024))
    if (maxRamMb > totalMb * 0.75) {
      const ram = Math.floor((totalMb * 0.5) / 512) * 512
      findings.push({
        id: 'ram-high',
        level: 'medium',
        title: 'Zu viel Arbeitsspeicher eingestellt',
        detail: `Minecraft darf ${(maxRamMb / 1024).toFixed(1)} von ${(totalMb / 1024).toFixed(0)} GB nehmen. Dann bleibt Windows zu wenig, es lagert aus und das Spiel ruckelt.`,
        fix: { kind: 'set-ram', label: `Auf ${(ram / 1024).toFixed(1)} GB setzen`, ram },
      })
    } else if (maxRamMb < 3072 && mods.size > 40) {
      findings.push({
        id: 'ram-low',
        level: 'medium',
        title: 'Wenig Arbeitsspeicher für so viele Mods',
        detail: `${mods.size} Mods mit ${(maxRamMb / 1024).toFixed(1)} GB. Ist der Speicher voll, räumt Java ständig auf und das Spiel hakt.`,
        fix: { kind: 'set-ram', label: 'Auf 4 GB setzen', ram: 4096 },
      })
    }

    const order = { high: 0, medium: 1, info: 2 }
    return findings.sort((a, b) => order[a.level] - order[b.level])
  }

  /** Switches a mod off by renaming it, the way the Mods tab does. */
  disableMod(instanceId: string, modFile: string | undefined): { ok: boolean; message: string } {
    if (!modFile || !isPlainFileName(modFile)) return { ok: false, message: 'Ungültiger Dateiname.' }
    const file = path.join(this.gameDir(instanceId), 'mods', modFile)
    if (!fs.existsSync(file)) return { ok: false, message: `${modFile} nicht gefunden.` }
    fs.renameSync(file, `${file}.disabled`)
    logger.info('client', `FPS-Doktor: ${modFile} ausgeschaltet`)
    return { ok: true, message: `${modFile} ist aus. Im Mods-Tab kannst du ihn wieder einschalten.` }
  }

  /**
   * Switches a Nexora module off in the instance's settings. Only while the
   * instance is not running: the game writes this file itself when it closes
   * and would put the module back.
   */
  disableModule(instanceId: string, module: string | undefined): { ok: boolean; message: string } {
    const config = this.nexoraConfig(instanceId)
    if (!module || !config?.json?.modules?.[module]) return { ok: false, message: 'Modul nicht gefunden.' }
    config.json.modules[module].enabled = false
    fs.writeFileSync(config.path, JSON.stringify(config.json, null, 2))
    logger.info('client', `FPS-Doktor: Modul ${module} ausgeschaltet`)
    return { ok: true, message: `${module} ist aus. Im Spiel kannst du es im Modmenü wieder einschalten.` }
  }
}
