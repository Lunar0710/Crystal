import fs from 'fs'
import os from 'os'
import path from 'path'

/**
 * Moving over from Lunar Client or Feather in one go: which of their mods
 * were on, where the HUD elements sat and how big, the zoom key, plus the
 * Minecraft options (FOV, sensitivity, key bindings), the server list and
 * the resource packs from .minecraft. Everything lands in one Nexora
 * instance; its previous config is kept next to it as crystal.json.before-import.
 *
 * Lunar keeps one mods.json per profile (~/.lunarclient/settings/game/<profile>/),
 * each mod with "enabled", an anchor ("topLeft" ...) and x/y measured inward
 * from it. Feather keeps one JSON per profile (.minecraft/feather/configuration/
 * profiles/), each mod with "enabled" as a string, "hudAnchor" ("top_left" ...)
 * and hudRelativeX/Y as signed offsets from that anchor. Both are in GUI pixels.
 */

export type ImportSource = 'lunar' | 'feather'

export interface DetectedClient {
  source: ImportSource
  name: string
  profiles: string[]
  active: string
}

export interface ImportItem {
  from: string
  to: string
  enabled: boolean
  moved: boolean
}

export interface ImportPreview {
  source: ImportSource
  profile: string
  items: ImportItem[]
  /** Their mods Nexora has nothing like (a minimap, say). */
  unmatched: string[]
  keybinds: string[]
  extras: { options: boolean; servers: boolean; packs: number }
}

export interface ImportResult extends ImportPreview {
  optionsCopied: number
  serversCopied: boolean
  packsCopied: number
  notes: string[]
}

const HOME = os.homedir()
const MINECRAFT = process.platform === 'win32'
  ? path.join(process.env.APPDATA || path.join(HOME, 'AppData', 'Roaming'), '.minecraft')
  : process.platform === 'darwin'
    ? path.join(HOME, 'Library', 'Application Support', 'minecraft')
    : path.join(HOME, '.minecraft')
const LUNAR_GAME = path.join(HOME, '.lunarclient', 'settings', 'game')
const FEATHER_PROFILES = path.join(MINECRAFT, 'feather', 'configuration', 'profiles')

const LUNAR: Record<string, string> = {
  FPS: 'FPS', CPS: 'CPS', TOGGLE_SNEAK: 'ToggleSneakSprint', ZOOM: 'Zoom', HYPIXEL_MOD: 'HypixelMods',
  HYPIXEL_BEDWARS: 'HypixelBedwars', QUICKPLAY: 'HypixelQuickplay', ARMORSTATUS: 'ArmorDisplay',
  KEYSTROKES: 'Keystrokes', COORDINATES: 'Coordinates', DAY_COUNTER: 'DayCounter', CROSSHAIR: 'Crosshair',
  POTION_EFFECTS: 'PotionEffects', DIRECTION_HUD: 'DirectionHUD', WAYPOINTS: 'Waypoints', HIT_COLOR: 'HitColor',
  SCOREBOARD: 'Scoreboard', TITLES: 'Titles', ITEM_COUNTER: 'ItemCounter', PING: 'Ping', MOTION_BLUR: 'MotionBlur',
  PACK_ORGANIZER: 'PackOrganizer', CHAT: 'Chat', TAB: 'TabEditor', NAMETAG: 'NameTags',
  SCROLLABLE_TOOLTIPS: 'ScrollableTooltips', PARTICLE_CHANGER: 'ParticleChanger', NICK_HIDER: 'NickHider',
  COOLDOWNS: 'Cooldowns', WORLDEDIT_CUI: 'WorldEditCUI', CLOCK: 'Clock', STOPWATCH: 'Stopwatch', PLAYTIME: 'Playtime',
  MEMORY: 'MemoryUsage', COMBO: 'ComboCounter', REACH_DISPLAY: 'ReachDisplay', TIME_CHANGER: 'TimeChanger',
  SERVER_ADDRESS: 'ServerAddress', SATURATION: 'Saturation', COLOR_SATURATION: 'ColorSaturation',
  ITEM_PHYSICS: 'ItemPhysics', TNT_COUNTDOWN: 'TNTCountdown', ITEM_TRACKER: 'ItemTracker', SHINY_POTS: 'ShinyPots',
  '3D_SKINS': '3D Skins', GLINT_COLORIZER: 'GlintColorizer', MOMENTUM: 'MomentumMod', BLOCK_OUTLINE: 'BlockOutline',
  SCREENSHOT: 'ScreenshotUploader', FOV: 'FOVChanger', FOG: 'FogCustomizer', AUTO_TEXT_HOTKEY: 'AutoTextHotkey',
  MUMBLE_LINK: 'MumbleLink', TOTEM_COUNTER: 'TotemPops', '2D_ITEMS': '2D Items', BOSSBAR: 'BossBar',
  FREELOOK: 'Freelook', PVP_INFO: 'PvPInfo', SNAPLOOK: 'Snaplook', TEAM_VIEW: 'TeamView', PACK_DISPLAY: 'PackDisplay',
  MENU_BLUR: 'MenuBlur', HITBOX: 'Hitbox', LIGHTING: 'Lighting', WEATHER_CHANGER: 'WeatherChanger',
  CHUNK_BORDERS: 'ChunkBorders', SOUND_CHANGER: 'BetterSounds', WAILA: 'WAILA', HURT_CAM: 'NoHurtCam',
}

const FEATHER: Record<string, string> = {
  bossBar: 'BossBar', blockOverlay: 'BlockOutline', reachDisplay: 'ReachDisplay', armorStatus: 'ArmorDisplay',
  customfog: 'FogCustomizer', weatherchanger: 'WeatherChanger', potionEffects: 'PotionEffects',
  comboDisplay: 'ComboCounter', zoom: 'Zoom', autoText: 'AutoTextHotkey', uhcoverlay: 'UHCOverlay',
  nickHider: 'NickHider', glint: 'GlintColorizer', fps: 'FPS', packdisplay: 'PackDisplay', hitindicator: 'HitMarker',
  crosshair: 'Crosshair', nametags: 'NameTags', hypixel: 'HypixelMods', tooltips: 'ScrollableTooltips',
  perspective: 'Freelook', toggleSprint: 'ToggleSneakSprint', reconnect: 'AutoReconnect', fovChanger: 'FOVChanger',
  coordinates: 'Coordinates', screenshot: 'ScreenshotUploader', timeChanger: 'TimeChanger', keystrokes: 'Keystrokes',
  tps: 'TPSDisplay', scoreboard: 'Scoreboard', stopwatch1: 'Stopwatch', speedMeter: 'SpeedDisplay',
  titletweaker: 'Titles', serverAddress: 'ServerAddress', direction: 'DirectionHUD', particles: 'ParticleChanger',
  waypoints: 'Waypoints', totem: 'TotemPops', ping: 'Ping', packOrganizer: 'PackOrganizer', snaplook: 'Snaplook',
  hitbox: 'Hitbox', itemCounter: 'ItemCounter', playtime: 'Playtime', customChat: 'Chat',
  colorSaturation: 'ColorSaturation', itemPhysic: 'ItemPhysics', motionBlur: 'MotionBlur', saturation: 'Saturation',
  teamtracker: 'TeamView', time: 'Clock', cps: 'CPS', lightleveloverlay: 'LightLevel', tnttimer: 'TNTCountdown',
  systemresources: 'MemoryUsage',
}

/** Nexora modules with a place on screen (X, Y, Scale settings). */
const HUD = new Set(['ArmorDisplay', 'BiomeDisplay', 'Clock', 'ComboCounter', 'Cooldowns', 'Coordinates', 'CPS',
  'DayCounter', 'DirectionHUD', 'FightSummary', 'FPS', 'FrameGraph', 'HypixelBedwars', 'HypixelMods', 'InventoryHUD',
  'ItemCounter', 'ItemTracker', 'Keystrokes', 'KillCam', 'LightLevel', 'Lyrics', 'MemoryUsage', 'MomentumMod',
  'OpponentArmor', 'PackDisplay', 'Ping', 'PlayerCount', 'Playtime', 'PotCounter', 'PotionEffects', 'PvPInfo',
  'ReachDisplay', 'Saturation', 'ServerAddress', 'SessionStats', 'SpeedDisplay', 'Spotify', 'Stopwatch', 'TargetHUD',
  'TPSDisplay', 'UHCOverlay', 'WAILA', 'Watermark'])

/**
 * Modules that are an action while switched on in Nexora (zoomed in, looking
 * around): turning them on here would leave the game zoomed in. Only their
 * key comes along.
 */
const ACTIONS = new Set(['Zoom', 'Freelook', 'Snaplook'])

/** Rough size of a Nexora element, to line it up with a right or bottom edge. */
const SIZE: Record<string, [number, number]> = {
  Keystrokes: [66, 66], ArmorDisplay: [60, 70], PotionEffects: [132, 42], TargetHUD: [150, 40], Cooldowns: [90, 22],
}

// ------------------------------------------------------------ detection

function readJson(file: string): any {
  try { return JSON.parse(fs.readFileSync(file, 'utf8')) } catch { return null }
}

export function detectClients(): DetectedClient[] {
  const found: DetectedClient[] = []
  const lunarProfiles = readJson(path.join(LUNAR_GAME, 'profile_manager.json'))
  if (Array.isArray(lunarProfiles)) {
    const names = lunarProfiles.map((p: any) => String(p.name)).filter(n => fs.existsSync(path.join(LUNAR_GAME, n, 'mods.json')))
    const active = lunarProfiles.find((p: any) => p.active)?.name
    if (names.length) found.push({ source: 'lunar', name: 'Lunar Client', profiles: names, active: names.includes(active) ? active : names[0] })
  }
  if (fs.existsSync(FEATHER_PROFILES)) {
    const names = fs.readdirSync(FEATHER_PROFILES).filter(f => f.endsWith('.json')).map(f => f.slice(0, -5))
    let current = ''
    try { current = fs.readFileSync(path.join(FEATHER_PROFILES, '..', 'current.profile'), 'utf8').trim() } catch {}
    const active = names.find(n => n.toLowerCase() === current.toLowerCase()) || names[0]
    if (names.length) found.push({ source: 'feather', name: 'Feather', profiles: names, active })
  }
  return found
}

// ------------------------------------------------------------ reading

interface Parsed {
  name: string
  to: string
  enabled: boolean
  /** Anchor as [horizontal, vertical]: -1 left/top, 0 centre, 1 right/bottom, with the offsets inward. */
  anchor?: [number, number]
  dx?: number
  dy?: number
  scale?: number
}

function lunarAnchor(position: string): [number, number] {
  const p = position.toLowerCase()
  const v = p.startsWith('top') ? -1 : p.startsWith('bottom') ? 1 : 0
  const h = p.endsWith('left') ? -1 : p.endsWith('right') ? 1 : 0
  return [h, v]
}

function featherAnchor(anchor: string): [number, number] {
  const [a, b] = anchor.toLowerCase().split('_')
  const v = a === 'top' ? -1 : a === 'bottom' ? 1 : 0
  const h = b === 'left' ? -1 : b === 'right' ? 1 : 0
  return [h, v]
}

function readLunar(profile: string): { mods: Parsed[]; unmatched: string[]; keys: Record<string, string> } {
  const json = readJson(path.join(LUNAR_GAME, profile, 'mods.json')) || {}
  const mods: Parsed[] = []
  const unmatched: string[] = []
  const keys: Record<string, string> = {}
  for (const [id, raw] of Object.entries<any>(json)) {
    if (!raw || typeof raw !== 'object') continue
    const to = LUNAR[id]
    if (!to) { if (raw.enabled) unmatched.push(id); continue }
    const item: Parsed = { name: id, to, enabled: raw.enabled === true }
    if (typeof raw.x === 'number' && typeof raw.y === 'number' && raw.position) {
      item.anchor = lunarAnchor(String(raw.position))
      // Lunar measures inward from the anchor on every side.
      item.dx = raw.x
      item.dy = raw.y
    }
    const scale = Number(raw.options?.scale)
    if (scale > 0) item.scale = scale
    if (id === 'ZOOM' && raw.options?.zoomKeybind) keys.Zoom = String(raw.options.zoomKeybind)
    mods.push(item)
  }
  return { mods, unmatched, keys }
}

function readFeather(profile: string): { mods: Parsed[]; unmatched: string[]; keys: Record<string, string> } {
  const json = readJson(path.join(FEATHER_PROFILES, profile + '.json')) || {}
  const mods: Parsed[] = []
  const unmatched: string[] = []
  for (const [id, raw] of Object.entries<any>(json)) {
    if (!raw || typeof raw !== 'object') continue
    const on = String(raw.enabled) === 'true'
    const to = FEATHER[id]
    if (!to) { if (on) unmatched.push(id); continue }
    const item: Parsed = { name: id, to, enabled: on }
    if (raw.hudAnchor && raw.hudRelativeX !== undefined) {
      item.anchor = featherAnchor(String(raw.hudAnchor))
      // Feather's offsets are signed (negative is up and left), so inward
      // from a right or bottom edge is the negative of the value.
      const x = Number(raw.hudRelativeX) || 0, y = Number(raw.hudRelativeY) || 0
      item.dx = item.anchor[0] === 1 ? -x : x
      item.dy = item.anchor[1] === 1 ? -y : y
    }
    const scale = Number(raw.hudScale)
    if (scale > 0) item.scale = scale
    mods.push(item)
  }
  return { mods, unmatched, keys: {} }
}

function read(source: ImportSource, profile: string) {
  return source === 'lunar' ? readLunar(profile) : readFeather(profile)
}

// ------------------------------------------------------------ preview

function packsIn(dir: string): string[] {
  try { return fs.readdirSync(dir).filter(f => !f.startsWith('.')) } catch { return [] }
}

export function previewImport(source: ImportSource, profile: string, gameDir?: string): ImportPreview {
  const { mods, unmatched, keys } = read(source, profile)
  const have = gameDir ? new Set(packsIn(path.join(gameDir, 'resourcepacks'))) : new Set<string>()
  return {
    source,
    profile,
    items: mods.map(m => ({ from: m.name, to: m.to, enabled: m.enabled && !ACTIONS.has(m.to), moved: m.anchor !== undefined && HUD.has(m.to) })),
    unmatched,
    keybinds: Object.entries(keys).map(([mod, key]) => `${mod}: ${key}`),
    extras: {
      options: fs.existsSync(path.join(MINECRAFT, 'options.txt')),
      servers: fs.existsSync(path.join(MINECRAFT, 'servers.dat')),
      packs: packsIn(path.join(MINECRAFT, 'resourcepacks')).filter(p => !have.has(p)).length,
    },
  }
}

// ------------------------------------------------------------ applying

/** The GUI size Minecraft will use on this screen with this gui scale (0 = auto), as Minecraft works it out. */
export function guiSize(screen: { width: number; height: number }, guiScaleOption: number): { width: number; height: number } {
  let scale = 1
  const max = guiScaleOption > 0 ? guiScaleOption : 1000
  while (scale < max && screen.width / (scale + 1) >= 320 && screen.height / (scale + 1) >= 240) scale++
  return { width: Math.floor(screen.width / scale), height: Math.floor(screen.height / scale) }
}

function optionValue(file: string, key: string): string | null {
  try {
    const line = fs.readFileSync(file, 'utf8').split(/\r?\n/).find(l => l.startsWith(key + ':'))
    return line ? line.slice(key.length + 1) : null
  } catch { return null }
}

/** GLFW key code for a Lunar key name such as KEY_C or KEY_F5; null for mouse buttons and unknown names. */
function glfwKey(name: string): number | null {
  const k = name.replace(/^KEY_/, '').toUpperCase()
  if (/^[A-Z]$/.test(k)) return k.charCodeAt(0)
  if (/^[0-9]$/.test(k)) return k.charCodeAt(0)
  const f = /^F([0-9]{1,2})$/.exec(k)
  if (f) return 289 + Number(f[1])
  const named: Record<string, number> = {
    SPACE: 32, TAB: 258, GRAVE: 96, MINUS: 45, EQUALS: 61, LBRACKET: 91, RBRACKET: 93, SEMICOLON: 59,
    APOSTROPHE: 39, BACKSLASH: 92, COMMA: 44, PERIOD: 46, SLASH: 47, LSHIFT: 340, RSHIFT: 344,
    LCONTROL: 341, RCONTROL: 345, LMENU: 342, RMENU: 346, CAPITAL: 280,
  }
  return named[k] ?? null
}

/** Minecraft options worth taking along: how the game feels and the keys, not the video settings of another PC. */
const OPTION_KEYS = /^(fov|fovEffectScale|mouseSensitivity|rawMouseInput|invertYMouse|gamma|guiScale|chatScale|chatOpacity|chatWidth|chatHeightFocused|chatHeightUnfocused|chatLineSpacing|textBackgroundOpacity|autoJump|toggleCrouch|toggleSprint|bobView|damageTiltStrength|screenEffectScale|showSubtitles|soundCategory_[a-z_]+|key_.+):/

function mergeOptions(fromFile: string, toFile: string): number {
  let from: string[]
  try { from = fs.readFileSync(fromFile, 'utf8').split(/\r?\n/) } catch { return 0 }
  const take = new Map<string, string>()
  for (const line of from) {
    if (OPTION_KEYS.test(line)) take.set(line.slice(0, line.indexOf(':')), line)
  }
  if (!take.size) return 0
  let lines: string[] = []
  try { lines = fs.readFileSync(toFile, 'utf8').split(/\r?\n/) } catch {}
  const out = lines.filter(l => l.trim()).map(l => {
    const key = l.slice(0, l.indexOf(':'))
    const line = take.get(key)
    if (line !== undefined) { take.delete(key); return line }
    return l
  })
  out.push(...take.values())
  fs.writeFileSync(toFile, out.join('\n') + '\n')
  return from.filter(l => OPTION_KEYS.test(l)).length
}

export async function applyImport(
  source: ImportSource,
  profile: string,
  gameDir: string,
  screen: { width: number; height: number },
): Promise<ImportResult> {
  const preview = previewImport(source, profile, gameDir)
  const { mods, keys } = read(source, profile)
  const notes: string[] = []

  // Minecraft options first: the gui scale decides where the HUD elements go.
  const options = path.join(gameDir, 'options.txt')
  const optionsCopied = mergeOptions(path.join(MINECRAFT, 'options.txt'), options)
  const gui = guiSize(screen, Number(optionValue(options, 'guiScale') ?? 0) || 0)

  const configDir = path.join(gameDir, '.crystal', 'config')
  const configFile = path.join(configDir, 'crystal.json')
  fs.mkdirSync(configDir, { recursive: true })
  const config = readJson(configFile) || {}
  if (fs.existsSync(configFile)) fs.copyFileSync(configFile, configFile + '.before-import')
  config.modules = config.modules || {}

  for (const mod of mods) {
    const entry = config.modules[mod.to] || {}
    if (!ACTIONS.has(mod.to)) entry.enabled = mod.enabled
    if (HUD.has(mod.to) && mod.anchor) {
      const [w, h] = SIZE[mod.to] || [56, 12]
      const scale = Math.max(0.5, Math.min(2, mod.scale ?? 1))
      const sw = w * scale, sh = h * scale
      const [ah, av] = mod.anchor
      const x = ah < 0 ? mod.dx! : ah > 0 ? gui.width - sw - mod.dx! : gui.width / 2 - sw / 2 + mod.dx!
      const y = av < 0 ? mod.dy! : av > 0 ? gui.height - sh - mod.dy! : gui.height / 2 - sh / 2 + mod.dy!
      entry.settings = {
        ...(entry.settings || {}),
        X: Math.round(Math.max(0, Math.min(gui.width - 4, x))),
        Y: Math.round(Math.max(0, Math.min(gui.height - 4, y))),
        Scale: Math.round(scale * 10) / 10,
      }
    }
    config.modules[mod.to] = entry
  }
  for (const [mod, key] of Object.entries(keys)) {
    const code = glfwKey(key)
    if (code === null) { notes.push(`${mod}-Taste (${key.replace(/^KEY_/, '')}) ist eine Maustaste; Nexora-Module gehen nur auf Tastatur-Tasten.`); continue }
    config.modules[mod] = { ...(config.modules[mod] || {}), keybind: code }
  }
  fs.writeFileSync(configFile, JSON.stringify(config, null, 2))

  // The server list only when this instance has none yet: an existing one is never replaced.
  let serversCopied = false
  const servers = path.join(gameDir, 'servers.dat')
  if (fs.existsSync(path.join(MINECRAFT, 'servers.dat'))) {
    if (!fs.existsSync(servers)) { fs.copyFileSync(path.join(MINECRAFT, 'servers.dat'), servers); serversCopied = true }
    else notes.push('Die Serverliste dieser Instanz bleibt, wie sie ist.')
  }

  // Resource packs that aren't here yet.
  let packsCopied = 0
  const packDir = path.join(gameDir, 'resourcepacks')
  fs.mkdirSync(packDir, { recursive: true })
  const have = new Set(packsIn(packDir))
  for (const pack of packsIn(path.join(MINECRAFT, 'resourcepacks'))) {
    if (have.has(pack)) continue
    try {
      await fs.promises.cp(path.join(MINECRAFT, 'resourcepacks', pack), path.join(packDir, pack), { recursive: true })
      packsCopied++
    } catch { notes.push(`Resource Pack „${pack}“ ließ sich nicht kopieren.`) }
  }

  return { ...preview, optionsCopied, serversCopied, packsCopied, notes }
}
