'use strict'
/**
 * Building blocks for Nexora's 3D cosmetic models: colours, the pixel-art
 * materials every model paints its cubes with, and a tiny PNG writer.
 *
 * Everything here is procedural and original: no texture is copied from
 * anywhere, each one is painted texel by texel from a palette.
 */
const zlib = require('zlib')

// ---------------------------------------------------------------- colours

function hex(c) {
  const h = c.replace('#', '')
  return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16), 255]
}

const clamp = v => Math.max(0, Math.min(255, Math.round(v)))

/** Multiplies a colour's brightness (k > 1 lightens, towards white above 1). */
function shade(c, k) {
  if (k <= 1) return [clamp(c[0] * k), clamp(c[1] * k), clamp(c[2] * k), c[3]]
  const t = Math.min(1, k - 1)
  return [clamp(c[0] + (255 - c[0]) * t), clamp(c[1] + (255 - c[1]) * t), clamp(c[2] + (255 - c[2]) * t), c[3]]
}

function mix(a, b, t) {
  return [clamp(a[0] + (b[0] - a[0]) * t), clamp(a[1] + (b[1] - a[1]) * t), clamp(a[2] + (b[2] - a[2]) * t), clamp(a[3] + (b[3] - a[3]) * t)]
}

const toHex = c => '#' + c.slice(0, 3).map(v => v.toString(16).padStart(2, '0')).join('')

/** Deterministic noise in 0..1 for a texel, so a model paints the same every run. */
function noise(seed, x, y) {
  let h = (seed * 374761393 + x * 668265263 + y * 2147483647) >>> 0
  h = Math.imul(h ^ (h >>> 13), 1274126177) >>> 0
  return ((h ^ (h >>> 16)) >>> 0) / 4294967295
}

// ---------------------------------------------------------------- materials
//
// A material is a function (t) => rgba or null (transparent), where t is
//   { face, x, y, w, h, P, seed }
// face: 'pz' front, 'nz' back, 'px' / 'nx' sides, 'py' top, 'ny' bottom
// x, y: texel inside the face, y = 0 at the face's top edge
// w, h: face size in texels; P: the palette (role -> rgba); seed: per cube.

const edge = t => t.x === 0 || t.y === 0 || t.x === t.w - 1 || t.y === t.h - 1
const big = t => t.w >= 3 && t.h >= 3

/** Solid colour with a little grain and a darker rim, the basic look. */
const plain = (role, { grain = 0.07, rim = 0.16, top = 1.08 } = {}) => t => {
  let c = shade(t.P[role], 1 + (noise(t.seed, t.x, t.y) - 0.5) * grain * 2)
  if (t.face === 'py') c = shade(c, top)
  if (big(t) && edge(t)) c = shade(c, 1 - rim)
  return c
}

/** Knitted wool: ribs running down, alternating rows. */
const knit = role => t => {
  const base = t.P[role]
  const rib = (t.x + (t.y % 2)) % 2 === 0 ? 1.0 : 0.86
  return shade(base, rib * (1 + (noise(t.seed, t.x, t.y) - 0.5) * 0.1))
}

/** Leather: grainy, with a stitched seam one texel inside the rim. */
const leather = (role, stitch = null) => t => {
  const base = t.P[role]
  let c = shade(base, 1 + (noise(t.seed, t.x, t.y) - 0.5) * 0.22)
  if (big(t) && edge(t)) return shade(base, 0.72)
  const inner = t.w >= 6 && t.h >= 6 && (t.x === 1 || t.y === 1 || t.x === t.w - 2 || t.y === t.h - 2)
  if (inner && (t.x + t.y) % 2 === 0) c = stitch ? t.P[stitch] : shade(base, 1.35)
  return c
}

/** Polished metal: bright at the top, darker below, one specular line. */
const metal = role => t => {
  const base = t.P[role]
  const v = t.h <= 1 ? 0.5 : t.y / (t.h - 1)
  let k = 1.3 - v * 0.5
  if (t.y === 1 && t.h > 3) k = 1.55
  if (t.face === 'py') k = 1.35
  if (big(t) && (t.x === 0 || t.x === t.w - 1)) k *= 0.8
  return shade(base, k)
}

/** Cut gem: a light corner, a dark corner and one bright facet line. */
const gem = role => t => {
  const base = t.P[role]
  const d = (t.x / Math.max(1, t.w - 1) + t.y / Math.max(1, t.h - 1)) / 2
  let k = 1.45 - d * 0.75
  if (t.x === t.y || t.x === t.y + 1) k += 0.18
  if (t.x <= 1 && t.y <= 1) k = 1.9
  return shade(base, k)
}

/** Glass lens: dark tinted with a diagonal reflection. */
const glass = (role, frame) => t => {
  if (frame && big(t) && edge(t)) return t.P[frame]
  const base = t.P[role]
  const diag = (t.x - t.y + t.h) % 7
  return shade(base, diag === 0 || diag === 1 ? 1.6 : 0.9 + (t.y / Math.max(1, t.h)) * 0.2)
}

/** Two-colour stripes. axis 'x' stripes across the face horizontally. */
const stripes = (a, b, period = 4, axis = 'y', width = 2) => t => {
  const p = axis === 'y' ? t.y : t.x
  const role = p % period < width ? a : b
  return shade(t.P[role], 1 + (noise(t.seed, t.x, t.y) - 0.5) * 0.08)
}

/** Round spots scattered over a base (toadstool caps, sprinkles). */
const spots = (base, spot, density = 0.05, radius = 1.4) => t => {
  // Spot centres on a coarse grid, jittered; a texel is spotted when it's near one.
  const cell = 5
  const cx = Math.floor(t.x / cell), cy = Math.floor(t.y / cell)
  for (let gx = cx - 1; gx <= cx + 1; gx++) {
    for (let gy = cy - 1; gy <= cy + 1; gy++) {
      if (noise(t.seed + 7, gx, gy) > density * 10) continue
      const sx = gx * cell + noise(t.seed + 1, gx, gy) * cell
      const sy = gy * cell + noise(t.seed + 2, gx, gy) * cell
      const dx = t.x + 0.5 - sx, dy = t.y + 0.5 - sy
      if (dx * dx + dy * dy <= radius * radius) return shade(t.P[spot], t.y % 2 ? 0.95 : 1.05)
    }
  }
  return plain(base, { rim: 0.12 })(t)
}

/** Little four-point stars on a base. */
const stars = (base, star, every = 7) => t => {
  const gx = Math.floor(t.x / every), gy = Math.floor(t.y / every)
  const ox = 2 + Math.floor(noise(t.seed + 3, gx, gy) * (every - 4))
  const oy = 2 + Math.floor(noise(t.seed + 4, gx, gy) * (every - 4))
  const lx = t.x - gx * every, ly = t.y - gy * every
  const show = noise(t.seed + 5, gx, gy) > 0.35
  if (show && ((lx === ox && Math.abs(ly - oy) <= 1) || (ly === oy && Math.abs(lx - ox) <= 1))) {
    return lx === ox && ly === oy ? shade(t.P[star], 1.4) : t.P[star]
  }
  return plain(base, { grain: 0.05, rim: 0.2 })(t)
}

/** Feather: light shaft down the middle, barbs, tips in the second colour. */
const feather = (a, b, tipFrom = 0.68) => t => {
  const v = t.h <= 1 ? 0 : t.y / (t.h - 1)
  let c = v > tipFrom ? mix(t.P[a], t.P[b], Math.min(1, (v - tipFrom) / (1 - tipFrom) * 1.4)) : t.P[a]
  const mid = Math.floor(t.w / 2)
  if (t.face === 'pz' || t.face === 'nz') {
    if (t.w >= 3 && t.x === mid) c = shade(c, 1.25)
    else if ((t.x + t.y) % 3 === 0) c = shade(c, 0.9)
    // Rounded, slightly ragged tip.
    if (t.y === t.h - 1 && (t.x === 0 || t.x === t.w - 1)) return null
  }
  return shade(c, 1 + (noise(t.seed, t.x, t.y) - 0.5) * 0.08)
}

/** Wing membrane: base colour with darker veins fanning out. */
const membrane = (a, vein) => t => {
  const v = (t.x * 2 + t.y) % 9
  if (v === 0) return shade(t.P[vein], 1.05)
  const k = 0.88 + (t.y / Math.max(1, t.h)) * 0.2 + (noise(t.seed, t.x, t.y) - 0.5) * 0.08
  return shade(t.P[a], k)
}

/** Fur: streaky, soft. */
const fur = role => t => {
  const streak = noise(t.seed, t.x, 0) * 0.16 - 0.08
  return shade(t.P[role], 1 + streak + (noise(t.seed, t.x, t.y) - 0.5) * 0.12)
}

/** Thruster flame: white-hot at the nozzle, fading to the tip, ragged edge. */
const flame = (hot, cool) => t => {
  const v = t.h <= 1 ? 0 : t.y / (t.h - 1)
  if (v > 0.55 && noise(t.seed, t.x, t.y) < (v - 0.55) * 1.6) return null
  return mix(shade(t.P[hot], 1.3), t.P[cool], v)
}

/** Paisley-ish swirl print: teardrops of the second colour on the first. */
const print = (base, ink) => t => {
  const cell = 6
  const lx = t.x % cell, ly = (t.y + (Math.floor(t.x / cell) % 2) * 3) % cell
  const drop = (lx - 2.5) * (lx - 2.5) + (ly - 2.2) * (ly - 2.2) * 1.6
  if (drop < 2.4) return lx === 2 && ly === 2 ? t.P[base] : t.P[ink]
  if (lx === 4 && ly === 4) return shade(t.P[ink], 1.2)
  return plain(base, { rim: 0.1 })(t)
}

/** A material that paints one face differently (a face, an emblem...). */
const onFace = (faceName, special, rest) => t => (t.face === faceName ? special(t) : rest(t))

/** Makes a material glow-coloured: brighter, used on full-bright cubes. */
const bright = (mat, k = 1.25) => t => {
  const c = mat(t)
  return c ? shade(c, k) : c
}

// ---------------------------------------------------------------- PNG

const CRC_TABLE = (() => {
  const table = new Uint32Array(256)
  for (let n = 0; n < 256; n++) {
    let c = n
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
    table[n] = c >>> 0
  }
  return table
})()

function crc32(buf) {
  let c = 0xffffffff
  for (const b of buf) c = CRC_TABLE[(c ^ b) & 0xff] ^ (c >>> 8)
  return (c ^ 0xffffffff) >>> 0
}

function chunk(type, data) {
  const len = Buffer.alloc(4)
  len.writeUInt32BE(data.length)
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data])
  const crc = Buffer.alloc(4)
  crc.writeUInt32BE(crc32(body))
  return Buffer.concat([len, body, crc])
}

/** RGBA pixels (Uint8Array, w*h*4) to PNG bytes. */
function png(width, height, rgba) {
  const header = Buffer.alloc(13)
  header.writeUInt32BE(width, 0)
  header.writeUInt32BE(height, 4)
  header[8] = 8   // bit depth
  header[9] = 6   // RGBA
  const raw = Buffer.alloc((width * 4 + 1) * height)
  for (let y = 0; y < height; y++) {
    raw[y * (width * 4 + 1)] = 0
    Buffer.from(rgba.buffer, rgba.byteOffset + y * width * 4, width * 4).copy(raw, y * (width * 4 + 1) + 1)
  }
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', header),
    chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ])
}

module.exports = {
  hex, shade, mix, toHex, noise,
  plain, knit, leather, metal, gem, glass, stripes, spots, stars, feather, membrane, fur, flame, print, onFace, bright,
  png,
}
