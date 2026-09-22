// Animated HD capes (Nexora+). Each painter draws one moment of a loop:
// t runs from 0 to 1 and t = 1 looks exactly like t = 0, so the frames
// repeat seamlessly. Painted in the same 160x256 space as the still HD capes.

export type AnimatedPainter = (ctx: CanvasRenderingContext2D, w: number, h: number, t: number) => void

const TAU = Math.PI * 2

function random(seed: number) {
  let s = seed >>> 0
  return () => {
    s = (s + 0x6d2b79f5) >>> 0
    let x = s
    x = Math.imul(x ^ (x >>> 15), x | 1)
    x ^= x + Math.imul(x ^ (x >>> 7), x | 61)
    return ((x ^ (x >>> 14)) >>> 0) / 4294967296
  }
}

function vertical(ctx: CanvasRenderingContext2D, w: number, h: number, stops: [number, string][]) {
  const g = ctx.createLinearGradient(0, 0, 0, h)
  for (const [at, color] of stops) g.addColorStop(at, color)
  ctx.fillStyle = g
  ctx.fillRect(0, 0, w, h)
}

const rain: AnimatedPainter = (ctx, w, h, t) => {
  vertical(ctx, w, h, [[0, '#0f172a'], [1, '#1e3a5f']])
  const r = random(64)
  for (let i = 0; i < 26; i++) {
    const x = r() * w, y = 90 + r() * 160, rad = 6 + r() * 14, phase = r()
    const color = ['251,191,36', '244,114,182', '96,165,250'][i % 3]
    const pulse = 0.45 + 0.25 * Math.sin(TAU * (t + phase))
    const g = ctx.createRadialGradient(x, y, 0, x, y, rad)
    g.addColorStop(0, `rgba(${color},${pulse})`)
    g.addColorStop(1, `rgba(${color},0)`)
    ctx.fillStyle = g
    ctx.fillRect(x - rad, y - rad, rad * 2, rad * 2)
  }
  for (let i = 0; i < 34; i++) {
    const x = r() * w, start = r() * h, len = 8 + r() * 22, speed = 1 + Math.floor(r() * 2)
    const y = (start + t * h * speed) % (h + len)
    ctx.strokeStyle = 'rgba(226,232,240,0.28)'
    ctx.lineWidth = 1
    ctx.beginPath(); ctx.moveTo(x, y - len); ctx.lineTo(x, y); ctx.stroke()
    ctx.fillStyle = 'rgba(226,232,240,0.6)'
    ctx.beginPath(); ctx.ellipse(x, y, 1.8, 2.6, 0, 0, TAU); ctx.fill()
  }
  ctx.fillStyle = '#0b1120'
  ctx.fillRect(w / 2 - 3, 0, 6, h)
  ctx.fillRect(0, h / 2 - 3, w, 6)
}

const twinkle: AnimatedPainter = (ctx, w, h, t) => {
  ctx.fillStyle = '#05030f'
  ctx.fillRect(0, 0, w, h)
  ctx.globalCompositeOperation = 'lighter'
  const blob = (x: number, y: number, rad: number, color: string) => {
    const g = ctx.createRadialGradient(x, y, 0, x, y, rad)
    g.addColorStop(0, color); g.addColorStop(1, 'rgba(0,0,0,0)')
    ctx.fillStyle = g
    ctx.fillRect(0, 0, w, h)
  }
  blob(50 + 10 * Math.sin(TAU * t), 80, 90, 'rgba(124,58,237,0.7)')
  blob(120, 150 + 12 * Math.cos(TAU * t), 100, 'rgba(236,72,153,0.55)')
  blob(70, 210, 80 + 8 * Math.sin(TAU * t), 'rgba(59,130,246,0.6)')
  ctx.globalCompositeOperation = 'source-over'
  const r = random(99)
  for (let i = 0; i < 80; i++) {
    const x = r() * w, y = r() * h, size = 0.6 + r() * 1.8, phase = r()
    ctx.globalAlpha = Math.max(0.1, 0.55 + 0.45 * Math.sin(TAU * (t * (1 + (i % 2)) + phase)))
    ctx.fillStyle = '#ffffff'
    ctx.beginPath(); ctx.arc(x, y, size, 0, TAU); ctx.fill()
  }
  ctx.globalAlpha = 1
  const flare = 0.6 + 0.4 * Math.sin(TAU * t)
  ctx.fillStyle = `rgba(255,255,255,${flare})`
  ctx.beginPath(); ctx.arc(104, 72, 3, 0, TAU); ctx.fill()
  ctx.fillRect(104 - 20 * flare, 71, 40 * flare, 1.5)
  ctx.fillRect(103, 72 - 20 * flare, 1.5, 40 * flare)
}

const auroraFlow: AnimatedPainter = (ctx, w, h, t) => {
  vertical(ctx, w, h, [[0, '#020617'], [1, '#0f172a']])
  const r = random(42)
  for (let i = 0; i < 60; i++) {
    ctx.globalAlpha = 0.4 + r() * 0.6
    ctx.fillStyle = '#ffffff'
    ctx.beginPath(); ctx.arc(r() * w, r() * h, 0.6 + r() * 1.4, 0, TAU); ctx.fill()
  }
  ctx.globalAlpha = 1
  ctx.globalCompositeOperation = 'lighter'
  const ribbon = (offset: number, color: string) => {
    for (let x = 0; x < w; x += 2) {
      const y = 70 + Math.sin(x / 25 + offset + TAU * t) * 30 + offset * 12
      const g = ctx.createLinearGradient(0, y - 60, 0, y + 10)
      g.addColorStop(0, 'rgba(0,0,0,0)'); g.addColorStop(1, color)
      ctx.fillStyle = g
      ctx.fillRect(x, y - 60, 2, 70)
    }
  }
  ribbon(0, 'rgba(52,211,153,0.55)')
  ribbon(2.2, 'rgba(167,139,250,0.45)')
  ribbon(4.1, 'rgba(34,211,238,0.35)')
  ctx.globalCompositeOperation = 'source-over'
  ctx.fillStyle = '#e2e8f0'
  ctx.beginPath(); ctx.moveTo(0, 230); ctx.quadraticCurveTo(60, 200, 100, 225); ctx.quadraticCurveTo(135, 205, w, 222); ctx.lineTo(w, h); ctx.lineTo(0, h); ctx.fill()
}

const neonPulse: AnimatedPainter = (ctx, w, h, t) => {
  ctx.fillStyle = '#07060d'
  ctx.fillRect(0, 0, w, h)
  const pulse = 0.5 + 0.5 * Math.sin(TAU * t)
  const hue = (t * 360) % 360
  ctx.strokeStyle = `hsl(${hue}, 95%, 62%)`
  ctx.shadowColor = `hsl(${hue}, 95%, 62%)`
  ctx.shadowBlur = 10 + 14 * pulse
  ctx.lineWidth = 4
  ctx.strokeRect(14, 14, w - 28, h - 28)
  // Nexora diamond in the middle
  const cx = w / 2, cy = h / 2 - 16, s = 34 + 4 * pulse
  ctx.beginPath()
  ctx.moveTo(cx, cy - s); ctx.lineTo(cx + s * 0.8, cy); ctx.lineTo(cx, cy + s); ctx.lineTo(cx - s * 0.8, cy); ctx.closePath()
  ctx.stroke()
  ctx.beginPath(); ctx.moveTo(cx - s * 0.8, cy); ctx.lineTo(cx + s * 0.8, cy); ctx.stroke()
  ctx.shadowBlur = 8 + 10 * pulse
  ctx.fillStyle = `hsl(${(hue + 180) % 360}, 95%, 70%)`
  ctx.font = "800 22px 'Segoe UI', system-ui, sans-serif"
  ctx.textAlign = 'center'
  ctx.fillText('CRYSTAL', cx, h - 44)
  ctx.shadowBlur = 0
}

const lavaFlow: AnimatedPainter = (ctx, w, h, t) => {
  vertical(ctx, w, h, [[0, '#1c0a00'], [1, '#3b0d02']])
  for (let i = 0; i < 7; i++) {
    const y = ((i / 7 + 1 - t) % 1) * (h + 60) - 30
    const g = ctx.createLinearGradient(0, y - 30, 0, y + 30)
    g.addColorStop(0, 'rgba(249,115,22,0)')
    g.addColorStop(0.5, i % 2 ? 'rgba(250,204,21,0.75)' : 'rgba(239,68,68,0.8)')
    g.addColorStop(1, 'rgba(249,115,22,0)')
    ctx.fillStyle = g
    ctx.beginPath()
    ctx.moveTo(0, y)
    for (let x = 0; x <= w; x += 8) ctx.lineTo(x, y + Math.sin(x / 18 + TAU * t + i) * 8)
    ctx.lineTo(w, y + 30); ctx.lineTo(0, y + 30); ctx.closePath()
    ctx.fill()
  }
  const r = random(7)
  for (let i = 0; i < 18; i++) {
    const x = r() * w, base = r() * h
    const y = (base - t * h * (1 + (i % 2))) % h
    const yy = y < 0 ? y + h : y
    ctx.fillStyle = 'rgba(254,240,138,0.85)'
    ctx.beginPath(); ctx.arc(x, yy, 1.5 + r() * 1.5, 0, TAU); ctx.fill()
  }
}

const oceanLoop: AnimatedPainter = (ctx, w, h, t) => {
  vertical(ctx, w, h, [[0, '#e0f7ff'], [0.35, '#7dd3fc'], [1, '#0c4a6e']])
  ctx.fillStyle = '#fff7d6'
  ctx.beginPath(); ctx.arc(118, 42, 16, 0, TAU); ctx.fill()
  const colors = ['#38bdf8', '#0ea5e9', '#0284c7', '#0369a1', '#075985']
  colors.forEach((color, i) => {
    const base = 90 + i * 34
    const shift = TAU * t * (i % 2 ? -1 : 1)
    ctx.fillStyle = color
    ctx.beginPath(); ctx.moveTo(0, h)
    for (let x = 0; x <= w; x += 4) ctx.lineTo(x, base + Math.sin(x / 18 + i * 1.3 + shift) * 8)
    ctx.lineTo(w, h); ctx.fill()
    ctx.strokeStyle = 'rgba(255,255,255,0.35)'
    ctx.lineWidth = 2
    ctx.beginPath()
    for (let x = 0; x <= w; x += 4) ctx.lineTo(x, base + Math.sin(x / 18 + i * 1.3 + shift) * 8)
    ctx.stroke()
  })
}

export const ANIMATED_ART: { name: string; animate: AnimatedPainter; glow?: string }[] = [
  { name: 'Regenfenster', animate: rain, glow: '#60a5fa' },
  { name: 'Galaxie Funkeln', animate: twinkle, glow: '#8b5cf6' },
  { name: 'Polarlicht', animate: auroraFlow, glow: '#34d399' },
  { name: 'Neon Puls', animate: neonPulse, glow: '#e879f9' },
  { name: 'Lavastrom', animate: lavaFlow, glow: '#f97316' },
  { name: 'Wellengang', animate: oceanLoop, glow: '#38bdf8' },
]

/** Frames per loop and playback speed shared by every animated cape. */
export const ANIMATION_FRAMES = 16
export const ANIMATION_FPS = 10
