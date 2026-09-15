import { HD_ART } from './hdCapes'
import type { RankId } from './ranks'

export type CapeCategory = 'art' | 'team' | 'plus' | 'emblem' | 'anime' | 'internet' | 'themed' | 'solid' | 'gradient' | 'pattern' | 'pixel' | 'neon'

type Painter = (ctx: CanvasRenderingContext2D, w: number, h: number) => void

export interface CapeDef {
  id: string
  name: string
  category: CapeCategory
  paint: Painter
  glow?: string
  /** Omitted = free for everyone. */
  requiredRank?: RankId
  /** Resolution multiplier for detailed (non-pixel) capes: the painter gets a 10*hd x 16*hd canvas. */
  hd?: number
}

// A Minecraft cape texture is 64x32, but only the 10x16 block at (1,1) is the
// face other players actually see. Painters therefore compose in a 10x16 space
// and the sheet is assembled around that — painting the full sheet directly
// would put most of a design outside the visible area.
const TEX_W = 64
const TEX_H = 32
const CAPE_W = 10
const CAPE_H = 16

const textureCache = new Map<string, string>()

export function capeTextureUrl(cape: CapeDef): string {
  const cached = textureCache.get(cape.id)
  if (cached) return cached

  // Design pass at the cape's real resolution (times hd for detailed capes).
  const k = cape.hd ?? 1
  const design = document.createElement('canvas')
  design.width = CAPE_W * k
  design.height = CAPE_H * k
  const designCtx = design.getContext('2d')!
  designCtx.imageSmoothingEnabled = k > 1
  cape.paint(designCtx, CAPE_W * k, CAPE_H * k)
  const url = sheetFromDesign(design, k)
  textureCache.set(cape.id, url)
  return url
}

/**
 * Lays a finished cape face (10:16) out as a Minecraft cape texture: 64x32
 * times k. Shared by built-in capes and pictures turned into capes.
 */
function sheetFromDesign(design: HTMLCanvasElement, k: number): string {

  const sheet = document.createElement('canvas')
  sheet.width = TEX_W * k
  sheet.height = TEX_H * k
  const ctx = sheet.getContext('2d')!
  ctx.imageSmoothingEnabled = k > 1

  // Stretch the design across the sheet first so the thin edge strips (top,
  // bottom and sides of the cape) pick up matching colours instead of showing
  // transparent pixels.
  ctx.drawImage(design, 0, 0, TEX_W * k, TEX_H * k)

  // Outside face — the one everyone sees.
  ctx.drawImage(design, k, k)

  // Inside face, mirrored so the design reads correctly from the back.
  ctx.save()
  ctx.translate((12 + CAPE_W) * k, k)
  ctx.scale(-1, 1)
  ctx.drawImage(design, 0, 0)
  ctx.restore()

  return sheet.toDataURL('image/png')
}

/** Sheet width/height 2:1 means the file already is a cape texture. */
export function isCapeTexture(width: number, height: number): boolean {
  return Math.abs(width / height - 2) < 0.05
}

/**
 * Turns any picture into a detailed HD cape (1024x512 texture): the picture is
 * cropped to the cape's 10:16 shape around its centre, like a phone wallpaper.
 */
export function pictureToCapeTexture(img: HTMLImageElement): string {
  const k = 16
  const design = document.createElement('canvas')
  design.width = CAPE_W * k
  design.height = CAPE_H * k
  const ctx = design.getContext('2d')!
  ctx.imageSmoothingQuality = 'high'
  const targetRatio = CAPE_W / CAPE_H
  const ratio = img.naturalWidth / img.naturalHeight
  let sw = img.naturalWidth, sh = img.naturalHeight, sx = 0, sy = 0
  if (ratio > targetRatio) { sw = sh * targetRatio; sx = (img.naturalWidth - sw) / 2 }
  else { sh = sw / targetRatio; sy = (img.naturalHeight - sh) / 2 }
  ctx.drawImage(img, sx, sy, sw, sh, 0, 0, design.width, design.height)
  return sheetFromDesign(design, k)
}

/** Scaled-up preview of just the visible face, for the picker tiles. */
export function capePreviewUrl(cape: CapeDef, scale = 8): string {
  const key = `${cape.id}@${scale}`
  const cached = textureCache.get(key)
  if (cached) return cached

  const k = cape.hd ?? 1
  const design = document.createElement('canvas')
  design.width = CAPE_W * k
  design.height = CAPE_H * k
  const designCtx = design.getContext('2d')!
  designCtx.imageSmoothingEnabled = k > 1
  cape.paint(designCtx, CAPE_W * k, CAPE_H * k)

  const out = document.createElement('canvas')
  out.width = CAPE_W * scale
  out.height = CAPE_H * scale
  const ctx = out.getContext('2d')!
  ctx.imageSmoothingEnabled = k > 1
  ctx.drawImage(design, 0, 0, out.width, out.height)

  const url = out.toDataURL('image/png')
  textureCache.set(key, url)
  return url
}

// ---- painter helpers -------------------------------------------------------

const solid = (color: string): Painter => (ctx, w, h) => {
  ctx.fillStyle = color
  ctx.fillRect(0, 0, w, h)
}

const gradient = (from: string, to: string): Painter => (ctx, w, h) => {
  const g = ctx.createLinearGradient(0, 0, w, h)
  g.addColorStop(0, from)
  g.addColorStop(1, to)
  ctx.fillStyle = g
  ctx.fillRect(0, 0, w, h)
}

const stripes = (a: string, b: string, size: number, diagonal = false): Painter => (ctx, w, h) => {
  ctx.fillStyle = a
  ctx.fillRect(0, 0, w, h)
  ctx.fillStyle = b
  for (let i = -h; i < w + h; i += size * 2) {
    ctx.beginPath()
    if (diagonal) {
      ctx.moveTo(i, 0)
      ctx.lineTo(i + size, 0)
      ctx.lineTo(i + size - h, h)
      ctx.lineTo(i - h, h)
    } else {
      ctx.rect(i, 0, size, h)
    }
    ctx.fill()
  }
}

const checkers = (a: string, b: string, size: number): Painter => (ctx, w, h) => {
  for (let y = 0; y < h; y += size) {
    for (let x = 0; x < w; x += size) {
      ctx.fillStyle = ((x / size + y / size) % 2 === 0) ? a : b
      ctx.fillRect(x, y, size, size)
    }
  }
}

const dots = (bg: string, dot: string, spacing: number, radius: number): Painter => (ctx, w, h) => {
  ctx.fillStyle = bg
  ctx.fillRect(0, 0, w, h)
  ctx.fillStyle = dot
  for (let y = spacing / 2; y < h; y += spacing) {
    for (let x = spacing / 2; x < w; x += spacing) {
      ctx.beginPath()
      ctx.arc(x, y, radius, 0, Math.PI * 2)
      ctx.fill()
    }
  }
}

const pixelNoise = (seed: number, palette: string[], cell: number): Painter => (ctx, w, h) => {
  const rand = (n: number) => Math.abs(Math.sin(seed + n * 12.9898) * 43758.5453) % 1
  let i = 0
  for (let y = 0; y < h; y += cell) {
    for (let x = 0; x < w; x += cell) {
      ctx.fillStyle = palette[Math.floor(rand(i++) * palette.length)]
      ctx.fillRect(x, y, cell, cell)
    }
  }
}

const neon = (base: string, glow: string): Painter => (ctx, w, h) => {
  ctx.fillStyle = base
  ctx.fillRect(0, 0, w, h)
  const g = ctx.createLinearGradient(0, 0, w, 0)
  g.addColorStop(0, 'rgba(0,0,0,0)')
  g.addColorStop(0.42, 'rgba(0,0,0,0)')
  g.addColorStop(0.5, glow)
  g.addColorStop(0.58, 'rgba(0,0,0,0)')
  g.addColorStop(1, 'rgba(0,0,0,0)')
  ctx.fillStyle = g
  ctx.fillRect(0, 0, w, h)
}

const scatter = (
  bg: Painter,
  shapes: { color: string; count: number; radius: number }[]
): Painter => (ctx, w, h) => {
  bg(ctx, w, h)
  let seed = 7
  const rand = () => {
    seed = (seed * 9301 + 49297) % 233280
    return seed / 233280
  }
  for (const shape of shapes) {
    ctx.fillStyle = shape.color
    for (let i = 0; i < shape.count; i++) {
      ctx.beginPath()
      ctx.arc(rand() * w, rand() * h, shape.radius, 0, Math.PI * 2)
      ctx.fill()
    }
  }
}

// ---- cape definitions ------------------------------------------------------

const SOLID_COLORS: [string, string][] = [
  ['Crimson', '#e11d48'], ['Amber', '#f59e0b'], ['Emerald', '#10b981'], ['Sky', '#0ea5e9'],
  ['Violet', '#8b5cf6'], ['Pink', '#ec4899'], ['Slate', '#475569'], ['Lime', '#84cc16'],
  ['Teal', '#14b8a6'], ['Indigo', '#6366f1'], ['Rose', '#f43f5e'], ['Obsidian', '#18181b'],
]

const GRADIENT_PAIRS: [string, string, string][] = [
  ['Sunset', '#f59e0b', '#e11d48'], ['Ocean', '#0ea5e9', '#6366f1'], ['Aurora', '#10b981', '#0ea5e9'],
  ['Bloom', '#ec4899', '#8b5cf6'], ['Ember', '#e11d48', '#f59e0b'], ['Nebula', '#6366f1', '#ec4899'],
  ['Forest', '#14b8a6', '#84cc16'], ['Dusk', '#8b5cf6', '#475569'], ['Gold', '#f59e0b', '#fef3c7'],
  ['Glacier', '#38bdf8', '#e0f2fe'], ['Wine', '#881337', '#e11d48'], ['Mint', '#10b981', '#d1fae5'],
]

const PATTERNS: { name: string; paint: Painter }[] = [
  { name: 'Stripes',   paint: stripes('#18181b', '#6366f1', 4, true) },
  { name: 'Checkers',  paint: checkers('#18181b', '#e11d48', 4) },
  { name: 'Dots',      paint: dots('#18181b', '#f59e0b', 8, 2) },
  { name: 'Bars',      paint: stripes('#0f172a', '#10b981', 3) },
  { name: 'Diagonal',  paint: stripes('#18181b', '#ec4899', 3, true) },
  { name: 'Tiles',     paint: checkers('#475569', '#18181b', 8) },
  { name: 'Bubbles',   paint: dots('#0f172a', '#0ea5e9', 10, 3) },
  { name: 'Weave',     paint: checkers('#8b5cf6', '#18181b', 2) },
]

const NEONS: { name: string; glow: string }[] = [
  { name: 'Neon Pink',   glow: '#ec4899' },
  { name: 'Neon Cyan',   glow: '#22d3ee' },
  { name: 'Neon Lime',   glow: '#a3e635' },
  { name: 'Neon Violet', glow: '#a855f7' },
  { name: 'Neon Amber',  glow: '#fbbf24' },
  { name: 'Neon Red',    glow: '#ef4444' },
  { name: 'Neon Mint',   glow: '#2dd4bf' },
  { name: 'Neon Indigo', glow: '#818cf8' },
]

// Original, style-based designs — deliberately no licensed characters or logos,
// since shipping someone else's artwork isn't ours to distribute. Users can
// upload their own art for anything we can't ship.
const THEMED: { name: string; paint: Painter; glow?: string }[] = [
  {
    name: 'Kawaii Pastell',
    paint: scatter(gradient('#ffc2e2', '#ffe9f4'), [
      { color: '#ffffffaa', count: 14, radius: 3 },
      { color: '#ff8fc4aa', count: 10, radius: 2 },
    ]),
  },
  {
    name: 'Sakura',
    paint: scatter(gradient('#4a1f38', '#83335e'), [
      { color: '#ff8fc4', count: 22, radius: 1.6 },
      { color: '#ffd1e8', count: 12, radius: 1 },
    ]),
  },
  {
    name: 'Cyber Sunset',
    paint: (ctx, w, h) => {
      const g = ctx.createLinearGradient(0, 0, 0, h)
      g.addColorStop(0, '#2b0f45')
      g.addColorStop(0.45, '#7b2d73')
      g.addColorStop(0.75, '#ff7a59')
      g.addColorStop(1, '#ffd36e')
      ctx.fillStyle = g
      ctx.fillRect(0, 0, w, h)
    },
  },
  {
    name: 'Retro Grid',
    glow: '#ff4ecd',
    paint: (ctx, w, h) => {
      gradient('#12022e', '#3b0a63')(ctx, w, h)
      ctx.strokeStyle = '#ff4ecd'
      ctx.lineWidth = 0.5
      for (let x = 0; x <= w; x += 6) {
        ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, h); ctx.stroke()
      }
      for (let y = 0; y <= h; y += 6) {
        ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(w, y); ctx.stroke()
      }
    },
  },
  {
    name: 'Galaxie',
    paint: scatter(gradient('#0b0524', '#6b2f8f'), [
      { color: '#ffffff', count: 40, radius: 0.6 },
      { color: '#c4b5fd', count: 14, radius: 1 },
    ]),
  },
  {
    name: 'Koi',
    paint: scatter(gradient('#14213d', '#1f3a5f'), [
      { color: '#ff6b4a', count: 8, radius: 3 },
      { color: '#ffffff', count: 6, radius: 2 },
    ]),
  },
  { name: 'Matcha', paint: stripes('#6b8f3f', '#86ab52', 5, true) },
  {
    name: 'Vaporwave',
    glow: '#9d8cff',
    paint: (ctx, w, h) => {
      const g = ctx.createLinearGradient(0, 0, 0, h)
      g.addColorStop(0, '#ff9ee6')
      g.addColorStop(0.5, '#9d8cff')
      g.addColorStop(1, '#6ce6ff')
      ctx.fillStyle = g
      ctx.fillRect(0, 0, w, h)
    },
  },
]

// ---- extra painters for the style-led categories ---------------------------

const halftone = (bg: string, dot: string, spacing: number): Painter => (ctx, w, h) => {
  ctx.fillStyle = bg
  ctx.fillRect(0, 0, w, h)
  ctx.fillStyle = dot
  for (let y = 0; y < h; y += spacing) {
    for (let x = 0; x < w; x += spacing) {
      // Dots shrink toward the bottom — the classic manga screentone fade.
      const r = Math.max(0.3, (1 - y / h) * (spacing * 0.42))
      ctx.beginPath()
      ctx.arc(x + spacing / 2, y + spacing / 2, r, 0, Math.PI * 2)
      ctx.fill()
    }
  }
}

const speedLines = (bg: string, line: string): Painter => (ctx, w, h) => {
  ctx.fillStyle = bg
  ctx.fillRect(0, 0, w, h)
  ctx.strokeStyle = line
  const cx = w / 2
  const cy = h / 2
  for (let a = 0; a < Math.PI * 2; a += Math.PI / 18) {
    ctx.lineWidth = 0.4 + (a % 0.5)
    ctx.beginPath()
    ctx.moveTo(cx + Math.cos(a) * 6, cy + Math.sin(a) * 6)
    ctx.lineTo(cx + Math.cos(a) * w, cy + Math.sin(a) * w)
    ctx.stroke()
  }
}

const sunRays = (sky: [string, string], ray: string): Painter => (ctx, w, h) => {
  gradient(sky[0], sky[1])(ctx, w, h)
  ctx.fillStyle = ray
  const cx = w / 2
  const cy = h * 0.62
  for (let i = 0; i < 12; i++) {
    const a = (i / 12) * Math.PI * 2
    ctx.beginPath()
    ctx.moveTo(cx, cy)
    ctx.lineTo(cx + Math.cos(a - 0.06) * w, cy + Math.sin(a - 0.06) * w)
    ctx.lineTo(cx + Math.cos(a + 0.06) * w, cy + Math.sin(a + 0.06) * w)
    ctx.fill()
  }
}

const horizon = (sky: [string, string], sun: string, land: string): Painter => (ctx, w, h) => {
  gradient(sky[0], sky[1])(ctx, w, h)
  ctx.fillStyle = sun
  ctx.beginPath()
  ctx.arc(w / 2, h * 0.5, h * 0.22, 0, Math.PI * 2)
  ctx.fill()
  ctx.fillStyle = land
  ctx.beginPath()
  ctx.moveTo(0, h)
  ctx.lineTo(0, h * 0.72)
  ctx.lineTo(w * 0.3, h * 0.58)
  ctx.lineTo(w * 0.55, h * 0.74)
  ctx.lineTo(w * 0.78, h * 0.6)
  ctx.lineTo(w, h * 0.76)
  ctx.lineTo(w, h)
  ctx.fill()
}

const torii = (sky: [string, string], gate: string): Painter => (ctx, w, h) => {
  gradient(sky[0], sky[1])(ctx, w, h)
  ctx.fillStyle = gate
  const cx = w / 2
  ctx.fillRect(cx - 14, h * 0.3, 28, 2.5)
  ctx.fillRect(cx - 11, h * 0.42, 22, 2)
  ctx.fillRect(cx - 8, h * 0.3, 2.5, h * 0.5)
  ctx.fillRect(cx + 5.5, h * 0.3, 2.5, h * 0.5)
}

const wave = (sea: string, foam: string, sky: string): Painter => (ctx, w, h) => {
  ctx.fillStyle = sky
  ctx.fillRect(0, 0, w, h)
  ctx.fillStyle = sea
  ctx.beginPath()
  ctx.moveTo(0, h)
  ctx.lineTo(0, h * 0.55)
  for (let x = 0; x <= w; x += 2) {
    ctx.lineTo(x, h * 0.55 + Math.sin(x / 7) * 4)
  }
  ctx.lineTo(w, h)
  ctx.fill()
  ctx.strokeStyle = foam
  ctx.lineWidth = 1
  ctx.beginPath()
  for (let x = 0; x <= w; x += 2) {
    const y = h * 0.55 + Math.sin(x / 7) * 4
    x === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y)
  }
  ctx.stroke()
}

const matrixRain = (): Painter => (ctx, w, h) => {
  ctx.fillStyle = '#020806'
  ctx.fillRect(0, 0, w, h)
  let seed = 3
  const rand = () => { seed = (seed * 9301 + 49297) % 233280; return seed / 233280 }
  for (let x = 0; x < w; x += 4) {
    const len = 4 + Math.floor(rand() * 8)
    const start = Math.floor(rand() * h)
    for (let i = 0; i < len; i++) {
      const y = (start + i * 3) % h
      ctx.fillStyle = i === 0 ? '#ccffdd' : `rgba(34,197,94,${1 - i / len})`
      ctx.fillRect(x, y, 2, 2)
    }
  }
}

const glitchBars = (): Painter => (ctx, w, h) => {
  gradient('#0f0f17', '#1c1c2b')(ctx, w, h)
  const colors = ['#ff2e88', '#22d3ee', '#fbbf24', '#a855f7']
  let seed = 11
  const rand = () => { seed = (seed * 9301 + 49297) % 233280; return seed / 233280 }
  for (let i = 0; i < 16; i++) {
    ctx.fillStyle = colors[Math.floor(rand() * colors.length)]
    ctx.globalAlpha = 0.5 + rand() * 0.5
    ctx.fillRect(0, Math.floor(rand() * h), w, 1 + Math.floor(rand() * 2))
  }
  ctx.globalAlpha = 1
}

const vhs = (): Painter => (ctx, w, h) => {
  const g = ctx.createLinearGradient(0, 0, 0, h)
  g.addColorStop(0, '#1b1030')
  g.addColorStop(0.5, '#3a1c5c')
  g.addColorStop(1, '#0d0720')
  ctx.fillStyle = g
  ctx.fillRect(0, 0, w, h)
  for (let y = 0; y < h; y += 2) {
    ctx.fillStyle = 'rgba(255,255,255,0.05)'
    ctx.fillRect(0, y, w, 1)
  }
  ctx.fillStyle = 'rgba(255,46,136,0.35)'
  ctx.fillRect(0, h * 0.35, w, 2)
  ctx.fillStyle = 'rgba(34,211,238,0.35)'
  ctx.fillRect(0, h * 0.62, w, 2)
}

const blueScreen = (): Painter => (ctx, w, h) => {
  ctx.fillStyle = '#0b5ed7'
  ctx.fillRect(0, 0, w, h)
  ctx.fillStyle = '#ffffff'
  ctx.fillRect(6, 6, 10, 10)
  for (let i = 0; i < 6; i++) {
    ctx.fillStyle = 'rgba(255,255,255,0.85)'
    ctx.fillRect(6, 22 + i * 3, w - 12 - (i % 3) * 8, 1.5)
  }
}

const barcode = (): Painter => (ctx, w, h) => {
  ctx.fillStyle = '#f8fafc'
  ctx.fillRect(0, 0, w, h)
  let seed = 5
  const rand = () => { seed = (seed * 9301 + 49297) % 233280; return seed / 233280 }
  let x = 2
  while (x < w - 2) {
    const bw = 1 + Math.floor(rand() * 3)
    ctx.fillStyle = '#0f172a'
    ctx.fillRect(x, 3, bw, h - 8)
    x += bw + 1 + Math.floor(rand() * 3)
  }
}

const pixelHeart = (color: string, bg: string): Painter => (ctx, w, h) => {
  ctx.fillStyle = bg
  ctx.fillRect(0, 0, w, h)
  const grid = [
    '0110011000',
    '1111111100',
    '1111111100',
    '0111111000',
    '0011110000',
    '0001100000',
  ]
  const cell = 3
  const ox = (w - grid[0].length * cell) / 2
  const oy = (h - grid.length * cell) / 2
  ctx.fillStyle = color
  grid.forEach((row, y) => {
    row.split('').forEach((c, x) => {
      if (c === '1') ctx.fillRect(ox + x * cell, oy + y * cell, cell, cell)
    })
  })
}

const smiley = (face: string, bg: string): Painter => (ctx, w, h) => {
  ctx.fillStyle = bg
  ctx.fillRect(0, 0, w, h)
  ctx.fillStyle = face
  ctx.beginPath(); ctx.arc(w / 2, h / 2, 11, 0, Math.PI * 2); ctx.fill()
  ctx.fillStyle = bg
  ctx.beginPath(); ctx.arc(w / 2 - 4, h / 2 - 3, 1.6, 0, Math.PI * 2); ctx.fill()
  ctx.beginPath(); ctx.arc(w / 2 + 4, h / 2 - 3, 1.6, 0, Math.PI * 2); ctx.fill()
  ctx.strokeStyle = bg
  ctx.lineWidth = 1.6
  ctx.beginPath(); ctx.arc(w / 2, h / 2 + 1, 6, 0.25 * Math.PI, 0.75 * Math.PI); ctx.stroke()
}

const rainbow = (): Painter => (ctx, w, h) => {
  const bands = ['#e40303', '#ff8c00', '#ffed00', '#008026', '#24408e', '#732982']
  const bandH = h / bands.length
  bands.forEach((c, i) => {
    ctx.fillStyle = c
    ctx.fillRect(0, i * bandH, w, bandH + 1)
  })
}

const loadingBar = (): Painter => (ctx, w, h) => {
  ctx.fillStyle = '#101828'
  ctx.fillRect(0, 0, w, h)
  ctx.strokeStyle = '#475569'
  ctx.lineWidth = 1
  ctx.strokeRect(6, h / 2 - 4, w - 12, 8)
  ctx.fillStyle = '#22d3ee'
  ctx.fillRect(7, h / 2 - 3, (w - 14) * 0.68, 6)
}

// Anime-*aesthetic* designs (screentone, speed lines, sakura, Hokusai-style
// wave). Deliberately no licensed characters — those aren't ours to ship.
const ANIME: { name: string; paint: Painter; glow?: string }[] = [
  { name: 'Screentone',    paint: halftone('#f8fafc', '#0f172a', 4) },
  { name: 'Screentone Ink',paint: halftone('#0f172a', '#f8fafc', 4) },
  { name: 'Speed Lines',   paint: speedLines('#f8fafc', '#0f172a') },
  { name: 'Impact',        paint: speedLines('#18181b', '#fbbf24'), glow: '#fbbf24' },
  { name: 'Rising Sun',    paint: sunRays(['#fde68a', '#f97316'], 'rgba(255,255,255,0.45)') },
  { name: 'Sunset Hills',  paint: horizon(['#fbbf24', '#f43f5e'], '#fff7ed', '#3b1d3d') },
  { name: 'Dusk Hills',    paint: horizon(['#6366f1', '#1e1b4b'], '#e0e7ff', '#0f0a24') },
  { name: 'Torii',         paint: torii(['#fda4af', '#7f1d1d'], '#450a0a') },
  { name: 'Torii Dawn',    paint: torii(['#fef3c7', '#fb7185'], '#7f1d1d') },
  { name: 'Great Wave',    paint: wave('#1d4ed8', '#e0f2fe', '#dbeafe') },
  { name: 'Night Wave',    paint: wave('#1e1b4b', '#a5b4fc', '#0f172a') },
  { name: 'Sakura Fall',   paint: scatter(gradient('#fce7f3', '#f9a8d4'), [
      { color: '#ec4899', count: 20, radius: 1.4 },
      { color: '#ffffff', count: 10, radius: 1 },
    ]) },
  { name: 'Sakura Night',  paint: scatter(gradient('#1e1b4b', '#4c1d95'), [
      { color: '#f9a8d4', count: 24, radius: 1.3 },
      { color: '#ffffff', count: 8, radius: 0.7 },
    ]) },
  { name: 'Chakra',        paint: speedLines('#0b1120', '#22d3ee'), glow: '#22d3ee' },
  { name: 'Aura Burst',    paint: sunRays(['#1a1005', '#7c2d12'], 'rgba(251,191,36,0.5)'), glow: '#fbbf24' },
]

// Internet-culture visuals, all generic — no specific meme images, which are
// almost always somebody's copyrighted artwork or photo.
const INTERNET: { name: string; paint: Painter; glow?: string }[] = [
  { name: 'Matrix Rain',  paint: matrixRain(), glow: '#22c55e' },
  { name: 'Glitch',       paint: glitchBars(), glow: '#ff2e88' },
  { name: 'VHS',          paint: vhs() },
  { name: 'Blue Screen',  paint: blueScreen() },
  { name: 'Barcode',      paint: barcode() },
  { name: 'Loading',      paint: loadingBar(), glow: '#22d3ee' },
  { name: 'Pixel Heart',  paint: pixelHeart('#ef4444', '#1c1917') },
  { name: 'Pixel Heart X',paint: pixelHeart('#22d3ee', '#0f172a') },
  { name: 'Smiley',       paint: smiley('#fbbf24', '#1c1917') },
  { name: 'Smiley Void',  paint: smiley('#e5e7eb', '#09090b') },
  { name: 'Rainbow',      paint: rainbow() },
  { name: 'Static',       paint: pixelNoise(4242, ['#0a0a0a', '#3f3f46', '#a1a1aa', '#e4e4e7'], 2) },
  { name: 'Terminal',     paint: (ctx, w, h) => {
      ctx.fillStyle = '#04140a'; ctx.fillRect(0, 0, w, h)
      ctx.fillStyle = '#4ade80'
      for (let i = 0; i < 9; i++) ctx.fillRect(4, 4 + i * 3, 6 + ((i * 13) % 40), 1.5)
    }, glow: '#4ade80' },
  { name: 'Wireframe',    paint: (ctx, w, h) => {
      ctx.fillStyle = '#0b1120'; ctx.fillRect(0, 0, w, h)
      ctx.strokeStyle = '#38bdf8'; ctx.lineWidth = 0.5
      for (let x = 0; x <= w; x += 8) { ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(w / 2, h); ctx.stroke() }
      for (let y = 0; y <= h; y += 6) { ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(w, y); ctx.stroke() }
    }, glow: '#38bdf8' },
  { name: 'Low Battery',  paint: (ctx, w, h) => {
      ctx.fillStyle = '#0a0a0a'; ctx.fillRect(0, 0, w, h)
      ctx.strokeStyle = '#ef4444'; ctx.lineWidth = 1.5
      ctx.strokeRect(14, h / 2 - 7, 34, 14)
      ctx.fillStyle = '#ef4444'
      ctx.fillRect(48, h / 2 - 3, 3, 6)
      ctx.fillRect(16, h / 2 - 5, 7, 10)
    }, glow: '#ef4444' },
]

// ---- emblem capes ----------------------------------------------------------
// Sprites are authored on the cape's real 10x16 grid, so every pixel drawn is
// a pixel players actually see. '.' is transparent (background shows through),
// any other character indexes into the palette.

type Sprite = { rows: string[]; palette: Record<string, string> }

const drawSprite = (sprite: Sprite, offsetY = 0): Painter => (ctx, w, h) => {
  sprite.rows.forEach((row, y) => {
    row.split('').forEach((key, x) => {
      const color = sprite.palette[key]
      if (!color) return
      ctx.fillStyle = color
      ctx.fillRect(x, y + offsetY, 1, 1)
    })
  })
}

/** Background + border trim + emblem, composed in one painter. */
const emblemCape = (
  background: Painter,
  emblem: Sprite,
  trim?: string
): Painter => (ctx, w, h) => {
  background(ctx, w, h)

  if (trim) {
    ctx.fillStyle = trim
    ctx.fillRect(0, 0, w, 1)
    ctx.fillRect(0, h - 1, w, 1)
    ctx.fillRect(0, 0, 1, h)
    ctx.fillRect(w - 1, 0, 1, h)
  }

  const offsetY = Math.floor((h - emblem.rows.length) / 2)
  drawSprite(emblem, offsetY)(ctx, w, h)
}

const SPRITES: Record<string, Sprite> = {
  bolt: {
    rows: [
      '....XX....',
      '...XXY....',
      '..XXY.....',
      '.XXY......',
      '.XXXXX....',
      '...XXY....',
      '....XY....',
      '...XY.....',
      '..XY......',
      '.XY.......',
    ],
    palette: { X: '#fde047', Y: '#f59e0b' },
  },
  heart: {
    rows: [
      '.XX..XX...',
      'XHXXXHHX..',
      'XHHHHHHX..',
      'XHHHHHHX..',
      '.XHHHHX...',
      '..XHHX....',
      '...XX.....',
    ],
    palette: { X: '#7f1d1d', H: '#ef4444' },
  },
  star: {
    rows: [
      '....XX....',
      '....YY....',
      '...XYYX...',
      'XXXXYYXXXX',
      '.XYYYYYYX.',
      '..XYYYYX..',
      '..XYY YX..',
      '.XY....YX.',
      '.X......X.',
    ],
    palette: { X: '#b45309', Y: '#fbbf24' },
  },
  moon: {
    rows: [
      '...XXXX...',
      '..XYYYYX..',
      '.XYYYX....',
      '.XYYX.....',
      '.XYYX.....',
      '.XYYX.....',
      '.XYYYX....',
      '..XYYYYX..',
      '...XXXX...',
    ],
    palette: { X: '#cbd5e1', Y: '#f8fafc' },
  },
  flame: {
    rows: [
      '....XX....',
      '...XYYX...',
      '..XYYYYX..',
      '.XYYZZYYX.',
      '.XYZZZZYX.',
      'XYZZZZZZYX',
      'XYZZZZZZYX',
      '.XYZZZZYX.',
      '..XYYYYX..',
      '...XXXX...',
    ],
    palette: { X: '#7c2d12', Y: '#f97316', Z: '#fde047' },
  },
  skull: {
    rows: [
      '..XXXXXX..',
      '.XWWWWWWX.',
      'XWWWWWWWWX',
      'XWKKWWKKWX',
      'XWKKWWKKWX',
      'XWWWWWWWWX',
      'XWWKWWKWWX',
      '.XWWWWWWX.',
      '..XKWKWKX.',
      '...XXXXX..',
    ],
    palette: { X: '#3f3f46', W: '#f4f4f5', K: '#18181b' },
  },
  leaf: {
    rows: [
      '.......XX.',
      '.....XXGX.',
      '...XXGGGX.',
      '..XGGGDGX.',
      '.XGGGDGGX.',
      '.XGGDGGGX.',
      '.XGDGGGX..',
      '.XDGGXX...',
      '..XXX.....',
      '..D.......',
    ],
    palette: { X: '#14532d', G: '#4ade80', D: '#166534' },
  },
  crown: {
    rows: [
      'X........X',
      'XX......XX',
      'XYX....XYX',
      'XYYX.XXYYX',
      'XYYYXYYYYX',
      'XYYYYYYYYX',
      'XYYRYYRYYX',
      'XYYYYYYYYX',
      'XXXXXXXXXX',
    ],
    palette: { X: '#92400e', Y: '#fbbf24', R: '#ef4444' },
  },
  crystal: {
    rows: [
      '...XXXX...',
      '..XAAAAX..',
      '.XABBBBAX.',
      'XABBBBBBAX',
      'XABBBBBBAX',
      '.XABBBBAX.',
      '..XABBAX..',
      '...XAAX...',
      '....XX....',
    ],
    palette: { X: '#312e81', A: '#818cf8', B: '#c7d2fe' },
  },
  torii: {
    rows: [
      'XXXXXXXXXX',
      '.XXXXXXXX.',
      '..X....X..',
      '.XXXXXXXX.',
      '..X....X..',
      '..X....X..',
      '..X....X..',
      '..X....X..',
      '..X....X..',
      '.XX....XX.',
    ],
    palette: { X: '#dc2626' },
  },
  eye: {
    rows: [
      '..XXXXXX..',
      '.XWWWWWWX.',
      'XWWIIIIWWX',
      'XWIIPPIIWX',
      'XWIIPPIIWX',
      'XWWIIIIWWX',
      '.XWWWWWWX.',
      '..XXXXXX..',
    ],
    palette: { X: '#0f172a', W: '#f8fafc', I: '#38bdf8', P: '#0f172a' },
  },
  sword: {
    rows: [
      '....XX....',
      '....BB....',
      '....BB....',
      '....BB....',
      '....BB....',
      '..GGBBGG..',
      '....HH....',
      '....HH....',
      '...HHHH...',
      '....HH....',
    ],
    palette: { X: '#e2e8f0', B: '#cbd5e1', G: '#f59e0b', H: '#78350f' },
  },
}

const EMBLEMS: { name: string; sprite: keyof typeof SPRITES; bg: Painter; trim?: string; glow?: string }[] = [
  { name: 'Bolt',      sprite: 'bolt',    bg: gradient('#1e1b4b', '#312e81'), trim: '#fbbf24', glow: '#fde047' },
  { name: 'Herz',      sprite: 'heart',   bg: gradient('#4c0519', '#881337'), trim: '#fda4af' },
  { name: 'Stern',     sprite: 'star',    bg: gradient('#0c0a1e', '#1e1b4b'), trim: '#fbbf24' },
  { name: 'Mond',      sprite: 'moon',    bg: gradient('#020617', '#1e293b'), trim: '#64748b' },
  { name: 'Flamme',    sprite: 'flame',   bg: gradient('#1c1917', '#431407'), trim: '#f97316', glow: '#f97316' },
  { name: 'Totenkopf', sprite: 'skull',   bg: gradient('#18181b', '#3f3f46'), trim: '#71717a' },
  { name: 'Blatt',     sprite: 'leaf',    bg: gradient('#052e16', '#166534'), trim: '#4ade80' },
  { name: 'Krone',     sprite: 'crown',   bg: gradient('#451a03', '#78350f'), trim: '#fbbf24', glow: '#fbbf24' },
  { name: 'Kristall',  sprite: 'crystal', bg: gradient('#0f172a', '#312e81'), trim: '#818cf8', glow: '#818cf8' },
  { name: 'Torii',     sprite: 'torii',   bg: gradient('#fef3c7', '#fb7185'), trim: '#7f1d1d' },
  { name: 'Auge',      sprite: 'eye',     bg: gradient('#0b1120', '#0e7490'), trim: '#38bdf8', glow: '#38bdf8' },
  { name: 'Schwert',   sprite: 'sword',   bg: gradient('#1e293b', '#475569'), trim: '#e2e8f0' },
]

const PIXEL_PALETTE = ['#e11d48', '#f59e0b', '#10b981', '#0ea5e9', '#8b5cf6', '#ec4899']

// ---- Crystal+ collection ----------------------------------------------------
// Hand-placed 10x16 pixel art. At this size smooth gradients turn to mush, so
// every pixel is set explicitly: each string is one row, each character a
// palette key.

const pixelMap = (rows: string[], palette: Record<string, string>): Painter => (ctx) => {
  rows.forEach((row, y) => {
    for (let x = 0; x < row.length; x++) {
      ctx.fillStyle = palette[row[x]] ?? palette['.']
      ctx.fillRect(x, y, 1, 1)
    }
  })
}

const PLUS_CAPES: { name: string; rows: string[]; palette: Record<string, string> }[] = [
  {
    name: 'Kristallsplitter',
    rows: [
      'tttttttttt', 't........t', 't...wb...t', 't..wabb..t',
      't..aabbc.t', 't.waabbcct', 't.aaabbcct', 't.aaabbcct',
      't..aabbc.t', 't..aabbc.t', 't...abc..t', 't...abc..t',
      't....b...t', 't........t', 't........t', 'tttttttttt',
    ],
    palette: { '.': '#0b1020', t: '#5b8af5', w: '#ffffff', a: '#bfe3ff', b: '#6fb6ff', c: '#2f6fd6' },
  },
  {
    name: 'Void',
    rows: [
      '..........', '.w.....g..', '......mm..', '.....mm...',
      '.g...mm...', '......mm.w', '..........', '..w.......',
      '......g...', '.g........', '....w.....', '........g.',
      '.w........', '......w...', '..g.......', '........w.',
    ],
    palette: { '.': '#05050a', w: '#ffffff', g: '#8a8aa8', m: '#e6e6f0' },
  },
  {
    name: 'Obsidian Gold',
    rows: [
      'yyyyyyyyyy', 'y........y', 'y........y', 'y...oo...y',
      'y..oyyo..y', 'y.oyYYyo.y', 'y.oyYYyo.y', 'y..oyyo..y',
      'y...oo...y', 'y........y', 'y........y', 'y.yyyyyy.y',
      'y........y', 'y.yyyyyy.y', 'y........y', 'yyyyyyyyyy',
    ],
    palette: { '.': '#0f0d0a', y: '#d4a02a', o: '#8a5a12', Y: '#ffe08a' },
  },
  {
    name: 'Polarlicht',
    rows: [
      '..........', '..g.......', '.gg....v..', '.gG...vv..',
      'gGG..vVv..', 'gGg..vVv..', '.gG.vVvv..', '.gGgvVv...',
      '..gGVv....', '..gGv.....', '...gg.....', '....g.....',
      '..........', 'ssssssssss', 'sSsssSssss', 'ssssssssss',
    ],
    palette: { '.': '#07121a', g: '#1f9e6e', G: '#5ef0b0', v: '#5b52c7', V: '#a79bff', s: '#c9d6e3', S: '#ffffff' },
  },
  {
    name: 'Terminal',
    rows: [
      ',,,,,,,,,,', '..........', ',G,,,,,,,,', '..G.......',
      ',G,,GGG,,,', '..........', ',,,,,,,,,,', '.gggg.....',
      ',,,,,,,,,,', '.ggggggg..', ',,,,,,,,,,', '.ggg......',
      ',,,,,,,,,,', '.G........', ',G,,,,,,,,', '..........',
    ],
    palette: { '.': '#020805', ',': '#062012', G: '#4ade80', g: '#15803d' },
  },
  {
    name: 'Sakura',
    rows: [
      '..........', '..pP......', '.pPp..bb..', '..p..bb...',
      '....bb.pP.', '...bb.pPp.', '..bb...p..', '.bb.......',
      'bb..pP....', 'b..pPp....', '....p.....', '........p.',
      '.......pPp', '........p.', '..p.......', '.pPp......',
    ],
    palette: { '.': '#2a0f28', p: '#f472b6', P: '#ffd1e8', b: '#6b3a2a' },
  },
  {
    name: 'Blaupause',
    rows: [
      'L..L..L..L', 'L..L..L..L', 'LLLLwwLLLL', 'L..w..w..L',
      'L.w.L..w.L', 'Lw..L...wL', 'w.LLLLLL.w', 'Lw..L...wL',
      'L.w.L..w.L', 'L..w..w..L', 'LLLLwwLLLL', 'L..L..L..L',
      'L..L..L..L', 'LLLLLLLLLL', 'L..L..L..L', 'L..L..L..L',
    ],
    palette: { '.': '#0a2344', L: '#1d4f86', w: '#e0f2ff' },
  },
  {
    name: 'Nebel',
    rows: [
      '....w.....', '.pp.......', 'pPPp...c..', 'pPPPp.cCc.',
      '.pPPp.cCCc', '..pp..cCc.', '......c..w', '.w..pp....',
      '...pPPp...', '..pPMPPp..', '..pPPPPp..', '...pPPp...',
      '....pp..c.', '.c......cC', 'cCc....w.c', '.c........',
    ],
    palette: { '.': '#0a0716', p: '#5b2a8c', P: '#b57cff', M: '#ffffff', c: '#0e6b7a', C: '#5ee7f5', w: '#ffffff' },
  },
]

// Pixel characters and meme symbols. Characters are Crystal's own designs and
// the meme motifs are generic (Moai, skull, "GG"): no licensed characters or
// meme artwork. Rows shorter than 16 are padded with background.
type PixelArt = { name: string; rows: string[]; palette: Record<string, string>; glow?: string }
const art = (a: PixelArt): Painter =>
  pixelMap([...a.rows, ...Array(Math.max(0, CAPE_H - a.rows.length)).fill('..........')], a.palette)

const ANIME_CHARACTERS: PixelArt[] = [
  {
    name: 'Neko-chan',
    rows: [
      'h........h', 'hh......hh', 'hhhhhhhhhh', 'hhhhhhhhhh',
      'hhsssssshh', 'hsEesseEsh', 'hseesseesh', 'hbssssssbh',
      'hsssmmsssh', 'h.ssssss.h', '...ssss...', '..pppppp..',
      '.pppppppp.', '.ppwppwpp.', '.pppppppp.',
    ],
    palette: { '.': '#fce7f3', h: '#3b2a4a', s: '#ffe4d1', e: '#1e1b4b', E: '#ffffff', b: '#fb7185', m: '#7f1d1d', p: '#a78bfa', w: '#ffffff' },
  },
  {
    name: 'Kitsune',
    rows: [
      'w........w', 'ww......ww', 'wrw....wrw', 'wrrwwwwrrw',
      'wwwwwwwwww', 'wrrwwwwrrw', 'wkrwwwwrkw', 'wwwwwwwwww',
      '.wwwwwwww.', '.wwwrrwww.', '..wwwwww..', '...wkkw...',
      '....ww....',
    ],
    palette: { '.': '#1c1917', w: '#f5f5f4', r: '#dc2626', k: '#0c0a09' },
  },
  {
    name: 'Oni',
    rows: [
      '.y......y.', '.yy....yy.', '..yrrrry..', '.rrrrrrrr.',
      'rRkkrrkkRr', 'rRrwrrwrRr', 'rrrrrrrrrr', 'rrrRrrRrrr',
      'rkkkkkkkkr', 'rkwkwwkwkr', 'rkkkkkkkkr', '.rrrrrrrr.',
      '..rrrrrr..',
    ],
    palette: { '.': '#0f0f14', y: '#fde68a', r: '#b91c1c', R: '#ef4444', k: '#000000', w: '#ffffff' },
    glow: '#ef4444',
  },
  {
    name: 'Onigiri',
    rows: [
      '..........', '..........', '....ww....', '...wwww...',
      '..wwwwww..', '..wewwew..', '.wbwwwwbw.', '.wwwwwwww.',
      'wwwnnnnwww', 'wwnnnnnnww', 'wwnnnnnnww', 'WWnnnnnnWW',
    ],
    palette: { '.': '#bae6fd', w: '#ffffff', W: '#e5e7eb', n: '#14532d', e: '#111111', b: '#fda4af' },
  },
  {
    name: 'Mahou Stern',
    rows: [
      's........s', '....y.....', '...yyy....', 'yyyyYyyyy.',
      '.yyYYYyy..', '..yyyyy...', '..yy.yy...', '.y.....y..',
      '....p.....', '....p...s.', '....p.....', '.s..p.....',
      '....p.....', '...ppp....', '....p....s',
    ],
    palette: { '.': '#312e81', y: '#fde047', Y: '#fef9c3', p: '#f9a8d4', s: '#e0e7ff' },
    glow: '#fde047',
  },
  {
    name: 'Schweißtropfen',
    rows: [
      '..........', '.......d..', '......dDd.', '......ddd.',
      '..........', '.ee....ee.', '..e....e..', '..........',
      '..........', '...mmmm...',
    ],
    palette: { '.': '#fef3c7', e: '#111111', d: '#38bdf8', D: '#e0f2fe', m: '#111111' },
  },
]

const MEME_CAPES: PixelArt[] = [
  {
    name: 'Moai',
    rows: [
      '..........', '...gggg...', '..gGGGGg..', '..gGGGGg..',
      '..kkkkkg..', '..gGkGGg..', '..gGkGGg..', '..gGGkGg..',
      '..gGGGGg..', '..gkkkkg..', '..gGGGGg..', '..gggggg..',
      '..gGGGGg..', '..gGGGGg..', 'llllllllll', 'llllllllll',
    ],
    palette: { '.': '#7dd3fc', g: '#57534e', G: '#78716c', k: '#292524', l: '#4d7c0f' },
  },
  {
    name: 'Skull',
    rows: [
      '..........', '..........', '..wwwwww..', '.wwwwwwww.',
      '.wwwwwwww.', '.wkkwwkkw.', '.wkkwwkkw.', '.wwwwwwww.',
      '.wwwkkwww.', '..wwwwww..', '..wkwkwk..', '..wwwwww..',
    ],
    palette: { '.': '#0a0a0a', w: '#f5f5f4', k: '#0a0a0a' },
  },
  {
    name: 'Stonks',
    rows: [
      '..........', '......gggg', '........gg', '.......g.g',
      '......g..g', '.....g....', '....g.....', '...g......',
      '..g.......', '.g........', 'g.........', '..........',
      '..........', '..........', 'llllllllll',
    ],
    palette: { '.': '#0b1220', g: '#22c55e', l: '#334155' },
    glow: '#22c55e',
  },
  {
    name: 'GG',
    rows: [
      '..........', '..........', '..........', '..........',
      '..........', '.www..www.', '.w....w...', '.w.w..w.w.',
      '.w.w..w.w.', '.www..www.', '..........', '.wwwwwwww.',
    ],
    palette: { '.': '#111827', w: '#facc15' },
    glow: '#facc15',
  },
  {
    name: 'W',
    rows: [
      '..........', '..........', '..........', '..........',
      'g........g', 'g........g', 'g...gg...g', '.g.g..g.g.',
      '.gg....gg.', '.g......g.',
    ],
    palette: { '.': '#052e16', g: '#4ade80' },
    glow: '#4ade80',
  },
  {
    name: 'L',
    rows: [
      '..........', '..........', '..........', '..rr......',
      '..rr......', '..rr......', '..rr......', '..rr......',
      '..rr......', '..rrrrrr..', '..rrrrrr..',
    ],
    palette: { '.': '#1f0a0a', r: '#ef4444' },
  },
  {
    name: 'UwU',
    rows: [
      '..........', '..........', '..........', '.k.k..k.k.',
      '.k.k..k.k.', '..k....k..', '..........', '..k.kk.k..',
      '...k..k...', '..........', '.b......b.',
    ],
    palette: { '.': '#f5d0fe', k: '#3b0764', b: '#f472b6' },
  },
  {
    name: 'Cool Shades',
    rows: [
      '..........', '..yyyyyy..', '.yyyyyyyy.', 'yyyyyyyyyy',
      'kkkkkkkkkk', 'ykwkyykwky', 'yykkyykkyy', 'yyyyyyyyyy',
      'yykyyyykyy', 'yyykkkkyyy', '.yyyyyyyy.', '..yyyyyy..',
    ],
    palette: { '.': '#18181b', y: '#facc15', k: '#000000', w: '#ffffff' },
  },
]


// Team capes: only for ranks from Media upwards (not Crystal+ or Member).
// Each rank has its own colours and symbol; 'Crystal Team' is shared by all.
const TEAM_CAPES: { name: string; rows: string[]; palette: Record<string, string>; glow: string }[] = [
  {
    name: 'Crystal Team',
    rows: [
      'gggggggggg', 'g........g', 'g...ww...g', 'g..wabb..g',
      'g..aabbc.g', 'g.waabbccg', 'g..aabbc.g', 'g...abc..g',
      'g....b...g', 'g........g', 'g.tt.ttt.g', 'g..t.t...g',
      'g..t.tt..g', 'g..t.ttt.g', 'g........g', 'gggggggggg',
    ],
    palette: { '.': '#0b1020', g: '#ffd54d', w: '#ffffff', a: '#bfe3ff', b: '#6fb6ff', c: '#2f6fd6', t: '#ffd54d' },
    glow: '#ffd54d',
  },
  {
    name: 'Owner Krone',
    rows: [
      'oooooooooo', 'o........o', 'o........o', 'o.y..y..yo',
      'o.yy.yy.yo', 'o.yyyyyyyo', 'o.yryybyyo', 'o.yyyyyyyo',
      'o.kkkkkkko', 'o........o', 'o........o', 'o...yy...o',
      'o..y..y..o', 'o...yy...o', 'o........o', 'oooooooooo',
    ],
    palette: { '.': '#1a1206', o: '#ff7a45', y: '#ffd54d', r: '#f5455b', b: '#5b8af5', k: '#b8860b' },
    glow: '#ffb03a',
  },
  {
    name: 'Admin Schild',
    rows: [
      'rrrrrrrrrr', 'r........r', 'r.ssssss.r', 'r.swwwws.r',
      'r.swsssw.r', 'r.swsssw.r', 'r.swwwws.r', 'r.swsssw.r',
      'r.swsssw.r', 'r..ssss..r', 'r...ss...r', 'r........r',
      'r.o.o.o.or', 'r........r', 'r........r', 'rrrrrrrrrr',
    ],
    palette: { '.': '#1a0608', r: '#f5455b', s: '#f57c3d', w: '#ffffff', o: '#f5455b' },
    glow: '#f5455b',
  },
  {
    name: 'Staff Haken',
    rows: [
      'mmmmmmmmmm', 'm........m', 'm.ssssss.m', 'm.s....s.m',
      'm.s...ws.m', 'm.s..w.s.m', 'm.sw.w.s.m', 'm.s.w..s.m',
      'm.s....s.m', 'm..s..s..m', 'm...ss...m', 'm........m',
      'm.m.m.m.mm', 'm........m', 'm........m', 'mmmmmmmmmm',
    ],
    palette: { '.': '#04140e', m: '#34d399', s: '#5bf5c9', w: '#ffffff' },
    glow: '#34d399',
  },
  {
    name: 'Developer Code',
    rows: [
      'pppppppppp', 'p........p', 'p........p', 'p..w..w..p',
      'p.w...w..p', 'pw...w...p', 'p.w..w...p', 'p..ww..w.p',
      'p...w...wp', 'p...w..w.p', 'p..w..w..p', 'p........p',
      'p.gg.g.g.p', 'p........p', 'p........p', 'pppppppppp',
    ],
    palette: { '.': '#0f0620', p: '#a35bf5', w: '#e9d5ff', g: '#7c3df5' },
    glow: '#a35bf5',
  },
  {
    name: 'Media Play',
    rows: [
      'kkkkkkkkkk', 'k........k', 'k.rrrrrr.k', 'k.rwrrrr.k',
      'k.rwwrrr.k', 'k.rwwwrr.k', 'k.rwwwwr.k', 'k.rwwwrr.k',
      'k.rwwrrr.k', 'k.rwrrrr.k', 'k.rrrrrr.k', 'k........k',
      'k.p.p.p.pk', 'k........k', 'k........k', 'kkkkkkkkkk',
    ],
    palette: { '.': '#1a0610', k: '#f56ba0', r: '#f5455b', w: '#ffffff', p: '#f56ba0' },
    glow: '#f56ba0',
  },
]

export const BUILTIN_CAPES: CapeDef[] = [
  ...HD_ART.map((a, i): CapeDef => ({
    id: `art-${i}`, name: a.name, category: 'art', paint: a.paint, glow: a.glow, hd: 16, requiredRank: 'crystal_plus',
  })),
  ...TEAM_CAPES.map((c, i): CapeDef => ({
    id: `team-${i}`, name: c.name, category: 'team', paint: pixelMap(c.rows, c.palette), glow: c.glow, requiredRank: 'media',
  })),
  ...PLUS_CAPES.map((c, i): CapeDef => ({
    id: `plus-${i}`, name: c.name, category: 'plus', paint: pixelMap(c.rows, c.palette), requiredRank: 'crystal_plus',
  })),
  ...EMBLEMS.map((e, i): CapeDef => ({
    id: `emblem-${i}`,
    name: e.name,
    category: 'emblem',
    paint: emblemCape(e.bg, SPRITES[e.sprite], e.trim),
    glow: e.glow,
  })),
  ...ANIME.map((a, i): CapeDef => ({
    id: `anime-${i}`, name: a.name, category: 'anime', paint: a.paint, glow: a.glow,
  })),
  // Appended after the existing ids so equipped capes keep their index.
  ...ANIME_CHARACTERS.map((a, i): CapeDef => ({
    id: `anime-${ANIME.length + i}`, name: a.name, category: 'anime', paint: art(a), glow: a.glow,
  })),
  ...INTERNET.map((n, i): CapeDef => ({
    id: `internet-${i}`, name: n.name, category: 'internet', paint: n.paint, glow: n.glow,
  })),
  ...MEME_CAPES.map((m, i): CapeDef => ({
    id: `internet-${INTERNET.length + i}`, name: m.name, category: 'internet', paint: art(m), glow: m.glow,
  })),
  ...THEMED.map((t, i): CapeDef => ({
    id: `themed-${i}`, name: t.name, category: 'themed', paint: t.paint, glow: t.glow,
  })),
  ...SOLID_COLORS.map(([name, color], i): CapeDef => ({
    id: `solid-${i}`, name, category: 'solid', paint: solid(color),
  })),
  ...GRADIENT_PAIRS.map(([name, a, b], i): CapeDef => ({
    id: `gradient-${i}`, name, category: 'gradient', paint: gradient(a, b),
  })),
  ...PATTERNS.map((p, i): CapeDef => ({
    id: `pattern-${i}`, name: p.name, category: 'pattern', paint: p.paint,
  })),
  ...Array.from({ length: 8 }, (_, i): CapeDef => ({
    id: `pixel-${i}`, name: `Pixel #${i + 1}`, category: 'pixel',
    paint: pixelNoise(i * 977, PIXEL_PALETTE, 8),
  })),
  ...NEONS.map((n, i): CapeDef => ({
    id: `neon-${i}`, name: n.name, category: 'neon', paint: neon('#0a0a0f', n.glow), glow: n.glow,
  })),
]

export const CAPE_CATEGORIES: { id: CapeCategory; label: string }[] = [
  { id: 'art', label: 'HD (Crystal+)' },
  { id: 'team', label: 'Team' },
  { id: 'plus', label: 'Crystal+' },
  { id: 'emblem', label: 'Embleme' },
  { id: 'anime', label: 'Anime-Stil' },
  { id: 'internet', label: 'Internet' },
  { id: 'themed', label: 'Themed' },
  { id: 'solid', label: 'Solid' },
  { id: 'gradient', label: 'Gradient' },
  { id: 'pattern', label: 'Pattern' },
  { id: 'pixel', label: 'Pixel' },
  { id: 'neon', label: 'Neon' },
]
