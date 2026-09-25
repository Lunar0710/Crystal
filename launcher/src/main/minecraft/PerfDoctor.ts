import fs from 'fs'
import os from 'os'
import path from 'path'
import { InstanceManager } from './InstanceManager'
import { JarReader } from '../util/jarReader'
import { crystalPath, isPlainFileName } from '../paths'
import { logger } from '../logs/Logger'

export type PerfFixKind = 'disable-mod' | 'disable-module' | 'enable-module' | 'install-perf-pack' | 'set-ram' | 'set-option'

export interface PerfFix {
  kind: PerfFixKind
  label: string
  modFile?: string
  module?: string
  ram?: number
  /** set-option: a key in the instance's options.txt and the value to write. */
  option?: string
  value?: string
}

/**
 * Minecraft settings that cost the most frames, what is too much, and the value
 * the fix writes. Keys as options.txt spells them.
 */
const OPTION_RULES: { key: string; tooMuch: (v: string) => boolean; value: string; level: PerfFinding['level']; title: string; detail: string; label: string }[] = [
  { key: 'graphicsMode', tooMuch: v => v === '2', value: '1', level: 'high', title: 'Grafik auf „Fabelhaft“',
    detail: 'Fabelhaft zeichnet durchsichtige Blöcke und Wolken in eigenen Durchgängen und kostet oft ein Drittel der FPS. „Schön“ sieht fast gleich aus.', label: 'Auf „Schön“ stellen' },
  { key: 'renderDistance', tooMuch: v => Number(v) > 16, value: '12', level: 'medium', title: 'Sehr große Sichtweite',
    detail: 'Jeder Chunk mehr Sichtweite kostet mehr als der davor. Auf Servern schickt der Server oft ohnehin nicht mehr als 10 bis 12.', label: 'Auf 12 Chunks' },
  { key: 'simulationDistance', tooMuch: v => Number(v) > 10, value: '8', level: 'info', title: 'Große Simulationsweite',
    detail: 'Bestimmt, wie weit Mobs und Redstone in Einzelspieler-Welten laufen. Auf Servern zählt nur die des Servers.', label: 'Auf 8 Chunks' },
  { key: 'enableVsync', tooMuch: v => v === 'true', value: 'false', level: 'info', title: 'V-Sync ist an',
    detail: 'V-Sync hält die FPS bei der Bildschirmfrequenz und fügt etwas Verzögerung zwischen Klick und Bild hinzu.', label: 'V-Sync aus' },
  { key: 'particles', tooMuch: v => v === '0', value: '1', level: 'info', title: 'Alle Partikel an',
    detail: 'Bei Explosionen, Tränken und Regen entstehen tausende Partikel. „Verringert“ spart viel, ohne dass etwas fehlt.', label: 'Auf „Verringert“' },
  { key: 'biomeBlendRadius', tooMuch: v => Number(v) > 2, value: '1', level: 'info', title: 'Hoher Biom-Übergang',
    detail: 'Weiche Farbübergänge zwischen Biomen machen das Laden neuer Chunks langsamer.', label: 'Auf 3x3' },
]

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

  /** The instance's Minecraft settings (options.txt) as key/value, empty if there are none yet. */
  private options(instanceId: string): Map<string, string> {
    const map = new Map<string, string>()
    try {
      for (const line of fs.readFileSync(path.join(this.gameDir(instanceId), 'options.txt'), 'utf8').split(/\r?\n/)) {
        const at = line.indexOf(':')
        if (at > 0) map.set(line.slice(0, at), line.slice(at + 1))
      }
    } catch { /* never started: nothing to check */ }
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

    // Farms and PvP arenas are full of dropped items and orbs; RenderLimits stops drawing the far ones.
    if (config?.json?.modules && !enabled('RenderLimits')) {
      findings.push({
        id: 'render-limits',
        level: 'info',
        title: 'Render-Grenzen sind aus',
        detail: 'RenderLimits zeichnet Items, Erfahrung, Rahmen und Schilder in großer Entfernung nicht mehr. Auf Farmen und in PvP-Arenen spart das spürbar FPS, Spieler und Mobs bleiben immer sichtbar.',
        fix: { kind: 'enable-module', label: 'RenderLimits einschalten', module: 'RenderLimits' },
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

    const options = this.options(instanceId)
    for (const rule of OPTION_RULES) {
      const current = options.get(rule.key)
      if (current === undefined || !rule.tooMuch(current)) continue
      findings.push({
        id: `option-${rule.key}`, level: rule.level, title: rule.title, detail: rule.detail,
        fix: { kind: 'set-option', label: rule.label, option: rule.key, value: rule.value },
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
   * Writes one of the known settings to options.txt; only values from
   * OPTION_RULES, so a page can't write anything else into the file.
   */
  setOption(instanceId: string, key: string | undefined, value: string | undefined): { ok: boolean; message: string } {
    const rule = OPTION_RULES.find(r => r.key === key && r.value === value)
    if (!rule) return { ok: false, message: 'Unbekannte Einstellung.' }
    const file = path.join(this.gameDir(instanceId), 'options.txt')
    let text: string
    try { text = fs.readFileSync(file, 'utf8') } catch { return { ok: false, message: 'options.txt nicht gefunden.' } }
    const pattern = new RegExp(`^${rule.key}:.*$`, 'm')
    if (!pattern.test(text)) return { ok: false, message: 'Einstellung nicht gefunden.' }
    fs.writeFileSync(file, text.replace(pattern, `${rule.key}:${rule.value}`))
    logger.info('client', `FPS-Doktor: ${rule.key} auf ${rule.value}`)
    return { ok: true, message: `${rule.label}: erledigt. Gilt beim nächsten Start.` }
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

  /** Switches on one of the modules the doctor recommends (only those), creating its entry if needed. */
  enableModule(instanceId: string, module: string | undefined): { ok: boolean; message: string } {
    if (module !== 'RenderLimits') return { ok: false, message: 'Modul nicht gefunden.' }
    const config = this.nexoraConfig(instanceId)
    if (!config?.json?.modules) return { ok: false, message: 'Starte die Instanz einmal mit Nexora, dann geht das.' }
    config.json.modules[module] = { ...(config.json.modules[module] ?? {}), enabled: true }
    fs.writeFileSync(config.path, JSON.stringify(config.json, null, 2))
    logger.info('client', `FPS-Doktor: Modul ${module} eingeschaltet`)
    return { ok: true, message: `${module} ist an. Die Entfernungen stellst du im Modmenü ein.` }
  }
}
