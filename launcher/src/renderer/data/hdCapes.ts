// Detailed capes drawn with smooth shapes, gradients and text instead of
// pixel grids. Painted at 16x the cape resolution (160x256 for the visible
// face), so they look like pictures on the character. All motifs are original.

type Painter = (ctx: CanvasRenderingContext2D, w: number, h: number) => void

/** Small deterministic random so a cape looks the same every time. */
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

function vertical(ctx: CanvasRenderingContext2D, w: number, h: number, stops: [number, string][]) {
  const g = ctx.createLinearGradient(0, 0, 0, h)
  for (const [at, color] of stops) g.addColorStop(at, color)
  ctx.fillStyle = g
  ctx.fillRect(0, 0, w, h)
}

function stars(ctx: CanvasRenderingContext2D, w: number, h: number, count: number, seed: number, maxY = 1) {
  const r = random(seed)
  for (let i = 0; i < count; i++) {
    const x = r() * w, y = r() * h * maxY, size = 0.6 + r() * 1.8
    ctx.globalAlpha = 0.4 + r() * 0.6
    ctx.fillStyle = '#ffffff'
    ctx.beginPath()
    ctx.arc(x, y, size, 0, Math.PI * 2)
    ctx.fill()
  }
  ctx.globalAlpha = 1
}

function text(ctx: CanvasRenderingContext2D, value: string, x: number, y: number, size: number, color: string,
              font = "'Segoe UI', system-ui, sans-serif", weight = 700, glow?: string) {
  ctx.font = `${weight} ${size}px ${font}`
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  if (glow) { ctx.shadowColor = glow; ctx.shadowBlur = size * 0.6 }
  ctx.fillStyle = color
  ctx.fillText(value, x, y)
  ctx.shadowBlur = 0
}

const lowCortisol: Painter = (ctx, w, h) => {
  vertical(ctx, w, h, [[0, '#b8c6ff'], [0.55, '#d9c8f5'], [1, '#f6d9e8']])
  // Soft clouds
  ctx.fillStyle = 'rgba(255,255,255,0.55)'
  for (const [cx, cy, r] of [[30, 200, 34], [70, 212, 40], [120, 198, 36], [150, 214, 30]]) {
    ctx.beginPath(); ctx.arc(cx, cy, r, 0, Math.PI * 2); ctx.fill()
  }
  // Crescent moon
  ctx.fillStyle = '#fffaf0'
  ctx.beginPath(); ctx.arc(w / 2, 62, 26, 0, Math.PI * 2); ctx.fill()
  ctx.fillStyle = '#c3c5f8'
  ctx.beginPath(); ctx.arc(w / 2 + 11, 55, 22, 0, Math.PI * 2); ctx.fill()
  stars(ctx, w, h, 26, 11, 0.45)
  text(ctx, 'low', w / 2, 118, 30, '#4b3f72', "Georgia, 'Times New Roman', serif", 600)
  text(ctx, 'cortisol', w / 2, 146, 30, '#4b3f72', "Georgia, 'Times New Roman', serif", 600)
  text(ctx, 'stay calm', w / 2, 176, 12, '#6f63a0', "'Segoe UI', sans-serif", 500)
}

const sunsetLofi: Painter = (ctx, w, h) => {
  vertical(ctx, w, h, [[0, '#2b1055'], [0.45, '#d53f8c'], [0.75, '#fb923c'], [1, '#fde68a']])
  // Striped sun
  ctx.save()
  ctx.beginPath(); ctx.arc(w / 2, 150, 50, 0, Math.PI * 2); ctx.clip()
  const sun = ctx.createLinearGradient(0, 100, 0, 200)
  sun.addColorStop(0, '#fff3b0'); sun.addColorStop(1, '#ff7a59')
  ctx.fillStyle = sun
  ctx.fillRect(0, 100, w, 100)
  ctx.fillStyle = '#d9478f'
  for (let y = 158; y < 200; y += 9) ctx.fillRect(0, y, w, 3 + (y - 158) / 10)
  ctx.restore()
  // Mountains
  ctx.fillStyle = '#3b0d4f'
  ctx.beginPath(); ctx.moveTo(0, 200); ctx.lineTo(40, 170); ctx.lineTo(75, 196); ctx.lineTo(115, 160); ctx.lineTo(w, 198); ctx.lineTo(w, h); ctx.lineTo(0, h); ctx.fill()
  ctx.fillStyle = '#240635'
  ctx.beginPath(); ctx.moveTo(0, 226); ctx.lineTo(55, 204); ctx.lineTo(100, 230); ctx.lineTo(140, 212); ctx.lineTo(w, 228); ctx.lineTo(w, h); ctx.lineTo(0, h); ctx.fill()
  stars(ctx, w, h, 30, 5, 0.3)
}

const sakuraNight: Painter = (ctx, w, h) => {
  vertical(ctx, w, h, [[0, '#0f1030'], [1, '#2a1b4d']])
  stars(ctx, w, h, 40, 21)
  // Branch
  ctx.strokeStyle = '#3a2418'
  ctx.lineCap = 'round'
  ctx.lineWidth = 8
  ctx.beginPath(); ctx.moveTo(-10, 40); ctx.quadraticCurveTo(70, 70, 150, 150); ctx.stroke()
  ctx.lineWidth = 4
  ctx.beginPath(); ctx.moveTo(60, 62); ctx.quadraticCurveTo(80, 30, 120, 22); ctx.stroke()
  ctx.beginPath(); ctx.moveTo(105, 104); ctx.quadraticCurveTo(70, 130, 60, 170); ctx.stroke()
  // Blossoms
  const r = random(7)
  const blossom = (x: number, y: number, s: number) => {
    for (let p = 0; p < 5; p++) {
      const a = (p / 5) * Math.PI * 2 + 0.3
      ctx.fillStyle = p % 2 ? '#fbcfe8' : '#f9a8d4'
      ctx.beginPath(); ctx.ellipse(x + Math.cos(a) * s, y + Math.sin(a) * s, s * 0.9, s * 0.6, a, 0, Math.PI * 2); ctx.fill()
    }
    ctx.fillStyle = '#fde68a'
    ctx.beginPath(); ctx.arc(x, y, s * 0.35, 0, Math.PI * 2); ctx.fill()
  }
  for (const [x, y] of [[40, 55], [75, 70], [110, 105], [140, 140], [95, 30], [125, 22], [70, 150], [58, 172], [20, 44]]) {
    blossom(x, y, 6 + r() * 4)
  }
  // Falling petals
  for (let i = 0; i < 22; i++) {
    ctx.fillStyle = 'rgba(249,168,212,0.85)'
    ctx.beginPath(); ctx.ellipse(r() * w, 120 + r() * 130, 3, 1.8, r() * Math.PI, 0, Math.PI * 2); ctx.fill()
  }
}

const galaxy: Painter = (ctx, w, h) => {
  ctx.fillStyle = '#05030f'
  ctx.fillRect(0, 0, w, h)
  const blob = (x: number, y: number, r: number, color: string) => {
    const g = ctx.createRadialGradient(x, y, 0, x, y, r)
    g.addColorStop(0, color); g.addColorStop(1, 'rgba(0,0,0,0)')
    ctx.fillStyle = g
    ctx.fillRect(0, 0, w, h)
  }
  ctx.globalCompositeOperation = 'lighter'
  blob(50, 80, 90, 'rgba(124,58,237,0.7)')
  blob(120, 150, 100, 'rgba(236,72,153,0.55)')
  blob(70, 210, 80, 'rgba(59,130,246,0.6)')
  ctx.globalCompositeOperation = 'source-over'
  stars(ctx, w, h, 90, 99)
  // A bright star with flare
  ctx.fillStyle = '#ffffff'
  ctx.beginPath(); ctx.arc(104, 72, 3, 0, Math.PI * 2); ctx.fill()
  ctx.fillRect(84, 71, 40, 1.5); ctx.fillRect(103, 52, 1.5, 40)
}

const oceanWaves: Painter = (ctx, w, h) => {
  vertical(ctx, w, h, [[0, '#e0f7ff'], [0.35, '#7dd3fc'], [1, '#0c4a6e']])
  const colors = ['#38bdf8', '#0ea5e9', '#0284c7', '#0369a1', '#075985']
  colors.forEach((color, i) => {
    const base = 90 + i * 34
    ctx.fillStyle = color
    ctx.beginPath(); ctx.moveTo(0, h)
    for (let x = 0; x <= w; x += 4) ctx.lineTo(x, base + Math.sin(x / 18 + i * 1.3) * 8)
    ctx.lineTo(w, h); ctx.fill()
    ctx.strokeStyle = 'rgba(255,255,255,0.35)'
    ctx.lineWidth = 2
    ctx.beginPath()
    for (let x = 0; x <= w; x += 4) ctx.lineTo(x, base + Math.sin(x / 18 + i * 1.3) * 8)
    ctx.stroke()
  })
  ctx.fillStyle = '#fff7d6'
  ctx.beginPath(); ctx.arc(118, 42, 16, 0, Math.PI * 2); ctx.fill()
}

const aurora: Painter = (ctx, w, h) => {
  vertical(ctx, w, h, [[0, '#020617'], [1, '#0f172a']])
  stars(ctx, w, h, 60, 42)
  ctx.globalCompositeOperation = 'lighter'
  const ribbon = (offset: number, color: string) => {
    for (let x = 0; x < w; x += 2) {
      const y = 70 + Math.sin(x / 25 + offset) * 30 + offset * 12
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
  // Snowy hills
  ctx.fillStyle = '#e2e8f0'
  ctx.beginPath(); ctx.moveTo(0, 230); ctx.quadraticCurveTo(60, 200, 100, 225); ctx.quadraticCurveTo(135, 205, w, 222); ctx.lineTo(w, h); ctx.lineTo(0, h); ctx.fill()
}

const mainCharacter: Painter = (ctx, w, h) => {
  vertical(ctx, w, h, [[0, '#111827'], [1, '#1f2937']])
  const g = ctx.createRadialGradient(w / 2, 90, 5, w / 2, 90, 120)
  g.addColorStop(0, 'rgba(250,204,21,0.55)'); g.addColorStop(1, 'rgba(250,204,21,0)')
  ctx.fillStyle = g
  ctx.fillRect(0, 0, w, h)
  // Spotlight cone
  ctx.fillStyle = 'rgba(255,255,255,0.08)'
  ctx.beginPath(); ctx.moveTo(w / 2 - 12, 0); ctx.lineTo(w / 2 + 12, 0); ctx.lineTo(w, 250); ctx.lineTo(0, 250); ctx.fill()
  text(ctx, 'MAIN', w / 2, 110, 34, '#fde047', "'Segoe UI', sans-serif", 900, '#facc15')
  text(ctx, 'CHARACTER', w / 2, 142, 21, '#ffffff', "'Segoe UI', sans-serif", 800)
  text(ctx, '★ ★ ★', w / 2, 172, 14, '#fde047')
}

const touchGrass: Painter = (ctx, w, h) => {
  vertical(ctx, w, h, [[0, '#7dd3fc'], [0.6, '#bae6fd'], [0.6, '#4ade80'], [1, '#15803d']])
  ctx.fillStyle = '#fef08a'
  ctx.beginPath(); ctx.arc(130, 36, 18, 0, Math.PI * 2); ctx.fill()
  const r = random(3)
  ctx.strokeStyle = '#166534'
  ctx.lineWidth = 2
  for (let i = 0; i < 120; i++) {
    const x = r() * w, y = 160 + r() * 96
    ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(x + (r() - 0.5) * 6, y - 6 - r() * 10); ctx.stroke()
  }
  text(ctx, 'touch', w / 2, 76, 28, '#14532d', "'Segoe UI', sans-serif", 800)
  text(ctx, 'grass', w / 2, 104, 28, '#14532d', "'Segoe UI', sans-serif", 800)
}

const cyberCity: Painter = (ctx, w, h) => {
  vertical(ctx, w, h, [[0, '#0b0221'], [0.7, '#3b0764'], [1, '#be185d']])
  ctx.fillStyle = 'rgba(244,114,182,0.9)'
  ctx.beginPath(); ctx.arc(w / 2, 120, 38, 0, Math.PI * 2); ctx.fill()
  const r = random(8)
  let x = 0
  while (x < w) {
    const bw = 14 + r() * 20, bh = 60 + r() * 120
    ctx.fillStyle = '#12051f'
    ctx.fillRect(x, h - bh, bw, bh)
    for (let wy = h - bh + 6; wy < h - 6; wy += 9) {
      for (let wx = x + 3; wx < x + bw - 4; wx += 6) {
        if (r() > 0.55) {
          ctx.fillStyle = r() > 0.5 ? '#22d3ee' : '#f0abfc'
          ctx.fillRect(wx, wy, 3, 4)
        }
      }
    }
    x += bw + 2
  }
}

const koiPond: Painter = (ctx, w, h) => {
  vertical(ctx, w, h, [[0, '#0e7490'], [1, '#164e63']])
  ctx.strokeStyle = 'rgba(255,255,255,0.18)'
  ctx.lineWidth = 2
  for (const [cx, cy, rr] of [[40, 60, 18], [40, 60, 30], [120, 190, 16], [120, 190, 28]]) {
    ctx.beginPath(); ctx.arc(cx, cy, rr, 0, Math.PI * 2); ctx.stroke()
  }
  const koi = (x: number, y: number, angle: number, body: string, spot: string) => {
    ctx.save(); ctx.translate(x, y); ctx.rotate(angle)
    ctx.fillStyle = body
    ctx.beginPath(); ctx.ellipse(0, 0, 30, 11, 0, 0, Math.PI * 2); ctx.fill()
    ctx.beginPath(); ctx.moveTo(-26, 0); ctx.lineTo(-44, -12); ctx.lineTo(-40, 0); ctx.lineTo(-44, 12); ctx.fill()
    ctx.fillStyle = spot
    ctx.beginPath(); ctx.ellipse(6, -2, 9, 6, 0.3, 0, Math.PI * 2); ctx.fill()
    ctx.beginPath(); ctx.ellipse(-12, 3, 6, 4, 0, 0, Math.PI * 2); ctx.fill()
    ctx.restore()
  }
  koi(90, 90, 0.6, '#fff7ed', '#f97316')
  koi(70, 170, 3.6, '#fb923c', '#fff7ed')
  // Lily pads
  ctx.fillStyle = '#16a34a'
  for (const [px, py, pr] of [[130, 50, 16], [30, 220, 20], [140, 230, 12]]) {
    ctx.beginPath(); ctx.moveTo(px, py); ctx.arc(px, py, pr, 0.3, Math.PI * 2 - 0.1); ctx.fill()
  }
  ctx.fillStyle = '#f9a8d4'
  ctx.beginPath(); ctx.arc(30, 220, 5, 0, Math.PI * 2); ctx.fill()
}

export const HD_ART: { name: string; paint: Painter; glow?: string }[] = [
  { name: 'Low Cortisol', paint: lowCortisol },
  { name: 'Sunset Lofi', paint: sunsetLofi, glow: '#fb923c' },
  { name: 'Sakura Night', paint: sakuraNight, glow: '#f9a8d4' },
  { name: 'Galaxy', paint: galaxy, glow: '#8b5cf6' },
  { name: 'Ocean Waves', paint: oceanWaves },
  { name: 'Aurora', paint: aurora, glow: '#34d399' },
  { name: 'Main Character', paint: mainCharacter, glow: '#facc15' },
  { name: 'Touch Grass', paint: touchGrass },
  { name: 'Cyber City', paint: cyberCity, glow: '#f0abfc' },
  { name: 'Koi Pond', paint: koiPond },
]
