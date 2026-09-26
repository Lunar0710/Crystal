import fs from 'fs'
import path from 'path'
import os from 'os'
import { crystalPath } from '../paths'

// Mirrors the CSS custom properties defined in globals.css for each theme id.
// Kept in sync manually since the renderer's CSS can't be read from the main process.
const THEME_COLORS: Record<string, Record<string, [number, number, number]>> = {
  'nexora-red':       { bg: [0,0,0], panel: [17,17,19], card: [23,23,26], border: [38,39,42], accent: [195,73,58], accent2: [214,104,90], text: [240,240,240], muted: [138,138,140] },
  'crystal-blue':     { bg: [8,8,9], panel: [14,14,16], card: [20,20,23], border: [42,42,47], accent: [255,255,255], accent2: [190,190,196], text: [240,240,243], muted: [133,133,142] },
  'crystal-crimson':  { bg: [18,11,12], panel: [26,15,17], card: [36,19,22], border: [54,26,30], accent: [245,69,91], accent2: [245,124,61], text: [240,228,229], muted: [138,107,110] },
  'crystal-bloom':    { bg: [22,12,18], panel: [30,17,25], card: [41,22,33], border: [61,30,46], accent: [245,107,160], accent2: [245,163,199], text: [240,229,235], muted: [138,107,122] },
  'crystal-amethyst': { bg: [16,12,22], panel: [22,17,32], card: [30,23,44], border: [45,34,64], accent: [163,91,245], accent2: [124,61,245], text: [232,228,240], muted: [116,107,138] },
  'crystal-emerald':  { bg: [10,18,15], panel: [15,26,21], card: [20,36,29], border: [28,54,44], accent: [52,211,153], accent2: [91,245,201], text: [228,240,234], muted: [107,138,122] },
  'crystal-light':    { bg: [245,247,250], panel: [255,255,255], card: [255,255,255], border: [226,231,238], accent: [91,138,245], accent2: [124,106,245], text: [22,27,38], muted: [107,114,128] },
  'midnight':         { bg: [8,11,24], panel: [13,18,38], card: [19,26,54], border: [30,41,82], accent: [96,133,255], accent2: [129,108,255], text: [226,232,255], muted: [110,122,160] },
  'sunset':           { bg: [24,12,10], panel: [35,17,14], card: [48,23,19], border: [71,35,27], accent: [249,115,22], accent2: [225,29,72], text: [253,235,228], muted: [150,112,96] },
  'ocean':            { bg: [6,18,24], panel: [9,27,36], card: [13,38,50], border: [20,56,72], accent: [14,165,233], accent2: [20,184,166], text: [224,244,250], muted: [100,132,146] },
  'rose':             { bg: [26,13,18], panel: [38,19,27], card: [52,26,37], border: [78,39,55], accent: [251,113,133], accent2: [249,168,212], text: [253,232,240], muted: [158,114,133] },
  'carbon':           { bg: [10,10,11], panel: [18,18,20], card: [26,26,29], border: [45,45,50], accent: [161,161,170], accent2: [82,82,91], text: [244,244,245], muted: [113,113,122] },
  'matcha':           { bg: [12,18,10], panel: [18,27,15], card: [25,37,20], border: [38,56,30], accent: [132,204,22], accent2: [77,124,15], text: [235,245,224], muted: [118,138,100] },
  'prism':            { bg: [9,10,20], panel: [15,17,32], card: [21,24,45], border: [34,39,70], accent: [34,211,238], accent2: [168,85,247], text: [230,236,252], muted: [116,126,160] },
  'obsidian':         { bg: [10,9,7], panel: [17,15,11], card: [25,22,16], border: [46,40,27], accent: [251,191,36], accent2: [180,83,9], text: [248,244,232], muted: [130,119,93] },
  'void':             { bg: [3,3,6], panel: [8,8,14], card: [13,13,22], border: [24,24,38], accent: [255,255,255], accent2: [180,180,210], text: [240,240,250], muted: [120,120,140] },
  'aurora':           { bg: [5,12,16], panel: [9,20,26], card: [13,28,36], border: [24,48,58], accent: [52,211,153], accent2: [129,140,248], text: [226,245,242], muted: [112,141,145] },
  'sakura':           { bg: [18,8,18], panel: [27,12,27], card: [36,17,36], border: [61,29,58], accent: [244,114,182], accent2: [126,34,206], text: [250,232,244], muted: [158,116,148] },
  'halloween':        { bg: [14,9,16], panel: [22,14,25], card: [31,19,34], border: [58,34,58], accent: [249,115,22], accent2: [126,34,206], text: [252,238,226], muted: [156,124,140] },
  'terminal':         { bg: [2,8,5], panel: [4,14,9], card: [6,20,13], border: [14,44,28], accent: [34,197,94], accent2: [22,163,74], text: [209,250,229], muted: [82,130,102] },
  'sandstorm':        { bg: [18,12,6], panel: [28,19,10], card: [37,25,13], border: [66,45,22], accent: [217,119,6], accent2: [245,158,11], text: [250,240,224], muted: [160,130,92] },
  'nebula':           { bg: [8,5,18], panel: [14,9,30], card: [20,13,42], border: [40,26,76], accent: [192,132,252], accent2: [34,211,238], text: [237,231,252], muted: [132,116,168] },
  'blueprint':        { bg: [6,16,33], panel: [9,23,46], card: [12,30,60], border: [24,56,98], accent: [56,189,248], accent2: [14,116,205], text: [224,240,255], muted: [112,146,186] },
}

function rgbToArgbHex(rgb: [number, number, number], alpha = 0xFF): string {
  const [r, g, b] = rgb
  return '0x' + [alpha, r, g, b].map(v => v.toString(16).padStart(2, '0')).join('')
}

/** '#rrggbb' to [r, g, b]; null for anything else. */
function hexRgb(hex: unknown): [number, number, number] | null {
  const m = typeof hex === 'string' ? /^#?([0-9a-f]{6})$/i.exec(hex.trim()) : null
  if (!m) return null
  const n = parseInt(m[1], 16)
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255]
}

/**
 * Tells the game the launcher's colours (config/theme.json), so the in-game
 * menu matches. The custom theme sends the colours the player picked.
 */
export function syncThemeToClient(themeId: string, custom?: unknown) {
  let colors = THEME_COLORS[themeId] || THEME_COLORS['nexora-red']
  if (themeId === 'custom' && custom && typeof custom === 'object') {
    const c = custom as Record<string, unknown>
    const pick = (key: string, fallback: [number, number, number]) => hexRgb(c[key]) ?? fallback
    const base = THEME_COLORS['nexora-red']
    colors = {
      bg: pick('bg', base.bg), panel: pick('panel', base.panel), card: pick('card', base.card), border: pick('border', base.border),
      accent: pick('accent', base.accent), accent2: pick('accent-2', base.accent2), text: pick('text', base.text), muted: pick('muted', base.muted),
    }
  }

  const payload = {
    id: themeId,
    colors: Object.fromEntries(
      Object.entries(colors).map(([k, v]) => [k, rgbToArgbHex(v as [number, number, number])])
    ),
  }

  const configDir = crystalPath('config')
  fs.mkdirSync(configDir, { recursive: true })
  fs.writeFileSync(path.join(configDir, 'theme.json'), JSON.stringify(payload, null, 2))
}

const PERK_RANKS = ['owner', 'co_owner', 'admin', 'staff', 'developer', 'media', 'crystal_plus']

/**
 * Tells the in-game client which rank the logged-in player has, so Nexora+
 * perks can unlock there too. Like ranks in general this is cosmetic, not a
 * security boundary: it only decides which visual extras are offered.
 */
export function syncProfileToClient(rank: string, tester = false) {
  const configDir = crystalPath('config')
  fs.mkdirSync(configDir, { recursive: true })
  const next = JSON.stringify({ rank, perks: PERK_RANKS.includes(rank), tester }, null, 2)
  const file = path.join(configDir, 'profile.json')
  // Only rewrite on change: the client watches the file's timestamp.
  if (fs.existsSync(file) && fs.readFileSync(file, 'utf8') === next) return
  fs.writeFileSync(file, next)
}
