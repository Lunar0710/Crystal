// The large HD cape collection (Crystal+): 20 drawn motifs, each in 10 colour
// moods, so 200 capes that share a style but never look like copies. Every
// motif is a painter in 160x256 space (like the other HD capes) that takes a
// palette and a seed; the seed moves stars, hills, bubbles and so on, so two
// capes of the same motif also differ in layout.

type Painter = (ctx: CanvasRenderingContext2D, w: number, h: number) => void

interface Palette {
  name: string
  /** Sky or background, top to bottom. */
  sky: [string, string, string]
  /** Main shapes (hills, waves, buildings), far to near. */
  land: [string, string, string]
  /** Highlights: sun, stars, neon, glow. */
  accent: string
  /** Secondary highlight. */
  accent2: string
  /** Light detail (snow, foam, text). */
  light: string
}

const PALETTES: Palette[] = [
  { name: 'Sonnenuntergang', sky: ['#2b1055', '#d53f8c', '#fbbf24'], land: ['#7c2d5b', '#4a1942', '#2a0f2e'], accent: '#fde68a', accent2: '#fb7185', light: '#fff7ed' },
  { name: 'Ozean', sky: ['#082f49', '#0369a1', '#7dd3fc'], land: ['#0e7490', '#155e75', '#083344'], accent: '#e0f2fe', accent2: '#22d3ee', light: '#f0f9ff' },
  { name: 'Wald', sky: ['#052e16', '#166534', '#bbf7d0'], land: ['#15803d', '#14532d', '#052e16'], accent: '#fef9c3', accent2: '#86efac', light: '#f0fdf4' },
  { name: 'Mitternacht', sky: ['#020617', '#1e1b4b', '#312e81'], land: ['#1e293b', '#0f172a', '#020617'], accent: '#e0e7ff', accent2: '#818cf8', light: '#f8fafc' },
  { name: 'Kirschblüte', sky: ['#fdf2f8', '#fbcfe8', '#f9a8d4'], land: ['#f472b6', '#db2777', '#9d174d'], accent: '#ffffff', accent2: '#fde68a', light: '#fff1f2' },
  { name: 'Lava', sky: ['#1c0a00', '#7c2d12', '#ea580c'], land: ['#431407', '#290a02', '#120401'], accent: '#fde047', accent2: '#f97316', light: '#fef3c7' },
  { name: 'Eis', sky: ['#f0f9ff', '#bae6fd', '#7dd3fc'], land: ['#e0f2fe', '#93c5fd', '#60a5fa'], accent: '#ffffff', accent2: '#a5f3fc', light: '#ffffff' },
  { name: 'Neon', sky: ['#0b0221', '#3b0764', '#701a75'], land: ['#1e1b4b', '#12051f', '#05010c'], accent: '#22d3ee', accent2: '#f0abfc', light: '#fdf4ff' },
  { name: 'Wüste', sky: ['#fef3c7', '#fcd34d', '#fb923c'], land: ['#d97706', '#b45309', '#78350f'], accent: '#fff7ed', accent2: '#fde68a', light: '#fffbeb' },
  { name: 'Lavendel', sky: ['#faf5ff', '#e9d5ff', '#c4b5fd'], land: ['#a78bfa', '#7c3aed', '#4c1d95'], accent: '#fef9c3', accent2: '#f5d0fe', light: '#ffffff' },
]

function random(seed: number) {
  let s = seed >>> 0
  return () => {
    s = (s + 0x6d2b79f5) >>> 0
    let t = s
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

function sky(ctx: CanvasRenderingContext2D, w: number, h: number, p: Palette, split = 1) {
  const g = ctx.createLinearGradient(0, 0, 0, h * split)
  g.addColorStop(0, p.sky[0]); g.addColorStop(0.55, p.sky[1]); g.addColorStop(1, p.sky[2])
  ctx.fillStyle = g
  ctx.fillRect(0, 0, w, h)
}

function stars(ctx: CanvasRenderingContext2D, w: number, h: number, color: string, r: () => number, count: number, maxY = 1) {
  ctx.fillStyle = color
  for (let i = 0; i < count; i++) {
    ctx.globalAlpha = 0.35 + r() * 0.65
    ctx.beginPath(); ctx.arc(r() * w, r() * h * maxY, 0.5 + r() * 1.5, 0, Math.PI * 2); ctx.fill()
  }
  ctx.globalAlpha = 1
}

function ridge(ctx: CanvasRenderingContext2D, w: number, h: number, base: number, amp: number, color: string, r: () => number, jag = false) {
  ctx.fillStyle = color
  ctx.beginPath(); ctx.moveTo(0, h)
  const steps = jag ? 7 : 24
  let y = base
  for (let i = 0; i <= steps; i++) {
    const x = (i / steps) * w
    y = jag ? base - r() * amp : base + Math.sin(i * 0.9 + r() * 0.6) * amp * 0.4
    ctx.lineTo(x, y)
  }
  ctx.lineTo(w, h); ctx.closePath(); ctx.fill()
}

function glowDisc(ctx: CanvasRenderingContext2D, x: number, y: number, rad: number, color: string) {
  const g = ctx.createRadialGradient(x, y, 0, x, y, rad * 2.4)
  g.addColorStop(0, color); g.addColorStop(0.4, color + '66'); g.addColorStop(1, color + '00')
  ctx.fillStyle = g
  ctx.fillRect(x - rad * 2.4, y - rad * 2.4, rad * 4.8, rad * 4.8)
  ctx.fillStyle = color
  ctx.beginPath(); ctx.arc(x, y, rad, 0, Math.PI * 2); ctx.fill()
}

type Motif = { name: string; paint: (ctx: CanvasRenderingContext2D, w: number, h: number, p: Palette, r: () => number) => void }

const MOTIFS: Motif[] = [
  { name: 'Horizont', paint: (ctx, w, h, p, r) => {
    sky(ctx, w, h, p)
    glowDisc(ctx, w / 2, 120, 30, p.accent)
    ridge(ctx, w, h, 150, 30, p.land[0], r); ridge(ctx, w, h, 185, 26, p.land[1], r); ridge(ctx, w, h, 220, 20, p.land[2], r)
  } },
  { name: 'Gipfel', paint: (ctx, w, h, p, r) => {
    sky(ctx, w, h, p); stars(ctx, w, h, p.light, r, 30, 0.4)
    ridge(ctx, w, h, 170, 90, p.land[0], r, true); ridge(ctx, w, h, 205, 60, p.land[1], r, true); ridge(ctx, w, h, 235, 30, p.land[2], r, true)
  } },
  { name: 'Wellen', paint: (ctx, w, h, p, r) => {
    sky(ctx, w, h, p)
    glowDisc(ctx, 40 + r() * 80, 50, 14, p.accent)
    for (let i = 0; i < 5; i++) {
      const base = 110 + i * 32, phase = r() * 6
      ctx.fillStyle = p.land[Math.min(2, Math.floor(i / 2))]
      ctx.globalAlpha = 0.75 + i * 0.05
      ctx.beginPath(); ctx.moveTo(0, h)
      for (let x = 0; x <= w; x += 4) ctx.lineTo(x, base + Math.sin(x / 16 + phase) * 7)
      ctx.lineTo(w, h); ctx.fill()
      ctx.globalAlpha = 1
      ctx.strokeStyle = p.light + '88'; ctx.lineWidth = 1.5
      ctx.beginPath(); for (let x = 0; x <= w; x += 4) ctx.lineTo(x, base + Math.sin(x / 16 + phase) * 7); ctx.stroke()
    }
  } },
  { name: 'Galaxie', paint: (ctx, w, h, p, r) => {
    ctx.fillStyle = p.land[2]; ctx.fillRect(0, 0, w, h)
    ctx.globalCompositeOperation = 'lighter'
    for (let i = 0; i < 4; i++) {
      const x = r() * w, y = r() * h, rad = 60 + r() * 60, color = [p.sky[1], p.sky[2], p.accent2, p.land[0]][i]
      const g = ctx.createRadialGradient(x, y, 0, x, y, rad)
      g.addColorStop(0, color + 'aa'); g.addColorStop(1, color + '00')
      ctx.fillStyle = g; ctx.fillRect(0, 0, w, h)
    }
    ctx.globalCompositeOperation = 'source-over'
    stars(ctx, w, h, p.light, r, 90)
  } },
  { name: 'Polarlicht', paint: (ctx, w, h, p, r) => {
    ctx.fillStyle = p.land[2]; ctx.fillRect(0, 0, w, h)
    stars(ctx, w, h, p.light, r, 50)
    ctx.globalCompositeOperation = 'lighter'
    for (const [k, color] of [[0, p.accent2], [2.1, p.sky[2]], [4.2, p.accent]] as [number, string][]) {
      const off = r() * 3
      for (let x = 0; x < w; x += 2) {
        const y = 80 + Math.sin(x / 24 + k + off) * 28 + k * 10
        const g = ctx.createLinearGradient(0, y - 60, 0, y + 8)
        g.addColorStop(0, color + '00'); g.addColorStop(1, color + '88')
        ctx.fillStyle = g; ctx.fillRect(x, y - 60, 2, 68)
      }
    }
    ctx.globalCompositeOperation = 'source-over'
    ridge(ctx, w, h, 225, 18, p.land[0], r)
  } },
  { name: 'Skyline', paint: (ctx, w, h, p, r) => {
    sky(ctx, w, h, p)
    glowDisc(ctx, w / 2, 110, 36, p.accent2)
    let x = 0
    while (x < w) {
      const bw = 14 + r() * 20, bh = 60 + r() * 120
      ctx.fillStyle = p.land[2]; ctx.fillRect(x, h - bh, bw, bh)
      for (let wy = h - bh + 6; wy < h - 6; wy += 9) for (let wx = x + 3; wx < x + bw - 4; wx += 6) {
        if (r() > 0.55) { ctx.fillStyle = r() > 0.5 ? p.accent : p.accent2; ctx.fillRect(wx, wy, 3, 4) }
      }
      x += bw + 2
    }
  } },
  { name: 'Mondnacht', paint: (ctx, w, h, p, r) => {
    sky(ctx, w, h, p); stars(ctx, w, h, p.light, r, 45, 0.7)
    const mx = 50 + r() * 60
    glowDisc(ctx, mx, 70, 22, p.light)
    ctx.fillStyle = p.sky[0]; ctx.beginPath(); ctx.arc(mx + 10, 64, 20, 0, Math.PI * 2); ctx.fill()
    ridge(ctx, w, h, 215, 22, p.land[1], r); ridge(ctx, w, h, 238, 12, p.land[2], r)
  } },
  { name: 'Wolken', paint: (ctx, w, h, p, r) => {
    sky(ctx, w, h, p)
    for (let i = 0; i < 16; i++) {
      ctx.fillStyle = (i % 2 ? p.light : p.sky[2]) + 'cc'
      const cx = r() * w, cy = 40 + r() * 200, s = 14 + r() * 22
      for (let k = 0; k < 4; k++) { ctx.beginPath(); ctx.arc(cx + (k - 1.5) * s * 0.7, cy + (k % 2) * -s * 0.3, s * (0.7 + (k % 2) * 0.3), 0, Math.PI * 2); ctx.fill() }
    }
  } },
  { name: 'Blasen', paint: (ctx, w, h, p, r) => {
    sky(ctx, w, h, p)
    for (let i = 0; i < 40; i++) {
      const x = r() * w, y = r() * h, rad = 3 + r() * 16
      ctx.strokeStyle = p.light + 'bb'; ctx.lineWidth = 1.5
      ctx.beginPath(); ctx.arc(x, y, rad, 0, Math.PI * 2); ctx.stroke()
      ctx.fillStyle = p.light + '99'; ctx.beginPath(); ctx.arc(x - rad * 0.35, y - rad * 0.35, rad * 0.22, 0, Math.PI * 2); ctx.fill()
    }
  } },
  { name: 'Kristalle', paint: (ctx, w, h, p, r) => {
    sky(ctx, w, h, p)
    for (let i = 0; i < 14; i++) {
      const cx = r() * w, base = 150 + r() * 110, tall = 40 + r() * 90, wide = 10 + r() * 14
      const g = ctx.createLinearGradient(cx - wide, base - tall, cx + wide, base)
      g.addColorStop(0, p.light); g.addColorStop(0.5, p.accent2); g.addColorStop(1, p.land[1])
      ctx.fillStyle = g; ctx.globalAlpha = 0.85
      ctx.beginPath(); ctx.moveTo(cx, base - tall); ctx.lineTo(cx + wide, base - tall * 0.25); ctx.lineTo(cx, base); ctx.lineTo(cx - wide, base - tall * 0.25); ctx.closePath(); ctx.fill()
    }
    ctx.globalAlpha = 1
  } },
  { name: 'Neon Raster', paint: (ctx, w, h, p, r) => {
    sky(ctx, w, h, p, 0.55)
    ctx.fillStyle = p.land[2]; ctx.fillRect(0, 143, w, h - 143)
    glowDisc(ctx, w / 2, 110, 34, p.accent2)
    ctx.strokeStyle = p.accent; ctx.lineWidth = 1.4
    for (let i = -8; i <= 8; i++) { ctx.beginPath(); ctx.moveTo(w / 2, 143); ctx.lineTo(w / 2 + i * 40, h); ctx.stroke() }
    for (let k = 0; k < 8; k++) { const y = 143 + Math.pow(k / 7, 2) * 113; ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(w, y); ctx.stroke() }
    stars(ctx, w, h, p.light, r, 20, 0.5)
  } },
  { name: 'Schneefall', paint: (ctx, w, h, p, r) => {
    sky(ctx, w, h, p)
    ridge(ctx, w, h, 200, 30, p.land[0], r); ridge(ctx, w, h, 228, 18, p.light, r)
    ctx.fillStyle = p.light
    for (let i = 0; i < 70; i++) { ctx.globalAlpha = 0.5 + r() * 0.5; ctx.beginPath(); ctx.arc(r() * w, r() * h, 0.8 + r() * 2.2, 0, Math.PI * 2); ctx.fill() }
    ctx.globalAlpha = 1
  } },
  { name: 'Glühwürmchen', paint: (ctx, w, h, p, r) => {
    ctx.fillStyle = p.land[2]; ctx.fillRect(0, 0, w, h)
    ridge(ctx, w, h, 180, 40, p.land[1], r)
    for (let i = 0; i < 40; i++) {
      const x = r() * w, y = 40 + r() * 200, rad = 1 + r() * 2
      const g = ctx.createRadialGradient(x, y, 0, x, y, rad * 5)
      g.addColorStop(0, p.accent); g.addColorStop(1, p.accent + '00')
      ctx.fillStyle = g; ctx.fillRect(x - rad * 5, y - rad * 5, rad * 10, rad * 10)
    }
  } },
  { name: 'Streifen', paint: (ctx, w, h, p, r) => {
    const colors = [p.sky[0], p.sky[1], p.sky[2], p.land[0], p.land[1], p.accent2]
    const band = 18 + Math.floor(r() * 14)
    ctx.save(); ctx.translate(w / 2, h / 2); ctx.rotate(-0.5 + r())
    for (let i = -20; i < 20; i++) { ctx.fillStyle = colors[(i + 40) % colors.length]; ctx.fillRect(-300, i * band, 600, band) }
    ctx.restore()
  } },
  { name: 'Orbs', paint: (ctx, w, h, p, r) => {
    ctx.fillStyle = p.land[2]; ctx.fillRect(0, 0, w, h)
    for (let i = 0; i < 9; i++) {
      const x = r() * w, y = r() * h, rad = 20 + r() * 50, color = [p.sky[1], p.sky[2], p.accent, p.accent2][i % 4]
      const g = ctx.createRadialGradient(x - rad * 0.3, y - rad * 0.3, rad * 0.1, x, y, rad)
      g.addColorStop(0, p.light + 'ee'); g.addColorStop(0.3, color); g.addColorStop(1, color + '00')
      ctx.fillStyle = g; ctx.beginPath(); ctx.arc(x, y, rad, 0, Math.PI * 2); ctx.fill()
    }
  } },
  { name: 'Regen', paint: (ctx, w, h, p, r) => {
    ctx.fillStyle = p.land[2]; ctx.fillRect(0, 0, w, h)
    for (let i = 0; i < 22; i++) {
      const x = r() * w, y = 90 + r() * 160, rad = 8 + r() * 14, color = [p.accent, p.accent2, p.sky[2]][i % 3]
      const g = ctx.createRadialGradient(x, y, 0, x, y, rad)
      g.addColorStop(0, color + 'aa'); g.addColorStop(1, color + '00')
      ctx.fillStyle = g; ctx.fillRect(x - rad, y - rad, rad * 2, rad * 2)
    }
    ctx.strokeStyle = p.light + '55'; ctx.lineWidth = 1
    for (let i = 0; i < 40; i++) { const x = r() * w, y = r() * h, l = 8 + r() * 20; ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(x, y + l); ctx.stroke() }
    ctx.fillStyle = p.land[1]; ctx.fillRect(w / 2 - 3, 0, 6, h); ctx.fillRect(0, h / 2 - 3, w, 6)
  } },
  { name: 'Blüten', paint: (ctx, w, h, p, r) => {
    sky(ctx, w, h, p)
    ctx.strokeStyle = p.land[2]; ctx.lineWidth = 6; ctx.lineCap = 'round'
    ctx.beginPath(); ctx.moveTo(-10, 40 + r() * 40); ctx.quadraticCurveTo(80, 70, 170, 140 + r() * 40); ctx.stroke()
    for (let i = 0; i < 12; i++) {
      const x = r() * w, y = 30 + r() * 170, s = 5 + r() * 5
      for (let k = 0; k < 5; k++) { const a = (k / 5) * Math.PI * 2; ctx.fillStyle = k % 2 ? p.accent2 : p.land[0]; ctx.beginPath(); ctx.ellipse(x + Math.cos(a) * s, y + Math.sin(a) * s, s * 0.9, s * 0.6, a, 0, Math.PI * 2); ctx.fill() }
      ctx.fillStyle = p.accent; ctx.beginPath(); ctx.arc(x, y, s * 0.35, 0, Math.PI * 2); ctx.fill()
    }
  } },
  { name: 'Dünen', paint: (ctx, w, h, p, r) => {
    sky(ctx, w, h, p)
    glowDisc(ctx, 30 + r() * 100, 60, 20, p.accent)
    for (let i = 0; i < 4; i++) {
      ctx.fillStyle = [p.land[0], p.land[1], p.land[2], p.land[2]][i]
      const base = 140 + i * 30
      ctx.beginPath(); ctx.moveTo(0, h); ctx.lineTo(0, base)
      ctx.bezierCurveTo(w * 0.3, base - 30 - r() * 20, w * 0.6, base + 20, w, base - 10 - r() * 20)
      ctx.lineTo(w, h); ctx.fill()
    }
  } },
  { name: 'Kosmos', paint: (ctx, w, h, p, r) => {
    ctx.fillStyle = p.land[2]; ctx.fillRect(0, 0, w, h)
    stars(ctx, w, h, p.light, r, 70)
    const px = 40 + r() * 80, py = 90 + r() * 80, pr = 28 + r() * 16
    const g = ctx.createRadialGradient(px - pr * 0.4, py - pr * 0.4, 2, px, py, pr)
    g.addColorStop(0, p.sky[2]); g.addColorStop(1, p.land[0])
    ctx.fillStyle = g; ctx.beginPath(); ctx.arc(px, py, pr, 0, Math.PI * 2); ctx.fill()
    ctx.strokeStyle = p.accent + 'cc'; ctx.lineWidth = 3
    ctx.beginPath(); ctx.ellipse(px, py, pr * 1.8, pr * 0.45, -0.35, 0, Math.PI * 2); ctx.stroke()
    glowDisc(ctx, r() * w, 200 + r() * 40, 6, p.accent2)
  } },
  { name: 'Prisma', paint: (ctx, w, h, p, r) => {
    ctx.fillStyle = p.land[2]; ctx.fillRect(0, 0, w, h)
    const colors = [p.sky[0], p.sky[1], p.sky[2], p.land[0], p.land[1], p.accent2, p.accent]
    for (let i = 0; i < 18; i++) {
      ctx.fillStyle = colors[Math.floor(r() * colors.length)]
      ctx.globalAlpha = 0.55 + r() * 0.4
      ctx.beginPath(); ctx.moveTo(r() * w, r() * h); ctx.lineTo(r() * w, r() * h); ctx.lineTo(r() * w, r() * h); ctx.closePath(); ctx.fill()
    }
    ctx.globalAlpha = 1
  } },
]

/** All 200 capes, motif by motif. Order and names are stable so equipped capes keep their id. */
export const HD_COLLECTION: { name: string; paint: Painter; glow: string }[] = MOTIFS.flatMap((motif, m) =>
  PALETTES.map((palette, p) => ({
    name: `${motif.name} · ${palette.name}`,
    glow: palette.accent2,
    paint: (ctx: CanvasRenderingContext2D, w: number, h: number) => {
      ctx.save()
      ctx.scale(w / 160, h / 256)
      motif.paint(ctx, 160, 256, palette, random(1000 + m * 97 + p * 13))
      ctx.restore()
    },
  })))
