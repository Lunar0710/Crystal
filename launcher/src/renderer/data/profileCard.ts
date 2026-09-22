import { RANKS, type RankId } from './ranks'

export interface ProfileCardData {
  username: string
  rank: RankId | null
  skinDataUrl: string | null
  slim: boolean
  capeUrl: string | null
  capeName: string | null
  playtimeMs: number
  sessions: number
}

export const CARD_W = 1200
export const CARD_H = 630

function loadImage(src: string): Promise<HTMLImageElement | null> {
  return new Promise(resolve => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = () => resolve(null)
    img.src = src
  })
}

export function formatPlaytime(ms: number): string {
  const minutes = Math.floor(ms / 60000)
  const hours = Math.floor(minutes / 60)
  if (hours === 0) return `${minutes} Min`
  return `${hours.toLocaleString('de-DE')} Std ${minutes % 60} Min`
}

/**
 * Front view of a Minecraft skin, 16x32 skin pixels: head, body, arms and
 * legs with their outer layer on top. Old 64x32 skins have no separate left
 * arm/leg, so those mirror the right ones like the game does.
 */
function drawSkin(ctx: CanvasRenderingContext2D, skin: HTMLImageElement, slim: boolean, x: number, y: number, s: number) {
  const legacy = skin.naturalHeight === 32
  const arm = slim ? 3 : 4
  const part = (sx: number, sy: number, w: number, h: number, dx: number, dy: number, mirror = false) => {
    ctx.save()
    if (mirror) {
      ctx.translate(x + (dx + w) * s, y + dy * s)
      ctx.scale(-1, 1)
      ctx.drawImage(skin, sx, sy, w, h, 0, 0, w * s, h * s)
    } else {
      ctx.drawImage(skin, sx, sy, w, h, x + dx * s, y + dy * s, w * s, h * s)
    }
    ctx.restore()
  }
  // Inner layer
  part(8, 8, 8, 8, 4, 0)                          // head
  part(20, 20, 8, 12, 4, 8)                       // body
  part(44, 20, arm, 12, 4 - arm, 8)               // right arm (viewer's left)
  if (legacy) part(44, 20, arm, 12, 12, 8, true)
  else part(36, 52, arm, 12, 12, 8)               // left arm
  part(4, 20, 4, 12, 4, 20)                       // right leg
  if (legacy) part(4, 20, 4, 12, 8, 20, true)
  else part(20, 52, 4, 12, 8, 20)                 // left leg
  if (legacy) {
    part(40, 8, 8, 8, 4, 0)                       // hat is the only overlay on old skins
    return
  }
  // Outer layer
  part(40, 8, 8, 8, 4, 0)
  part(20, 36, 8, 12, 4, 8)
  part(44, 36, arm, 12, 4 - arm, 8)
  part(52, 52, arm, 12, 12, 8)
  part(4, 36, 4, 12, 4, 20)
  part(4, 52, 4, 12, 8, 20)
}

function roundRect(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) {
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.arcTo(x + w, y, x + w, y + h, r)
  ctx.arcTo(x + w, y + h, x, y + h, r)
  ctx.arcTo(x, y + h, x, y, r)
  ctx.arcTo(x, y, x + w, y, r)
  ctx.closePath()
}

/** Renders the shareable card and returns it as a PNG data URL. */
export async function renderProfileCard(data: ProfileCardData): Promise<string> {
  const canvas = document.createElement('canvas')
  canvas.width = CARD_W
  canvas.height = CARD_H
  const ctx = canvas.getContext('2d')!
  const rank = data.rank ? RANKS[data.rank] : null
  const [c1, c2] = rank && data.rank !== 'member' ? rank.colors : ['#5b8af5', '#7c6af5']

  // Ground with a soft glow in the rank's colours.
  ctx.fillStyle = '#0b0e16'
  ctx.fillRect(0, 0, CARD_W, CARD_H)
  const glow = ctx.createRadialGradient(300, 330, 20, 300, 330, 520)
  glow.addColorStop(0, c1 + '55')
  glow.addColorStop(1, '#0b0e1600')
  ctx.fillStyle = glow
  ctx.fillRect(0, 0, CARD_W, CARD_H)
  const glow2 = ctx.createRadialGradient(1080, 80, 10, 1080, 80, 420)
  glow2.addColorStop(0, c2 + '33')
  glow2.addColorStop(1, '#0b0e1600')
  ctx.fillStyle = glow2
  ctx.fillRect(0, 0, CARD_W, CARD_H)

  // Faint facets, the Nexora logo's shape language.
  ctx.strokeStyle = '#ffffff0d'
  ctx.lineWidth = 2
  for (let i = 0; i < 6; i++) {
    ctx.beginPath()
    ctx.moveTo(640 + i * 90, CARD_H)
    ctx.lineTo(880 + i * 90, 0)
    ctx.stroke()
  }

  // Skin
  ctx.imageSmoothingEnabled = false
  const skin = data.skinDataUrl ? await loadImage(data.skinDataUrl) : null
  const scale = 15
  const skinX = 300 - 8 * scale, skinY = 315 - 16 * scale
  ctx.fillStyle = '#00000055'
  ctx.beginPath()
  ctx.ellipse(300, skinY + 32 * scale + 14, 120, 18, 0, 0, Math.PI * 2)
  ctx.fill()
  if (skin) {
    drawSkin(ctx, skin, data.slim, skinX, skinY, scale)
  } else {
    ctx.fillStyle = '#1f2433'
    roundRect(ctx, skinX, skinY, 16 * scale, 32 * scale, 12)
    ctx.fill()
  }

  // Name and rank
  const left = 600
  ctx.imageSmoothingEnabled = true
  ctx.fillStyle = '#8e97b0'
  ctx.font = "600 22px 'Segoe UI', system-ui, sans-serif"
  ctx.fillText('CRYSTAL CLIENT', left, 150)
  ctx.fillStyle = '#ffffff'
  let size = 72
  ctx.font = `700 ${size}px 'Segoe UI', system-ui, sans-serif`
  while (ctx.measureText(data.username).width > 540 && size > 36) {
    size -= 4
    ctx.font = `700 ${size}px 'Segoe UI', system-ui, sans-serif`
  }
  ctx.fillText(data.username, left, 150 + size + 10)

  let y = 150 + size + 40
  if (rank && data.rank !== 'member') {
    ctx.font = "700 24px 'Segoe UI', system-ui, sans-serif"
    const label = rank.label.toUpperCase()
    const w = ctx.measureText(label).width + 36
    const pill = ctx.createLinearGradient(left, 0, left + w, 0)
    pill.addColorStop(0, c1)
    pill.addColorStop(1, c2)
    ctx.fillStyle = pill
    roundRect(ctx, left, y, w, 44, 22)
    ctx.fill()
    ctx.fillStyle = '#0b0e16'
    ctx.fillText(label, left + 18, y + 31)
    y += 44
  }

  // Stats
  y += 50
  const stat = (label: string, value: string, x: number) => {
    ctx.fillStyle = '#8e97b0'
    ctx.font = "600 18px 'Segoe UI', system-ui, sans-serif"
    ctx.fillText(label.toUpperCase(), x, y)
    ctx.fillStyle = '#e4e8f3'
    ctx.font = "700 34px 'Segoe UI', system-ui, sans-serif"
    ctx.fillText(value, x, y + 42)
  }
  stat('Spielzeit', formatPlaytime(data.playtimeMs), left)
  stat('Sessions', data.sessions.toLocaleString('de-DE'), left + 300)

  // Equipped cape
  if (data.capeUrl) {
    const cape = await loadImage(data.capeUrl)
    if (cape) {
      const cy = y + 90
      const k = cape.naturalWidth / 64
      ctx.imageSmoothingEnabled = k < 2 ? false : true
      ctx.fillStyle = '#ffffff10'
      roundRect(ctx, left - 10, cy - 10, 84, 124, 10)
      ctx.fill()
      ctx.drawImage(cape, 1 * k, 1 * k, 10 * k, 16 * k, left, cy, 64, 104)
      ctx.imageSmoothingEnabled = true
      ctx.fillStyle = '#8e97b0'
      ctx.font = "600 18px 'Segoe UI', system-ui, sans-serif"
      ctx.fillText('CAPE', left + 96, cy + 36)
      ctx.fillStyle = '#e4e8f3'
      ctx.font = "700 30px 'Segoe UI', system-ui, sans-serif"
      ctx.fillText(data.capeName || 'Eigenes Cape', left + 96, cy + 74)
    }
  }

  return canvas.toDataURL('image/png')
}
