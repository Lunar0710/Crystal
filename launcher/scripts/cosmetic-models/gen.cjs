#!/usr/bin/env node
'use strict'
/**
 * Builds Nexora's 3D model cosmetics from defs.cjs:
 *
 *   client/src/main/resources/assets/crystal/cosmetics/models/<id>.json
 *   client/src/main/resources/assets/crystal/textures/cosmetics/models/<id>/<variant>.png
 *   launcher/src/renderer/data/cosmeticModels.generated.ts
 *
 * The game and the launcher preview read the very same geometry, so the
 * Cosmetics page shows exactly what you get in game. Each colour variant is
 * its own small texture, painted once here: nothing is recoloured at runtime.
 *
 * Run from the launcher folder: npm run gen:cosmetics
 * (--check only verifies that the committed files are up to date.)
 */
const fs = require('fs')
const path = require('path')
const L = require('./lib.cjs')
const DEFS = require('./defs.cjs')

/** Texels per skin pixel: twice the skin's resolution, crisp but still pixel art. */
const DENSITY = 2
const REPO = path.resolve(__dirname, '..', '..', '..')
const CLIENT_ASSETS = path.join(REPO, 'client', 'src', 'main', 'resources', 'assets', 'crystal')
const TS_OUT = path.join(REPO, 'launcher', 'src', 'renderer', 'data', 'cosmeticModels.generated.ts')
const FACES = ['pz', 'nz', 'px', 'nx', 'py', 'ny']
const ANIMS = new Set(['sway', 'swayz', 'spin', 'bob', 'bounce', 'fold', 'flicker'])
const MAX_FALLBACK_BOXES = 64

const round = (v, n = 4) => Math.round(v * 10 ** n) / 10 ** n

/** Texel size of each face of a cube. */
function faceSizes([w, h, d]) {
  const t = v => Math.max(1, Math.round(v * DENSITY))
  return { pz: [t(w), t(h)], nz: [t(w), t(h)], px: [t(d), t(h)], nx: [t(d), t(h)], py: [t(w), t(d)], ny: [t(w), t(d)] }
}

/** Shelf-packs every face rectangle; returns the atlas size and each rect's corner. */
function pack(rects) {
  for (const width of [64, 128, 256, 512]) {
    const sorted = [...rects].sort((a, b) => b.h - a.h || b.w - a.w)
    let x = 0, y = 0, shelf = 0, ok = true
    for (const r of sorted) {
      if (r.w > width) { ok = false; break }
      if (x + r.w > width) { x = 0; y += shelf; shelf = 0 }
      r.u = x; r.v = y
      x += r.w
      shelf = Math.max(shelf, r.h)
    }
    const height = y + shelf
    if (!ok) continue
    let h = 16
    while (h < height) h *= 2
    if (h <= width * 2) return { width, height: h }
  }
  throw new Error('model too big for a 512 texture')
}

// ---------------------------------------------------------------- matrices (for the fallback boxes)

const identity = () => [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]
function mul(a, b) {
  const r = new Array(16).fill(0)
  for (let i = 0; i < 4; i++) for (let j = 0; j < 4; j++) for (let k = 0; k < 4; k++) r[i * 4 + j] += a[i * 4 + k] * b[k * 4 + j]
  return r
}
const translate = (x, y, z) => [1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1]
function rotX(a) { const c = Math.cos(a), s = Math.sin(a); return [1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0, 0, 0, 0, 1] }
function rotY(a) { const c = Math.cos(a), s = Math.sin(a); return [c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0, 0, 0, 0, 1] }
function rotZ(a) { const c = Math.cos(a), s = Math.sin(a); return [c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1] }
const apply = (m, [x, y, z]) => [m[0] * x + m[1] * y + m[2] * z + m[3], m[4] * x + m[5] * y + m[6] * z + m[7], m[8] * x + m[9] * y + m[10] * z + m[11]]
const RAD = Math.PI / 180

// ---------------------------------------------------------------- build

function build(def) {
  const boneNames = new Set(def.bones.map(b => b.name))
  const rects = []
  const cubes = []
  def.bones.forEach((bone, bi) => {
    if (bone.parent && !boneNames.has(bone.parent)) throw new Error(`${def.id}: bone ${bone.name} has unknown parent ${bone.parent}`)
    if (bone.anim && !ANIMS.has(bone.anim)) throw new Error(`${def.id}: unknown anim ${bone.anim}`)
    bone.cubes.forEach((cube, ci) => {
      if (!def.mats[cube.mat]) throw new Error(`${def.id}: unknown material ${cube.mat}`)
      const sizes = faceSizes(cube.size)
      const faces = {}
      for (const f of FACES) {
        const r = { w: sizes[f][0], h: sizes[f][1], face: f }
        faces[f] = r
        rects.push(r)
      }
      cubes.push({ bone: bi, cube, faces, seed: (bi + 1) * 97 + ci * 13 + def.id.length })
    })
  })
  const atlas = pack(rects)

  const textures = {}
  const fallback = {}
  for (const [variant, palette] of Object.entries(def.palettes)) {
    const P = {}
    for (const [role, value] of Object.entries(palette)) if (role !== 'name') P[role] = L.hex(value)
    const px = new Uint8Array(atlas.width * atlas.height * 4)
    const faceAverage = new Map()
    for (const entry of cubes) {
      const mat = def.mats[entry.cube.mat]
      const sum = [0, 0, 0]
      let n = 0
      for (const f of FACES) {
        const r = entry.faces[f]
        for (let y = 0; y < r.h; y++) {
          for (let x = 0; x < r.w; x++) {
            const col = mat({ face: f, x, y, w: r.w, h: r.h, P, seed: entry.seed })
            if (!col) continue
            const o = ((r.v + y) * atlas.width + (r.u + x)) * 4
            px[o] = col[0]; px[o + 1] = col[1]; px[o + 2] = col[2]; px[o + 3] = col[3] ?? 255
            if (f === 'pz' || f === 'nz') { sum[0] += col[0]; sum[1] += col[1]; sum[2] += col[2]; n++ }
          }
        }
      }
      faceAverage.set(entry, n ? sum.map(v => Math.round(v / n)) : [128, 128, 128])
    }
    textures[variant] = L.png(atlas.width, atlas.height, px)
    fallback[variant] = fallbackBoxes(def, cubes, faceAverage)
  }

  const model = {
    format: 'nexora-model/1',
    id: def.id,
    anchor: def.anchor,
    texture: [atlas.width, atlas.height],
    variants: Object.keys(def.palettes),
    bones: def.bones.map((bone, bi) => {
      const out = { name: bone.name }
      if (bone.parent) out.parent = bone.parent
      out.pivot = bone.pivot
      if (bone.rotation) out.rotation = bone.rotation
      if (bone.anim) out.anim = bone.anim
      if (bone.amp !== undefined) out.amp = bone.amp
      if (bone.speed !== undefined) out.speed = bone.speed
      out.cubes = cubes.filter(e => e.bone === bi).map(e => {
        const [x, y, z] = e.cube.from, [w, h, d] = e.cube.size
        const c = { from: [x, y, z].map(v => round(v)), to: [x + w, y + h, z + d].map(v => round(v)) }
        if (e.cube.glow) c.glow = true
        c.uv = {}
        for (const f of FACES) { const r = e.faces[f]; c.uv[f] = [r.u, r.v, r.u + r.w, r.v + r.h] }
        return c
      })
      return out
    }),
  }
  return { model, textures, fallback }
}

/**
 * Plain coloured boxes for players whose game or Nexora server predates model
 * cosmetics: the same cubes, placed where the bones put them, in each cube's
 * average colour. Rotations other than around z are lost, but it reads.
 */
function fallbackBoxes(def, cubes, faceAverage) {
  const world = new Map()
  const zAngle = new Map()
  const matrixOf = name => {
    if (world.has(name)) return world.get(name)
    const bone = def.bones.find(b => b.name === name)
    const parent = bone.parent ? matrixOf(bone.parent) : identity()
    const [rx, ry, rz] = (bone.rotation ?? [0, 0, 0]).map(v => v * RAD)
    const [px, py, pz] = bone.pivot
    const m = mul(parent, mul(translate(px, py, pz), mul(rotZ(rz), mul(rotY(ry), mul(rotX(rx), translate(-px, -py, -pz))))))
    world.set(name, m)
    zAngle.set(name, (bone.parent ? zAngle.get(bone.parent) : 0) + rz)
    return m
  }
  const boxes = cubes.map(e => {
    const bone = def.bones[e.bone]
    const m = matrixOf(bone.name)
    const [x, y, z] = e.cube.from, [w, h, d] = e.cube.size
    const [cx, cy, cz] = apply(m, [x + w / 2, y + h / 2, z + d / 2])
    const box = { x: round(cx, 3), y: round(cy, 3), z: round(cz, 3), w, h, d, color: L.toHex(faceAverage.get(e)) }
    const rz = zAngle.get(bone.name)
    if (Math.abs(rz) > 1e-3) box.rz = round(rz, 4)
    if (e.cube.glow) box.glow = true
    return box
  })
  return boxes.sort((a, b) => b.w * b.h * b.d - a.w * a.h * a.d).slice(0, MAX_FALLBACK_BOXES)
}

// ---------------------------------------------------------------- write

function main() {
  const check = process.argv.includes('--check')
  const outputs = new Map()
  const tsModels = {}, tsTextures = {}, tsItems = []

  for (const def of DEFS) {
    const { model, textures, fallback } = build(def)
    outputs.set(path.join(CLIENT_ASSETS, 'cosmetics', 'models', `${def.id}.json`), JSON.stringify(model) + '\n')
    tsModels[def.id] = model
    tsTextures[def.id] = {}
    for (const [variant, bytes] of Object.entries(textures)) {
      outputs.set(path.join(CLIENT_ASSETS, 'textures', 'cosmetics', 'models', def.id, `${variant}.png`), bytes)
      tsTextures[def.id][variant] = `data:image/png;base64,${bytes.toString('base64')}`
    }
    tsItems.push({
      model: def.id,
      name: def.name,
      slot: def.slot,
      anchor: def.anchor,
      variants: Object.entries(def.palettes).map(([id, p]) => ({ id, name: p.name, color: p.a, secondary: p.b })),
      fallback,
    })
  }

  const ts = `// Generated by launcher/scripts/cosmetic-models/gen.cjs from defs.cjs. Do not edit by hand:
// change defs.cjs and run \`npm run gen:cosmetics\`.
/* eslint-disable */
import type { NexoraModel, ModelItemData } from './cosmeticModels'

export const MODEL_GEOMETRY: Record<string, NexoraModel> = ${JSON.stringify(tsModels)}

export const MODEL_TEXTURES: Record<string, Record<string, string>> = ${JSON.stringify(tsTextures, null, 1)}

export const MODEL_ITEM_DATA: ModelItemData[] = ${JSON.stringify(tsItems)}
`
  outputs.set(TS_OUT, ts)

  let stale = 0
  for (const [file, content] of outputs) {
    const buf = Buffer.isBuffer(content) ? content : Buffer.from(content, 'utf8')
    const same = fs.existsSync(file) && fs.readFileSync(file).equals(buf)
    if (same) continue
    stale++
    if (check) { console.log(`stale: ${path.relative(REPO, file)}`); continue }
    fs.mkdirSync(path.dirname(file), { recursive: true })
    fs.writeFileSync(file, buf)
  }
  const pngs = [...outputs.keys()].filter(f => f.endsWith('.png')).length
  console.log(`${DEFS.length} models, ${pngs} textures, ${check ? `${stale} stale` : `${stale} written`}`)
  if (check && stale) process.exit(1)
}

main()
