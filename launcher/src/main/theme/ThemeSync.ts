import fs from 'fs'
import path from 'path'
import os from 'os'

// Mirrors the CSS custom properties defined in globals.css for each theme id.
// Kept in sync manually since the renderer's CSS can't be read from the main process.
const THEME_COLORS: Record<string, Record<string, [number, number, number]>> = {
  'crystal-blue':     { bg: [13,15,20], panel: [19,23,32], card: [26,31,46], border: [37,43,58], accent: [91,138,245], accent2: [124,106,245], text: [228,232,240], muted: [107,114,128] },
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
}

function rgbToArgbHex(rgb: [number, number, number], alpha = 0xFF): string {
  const [r, g, b] = rgb
  return '0x' + [alpha, r, g, b].map(v => v.toString(16).padStart(2, '0')).join('')
}

function hexToRgb(hex: string): [number, number, number] {
  const clean = hex.replace('#', '')
  return [
    parseInt(clean.substring(0, 2), 16) || 0,
    parseInt(clean.substring(2, 4), 16) || 0,
    parseInt(clean.substring(4, 6), 16) || 0,
  ]
}

export interface CustomThemeColors {
  bg: string; panel: string; card: string; border: string
  accent: string; accent2: string; text: string; muted: string
}

export function syncThemeToClient(themeId: string, customColors?: CustomThemeColors) {
  const colors = themeId === 'custom' && customColors
    ? {
        bg: hexToRgb(customColors.bg), panel: hexToRgb(customColors.panel),
        card: hexToRgb(customColors.card), border: hexToRgb(customColors.border),
        accent: hexToRgb(customColors.accent), accent2: hexToRgb(customColors.accent2),
        text: hexToRgb(customColors.text), muted: hexToRgb(customColors.muted),
      }
    : THEME_COLORS[themeId] || THEME_COLORS['crystal-blue']

  const payload = {
    id: themeId,
    colors: Object.fromEntries(
      Object.entries(colors).map(([k, v]) => [k, rgbToArgbHex(v as [number, number, number])])
    ),
  }

  const configDir = path.join(os.homedir(), '.crystal', 'config')
  fs.mkdirSync(configDir, { recursive: true })
  fs.writeFileSync(path.join(configDir, 'theme.json'), JSON.stringify(payload, null, 2))
}
