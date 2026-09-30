/**
 * Build-time item pictures: every cosmetic in every colour variant, drawn by
 * the launcher's own drawPicture (cosmeticThumbs.ts), plus contact sheets of
 * the Cosmetics page's tiles for docs/cosmetics. Bundled and run by thumbs.cjs
 * in a hidden Electron window.
 */
import * as THREE from 'three'
import { COSMETICS_BY_SLOT, SLOTS, NonCapeSlot, CosmeticDef } from '../../../src/renderer/data/cosmetics'
import { drawPicture, THUMB_W, THUMB_H } from '../../../src/renderer/data/cosmeticThumbs'

declare global { interface Window { shots: Record<string, string>; done: boolean; error?: string } }
window.shots = {}

const SMALL_W = 120, SMALL_H = 90

const img = (url: string) => new Promise<HTMLImageElement>((resolve, reject) => {
  const i = new Image()
  i.onload = () => resolve(i)
  i.onerror = reject
  i.src = url
})

async function downscale(url: string, w: number, h: number): Promise<string> {
  const c = document.createElement('canvas')
  c.width = w
  c.height = h
  const g = c.getContext('2d')!
  g.imageSmoothingEnabled = true
  g.imageSmoothingQuality = 'high'
  g.drawImage(await img(url), 0, 0, w, h)
  return c.toDataURL('image/png')
}

const variantsOf = (def: CosmeticDef) => def.variants?.length ? def.variants : [{ id: 'default', name: 'Standard', color: def.color, secondary: def.secondary }]

type Slot = { id: NonCapeSlot; label: string }

async function main() {
  const canvas = document.createElement('canvas')
  const r = new THREE.WebGLRenderer({ canvas, alpha: true, antialias: true, preserveDrawingBuffer: true })
  r.setPixelRatio(1)
  r.setSize(THUMB_W, THUMB_H, false)
  r.outputColorSpace = THREE.SRGBColorSpace

  const only = new URLSearchParams(location.search).get('only')
  const slots = SLOTS.filter(s => s.id !== 'cape') as Slot[]
  const pics: Record<string, string> = {}
  for (const s of slots) {
    for (const def of COSMETICS_BY_SLOT[s.id]) {
      if (only && !def.id.includes(only)) continue
      for (const v of variantsOf(def)) {
        // Textures load asynchronously: draw once to start them, wait, then draw for real.
        drawPicture(r, def, { [def.id]: v.id }, THUMB_W, THUMB_H)
        await new Promise(res => setTimeout(res, 40))
        const url = drawPicture(r, def, { [def.id]: v.id }, THUMB_W, THUMB_H)
        if (!url) throw new Error(`no picture for ${def.id}/${v.id}`)
        pics[`${def.id}/${v.id}`] = url
        window.shots[`thumb/${def.id}/${v.id}`] = url
        window.shots[`small/${def.id}/${v.id}`] = await downscale(url, SMALL_W, SMALL_H)
      }
    }
  }
  if (!only) {
    window.shots['sheet/contact-sheet'] = await tileSheet(slots, pics)
    window.shots['sheet/contact-sheet-variants'] = await variantSheet(slots, pics)
  }
  window.done = true
}

/** Rounded rectangle path. */
function round(g: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) {
  g.beginPath()
  g.moveTo(x + r, y)
  g.arcTo(x + w, y, x + w, y + h, r)
  g.arcTo(x + w, y + h, x, y + h, r)
  g.arcTo(x, y + h, x, y, r)
  g.arcTo(x, y, x + w, y, r)
  g.closePath()
}

/** The Cosmetics page's grid, tab by tab: picture, variant pictures, name. */
async function tileSheet(slots: Slot[], pics: Record<string, string>) {
  const cols = 8, tw = 168, ih = 126, lh = 26, gap = 10, pad = 24, head = 34
  let rows = 0
  for (const s of slots) rows += Math.ceil(COSMETICS_BY_SLOT[s.id].length / cols)
  const c = document.createElement('canvas')
  c.width = pad * 2 + cols * tw + (cols - 1) * gap
  c.height = pad * 2 + slots.length * head + rows * (ih + lh + gap)
  const g = c.getContext('2d')!
  g.fillStyle = '#101216'
  g.fillRect(0, 0, c.width, c.height)
  let y = pad
  for (const s of slots) {
    const list = COSMETICS_BY_SLOT[s.id]
    g.fillStyle = '#e7e9ee'
    g.font = '600 16px sans-serif'
    g.fillText(`${s.label} (${list.length})`, pad, y + 20)
    y += head
    for (let i = 0; i < list.length; i++) {
      const def = list[i]
      const x = pad + (i % cols) * (tw + gap)
      const ty = y + Math.floor(i / cols) * (ih + lh + gap)
      g.save()
      round(g, x, ty, tw, ih + lh, 8)
      g.clip()
      const grad = g.createRadialGradient(x + tw / 2, ty + ih * 0.4, 4, x + tw / 2, ty + ih * 0.4, tw * 0.7)
      // The page's tile: a soft neutral spotlight with a faint hint of the item's colour.
      grad.addColorStop(0, '#383e4c')
      grad.addColorStop(1, '#16181e')
      g.fillStyle = grad
      g.fillRect(x, ty, tw, ih)
      g.fillStyle = def.color + '14'
      g.fillRect(x, ty, tw, ih)
      const vs = variantsOf(def)
      g.drawImage(await img(pics[`${def.id}/${vs[0].id}`]), x, ty, tw, ih)
      if (vs.length > 1) {
        // The variant picker: a small picture of each variant.
        for (let k = 0; k < vs.length; k++) {
          const vx = x + 5 + k * 30, vy = ty + ih - 27
          round(g, vx, vy, 27, 22, 4)
          g.fillStyle = '#0d0f13d0'
          g.fill()
          g.strokeStyle = k === 0 ? '#8fb4ff' : '#3a3f4a'
          g.lineWidth = k === 0 ? 2 : 1
          g.stroke()
          g.drawImage(await img(pics[`${def.id}/${vs[k].id}`]), vx + 1, vy + 1, 25, 20)
        }
      }
      g.fillStyle = '#1d2129'
      g.fillRect(x, ty + ih, tw, lh)
      g.fillStyle = '#e7e9ee'
      g.font = '12px sans-serif'
      g.fillText(def.name, x + 8, ty + ih + 17, tw - 16)
      if (def.isNew) {
        round(g, x + 6, ty + 6, 30, 15, 3)
        g.fillStyle = '#8fb4ff'
        g.fill()
        g.fillStyle = '#000'
        g.font = '600 9px sans-serif'
        g.fillText('NEU', x + 11, ty + 17)
      }
      g.restore()
      round(g, x, ty, tw, ih + lh, 8)
      g.strokeStyle = '#2a2e37'
      g.lineWidth = 1
      g.stroke()
    }
    y += Math.ceil(list.length / cols) * (ih + lh + gap)
  }
  return c.toDataURL('image/png')
}

/** Every variant picture of every item with variants, one row per item. */
async function variantSheet(slots: Slot[], pics: Record<string, string>) {
  const items = slots.flatMap(s => COSMETICS_BY_SLOT[s.id]).filter(d => variantsOf(d).length > 1)
  const w = 132, h = 99, name = 170, pad = 16
  const cols = Math.max(...items.map(d => variantsOf(d).length))
  const c = document.createElement('canvas')
  c.width = pad * 2 + name + cols * (w + 6)
  c.height = pad * 2 + items.length * (h + 18)
  const g = c.getContext('2d')!
  g.fillStyle = '#101216'
  g.fillRect(0, 0, c.width, c.height)
  for (let i = 0; i < items.length; i++) {
    const def = items[i], y = pad + i * (h + 18)
    g.fillStyle = '#e7e9ee'
    g.font = '600 13px sans-serif'
    g.fillText(def.name, pad, y + h / 2)
    const vs = variantsOf(def)
    for (let k = 0; k < vs.length; k++) {
      const x = pad + name + k * (w + 6)
      g.fillStyle = '#171a20'
      g.fillRect(x, y, w, h)
      g.drawImage(await img(pics[`${def.id}/${vs[k].id}`]), x, y, w, h)
      g.fillStyle = '#9aa0ab'
      g.font = '11px sans-serif'
      g.fillText(vs[k].name, x + 4, y + h + 13, w - 8)
    }
  }
  return c.toDataURL('image/png')
}

main().catch(e => { window.error = String(e?.stack || e); window.done = true })
