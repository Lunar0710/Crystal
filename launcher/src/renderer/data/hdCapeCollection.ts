// The large HD cape collection (Nexora+): 200 capes, each one different.
// Every cape is built from three layers (background, centrepiece, foreground)
// and its own colour: no two capes share the same three layers, and every cape
// gets its own hue, so the collection has no recoloured copies. All painters
// draw in 160x256 space, like the other HD capes.

type Painter = (ctx: CanvasRenderingContext2D, w: number, h: number) => void
type Rand = () => number

interface Palette {
  /** Background, top to bottom. */
  sky: [string, string, string]
  /** Foreground shapes, far to near. */
  land: [string, string, string]
  /** Main highlight (sun, gem, neon). */
  accent: string
  /** Second highlight. */
  accent2: string
  /** Light detail (stars, foam, windows). */
  light: string
}

type Layer = (ctx: CanvasRenderingContext2D, p: Palette, r: Rand) => void

const W = 160
const H = 256

function random(seed: number): Rand {
  let s = seed >>> 0
  return () => {
    s = (s + 0x6d2b79f5) >>> 0
    let t = s
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

const hsl = (h: number, s: number, l: number) => `hsl(${((h % 360) + 360) % 360} ${Math.min(100, s)}% ${l}%)`

/** A palette around one hue; `mood` shifts it between dark night tones and bright day tones. */
function palette(hue: number, mood: number): Palette {
  const dark = mood < 0.5
  const sat = 55 + mood * 35
  return {
    sky: dark
      ? [hsl(hue + 20, sat, 6), hsl(hue, sat, 18), hsl(hue - 25, sat, 34)]
      : [hsl(hue + 25, sat, 30), hsl(hue, sat, 55), hsl(hue - 30, sat + 5, 78)],
    land: dark
      ? [hsl(hue - 10, sat - 15, 22), hsl(hue - 5, sat - 20, 13), hsl(hue, sat - 25, 6)]
      : [hsl(hue - 15, sat - 10, 40), hsl(hue - 10, sat - 10, 27), hsl(hue, sat - 15, 15)],
    accent: hsl(hue + 180, 90, dark ? 70 : 85),
    accent2: hsl(hue + 40, 95, dark ? 62 : 70),
    light: hsl(hue + 180, 60, 95),
  }
}

// ---------------------------------------------------------------- helpers

function glow(ctx: CanvasRenderingContext2D, x: number, y: number, rad: number, color: string) {
  const g = ctx.createRadialGradient(x, y, 0, x, y, rad)
  g.addColorStop(0, color)
  g.addColorStop(1, 'transparent')
  ctx.save()
  ctx.globalAlpha = 0.55
  ctx.fillStyle = g
  ctx.fillRect(x - rad, y - rad, rad * 2, rad * 2)
  ctx.restore()
}

function dots(ctx: CanvasRenderingContext2D, color: string, r: Rand, count: number, maxY: number) {
  ctx.fillStyle = color
  for (let i = 0; i < count; i++) {
    ctx.globalAlpha = 0.3 + r() * 0.7
    ctx.beginPath(); ctx.arc(r() * W, r() * maxY, 0.4 + r() * 1.4, 0, Math.PI * 2); ctx.fill()
  }
  ctx.globalAlpha = 1
}

function star(ctx: CanvasRenderingContext2D, x: number, y: number, outer: number, inner: number, points: number) {
  ctx.beginPath()
  for (let i = 0; i < points * 2; i++) {
    const a = (i / (points * 2)) * Math.PI * 2 - Math.PI / 2
    const rad = i % 2 === 0 ? outer : inner
    ctx.lineTo(x + Math.cos(a) * rad, y + Math.sin(a) * rad)
  }
  ctx.closePath()
}

function ground(ctx: CanvasRenderingContext2D, color: string, points: [number, number][]) {
  ctx.fillStyle = color
  ctx.beginPath(); ctx.moveTo(0, H)
  points.forEach(([x, y]) => ctx.lineTo(x, y))
  ctx.lineTo(W, H); ctx.closePath(); ctx.fill()
}

// ---------------------------------------------------------------- backgrounds

const BACKGROUNDS: { name: string; paint: Layer }[] = [
  { name: 'Himmel', paint: (ctx, p, r) => {
    const g = ctx.createLinearGradient(0, 0, 0, H)
    g.addColorStop(0, p.sky[0]); g.addColorStop(0.55, p.sky[1]); g.addColorStop(1, p.sky[2])
    ctx.fillStyle = g; ctx.fillRect(0, 0, W, H)
    dots(ctx, p.light, r, 25, H * 0.5)
  } },
  { name: 'Leuchten', paint: (ctx, p) => {
    const g = ctx.createRadialGradient(W / 2, H * 0.4, 5, W / 2, H * 0.4, H * 0.75)
    g.addColorStop(0, p.sky[2]); g.addColorStop(0.5, p.sky[1]); g.addColorStop(1, p.sky[0])
    ctx.fillStyle = g; ctx.fillRect(0, 0, W, H)
  } },
  { name: 'Streifen', paint: (ctx, p, r) => {
    ctx.fillStyle = p.sky[0]; ctx.fillRect(0, 0, W, H)
    ctx.save(); ctx.translate(W / 2, H / 2); ctx.rotate(-0.5 + r() * 0.3)
    for (let i = -12; i < 12; i++) {
      ctx.fillStyle = i % 2 ? p.sky[1] : p.sky[2]
      ctx.globalAlpha = 0.35 + (Math.abs(i) % 3) * 0.15
      ctx.fillRect(i * 22, -H, 11, H * 2)
    }
    ctx.restore(); ctx.globalAlpha = 1
  } },
  { name: 'Raster', paint: (ctx, p) => {
    ctx.fillStyle = p.sky[0]; ctx.fillRect(0, 0, W, H)
    const horizon = H * 0.55
    const g = ctx.createLinearGradient(0, 0, 0, horizon)
    g.addColorStop(0, p.sky[0]); g.addColorStop(1, p.sky[1])
    ctx.fillStyle = g; ctx.fillRect(0, 0, W, horizon)
    ctx.strokeStyle = p.accent2; ctx.lineWidth = 1; ctx.globalAlpha = 0.7
    for (let i = 0; i < 12; i++) {
      const y = horizon + Math.pow(i / 11, 1.8) * (H - horizon)
      ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(W, y); ctx.stroke()
    }
    for (let i = -8; i <= 8; i++) {
      ctx.beginPath(); ctx.moveTo(W / 2 + i * 6, horizon); ctx.lineTo(W / 2 + i * 40, H); ctx.stroke()
    }
    ctx.globalAlpha = 1
  } },
  { name: 'Sternenfeld', paint: (ctx, p, r) => {
    ctx.fillStyle = p.sky[0]; ctx.fillRect(0, 0, W, H)
    for (let i = 0; i < 3; i++) glow(ctx, r() * W, r() * H, 50 + r() * 40, p.sky[1 + (i % 2)])
    dots(ctx, p.light, r, 110, H)
  } },
  { name: 'Polarlicht', paint: (ctx, p, r) => {
    ctx.fillStyle = p.sky[0]; ctx.fillRect(0, 0, W, H)
    for (let band = 0; band < 4; band++) {
      ctx.strokeStyle = band % 2 ? p.accent2 : p.accent
      ctx.globalAlpha = 0.18; ctx.lineWidth = 14 + band * 4
      ctx.beginPath()
      const base = 40 + band * 30 + r() * 20, shift = r() * 6
      for (let x = -10; x <= W + 10; x += 8) ctx.lineTo(x, base + Math.sin(x * 0.05 + band + shift) * 18)
      ctx.stroke()
    }
    ctx.globalAlpha = 1
    dots(ctx, p.light, r, 40, H * 0.6)
  } },
  { name: 'Zweiklang', paint: (ctx, p, r) => {
    const cut = H * (0.35 + r() * 0.3)
    ctx.fillStyle = p.sky[1]; ctx.fillRect(0, 0, W, H)
    ctx.fillStyle = p.sky[0]
    ctx.beginPath(); ctx.moveTo(0, 0); ctx.lineTo(W, 0); ctx.lineTo(W, cut - 40); ctx.lineTo(0, cut + 40); ctx.closePath(); ctx.fill()
    ctx.strokeStyle = p.accent; ctx.lineWidth = 2
    ctx.beginPath(); ctx.moveTo(W, cut - 40); ctx.lineTo(0, cut + 40); ctx.stroke()
  } },
  { name: 'Ringe', paint: (ctx, p, r) => {
    ctx.fillStyle = p.sky[0]; ctx.fillRect(0, 0, W, H)
    const cx = W * (0.3 + r() * 0.4), cy = H * (0.3 + r() * 0.3)
    for (let i = 14; i > 0; i--) {
      ctx.fillStyle = i % 2 ? p.sky[1] : p.sky[0]
      ctx.beginPath(); ctx.arc(cx, cy, i * 14, 0, Math.PI * 2); ctx.fill()
    }
  } },
]

// ---------------------------------------------------------------- centrepieces

const CENTERS: { name: string; paint: Layer }[] = [
  { name: 'Sonne', paint: (ctx, p, r) => {
    const y = 70 + r() * 30
    glow(ctx, W / 2, y, 70, p.accent)
    ctx.fillStyle = p.accent; ctx.beginPath(); ctx.arc(W / 2, y, 28, 0, Math.PI * 2); ctx.fill()
    ctx.fillStyle = p.sky[1]
    for (let i = 0; i < 4; i++) ctx.fillRect(W / 2 - 30, y + 6 + i * 6, 60, 2 + i * 0.6)
  } },
  { name: 'Planet', paint: (ctx, p, r) => {
    const x = 50 + r() * 60, y = 70 + r() * 30
    ctx.fillStyle = p.accent2; ctx.beginPath(); ctx.arc(x, y, 24, 0, Math.PI * 2); ctx.fill()
    ctx.fillStyle = 'rgba(0,0,0,0.25)'; ctx.beginPath(); ctx.arc(x + 8, y + 6, 22, 0, Math.PI * 2); ctx.fill()
    ctx.strokeStyle = p.accent; ctx.lineWidth = 3
    ctx.beginPath(); ctx.ellipse(x, y, 44, 10, -0.35, 0, Math.PI * 2); ctx.stroke()
  } },
  { name: 'Kristall', paint: (ctx, p) => {
    const cx = W / 2, cy = 90
    glow(ctx, cx, cy, 60, p.accent)
    const pts: [number, number][] = [[cx, cy - 50], [cx + 26, cy - 10], [cx + 16, cy + 40], [cx - 16, cy + 40], [cx - 26, cy - 10]]
    ctx.fillStyle = p.accent2
    ctx.beginPath(); pts.forEach(([x, y]) => ctx.lineTo(x, y)); ctx.closePath(); ctx.fill()
    ctx.fillStyle = p.light; ctx.globalAlpha = 0.6
    ctx.beginPath(); ctx.moveTo(cx, cy - 50); ctx.lineTo(cx + 26, cy - 10); ctx.lineTo(cx, cy); ctx.closePath(); ctx.fill()
    ctx.globalAlpha = 0.3
    ctx.beginPath(); ctx.moveTo(cx, cy); ctx.lineTo(cx - 16, cy + 40); ctx.lineTo(cx + 16, cy + 40); ctx.closePath(); ctx.fill()
    ctx.globalAlpha = 1
  } },
  { name: 'Mond', paint: (ctx, p, r) => {
    const x = 60 + r() * 40, y = 60 + r() * 30
    glow(ctx, x, y, 55, p.light)
    ctx.fillStyle = p.light; ctx.beginPath(); ctx.arc(x, y, 26, 0, Math.PI * 2); ctx.fill()
    ctx.fillStyle = p.sky[0]; ctx.beginPath(); ctx.arc(x + 12, y - 6, 22, 0, Math.PI * 2); ctx.fill()
  } },
  { name: 'Auge', paint: (ctx, p) => {
    const cx = W / 2, cy = 95
    ctx.fillStyle = p.light
    ctx.beginPath(); ctx.moveTo(cx - 50, cy); ctx.quadraticCurveTo(cx, cy - 40, cx + 50, cy); ctx.quadraticCurveTo(cx, cy + 40, cx - 50, cy); ctx.fill()
    ctx.fillStyle = p.accent2; ctx.beginPath(); ctx.arc(cx, cy, 17, 0, Math.PI * 2); ctx.fill()
    ctx.fillStyle = p.sky[0]; ctx.beginPath(); ctx.arc(cx, cy, 8, 0, Math.PI * 2); ctx.fill()
    ctx.fillStyle = p.light; ctx.beginPath(); ctx.arc(cx + 5, cy - 5, 3, 0, Math.PI * 2); ctx.fill()
  } },
  { name: 'Blitz', paint: (ctx, p, r) => {
    const x = 60 + r() * 40
    glow(ctx, x + 10, 100, 70, p.accent)
    ctx.fillStyle = p.accent
    ctx.beginPath(); ctx.moveTo(x + 18, 30); ctx.lineTo(x - 8, 105); ctx.lineTo(x + 10, 105); ctx.lineTo(x - 2, 175); ctx.lineTo(x + 34, 88); ctx.lineTo(x + 14, 88); ctx.lineTo(x + 32, 30); ctx.closePath(); ctx.fill()
  } },
  { name: 'Blüte', paint: (ctx, p, r) => {
    const cx = W / 2, cy = 90, petals = 5 + Math.floor(r() * 4)
    for (let i = 0; i < petals; i++) {
      ctx.save(); ctx.translate(cx, cy); ctx.rotate((i / petals) * Math.PI * 2)
      ctx.fillStyle = i % 2 ? p.accent2 : p.accent
      ctx.beginPath(); ctx.ellipse(0, -24, 11, 24, 0, 0, Math.PI * 2); ctx.fill()
      ctx.restore()
    }
    ctx.fillStyle = p.light; ctx.beginPath(); ctx.arc(cx, cy, 9, 0, Math.PI * 2); ctx.fill()
  } },
  { name: 'Stern', paint: (ctx, p, r) => {
    const cx = W / 2, cy = 90, points = 4 + Math.floor(r() * 5)
    glow(ctx, cx, cy, 75, p.accent)
    ctx.fillStyle = p.accent; star(ctx, cx, cy, 48, 14, points); ctx.fill()
    ctx.fillStyle = p.light; star(ctx, cx, cy, 20, 7, points); ctx.fill()
  } },
  { name: 'Spirale', paint: (ctx, p, r) => {
    const cx = W / 2, cy = 95, turns = 3 + r() * 2
    ctx.strokeStyle = p.accent; ctx.lineWidth = 4; ctx.lineCap = 'round'
    ctx.beginPath()
    for (let a = 0; a < turns * Math.PI * 2; a += 0.1) {
      const rad = 3 + a * 3.2
      ctx.lineTo(cx + Math.cos(a) * rad, cy + Math.sin(a) * rad)
    }
    ctx.stroke()
    glow(ctx, cx, cy, 30, p.accent2)
  } },
  { name: 'Baum', paint: (ctx, p, r) => {
    const cx = W / 2, base = 170
    ctx.strokeStyle = p.land[2]; ctx.lineCap = 'round'
    const branch = (x: number, y: number, len: number, angle: number, depth: number) => {
      if (depth === 0) {
        ctx.fillStyle = r() > 0.5 ? p.accent : p.accent2
        ctx.beginPath(); ctx.arc(x, y, 5 + r() * 4, 0, Math.PI * 2); ctx.fill()
        return
      }
      const x2 = x + Math.cos(angle) * len, y2 = y + Math.sin(angle) * len
      ctx.lineWidth = depth * 1.6
      ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(x2, y2); ctx.stroke()
      branch(x2, y2, len * 0.72, angle - 0.45 - r() * 0.2, depth - 1)
      branch(x2, y2, len * 0.72, angle + 0.45 + r() * 0.2, depth - 1)
    }
    branch(cx, base, 38, -Math.PI / 2, 5)
  } },
]

// ---------------------------------------------------------------- foregrounds

const FOREGROUNDS: { name: string; paint: Layer }[] = [
  { name: 'Gipfel', paint: (ctx, p, r) => {
    for (let layer = 0; layer < 3; layer++) {
      const base = 180 + layer * 22
      const pts: [number, number][] = []
      for (let i = 0; i <= 6; i++) pts.push([(i / 6) * W, base - (i % 2 ? 20 + r() * 40 : r() * 10)])
      ground(ctx, p.land[layer], pts)
      if (layer === 0) {
        ctx.fillStyle = p.light; ctx.globalAlpha = 0.8
        pts.forEach(([x, y], i) => {
          if (i % 2) { ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(x - 6, y + 9); ctx.lineTo(x + 6, y + 9); ctx.fill() }
        })
        ctx.globalAlpha = 1
      }
    }
  } },
  { name: 'Hügel', paint: (ctx, p, r) => {
    for (let layer = 0; layer < 3; layer++) {
      const base = 190 + layer * 20, phase = r() * 6
      const pts: [number, number][] = []
      for (let x = 0; x <= W; x += 8) pts.push([x, base + Math.sin(x * 0.03 + phase) * 12])
      ground(ctx, p.land[layer], pts)
    }
  } },
  { name: 'Wellen', paint: (ctx, p, r) => {
    for (let layer = 0; layer < 4; layer++) {
      const base = 185 + layer * 18, phase = r() * 6
      const pts: [number, number][] = []
      for (let x = 0; x <= W; x += 4) pts.push([x, base + Math.sin(x * 0.08 + phase) * 5])
      ground(ctx, p.land[Math.max(0, Math.min(2, layer - 1))], pts)
      ctx.strokeStyle = p.light; ctx.globalAlpha = 0.5; ctx.lineWidth = 1.2
      ctx.beginPath(); pts.forEach(([x, y]) => ctx.lineTo(x, y)); ctx.stroke(); ctx.globalAlpha = 1
    }
  } },
  { name: 'Skyline', paint: (ctx, p, r) => {
    for (let layer = 0; layer < 2; layer++) {
      let x = 0
      while (x < W) {
        const bw = 12 + r() * 18, bh = 40 + r() * (layer ? 50 : 80)
        const top = H - bh - layer * 4
        ctx.fillStyle = p.land[layer + 1]; ctx.fillRect(x, top, bw, bh + 10)
        if (layer === 1) {
          ctx.fillStyle = p.accent2
          for (let wy = top + 5; wy < H - 6; wy += 7) {
            for (let wx = x + 3; wx < x + bw - 3; wx += 5) {
              if (r() > 0.55) { ctx.globalAlpha = 0.5 + r() * 0.5; ctx.fillRect(wx, wy, 2, 3) }
            }
          }
          ctx.globalAlpha = 1
        }
        x += bw + 1
      }
    }
  } },
  { name: 'Wald', paint: (ctx, p, r) => {
    for (let layer = 0; layer < 3; layer++) {
      ctx.fillStyle = p.land[layer]
      const baseY = 200 + layer * 20
      ctx.fillRect(0, baseY, W, H - baseY)
      for (let x = -10; x < W + 10; x += 10 + r() * 8) {
        const th = 30 + r() * 30
        ctx.beginPath(); ctx.moveTo(x, baseY - th); ctx.lineTo(x - 9, baseY + 2); ctx.lineTo(x + 9, baseY + 2); ctx.closePath(); ctx.fill()
      }
    }
  } },
  { name: 'Dünen', paint: (ctx, p, r) => {
    for (let layer = 0; layer < 3; layer++) {
      const base = 195 + layer * 20, peak = r() * W
      const pts: [number, number][] = []
      for (let x = 0; x <= W; x += 6) pts.push([x, base - 22 * Math.exp(-Math.pow((x - peak) / 45, 2))])
      ground(ctx, p.land[layer], pts)
    }
  } },
  { name: 'Wolken', paint: (ctx, p, r) => {
    for (let layer = 0; layer < 3; layer++) {
      ctx.fillStyle = layer === 2 ? p.light : p.sky[2 - layer]
      ctx.globalAlpha = 0.55 + layer * 0.2
      const y = 200 + layer * 18
      for (let x = -10; x < W + 20; x += 18 + r() * 10) {
        ctx.beginPath(); ctx.arc(x, y, 14 + r() * 10, 0, Math.PI * 2); ctx.fill()
      }
      ctx.fillRect(0, y, W, H - y)
    }
    ctx.globalAlpha = 1
  } },
  { name: 'Scherben', paint: (ctx, p, r) => {
    for (let i = 0; i < 9; i++) {
      const x = r() * W, hgt = 30 + r() * 60, wid = 8 + r() * 14
      ctx.fillStyle = i % 2 ? p.accent2 : p.land[0]
      ctx.globalAlpha = 0.75
      ctx.beginPath(); ctx.moveTo(x, H - hgt); ctx.lineTo(x + wid, H); ctx.lineTo(x - wid, H); ctx.closePath(); ctx.fill()
    }
    ctx.globalAlpha = 1
  } },
]

// ---------------------------------------------------------------- the collection

export interface CapeRecipe { bg: number; center: number; fg: number; hue: number; mood: number; seed: number }

/**
 * 200 distinct recipes, fixed by a seed so every launcher shows the same capes
 * under the same ids. Each (background, centrepiece, foreground) triple is used
 * once; hues step by the golden angle so neighbours never share a colour.
 */
export function collectionRecipes(): CapeRecipe[] {
  const r = random(20260916)
  const triples: [number, number, number][] = []
  for (let b = 0; b < BACKGROUNDS.length; b++)
    for (let c = 0; c < CENTERS.length; c++)
      for (let f = 0; f < FOREGROUNDS.length; f++) triples.push([b, c, f])
  for (let i = triples.length - 1; i > 0; i--) {
    const j = Math.floor(r() * (i + 1))
    const tmp = triples[i]; triples[i] = triples[j]; triples[j] = tmp
  }
  return triples.slice(0, 200).map(([bg, center, fg], i) => ({
    bg, center, fg,
    hue: (i * 137.508) % 360,
    mood: (i * 0.3819660113) % 1,
    seed: 5000 + i * 7919,
  }))
}

/** Named after its three layers, which no other cape shares. */
function capeName(rec: CapeRecipe): string {
  return `${CENTERS[rec.center].name} · ${FOREGROUNDS[rec.fg].name} · ${BACKGROUNDS[rec.bg].name}`
}

/** All 200 capes. Order is stable so equipped capes keep their id. */
export const HD_COLLECTION: { name: string; paint: Painter; glow: string }[] = collectionRecipes().map(rec => {
  const pal = palette(rec.hue, rec.mood)
  return {
    name: capeName(rec),
    glow: pal.accent2,
    paint: (ctx: CanvasRenderingContext2D, w: number, h: number) => {
      ctx.save()
      ctx.scale(w / W, h / H)
      const r = random(rec.seed)
      BACKGROUNDS[rec.bg].paint(ctx, pal, r)
      CENTERS[rec.center].paint(ctx, pal, r)
      FOREGROUNDS[rec.fg].paint(ctx, pal, r)
      ctx.restore()
    },
  }
})
