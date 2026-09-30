import * as THREE from 'three'
import { CosmeticDef, CosmeticVariants, resolveCosmetic, variantOf } from './cosmetics'
import { shapeFor, ShapeBox } from './cosmeticShapes'
import { buildModel, disposeObject } from './cosmeticModels'

/**
 * Small 3D pictures of each item for the Cosmetics grid, drawn by one shared
 * offscreen renderer, one item per frame so the page never stutters, and
 * cached for the rest of the session.
 */
const W = 200, H = 150
const cache = new Map<string, string>()
const waiting = new Map<string, ((url: string | null) => void)[]>()
const queue: { key: string; def: CosmeticDef; variants?: CosmeticVariants }[] = []
let renderer: THREE.WebGLRenderer | null = null
let busy = false

function setup(): THREE.WebGLRenderer | null {
  if (renderer) return renderer
  try {
    const canvas = document.createElement('canvas')
    canvas.width = W
    canvas.height = H
    renderer = new THREE.WebGLRenderer({ canvas, alpha: true, antialias: true, preserveDrawingBuffer: true })
    renderer.setSize(W, H, false)
    renderer.outputColorSpace = THREE.SRGBColorSpace
  } catch {
    renderer = null
  }
  return renderer
}

function boxes(parent: THREE.Object3D, list: ShapeBox[], mirror = 1) {
  for (const b of list) {
    const material = new THREE.MeshLambertMaterial({ color: new THREE.Color(b.color) })
    if (b.glow) { material.emissive = new THREE.Color(b.color); material.emissiveIntensity = 0.6 }
    const mesh = new THREE.Mesh(new THREE.BoxGeometry(b.w, b.h, b.d), material)
    mesh.position.set(b.x * mirror, b.y, b.z)
    if (b.rz) mesh.rotation.z = b.rz * mirror
    parent.add(mesh)
  }
}

/** The item on its own, in its anchor's space; wings as a spread pair. */
function itemObject(def: CosmeticDef, variants?: CosmeticVariants): THREE.Object3D | null {
  const root = new THREE.Group()
  const variant = variantOf(def, variants)?.id
  const wing = (side: 1 | -1) => {
    const pivot = new THREE.Group()
    pivot.position.set(side * 1.5, 0, 0)
    pivot.rotation.y = side * 0.35
    if (def.model) {
      const built = buildModel(def.model, variant)
      if (!built) return null
      const holder = new THREE.Group()
      holder.scale.x = side
      holder.add(built.root)
      pivot.add(holder)
    } else {
      const shape = shapeFor(resolveCosmetic(def, variants)!)
      if (!shape) return null
      boxes(pivot, shape.boxes, side)
    }
    return pivot
  }
  if (def.slot === 'wings') {
    const r = wing(1), l = wing(-1)
    if (!r || !l) return null
    root.add(r, l)
    return root
  }
  if (def.model) {
    const built = buildModel(def.model, variant)
    if (!built) return null
    root.add(built.root)
    return root
  }
  const shape = shapeFor(resolveCosmetic(def, variants)!)
  if (!shape || !shape.boxes.length) return null
  boxes(root, shape.boxes)
  return root
}

function draw(def: CosmeticDef, variants?: CosmeticVariants): string | null {
  const r = setup()
  const obj = r ? itemObject(def, variants) : null
  if (!r || !obj) return null
  const scene = new THREE.Scene()
  scene.add(new THREE.AmbientLight(0xffffff, 1.7))
  const sun = new THREE.DirectionalLight(0xffffff, 1.6)
  sun.position.set(0.6, 1, 0.9)
  scene.add(sun)

  // Three-quarter view from the front, or from behind for things worn on the back.
  const back = def.slot === 'wings' || def.slot === 'backpack'
  const turn = new THREE.Group()
  turn.rotation.set(0.35, back ? Math.PI + 0.55 : -0.55, 0, 'XYZ')
  turn.add(obj)
  scene.add(turn)

  const box = new THREE.Box3().setFromObject(turn)
  const size = box.getSize(new THREE.Vector3())
  const centre = box.getCenter(new THREE.Vector3())
  turn.position.sub(centre)
  const camera = new THREE.PerspectiveCamera(30, W / H, 0.1, 500)
  const fit = Math.max(size.y, size.x / (W / H)) / 2 / Math.tan(THREE.MathUtils.degToRad(15))
  camera.position.set(0, 0, fit * 1.18 + size.z / 2)
  camera.lookAt(0, 0, 0)

  r.setClearColor(0x000000, 0)
  r.render(scene, camera)
  const url = r.domElement.toDataURL('image/png')
  disposeObject(turn)
  return url
}

function pump() {
  if (busy) return
  const next = queue.shift()
  if (!next) return
  busy = true
  requestAnimationFrame(() => {
    let url: string | null = null
    try { url = draw(next.def, next.variants) } catch { url = null }
    if (url) cache.set(next.key, url)
    for (const done of waiting.get(next.key) ?? []) done(url)
    waiting.delete(next.key)
    busy = false
    pump()
  })
}

/** A picture of the item in its variant, or null when it can't be drawn (auras). */
export function thumbnail(def: CosmeticDef, variants?: CosmeticVariants): Promise<string | null> {
  if (def.slot === 'aura') return Promise.resolve(null)
  const key = `${def.id}#${variantOf(def, variants)?.id ?? ''}`
  const hit = cache.get(key)
  if (hit) return Promise.resolve(hit)
  return new Promise(resolve => {
    const list = waiting.get(key)
    if (list) { list.push(resolve); return }
    waiting.set(key, [resolve])
    queue.push({ key, def, variants })
    pump()
  })
}

export function cachedThumbnail(def: CosmeticDef, variants?: CosmeticVariants): string | undefined {
  return cache.get(`${def.id}#${variantOf(def, variants)?.id ?? ''}`)
}
