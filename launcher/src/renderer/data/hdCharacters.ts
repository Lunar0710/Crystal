// Character capes: original chibi-style characters drawn with smooth shapes
// at 160x256 (16x the cape face). None of them are based on existing
// franchises, so they can ship with the launcher.

type Painter = (ctx: CanvasRenderingContext2D, w: number, h: number) => void

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

function background(ctx: CanvasRenderingContext2D, w: number, h: number, top: string, bottom: string) {
  const g = ctx.createLinearGradient(0, 0, 0, h)
  g.addColorStop(0, top)
  g.addColorStop(1, bottom)
  ctx.fillStyle = g
  ctx.fillRect(0, 0, w, h)
}

function sparkles(ctx: CanvasRenderingContext2D, w: number, h: number, color: string, seed: number, count = 18) {
  const r = random(seed)
  ctx.fillStyle = color
  for (let i = 0; i < count; i++) {
    const x = r() * w, y = r() * h, s = 1 + r() * 2.5
    ctx.globalAlpha = 0.5 + r() * 0.5
    ctx.beginPath()
    ctx.moveTo(x, y - s * 2); ctx.lineTo(x + s * 0.5, y - s * 0.5); ctx.lineTo(x + s * 2, y)
    ctx.lineTo(x + s * 0.5, y + s * 0.5); ctx.lineTo(x, y + s * 2); ctx.lineTo(x - s * 0.5, y + s * 0.5)
    ctx.lineTo(x - s * 2, y); ctx.lineTo(x - s * 0.5, y - s * 0.5); ctx.fill()
  }
  ctx.globalAlpha = 1
}

function circle(ctx: CanvasRenderingContext2D, x: number, y: number, r: number, color: string) {
  ctx.fillStyle = color
  ctx.beginPath(); ctx.arc(x, y, r, 0, Math.PI * 2); ctx.fill()
}

function ellipse(ctx: CanvasRenderingContext2D, x: number, y: number, rx: number, ry: number, color: string, rot = 0) {
  ctx.fillStyle = color
  ctx.beginPath(); ctx.ellipse(x, y, rx, ry, rot, 0, Math.PI * 2); ctx.fill()
}

function poly(ctx: CanvasRenderingContext2D, color: string, points: number[]) {
  ctx.fillStyle = color
  ctx.beginPath()
  ctx.moveTo(points[0], points[1])
  for (let i = 2; i < points.length; i += 2) ctx.lineTo(points[i], points[i + 1])
  ctx.closePath()
  ctx.fill()
}

/** Big shiny anime eyes, centred on (x, y). */
function animeEye(ctx: CanvasRenderingContext2D, x: number, y: number, iris: string, size = 1) {
  ellipse(ctx, x, y, 7 * size, 9 * size, '#ffffff')
  ellipse(ctx, x, y + 1 * size, 5.5 * size, 7.5 * size, iris)
  ellipse(ctx, x, y + 2 * size, 3 * size, 4.5 * size, '#1e1b2e')
  circle(ctx, x - 2 * size, y - 3 * size, 2 * size, '#ffffff')
  circle(ctx, x + 2 * size, y + 3 * size, 1 * size, 'rgba(255,255,255,0.8)')
  ctx.strokeStyle = '#1e1b2e'
  ctx.lineWidth = 2 * size
  ctx.beginPath(); ctx.ellipse(x, y, 7 * size, 9 * size, 0, Math.PI * 1.1, Math.PI * 1.9); ctx.stroke()
}

/** Round chibi face with blush and a small mouth. Returns nothing; draws at (x, y) with radius r. */
function face(ctx: CanvasRenderingContext2D, x: number, y: number, r: number, skin: string, iris: string, mouth: 'smile' | 'cat' | 'open' = 'smile') {
  circle(ctx, x, y, r, skin)
  animeEye(ctx, x - r * 0.38, y + r * 0.08, iris, r / 34)
  animeEye(ctx, x + r * 0.38, y + r * 0.08, iris, r / 34)
  ellipse(ctx, x - r * 0.62, y + r * 0.42, r * 0.14, r * 0.08, 'rgba(244,114,182,0.55)')
  ellipse(ctx, x + r * 0.62, y + r * 0.42, r * 0.14, r * 0.08, 'rgba(244,114,182,0.55)')
  ctx.strokeStyle = '#7a3b3b'
  ctx.lineWidth = 2
  ctx.lineCap = 'round'
  ctx.beginPath()
  if (mouth === 'cat') {
    ctx.moveTo(x - 6, y + r * 0.5); ctx.quadraticCurveTo(x - 3, y + r * 0.62, x, y + r * 0.5)
    ctx.quadraticCurveTo(x + 3, y + r * 0.62, x + 6, y + r * 0.5)
    ctx.stroke()
  } else if (mouth === 'open') {
    ctx.stroke()
    ellipse(ctx, x, y + r * 0.55, 5, 4, '#9f1239')
  } else {
    ctx.arc(x, y + r * 0.42, 5, 0.2, Math.PI - 0.2)
    ctx.stroke()
  }
}

function body(ctx: CanvasRenderingContext2D, x: number, top: number, width: number, color: string, h: number) {
  ctx.fillStyle = color
  ctx.beginPath()
  ctx.moveTo(x - width * 0.35, top)
  ctx.lineTo(x + width * 0.35, top)
  ctx.lineTo(x + width * 0.6, h)
  ctx.lineTo(x - width * 0.6, h)
  ctx.closePath()
  ctx.fill()
}

const catGirl: Painter = (ctx, w, h) => {
  background(ctx, w, h, '#fbcfe8', '#c4b5fd')
  sparkles(ctx, w, h, '#ffffff', 1)
  const cx = w / 2, cy = 118
  body(ctx, cx, 158, 90, '#1e1b4b', h)
  poly(ctx, '#ffffff', [cx - 14, 158, cx + 14, 158, cx, 184])
  ellipse(ctx, cx, 172, 8, 5, '#f43f5e')
  // Hair back
  ellipse(ctx, cx, 112, 52, 56, '#fef3c7')
  // Ears
  poly(ctx, '#fef3c7', [cx - 46, 90, cx - 40, 44, cx - 14, 72])
  poly(ctx, '#fef3c7', [cx + 46, 90, cx + 40, 44, cx + 14, 72])
  poly(ctx, '#fda4af', [cx - 40, 82, cx - 37, 56, cx - 22, 74])
  poly(ctx, '#fda4af', [cx + 40, 82, cx + 37, 56, cx + 22, 74])
  face(ctx, cx, cy, 38, '#ffe4d6', '#8b5cf6', 'cat')
  // Bangs
  ctx.fillStyle = '#fde68a'
  ctx.beginPath()
  ctx.moveTo(cx - 42, 108); ctx.quadraticCurveTo(cx - 40, 70, cx, 72); ctx.quadraticCurveTo(cx + 40, 70, cx + 42, 108)
  ctx.lineTo(cx + 26, 96); ctx.lineTo(cx + 14, 106); ctx.lineTo(cx, 94); ctx.lineTo(cx - 14, 106); ctx.lineTo(cx - 26, 96)
  ctx.closePath(); ctx.fill()
}

const foxSpirit: Painter = (ctx, w, h) => {
  background(ctx, w, h, '#1e1b4b', '#7c2d12')
  const r = random(4)
  for (let i = 0; i < 12; i++) {
    const g = ctx.createRadialGradient(0, 0, 0, 0, 0, 8)
    ctx.save(); ctx.translate(r() * w, r() * h)
    g.addColorStop(0, 'rgba(56,189,248,0.95)'); g.addColorStop(1, 'rgba(56,189,248,0)')
    ctx.fillStyle = g; ctx.fillRect(-8, -8, 16, 16); ctx.restore()
  }
  const cx = w / 2
  // Tails
  for (const [dx, rot] of [[-40, -0.6], [40, 0.6], [0, 0]]) {
    ellipse(ctx, cx + dx, 200, 18, 50, '#f97316', rot)
    ellipse(ctx, cx + dx * 1.35, 160, 9, 16, '#fff7ed', rot)
  }
  body(ctx, cx, 160, 86, '#f8fafc', h)
  poly(ctx, '#dc2626', [cx - 30, 170, cx + 30, 170, cx + 36, 186, cx - 36, 186])
  ellipse(ctx, cx, 112, 50, 52, '#fdba74')
  poly(ctx, '#fb923c', [cx - 44, 88, cx - 46, 36, cx - 12, 70])
  poly(ctx, '#fb923c', [cx + 44, 88, cx + 46, 36, cx + 12, 70])
  poly(ctx, '#1f2937', [cx - 44, 50, cx - 46, 36, cx - 36, 44])
  poly(ctx, '#1f2937', [cx + 44, 50, cx + 46, 36, cx + 36, 44])
  face(ctx, cx, 120, 36, '#fff1e6', '#f59e0b')
  // Red face markings
  ctx.strokeStyle = '#dc2626'; ctx.lineWidth = 2
  for (const s of [-1, 1]) {
    ctx.beginPath(); ctx.moveTo(cx + s * 22, 104); ctx.lineTo(cx + s * 32, 100); ctx.stroke()
  }
  ctx.fillStyle = '#fb923c'
  ctx.beginPath(); ctx.moveTo(cx - 40, 108); ctx.quadraticCurveTo(cx, 60, cx + 40, 108); ctx.lineTo(cx + 18, 96); ctx.lineTo(cx, 104); ctx.lineTo(cx - 18, 96); ctx.closePath(); ctx.fill()
}

const ninja: Painter = (ctx, w, h) => {
  background(ctx, w, h, '#0f172a', '#334155')
  circle(ctx, 120, 50, 26, '#fef3c7')
  circle(ctx, 110, 44, 24, '#172033')
  const cx = w / 2
  body(ctx, cx, 158, 92, '#111827', h)
  poly(ctx, '#b91c1c', [cx - 44, 190, cx + 44, 180, cx + 46, 190, cx - 44, 200])
  circle(ctx, cx, 116, 40, '#111827')
  // Eye slit
  ctx.fillStyle = '#ffe4d6'
  ctx.beginPath(); ctx.ellipse(cx, 112, 32, 13, 0, 0, Math.PI * 2); ctx.fill()
  animeEye(ctx, cx - 14, 112, '#ef4444', 0.8)
  animeEye(ctx, cx + 14, 112, '#ef4444', 0.8)
  // Headband with tails
  ctx.fillStyle = '#b91c1c'
  ctx.fillRect(cx - 40, 90, 80, 9)
  poly(ctx, '#b91c1c', [cx + 36, 92, cx + 70, 84, cx + 66, 96])
  poly(ctx, '#b91c1c', [cx + 36, 96, cx + 62, 110, cx + 54, 114])
  // Kunai
  ctx.save(); ctx.translate(38, 210); ctx.rotate(-0.7)
  poly(ctx, '#cbd5e1', [0, -26, 6, -6, 0, 0, -6, -6]); ctx.fillStyle = '#7f1d1d'; ctx.fillRect(-2, 0, 4, 16)
  ctx.restore()
}

const witch: Painter = (ctx, w, h) => {
  background(ctx, w, h, '#312e81', '#0f766e')
  sparkles(ctx, w, h, '#fde68a', 9, 22)
  const cx = w / 2
  body(ctx, cx, 160, 96, '#4c1d95', h)
  ellipse(ctx, cx, 124, 50, 54, '#1e1b4b')
  face(ctx, cx, 126, 36, '#ffe4e6', '#10b981', 'open')
  // Hat
  poly(ctx, '#4c1d95', [cx - 20, 94, cx + 26, 94, cx + 36, 18, cx + 8, 40])
  ellipse(ctx, cx, 96, 64, 12, '#4c1d95')
  ctx.fillStyle = '#f59e0b'; ctx.fillRect(cx - 24, 86, 50, 7)
  // Hair sides
  ellipse(ctx, cx - 40, 146, 12, 30, '#1e1b4b')
  ellipse(ctx, cx + 40, 146, 12, 30, '#1e1b4b')
  // Wand star
  ctx.strokeStyle = '#78350f'; ctx.lineWidth = 3
  ctx.beginPath(); ctx.moveTo(126, 230); ctx.lineTo(142, 190); ctx.stroke()
  sparkles(ctx, 20, 20, '#fde047', 2, 0)
  ctx.save(); ctx.translate(143, 186)
  poly(ctx, '#fde047', [0, -10, 3, -3, 10, -3, 4, 2, 6, 10, 0, 5, -6, 10, -4, 2, -10, -3, -3, -3])
  ctx.restore()
}

const robot: Painter = (ctx, w, h) => {
  background(ctx, w, h, '#0e7490', '#082f49')
  ctx.strokeStyle = 'rgba(34,211,238,0.25)'; ctx.lineWidth = 1
  for (let y = 0; y < h; y += 16) { ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(w, y); ctx.stroke() }
  const cx = w / 2
  body(ctx, cx, 160, 90, '#cbd5e1', h)
  ctx.fillStyle = '#0ea5e9'; ctx.fillRect(cx - 16, 182, 32, 20)
  circle(ctx, cx, 192, 5, '#e0f2fe')
  // Head
  ctx.fillStyle = '#e2e8f0'
  ctx.beginPath(); ctx.roundRect(cx - 44, 80, 88, 76, 22); ctx.fill()
  ctx.fillStyle = '#0f172a'
  ctx.beginPath(); ctx.roundRect(cx - 34, 96, 68, 42, 14); ctx.fill()
  // Glowing eyes
  ctx.shadowColor = '#22d3ee'; ctx.shadowBlur = 10
  ctx.fillStyle = '#22d3ee'
  ctx.beginPath(); ctx.roundRect(cx - 24, 108, 16, 12, 5); ctx.fill()
  ctx.beginPath(); ctx.roundRect(cx + 8, 108, 16, 12, 5); ctx.fill()
  ctx.shadowBlur = 0
  ctx.strokeStyle = '#22d3ee'; ctx.lineWidth = 2
  ctx.beginPath(); ctx.arc(cx, 124, 6, 0.3, Math.PI - 0.3); ctx.stroke()
  // Antenna
  ctx.strokeStyle = '#94a3b8'; ctx.lineWidth = 3
  ctx.beginPath(); ctx.moveTo(cx, 80); ctx.lineTo(cx, 58); ctx.stroke()
  circle(ctx, cx, 54, 6, '#f43f5e')
  ellipse(ctx, cx - 30, 146, 6, 3, 'rgba(244,114,182,0.7)')
  ellipse(ctx, cx + 30, 146, 6, 3, 'rgba(244,114,182,0.7)')
}

const dragonKid: Painter = (ctx, w, h) => {
  background(ctx, w, h, '#fef3c7', '#fb7185')
  const cx = w / 2
  // Wings
  poly(ctx, '#16a34a', [cx - 30, 170, cx - 78, 120, cx - 70, 190, cx - 50, 176, cx - 44, 210])
  poly(ctx, '#16a34a', [cx + 30, 170, cx + 78, 120, cx + 70, 190, cx + 50, 176, cx + 44, 210])
  body(ctx, cx, 160, 90, '#22c55e', h)
  ellipse(ctx, cx, 214, 24, 40, '#fde68a')
  // Hood
  circle(ctx, cx, 118, 50, '#22c55e')
  poly(ctx, '#fef9c3', [cx - 36, 78, cx - 30, 50, cx - 18, 76])
  poly(ctx, '#fef9c3', [cx + 36, 78, cx + 30, 50, cx + 18, 76])
  for (const x of [-14, 0, 14]) poly(ctx, '#15803d', [cx + x - 6, 70, cx + x, 58, cx + x + 6, 70])
  face(ctx, cx, 126, 34, '#ffe4d6', '#16a34a', 'open')
  // Tiny fire puff
  ellipse(ctx, 132, 150, 10, 7, '#f97316')
  ellipse(ctx, 142, 146, 7, 5, '#fde047')
}

const astronaut: Painter = (ctx, w, h) => {
  background(ctx, w, h, '#020617', '#1e1b4b')
  const r = random(12)
  for (let i = 0; i < 50; i++) circle(ctx, r() * w, r() * h, 0.6 + r() * 1.4, 'rgba(255,255,255,0.8)')
  circle(ctx, 30, 220, 26, '#f97316')
  ellipse(ctx, 30, 220, 42, 8, 'rgba(253,186,116,0.6)', -0.3)
  const cx = w / 2
  body(ctx, cx, 160, 92, '#f1f5f9', h)
  ctx.fillStyle = '#3b82f6'; ctx.fillRect(cx - 20, 180, 40, 16)
  circle(ctx, cx, 116, 48, '#f8fafc')
  circle(ctx, cx, 118, 38, '#0f172a')
  face(ctx, cx, 120, 30, '#ffe4d6', '#0ea5e9')
  // Visor shine
  ctx.fillStyle = 'rgba(255,255,255,0.35)'
  ctx.beginPath(); ctx.ellipse(cx - 16, 96, 12, 6, -0.6, 0, Math.PI * 2); ctx.fill()
}

const ghostGirl: Painter = (ctx, w, h) => {
  background(ctx, w, h, '#1e1b4b', '#581c87')
  sparkles(ctx, w, h, '#c4b5fd', 31)
  const cx = w / 2
  // Ghost body
  ctx.fillStyle = 'rgba(241,245,249,0.95)'
  ctx.beginPath()
  ctx.moveTo(cx - 50, 120); ctx.quadraticCurveTo(cx - 52, 60, cx, 58); ctx.quadraticCurveTo(cx + 52, 60, cx + 50, 120)
  ctx.lineTo(cx + 52, 230)
  for (let i = 0; i < 5; i++) {
    const x = cx + 52 - (i + 1) * 20.8
    ctx.quadraticCurveTo(x + 10, i % 2 ? 244 : 216, x, 230)
  }
  ctx.closePath(); ctx.fill()
  face(ctx, cx, 120, 32, '#f8fafc', '#a855f7', 'cat')
  // Little bow
  poly(ctx, '#f472b6', [cx + 16, 70, cx + 36, 60, cx + 34, 80])
  poly(ctx, '#f472b6', [cx + 16, 70, cx + 2, 58, cx + 2, 80])
  circle(ctx, cx + 16, 70, 4, '#db2777')
}

const samurai: Painter = (ctx, w, h) => {
  background(ctx, w, h, '#fde68a', '#dc2626')
  circle(ctx, w / 2, 90, 60, 'rgba(255,255,255,0.35)')
  const cx = w / 2
  body(ctx, cx, 158, 98, '#1f2937', h)
  poly(ctx, '#b91c1c', [cx - 48, 172, cx + 48, 172, cx + 54, 196, cx - 54, 196])
  for (let y = 204; y < h; y += 14) { ctx.fillStyle = '#374151'; ctx.fillRect(cx - 56, y, 112, 6) }
  circle(ctx, cx, 120, 38, '#ffe4d6')
  face(ctx, cx, 122, 36, '#ffe4d6', '#1f2937')
  // Topknot hair
  ctx.fillStyle = '#111827'
  ctx.beginPath(); ctx.moveTo(cx - 40, 112); ctx.quadraticCurveTo(cx, 60, cx + 40, 112); ctx.lineTo(cx + 22, 100); ctx.lineTo(cx, 108); ctx.lineTo(cx - 22, 100); ctx.closePath(); ctx.fill()
  ellipse(ctx, cx, 72, 10, 8, '#111827')
  // Katana
  ctx.save(); ctx.translate(132, 230); ctx.rotate(-0.5)
  ctx.fillStyle = '#e5e7eb'; ctx.fillRect(-2, -90, 4, 78)
  ctx.fillStyle = '#f59e0b'; ctx.fillRect(-7, -12, 14, 4)
  ctx.fillStyle = '#111827'; ctx.fillRect(-3, -8, 6, 26)
  ctx.restore()
}

const slimeFriend: Painter = (ctx, w, h) => {
  background(ctx, w, h, '#a7f3d0', '#60a5fa')
  sparkles(ctx, w, h, '#ffffff', 55, 14)
  const cx = w / 2
  ellipse(ctx, cx, 238, 64, 10, 'rgba(15,23,42,0.18)')
  const g = ctx.createRadialGradient(cx - 20, 130, 10, cx, 170, 80)
  g.addColorStop(0, '#bbf7d0'); g.addColorStop(1, '#22c55e')
  ctx.fillStyle = g
  ctx.beginPath()
  ctx.moveTo(cx - 62, 232); ctx.quadraticCurveTo(cx - 70, 110, cx, 100); ctx.quadraticCurveTo(cx + 70, 110, cx + 62, 232)
  ctx.closePath(); ctx.fill()
  ellipse(ctx, cx - 28, 136, 12, 7, 'rgba(255,255,255,0.6)', -0.5)
  animeEye(ctx, cx - 20, 176, '#065f46', 1.3)
  animeEye(ctx, cx + 20, 176, '#065f46', 1.3)
  ctx.strokeStyle = '#065f46'; ctx.lineWidth = 3; ctx.lineCap = 'round'
  ctx.beginPath(); ctx.arc(cx, 200, 7, 0.2, Math.PI - 0.2); ctx.stroke()
  // Crown
  poly(ctx, '#facc15', [cx - 16, 106, cx - 18, 84, cx - 8, 96, cx, 80, cx + 8, 96, cx + 18, 84, cx + 16, 106])
}

export const HD_CHARACTERS: { name: string; paint: Painter; glow?: string }[] = [
  { name: 'Neko Chan', paint: catGirl, glow: '#f9a8d4' },
  { name: 'Kitsune', paint: foxSpirit, glow: '#38bdf8' },
  { name: 'Shadow Ninja', paint: ninja, glow: '#ef4444' },
  { name: 'Little Witch', paint: witch, glow: '#a78bfa' },
  { name: 'Robo Buddy', paint: robot, glow: '#22d3ee' },
  { name: 'Dragon Kid', paint: dragonKid, glow: '#22c55e' },
  { name: 'Space Cadet', paint: astronaut, glow: '#60a5fa' },
  { name: 'Boo Chan', paint: ghostGirl, glow: '#c4b5fd' },
  { name: 'Samurai', paint: samurai, glow: '#dc2626' },
  { name: 'Slime King', paint: slimeFriend, glow: '#4ade80' },
]
