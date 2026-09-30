/**
 * Showcase renderer for docs/cosmetics: draws the model cosmetics exactly as
 * the launcher preview does (skinview3d player + cosmeticModels.ts) into a
 * few PNG sheets. Bundled and run by showcase.cjs in a hidden Electron window.
 */
import * as skinview3d from 'skinview3d'
import * as THREE from 'three'
import { buildModel, animateModel, BuiltModel, modelGeometry } from '../../../src/renderer/data/cosmeticModels'
import { MODEL_ITEM_DATA } from '../../../src/renderer/data/cosmeticModels.generated'

declare global { interface Window { shots: Record<string, string>; done: boolean } }
window.shots = {}

const W = 360, H = 440

/** A plain original skin (no Mojang default): shirt, trousers, face. */
function skin(): string {
  const c = document.createElement('canvas')
  c.width = 64; c.height = 64
  const g = c.getContext('2d')!
  const box = (x: number, y: number, w: number, h: number, col: string) => { g.fillStyle = col; g.fillRect(x, y, w, h) }
  // head
  box(0, 8, 32, 8, '#c8906a'); box(8, 0, 16, 8, '#4a3222'); box(0, 8, 32, 2, '#4a3222')
  box(9, 12, 2, 1, '#2b2b40'); box(13, 12, 2, 1, '#2b2b40'); box(10, 14, 4, 1, '#8a5a44')
  // body + arms (shirt)
  box(16, 16, 24, 16, '#3b6fb6'); box(40, 16, 16, 16, '#3b6fb6'); box(32, 48, 16, 16, '#3b6fb6')
  box(44, 28, 12, 4, '#c8906a'); box(36, 60, 12, 4, '#c8906a')
  // legs
  box(0, 16, 16, 16, '#2d2f38'); box(16, 48, 16, 16, '#2d2f38')
  return c.toDataURL()
}

async function frame(viewer: skinview3d.SkinViewer) {
  await new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))
  viewer.render()
  return viewer.canvas.toDataURL('image/png')
}

async function main() {
  const canvas = document.createElement('canvas')
  document.body.appendChild(canvas)
  const viewer = new skinview3d.SkinViewer({ canvas, width: W, height: H, preserveDrawingBuffer: true } as any)
  viewer.renderPaused = true
  viewer.zoom = 0.78
  viewer.background = new THREE.Color('#15171d') as any
  await viewer.loadSkin(skin())

  const skinObj = viewer.playerObject.skin
  const head = skinObj.head as unknown as THREE.Object3D
  const body = viewer.playerObject.skin as unknown as THREE.Object3D

  const wear = (ids: { model: string; variant?: string }[]) => {
    const built: BuiltModel[] = []
    const holders: THREE.Object3D[] = []
    for (const { model, variant } of ids) {
      const geo = modelGeometry(model)!
      if (geo.anchor === 'wing') {
        for (const side of [1, -1] as const) {
          const pivot = new THREE.Group()
          pivot.position.set(side * 1.5, -2.5, -2.6)
          pivot.rotation.y = side * 0.75
          const holder = new THREE.Group()
          holder.scale.x = side
          const b = buildModel(model, variant)!
          holder.add(b.root); pivot.add(holder); body.add(pivot)
          holders.push(pivot); built.push(b)
        }
      } else {
        const holder = new THREE.Group()
        if (geo.anchor === 'head') { holder.position.y = 4; head.add(holder) } else body.add(holder)
        const b = buildModel(model, variant)!
        holder.add(b.root)
        holders.push(holder); built.push(b)
      }
    }
    for (const b of built) animateModel(b, { time: 1.3, move: 0.4, flap: 0.1 })
    return () => holders.forEach(h => h.removeFromParent())
  }

  const outfits: { name: string; items: { model: string; variant?: string }[]; back?: boolean }[] = [
    { name: 'outfit-wizard', items: [{ model: 'wizard_hat' }, { model: 'seraph_wings' }] },
    { name: 'outfit-wizard-back', items: [{ model: 'wizard_hat' }, { model: 'seraph_wings' }], back: true },
    { name: 'outfit-crown', items: [{ model: 'crystal_crown' }, { model: 'prism_wings' }] },
    { name: 'outfit-crown-back', items: [{ model: 'crystal_crown' }, { model: 'prism_wings' }], back: true },
    { name: 'outfit-aviator', items: [{ model: 'aviator_cap' }, { model: 'expedition_pack' }] },
    { name: 'outfit-aviator-back', items: [{ model: 'aviator_cap' }, { model: 'expedition_pack' }], back: true },
    { name: 'outfit-dragon-back', items: [{ model: 'neon_headset', variant: 'magenta' }, { model: 'dragon_wings' }], back: true },
    { name: 'outfit-jet-back', items: [{ model: 'propeller_cap' }, { model: 'jetpack_mk2' }], back: true },
    { name: 'outfit-frog', items: [{ model: 'frog_beanie' }, { model: 'cat_pack' }] },
    { name: 'outfit-frog-back', items: [{ model: 'frog_beanie' }, { model: 'cat_pack', variant: 'tux' }], back: true },
    { name: 'outfit-ninja', items: [{ model: 'ninja_band' }] },
    { name: 'outfit-ninja-back', items: [{ model: 'ninja_band', variant: 'crimson' }, { model: 'dragon_wings', variant: 'emerald' }], back: true },
    { name: 'outfit-toadstool', items: [{ model: 'toadstool_cap' }, { model: 'prism_wings', variant: 'rose' }] },
    { name: 'outfit-paisley', items: [{ model: 'paisley_bandana', variant: 'navy' }, { model: 'jetpack_mk2', variant: 'rocket' }] },
  ]
  for (const o of outfits) {
    const off = wear(o.items)
    viewer.playerObject.rotation.y = o.back ? Math.PI + 0.55 : -0.5
    viewer.playerObject.rotation.x = 0.08
    window.shots[o.name] = await frame(viewer)
    off()
  }

  // Every variant of every model, one tile each, on the player's head/back.
  for (const item of MODEL_ITEM_DATA) {
    for (const v of item.variants) {
      const off = wear([{ model: item.model, variant: v.id }])
      viewer.playerObject.rotation.y = item.anchor === 'head' ? -0.5 : Math.PI + 0.5
      window.shots[`variant-${item.model}-${v.id}`] = await frame(viewer)
      off()
    }
  }
  window.shots['sheet-outfits'] = await sheet(outfits.map(o => o.name), 7, 0.6)
  window.shots['sheet-variants'] = await sheet(Object.keys(window.shots).filter(k => k.startsWith('variant-')), 10, 0.42)
  window.done = true
}

/** Several shots side by side on one image. */
async function sheet(names: string[], cols: number, scale: number): Promise<string> {
  const w = Math.round(W * scale), h = Math.round(H * scale)
  const rows = Math.ceil(names.length / cols)
  const c = document.createElement('canvas')
  c.width = cols * w
  c.height = rows * h
  const g = c.getContext('2d')!
  g.fillStyle = '#15171d'
  g.fillRect(0, 0, c.width, c.height)
  for (let i = 0; i < names.length; i++) {
    const img = new Image()
    await new Promise(r => { img.onload = r; img.src = window.shots[names[i]] })
    g.drawImage(img, (i % cols) * w, Math.floor(i / cols) * h, w, h)
  }
  return c.toDataURL('image/png')
}

main().catch(e => { (window as any).error = String(e?.stack || e); window.done = true })
