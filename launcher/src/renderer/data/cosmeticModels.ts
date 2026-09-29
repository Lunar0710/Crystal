import * as THREE from 'three'
import type { ShapeBox } from './cosmeticShapes'
import { MODEL_GEOMETRY, MODEL_TEXTURES } from './cosmeticModels.generated'

/**
 * Nexora's 3D model cosmetics in the launcher: the same model JSON the game
 * renders (CosmeticModels.java), built into three.js meshes for the preview
 * and the item thumbnails. Geometry and textures come from
 * cosmeticModels.generated.ts, which gen.cjs writes next to the game's copy.
 *
 * Model space: skin pixels, y up, +z towards the face (see defs.cjs).
 */

export type ModelFace = 'pz' | 'nz' | 'px' | 'nx' | 'py' | 'ny'
export type ModelAnim = 'sway' | 'swayz' | 'spin' | 'bob' | 'bounce' | 'fold' | 'flicker'

export interface ModelCube {
  from: [number, number, number]
  to: [number, number, number]
  glow?: boolean
  /** Texel rectangle per face: u1, v1 (top left as seen from outside), u2, v2. */
  uv: Record<ModelFace, [number, number, number, number]>
}

export interface ModelBone {
  name: string
  parent?: string
  pivot: [number, number, number]
  /** Degrees, applied X, then Y, then Z. */
  rotation?: [number, number, number]
  anim?: ModelAnim
  amp?: number
  speed?: number
  cubes: ModelCube[]
}

export interface NexoraModel {
  format: 'nexora-model/1'
  id: string
  anchor: 'head' | 'body' | 'wing'
  texture: [number, number]
  variants: string[]
  bones: ModelBone[]
}

export interface ModelVariant {
  id: string
  name: string
  /** Main colour, for the swatch. */
  color: string
  secondary: string
}

export interface ModelItemData {
  model: string
  name: string
  slot: 'hat' | 'bandana' | 'wings' | 'backpack'
  anchor: 'head' | 'body' | 'wing'
  variants: ModelVariant[]
  /** Plain boxes per variant, for games or servers that predate model cosmetics. */
  fallback: Record<string, ShapeBox[]>
}

export function modelGeometry(id: string | undefined): NexoraModel | null {
  return id ? MODEL_GEOMETRY[id] ?? null : null
}

export function modelTextureUrl(id: string, variant: string | undefined): string | null {
  const set = MODEL_TEXTURES[id]
  if (!set) return null
  return set[variant ?? 'default'] ?? set.default ?? null
}

// Corners of each face, top left, top right, bottom right, bottom left as
// seen from outside; the same table as CosmeticModels.java.
type Corner = [0 | 1, 0 | 1, 0 | 1]
const FACE_CORNERS: Record<ModelFace, { n: [number, number, number]; c: [Corner, Corner, Corner, Corner] }> = {
  pz: { n: [0, 0, 1], c: [[0, 1, 1], [1, 1, 1], [1, 0, 1], [0, 0, 1]] },
  nz: { n: [0, 0, -1], c: [[1, 1, 0], [0, 1, 0], [0, 0, 0], [1, 0, 0]] },
  px: { n: [1, 0, 0], c: [[1, 1, 1], [1, 1, 0], [1, 0, 0], [1, 0, 1]] },
  nx: { n: [-1, 0, 0], c: [[0, 1, 0], [0, 1, 1], [0, 0, 1], [0, 0, 0]] },
  py: { n: [0, 1, 0], c: [[0, 1, 0], [1, 1, 0], [1, 1, 1], [0, 1, 1]] },
  ny: { n: [0, -1, 0], c: [[0, 0, 1], [1, 0, 1], [1, 0, 0], [0, 0, 0]] },
}
const FACE_ORDER: ModelFace[] = ['pz', 'nz', 'px', 'nx', 'py', 'ny']

const textureCache = new Map<string, THREE.Texture>()

function texture(url: string): THREE.Texture {
  let tex = textureCache.get(url)
  if (!tex) {
    tex = new THREE.TextureLoader().load(url)
    tex.magFilter = THREE.NearestFilter
    tex.minFilter = THREE.NearestFilter
    tex.colorSpace = THREE.SRGBColorSpace
    tex.generateMipmaps = false
    textureCache.set(url, tex)
  }
  return tex
}

/** One bone's cubes as a single geometry, positioned relative to the bone's pivot. */
function boneGeometry(model: NexoraModel, bone: ModelBone, glow: boolean): THREE.BufferGeometry | null {
  const [tw, th] = model.texture
  const pos: number[] = [], nor: number[] = [], uv: number[] = [], idx: number[] = []
  for (const cube of bone.cubes) {
    if (!!cube.glow !== glow) continue
    for (const face of FACE_ORDER) {
      const { n, c } = FACE_CORNERS[face]
      const [u1, v1, u2, v2] = cube.uv[face]
      const uvs = [[u1, v1], [u2, v1], [u2, v2], [u1, v2]]
      const base = pos.length / 3
      c.forEach((corner, i) => {
        pos.push(
          (corner[0] ? cube.to[0] : cube.from[0]) - bone.pivot[0],
          (corner[1] ? cube.to[1] : cube.from[1]) - bone.pivot[1],
          (corner[2] ? cube.to[2] : cube.from[2]) - bone.pivot[2],
        )
        nor.push(...n)
        uv.push(uvs[i][0] / tw, 1 - uvs[i][1] / th)
      })
      // Counter-clockwise from outside: bottom left, bottom right, top right, top left.
      idx.push(base + 3, base + 2, base + 1, base + 3, base + 1, base)
    }
  }
  if (!pos.length) return null
  const geo = new THREE.BufferGeometry()
  geo.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3))
  geo.setAttribute('normal', new THREE.Float32BufferAttribute(nor, 3))
  geo.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2))
  geo.setIndex(idx)
  return geo
}

export interface BuiltModel {
  root: THREE.Group
  /** Animated bones and their rest rotation / position. */
  animated: { obj: THREE.Object3D; bone: ModelBone; rest: THREE.Euler; restY: number }[]
}

/**
 * Builds a model as three.js groups, one per bone, nested like the bones.
 * The root sits at the model's origin in its anchor space.
 */
export function buildModel(id: string, variant?: string): BuiltModel | null {
  const model = modelGeometry(id)
  const url = model ? modelTextureUrl(id, variant) : null
  if (!model || !url) return null
  const tex = texture(url)
  const solid = new THREE.MeshLambertMaterial({ map: tex, alphaTest: 0.5, side: THREE.DoubleSide })
  const lit = new THREE.MeshBasicMaterial({ map: tex, alphaTest: 0.5, side: THREE.DoubleSide })

  const root = new THREE.Group()
  const groups = new Map<string, THREE.Group>()
  const animated: BuiltModel['animated'] = []
  for (const bone of model.bones) {
    const g = new THREE.Group()
    g.name = bone.name
    const parent = bone.parent ? groups.get(bone.parent) : undefined
    const parentPivot = bone.parent ? model.bones.find(b => b.name === bone.parent)!.pivot : [0, 0, 0]
    g.position.set(bone.pivot[0] - parentPivot[0], bone.pivot[1] - parentPivot[1], bone.pivot[2] - parentPivot[2])
    const [rx, ry, rz] = (bone.rotation ?? [0, 0, 0]).map(THREE.MathUtils.degToRad)
    // X first, then Y, then Z: the matrix is Rz * Ry * Rx.
    g.rotation.set(rx, ry, rz, 'ZYX')
    for (const glow of [false, true]) {
      const geo = boneGeometry(model, bone, glow)
      if (geo) g.add(new THREE.Mesh(geo, glow ? lit : solid))
    }
    ;(parent ?? root).add(g)
    groups.set(bone.name, g)
    if (bone.anim) animated.push({ obj: g, bone, rest: g.rotation.clone(), restY: g.position.y })
  }
  return { root, animated }
}

/** Movement the animations react to, 0..1. */
export interface ModelMotion {
  /** Seconds since start. */
  time: number
  /** How fast the player walks, 0..1. */
  move: number
  /** Current wing flap angle, radians, for wing tips that trail it. */
  flap?: number
}

/**
 * Plays a model's bone animations. The same formulas as
 * CosmeticModelRenderer.java, in ticks (20 per second).
 */
export function animateModel(built: BuiltModel, m: ModelMotion) {
  const age = m.time * 20
  for (const a of built.animated) {
    const { obj, bone, rest } = a
    const amp = bone.amp ?? 8
    const speed = bone.speed ?? 1
    obj.rotation.copy(rest)
    obj.position.y = a.restY
    const deg = THREE.MathUtils.degToRad
    switch (bone.anim) {
      case 'sway':
        obj.rotation.x = rest.x + deg(amp) * (0.35 + m.move) * Math.sin(age * 0.11 * speed)
        break
      case 'swayz':
        obj.rotation.z = rest.z + deg(amp) * (0.35 + m.move) * Math.sin(age * 0.1 * speed + 1)
        break
      case 'spin':
        obj.rotation.y = rest.y + deg((age * (bone.speed ?? 2)) % 360)
        break
      case 'bob':
        obj.position.y = a.restY + (bone.amp ?? 0.4) * Math.sin(age * 0.08 * speed)
        break
      case 'bounce':
        obj.position.y = a.restY - (bone.amp ?? 0.3) * Math.abs(Math.sin(age * 0.33)) * m.move
        break
      case 'fold':
        obj.rotation.y = rest.y + (m.flap ?? 0) * 0.45
        break
      case 'flicker': {
        const s = 0.8 + 0.2 * Math.sin(age * 0.9 * speed) + 0.1 * Math.sin(age * 2.3 * speed + 1)
        obj.scale.set(1, s, 1)
        break
      }
    }
  }
}

export function disposeObject(obj: THREE.Object3D) {
  obj.traverse(o => {
    const mesh = o as THREE.Mesh
    mesh.geometry?.dispose()
    const material = mesh.material as THREE.Material | THREE.Material[] | undefined
    // Textures are shared (textureCache), materials are per model.
    if (Array.isArray(material)) material.forEach(m => m.dispose())
    else material?.dispose()
  })
}
