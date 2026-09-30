import * as THREE from 'three'
import { CosmeticDef, CosmeticVariants, resolveCosmetic, variantOf } from './cosmetics'
import { shapeFor, ShapeBox } from './cosmeticShapes'
import { buildModel, disposeObject, auraSpriteUrl } from './cosmeticModels'
import { pictureOf } from './cosmeticPictures'

/**
 * Pictures of each item for the Cosmetics grid. Every item and colour variant
 * is rendered ahead of time (showcase/thumbs.cjs, with drawPicture below) and
 * shipped as a PNG, so the page shows them at once. Only something without a
 * shipped picture is drawn here, by one shared offscreen renderer, one item
 * per frame so the page never stutters, and cached for the session.
 */
export const THUMB_W = 240, THUMB_H = 180
const cache = new Map<string, string>()
const waiting = new Map<string, ((url: string | null) => void)[]>()
const queue: { key: string; def: CosmeticDef; variants?: CosmeticVariants }[] = []
let renderer: THREE.WebGLRenderer | null = null
let busy = false

function setup(): THREE.WebGLRenderer | null {
  if (renderer) return renderer
  try {
    const canvas = document.createElement('canvas')
    canvas.width = THUMB_W
    canvas.height = THUMB_H
    renderer = new THREE.WebGLRenderer({ canvas, alpha: true, antialias: true, preserveDrawingBuffer: true })
    renderer.setSize(THUMB_W, THUMB_H, false)
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
    const built = def.model ? buildModel(def.model, variant) : null
    if (built) {
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
  const built = def.model ? buildModel(def.model, variant) : null
  if (built) {
    root.add(built.root)
    return root
  }
  const shape = shapeFor(resolveCosmetic(def, variants)!)
  if (!shape || !shape.boxes.length) return null
  boxes(root, shape.boxes)
  return root
}

export interface AuraParticle { x: number; y: number; z: number; s: number }

/**
 * Where an aura's particles are at one moment: the same motion per style as
 * the game (CosmeticsFeatureRenderer.renderAura), in body space (neck at 0,
 * feet at -24).
 */
export function auraParticles(style: string | undefined, age = 37): AuraParticle[] {
  const count = style === 'storm' || style === 'sphere' ? 18 : style === 'ring' ? 20 : 14
  const out: AuraParticle[] = []
  const { sin, cos, sqrt, max, PI } = Math
  for (let i = 0; i < count; i++) {
    const phase = (i * PI * 2) / count
    let x = 0, y = 0, z = 0, s = 1.3
    switch (style) {
      case 'ring': { const a = age * 0.03 + phase; x = cos(a) * 12; y = -19.5; z = sin(a) * 12; s = 2.2; break }
      case 'rising': { const climb = (age * 0.35 + i * 3.1) % 26, a = phase + climb * 0.12, r = 7 - climb * 0.12; x = cos(a) * r; y = -20 + climb; z = sin(a) * r; s = 1.6; break }
      case 'snow': { const fall = (age * 0.22 + i * 2.7) % 24, a = phase + sin(age * 0.03 + i) * 0.4, r = 9 + sin(age * 0.05 + i * 1.7) * 2.5; x = cos(a) * r; y = 2 - fall; z = sin(a) * r; s = 1.6; break }
      case 'petals': { const a = age * 0.045 + phase, r = 10 + sin(age * 0.05 + i * 2) * 2; x = cos(a) * r; y = -16 + sin(age * 0.06 + i) * 5; z = sin(a) * r; s = 2.2; break }
      case 'sphere': { const t = phase + age * 0.02, yy = cos(t * 1.7 + i) * 9, r = sqrt(max(0.5, 81 - yy * yy)), a = t * 2.3; x = cos(a) * r; y = -10 + yy; z = sin(a) * r; s = 1.5; break }
      case 'storm': { const a = age * 0.11 + phase, r = 8 + (i % 3) * 2.5 + sin(age * 0.3 + i) * 1.5; x = cos(a) * r; y = -18 + (i % 4) * 5 + sin(age * 0.25 + i * 2) * 2; z = sin(a) * r; s = 1.8; break }
      case 'bubbles': { const climb = (age * 0.18 + i * 2.4) % 24, a = phase + sin(age * 0.05 + i) * 0.6, r = 8 + sin(climb * 0.4 + i) * 2; x = cos(a) * r; y = -19 + climb; z = sin(a) * r; s = 1.4 + (i % 3) * 0.6; break }
      case 'bolts': { const a = age * 0.08 + phase, r = 9 + (i % 2) * 3; x = cos(a) * r; y = sin(age * 0.45 + i * 3) > 0.6 ? -8 : -15; z = sin(a) * r; s = 2.6; break }
      case 'notes': { const climb = (age * 0.25 + i * 3.3) % 22, a = phase + sin(climb * 0.25) * 0.8, r = 7 + sin(age * 0.04 + i) * 1.5; x = cos(a) * r; y = -14 + climb; z = sin(a) * r; s = 2; break }
      case 'leaves': { const fall = (age * 0.2 + i * 2.9) % 24, a = phase + fall * 0.22, r = 9 + sin(fall * 0.3) * 2.5; x = cos(a) * r; y = 4 - fall; z = sin(a) * r; s = 2.2; break }
      default: { const a = age * 0.05 + phase, r = 11 + sin(age * 0.04 + i) * 1.2; x = cos(a) * r; y = -18 + sin(age * 0.09 + i * 0.9) * 1.6 + (i % 3) * 2.5; z = sin(a) * r; s = 1.6 }
    }
    out.push({ x, y, z, s })
  }
  return out
}

const sprites = new Map<string, THREE.Texture>()

/** A sprite texture, crisp, loaded once. */
export function spriteTexture(url: string): THREE.Texture {
  const hit = sprites.get(url)
  if (hit) return hit
  const tex = new THREE.TextureLoader().load(url)
  sprites.set(url, tex)
  tex.magFilter = THREE.NearestFilter
  tex.minFilter = THREE.NearestFilter
  tex.colorSpace = THREE.SRGBColorSpace
  return tex
}

/** An aura: its sprites around a faint figure, so the tile shows the effect itself. */
function auraObject(def: CosmeticDef): THREE.Object3D | null {
  const url = auraSpriteUrl(def.model)
  if (!url) return null
  const root = new THREE.Group()
  // A glassy stand-in figure: head, body, arms, legs (body space, neck at 0).
  const ghost = new THREE.MeshLambertMaterial({ color: 0x9aa3b8, transparent: true, opacity: 0.2, depthWrite: false })
  for (const [x, y, w, h] of [[0, 4, 8, 8], [0, -6, 8, 12], [-6, -6, 4, 12], [6, -6, 4, 12], [-2, -18, 4, 12], [2, -18, 4, 12]]) {
    const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, 4), ghost)
    m.position.set(x, y, 0)
    root.add(m)
  }
  const material = new THREE.SpriteMaterial({ map: spriteTexture(url), alphaTest: 0.5 })
  for (const p of auraParticles(def.variant)) {
    const s = new THREE.Sprite(material)
    s.position.set(p.x, p.y, p.z)
    s.scale.setScalar(p.s * 2.8)
    root.add(s)
  }
  return root
}

/**
 * A plain stand-in for the part the item is worn on (a head, a torso), so a
 * bandana wraps something and backpack straps don't float in the air.
 */
function mannequin(def: CosmeticDef): THREE.Object3D | null {
  const parts: [number, number, number, number, number, number][] =
    // Hats only need the top of the head: the hat stays the biggest thing in the picture.
    def.slot === 'hat' ? [[0, 1.5, 0, 8, 5, 8]]
      : def.slot === 'bandana' || def.slot === 'mask' ? [[0, 0, 0, 8, 8, 8]]
      : def.slot === 'backpack' ? [[0, -6, 0, 8, 12, 4]]
        : []
  if (!parts.length) return null
  const g = new THREE.Group()
  const material = new THREE.MeshLambertMaterial({ color: 0x5b6272 })
  for (const [x, y, z, w, h, d] of parts) {
    const m = new THREE.Mesh(new THREE.BoxGeometry(w * 0.995, h * 0.995, d * 0.995), material)
    m.position.set(x, y, z)
    g.add(m)
  }
  return g
}

/** The scene for one picture, ready to render; dispose() after. */
function scene(def: CosmeticDef, variants: CosmeticVariants | undefined, w: number, h: number) {
  const aura = def.slot === 'aura'
  const obj = aura ? auraObject(def) : itemObject(def, variants)
  if (!obj) return null
  const scene = new THREE.Scene()
  scene.add(new THREE.AmbientLight(0xffffff, 1.45))
  const key = new THREE.DirectionalLight(0xffffff, 1.9)
  key.position.set(0.6, 1, 0.9)
  const rim = new THREE.DirectionalLight(0xbfd4ff, 0.8)
  rim.position.set(-0.8, 0.4, -1)
  scene.add(key, rim)

  // Three-quarter view from the front, or from behind for things worn on the back.
  const back = def.slot === 'wings' || def.slot === 'backpack'
  const turn = new THREE.Group()
  turn.rotation.set(aura ? 0.2 : 0.35, back ? Math.PI + 0.55 : -0.55, 0, 'XYZ')
  turn.add(obj)
  const stand = mannequin(def)
  if (stand) turn.add(stand)
  scene.add(turn)

  const box = new THREE.Box3().setFromObject(turn)
  const size = box.getSize(new THREE.Vector3())
  const centre = box.getCenter(new THREE.Vector3())
  turn.position.sub(centre)
  const camera = new THREE.PerspectiveCamera(30, w / h, 0.1, 500)
  const fit = Math.max(size.y, size.x / (w / h)) / 2 / Math.tan(THREE.MathUtils.degToRad(15))
  camera.position.set(0, 0, fit * (aura ? 1.08 : 1.16) + size.z / 2)
  camera.lookAt(0, 0, 0)
  return { scene, camera, dispose: () => disposeObject(turn) }
}

/**
 * Renders one item in one variant with the given renderer (sized w × h) and
 * returns the PNG data URL, or null when there's nothing to draw. The
 * build-time thumbnails (showcase/thumbs.cjs) are made with this too.
 */
export function drawPicture(r: THREE.WebGLRenderer, def: CosmeticDef, variants: CosmeticVariants | undefined, w: number, h: number): string | null {
  const s = scene(def, variants, w, h)
  if (!s) return null
  r.setClearColor(0x000000, 0)
  r.render(s.scene, s.camera)
  const url = r.domElement.toDataURL('image/png')
  s.dispose()
  return url
}

function pump() {
  if (busy) return
  const next = queue.shift()
  if (!next) return
  busy = true
  requestAnimationFrame(() => {
    let url: string | null = null
    try {
      const r = setup()
      url = r ? drawPicture(r, next.def, next.variants, THUMB_W, THUMB_H) : null
    } catch { url = null }
    if (url) cache.set(next.key, url)
    for (const done of waiting.get(next.key) ?? []) done(url)
    waiting.delete(next.key)
    busy = false
    pump()
  })
}

const keyOf = (def: CosmeticDef, variants?: CosmeticVariants) => `${def.id}#${variantOf(def, variants)?.id ?? 'default'}`

/** A picture of the item in its variant: the shipped one, or drawn now. */
export function thumbnail(def: CosmeticDef, variants?: CosmeticVariants): Promise<string | null> {
  const shipped = pictureOf(def.id, variantOf(def, variants)?.id)
  if (shipped) return Promise.resolve(shipped)
  const key = keyOf(def, variants)
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
  return pictureOf(def.id, variantOf(def, variants)?.id) ?? cache.get(keyOf(def, variants))
}
