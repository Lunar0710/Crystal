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

// ---------------------------------------------------------------- more materials
//
// The block cosmetics (blocks.cjs) are painted with these: every one of them
// is a proper surface (weave, grain, plating, scales...) rather than a flat
// colour, so the old items read like the model ones.

const grain = (c, t, k = 0.08, salt = 0) => shade(c, 1 + (noise(t.seed + salt, t.x, t.y) - 0.5) * k * 2)
const along = t => (t.w >= t.h ? { u: t.x, v: t.y, len: t.w, span: t.h } : { u: t.y, v: t.x, len: t.h, span: t.w })

/** Woven cloth: a fine checker, a seam one texel inside the rim. */
const canvas = role => t => {
  let c = shade(t.P[role], (t.x + t.y) % 2 ? 0.95 : 1.03)
  c = grain(c, t, 0.06)
  if (t.face === 'py') c = shade(c, 1.06)
  if (big(t) && edge(t)) return shade(c, 0.8)
  if (t.w >= 7 && t.h >= 5 && (t.y === 1 || t.y === t.h - 2) && t.x % 2 === 0) return shade(c, 1.18)
  return c
}

/** Felt: soft, lighter towards the top, a gentle dark rim. */
const felt = role => t => {
  const v = t.h <= 1 ? 0.5 : t.y / (t.h - 1)
  let c = grain(shade(t.P[role], 1.1 - v * 0.2), t, 0.05)
  if (t.face === 'py') c = shade(c, 1.1)
  if (big(t) && edge(t)) c = shade(c, 0.82)
  return c
}

/** Straw weave: strands crossing every two texels. */
const straw = role => t => {
  const cell = ((t.x >> 1) + (t.y >> 1)) % 2
  const inStrand = cell ? t.x % 2 : t.y % 2
  let c = shade(t.P[role], cell ? 1.06 : 0.9)
  if (inStrand) c = shade(c, 1.1)
  c = grain(c, t, 0.1)
  if (big(t) && edge(t)) c = shade(c, 0.78)
  return c
}

/** Wood: grain lines along the long side, planks, a knot now and then. */
const wood = role => t => {
  const { u, v } = along(t)
  const wave = Math.sin(u * 0.45 + noise(t.seed, 0, v) * 6) * 0.5 + 0.5
  let c = shade(t.P[role], 0.9 + wave * 0.16)
  if ((v + 1) % 4 === 0) c = shade(c, 0.74)
  if (noise(t.seed + 9, u >> 2, v >> 2) > 0.93 && (u + v) % 3 === 0) c = shade(c, 0.7)
  if (big(t) && edge(t)) c = shade(c, 0.82)
  return grain(c, t, 0.05)
}

/** Overlapping scales, row by row. */
const scales = (role, rim = null) => t => {
  const row = Math.floor(t.y / 2)
  const x = t.x + (row % 2) * 2
  const lx = x % 4, ly = t.y % 2
  let c = shade(t.P[role], ly === 0 ? 1.12 : 0.94)
  if (lx === 0 || (lx === 3 && ly === 1)) c = rim ? shade(t.P[rim], 0.9) : shade(c, 0.75)
  if (lx === 1 && ly === 0) c = shade(c, 1.18)
  return grain(c, t, 0.05)
}

/** Metal plates with seams and rivets. */
const plating = role => t => {
  let c = metal(role)(t)
  const px = t.x % 6, py = t.y % 6
  if (t.w >= 6 && px === 5) c = shade(c, 0.75)
  if (t.h >= 6 && py === 5) c = shade(c, 0.75)
  if (t.w >= 4 && t.h >= 4 && (t.x === 1 || t.x === t.w - 2) && (t.y === 1 || t.y === t.h - 2)) c = shade(c, 1.45)
  return c
}

/** Smooth plastic: a highlight along the top, a shine spot, darker rim. */
const plastic = role => t => {
  let c = shade(t.P[role], 1.05 - (t.h <= 1 ? 0 : t.y / (t.h - 1)) * 0.12)
  if (t.y === 0 && t.h > 2) c = shade(c, 1.22)
  if (t.w >= 4 && t.h >= 4 && t.x === 1 && t.y === 1) c = shade(c, 1.6)
  if (big(t) && (t.x === 0 || t.x === t.w - 1 || t.y === t.h - 1)) c = shade(c, 0.8)
  return grain(c, t, 0.03)
}

/** A lit panel: bright core, scan lines, brighter frame. */
const lit = role => t => {
  let c = shade(t.P[role], 1.18)
  if (t.y % 2 === 1 && t.h > 2) c = shade(c, 0.9)
  if (big(t) && edge(t)) c = shade(t.P[role], 1.45)
  const d = Math.abs(t.x - t.w / 2) / Math.max(1, t.w)
  return shade(c, 1.05 - d * 0.2)
}

/** Glossy eye: dark, one catchlight in the upper corner. */
const eye = role => t => {
  const base = t.P[role]
  if (t.w >= 2 && t.h >= 2 && t.x === 0 && t.y === 0) return [235, 240, 250, 255]
  return shade(base, 1 - t.y * 0.05)
}

/** Hair or bristles: streaks, lighter tips. */
const hair = role => t => {
  const streak = noise(t.seed, t.x, 0) * 0.3 - 0.15
  const v = t.h <= 1 ? 0.5 : t.y / (t.h - 1)
  return shade(t.P[role], 1.12 - v * 0.2 + streak + (noise(t.seed, t.x, t.y) - 0.5) * 0.06)
}

/** A leaf: midrib down the long side, veins off it, darker edge. */
const leaf = role => t => {
  const { u, v, span } = along(t)
  const mid = Math.floor(span / 2)
  let c = shade(t.P[role], 1 + (Math.abs(v - mid) / Math.max(1, span)) * -0.2)
  if (v === mid && span >= 3) c = shade(c, 1.35)
  else if (span >= 4 && (u + Math.abs(v - mid)) % 4 === 0) c = shade(c, 1.15)
  if (big(t) && edge(t)) c = shade(c, 0.76)
  return grain(c, t, 0.05)
}

/** Old bone: off-white, cracks, darker joints at the ends. */
const bone = role => t => {
  const { u, len } = along(t)
  let c = grain(t.P[role], t, 0.1)
  if (u === 0 || u === len - 1) c = shade(c, 0.82)
  if (noise(t.seed + 3, t.x, t.y) > 0.94) c = shade(c, 0.7)
  return c
}

/** Bandage gauze: a loose cross weave. */
const gauze = role => t => {
  let c = shade(t.P[role], (t.x % 3 === 0 || t.y % 3 === 0) ? 1.06 : 0.93)
  if (big(t) && edge(t)) c = shade(c, 0.85)
  return grain(c, t, 0.06)
}

/** Satin: diagonal sheen bands. */
const satin = role => t => {
  const band = (t.x + t.y) % 6
  const c = shade(t.P[role], band < 2 ? 1.25 : band < 3 ? 1.1 : 0.94)
  return big(t) && edge(t) ? shade(c, 0.8) : c
}

/** Horn: ridged rings across the long side, lighter towards the tip. */
const horn = role => t => {
  const { u, len } = along(t)
  let c = shade(t.P[role], 0.9 + (u / Math.max(1, len - 1)) * 0.25)
  if (u % 3 === 2) c = shade(c, 0.8)
  return grain(c, t, 0.05)
}

/** Slime or jelly: a lit corner, a darker lower edge, a core. */
const gel = role => t => {
  const fx = t.x / Math.max(1, t.w - 1), fy = t.y / Math.max(1, t.h - 1)
  let c = shade(t.P[role], 1.2 - (fx + fy) * 0.18)
  if (t.w >= 4 && t.h >= 4 && t.x === 1 && t.y <= 2) c = shade(c, 1.6)
  if (big(t) && edge(t)) c = shade(c, 0.85)
  return c
}

/** Carved pumpkin: ribs with grooves. */
const pumpkin = role => t => {
  let c = shade(t.P[role], 1.05 - Math.abs(((t.x % 4) - 1.5) / 1.5) * 0.12)
  if (t.x % 4 === 3) c = shade(c, 0.72)
  if (t.face === 'py' || t.face === 'ny') c = shade(t.P[role], 0.95)
  return grain(c, t, 0.06)
}

/** Butterfly / moth scales: soft gradient, dark veins, a light spot. */
const wingScale = (role, spot) => t => {
  const fx = t.x / Math.max(1, t.w - 1), fy = t.y / Math.max(1, t.h - 1)
  let c = shade(t.P[role], 1.12 - fx * 0.2)
  if ((t.x + t.y * 2) % 7 === 0) c = shade(c, 0.7)
  const dx = fx - 0.72, dy = fy - 0.35
  if (dx * dx + dy * dy < 0.018) c = shade(t.P[spot], 1.1)
  if (big(t) && edge(t)) c = shade(c, 0.55)
  return grain(c, t, 0.04)
}

/** Glowing ember: white-hot at the root, the colour at the tip. */
const ember = role => t => {
  const { u, len } = along(t)
  const f = u / Math.max(1, len - 1)
  let c = mix([255, 250, 220, 255], t.P[role], Math.min(1, f * 1.6))
  if (noise(t.seed, t.x, t.y) > 0.8) c = shade(c, 1.15)
  return c
}

/** Speaker grille: dots on a dark base. */
const grille = role => t => (t.x % 2 === 1 && t.y % 2 === 1 ? shade(t.P[role], 0.45) : grain(shade(t.P[role], 0.9), t, 0.05))

/** Turtle shell: plates with dark seams. */
const scute = role => t => {
  const lx = (t.x + (Math.floor(t.y / 4) % 2) * 2) % 4, ly = t.y % 4
  let c = shade(t.P[role], 1.08 - ly * 0.04)
  if (lx === 0 || ly === 0) c = shade(c, 0.66)
  return grain(c, t, 0.05)
}

// ---------------------------------------------------------------- premium materials
//
// Painted at four texels per skin pixel (def.density 4) for the premium
// models in premium.cjs: smooth gradients and real detail instead of blocks.

const uv = t => ({ u: (t.x + 0.5) / t.w, v: (t.y + 0.5) / t.h })
const front = t => t.face === 'pz' || t.face === 'nz'

/**
 * A feather: dark shaft down the middle, barbs slanting off it, the vane
 * darker towards its edges, colour running from `a` at the root to `b` at the
 * tip and a rounded tip. `tipFrom` is where the tip colour starts (0..1).
 */
const plume = (a, b, tipFrom = 0.5) => t => {
  const { v } = uv(t)
  const mid = (t.w - 1) / 2, dx = Math.abs(t.x - mid)
  if (front(t) && t.w >= 4) {
    const round = Math.min(t.h, Math.round(t.w * 0.6))
    const fromEnd = t.h - 1 - t.y
    if (fromEnd < round) {
      const k = (round - fromEnd) / (round + 0.5)
      if (dx > mid * Math.sqrt(Math.max(0, 1 - k * k)) + 0.35) return null
    }
  }
  let c = v > tipFrom ? mix(t.P[a], t.P[b], Math.min(1, (v - tipFrom) / (1 - tipFrom))) : t.P[a]
  if (front(t) && t.w >= 4) {
    if (dx < 0.6) return shade(c, 1.2)
    const barb = (t.y + dx * 1.3 + noise(t.seed, Math.round(dx), 7) * 3) % 4
    c = shade(c, barb < 1 ? 0.95 : 1.03)
    c = shade(c, 1.06 - (dx / Math.max(1, mid)) * 0.12)
  }
  return grain(c, t, 0.03)
}

/**
 * A flat shape cut out of a thin plane: `inside(px, py)` (skin pixels from
 * the face's top left) decides which texels exist, `paint(t, px, py)` colours
 * them. The plane's edges are left out, so only the shape shows, from both sides.
 */
const cutout = (inside, paint) => t => {
  if (!front(t)) return null
  const d = t.density ?? 4
  // Mirror the back face so the shape lines up seen from behind.
  const x = t.face === 'nz' ? t.w - 1 - t.x : t.x
  const px = (x + 0.5) / d, py = (t.y + 0.5) / d
  return inside(px, py, t) ? paint(t, px, py) : null
}

/** Polished gold or silver: brushed, a bright specular band near the top, darker below. */
const gilded = role => t => {
  const { u, v } = uv(t)
  let k = 1.22 - v * 0.42
  const band = Math.abs(v - 0.28)
  if (band < 0.1) k += 0.38 * (1 - band / 0.1)
  if (t.face === 'py') k = 1.35 - Math.hypot(u - 0.3, v - 0.3) * 0.3
  if (t.face === 'ny') k = 0.72
  return grain(shade(t.P[role], k), t, 0.025)
}

/** A cut gem: a bright table in the middle, light and dark facets around it, one sparkle. */
const facet = role => t => {
  const { u, v } = uv(t)
  const x = u - 0.5, y = v - 0.5
  let k
  if (Math.abs(x) + Math.abs(y) < 0.24) k = 1.42
  else if (x < 0 && y < 0) k = 1.22
  else if (y < 0) k = 1.02
  else if (x < 0) k = 0.84
  else k = 0.68
  if (Math.abs(Math.abs(x) - Math.abs(y)) < 0.05) k += 0.16
  if (t.w >= 4 && t.h >= 4 && t.x === 1 && t.y === 1) return [255, 255, 255, 255]
  return shade(t.P[role], k)
}

/** Velvet: soft folds and a lighter nap on top. */
const velvet = role => t => {
  const fold = Math.sin(t.x * 0.8 + noise(t.seed, 0, t.y >> 2) * 2.5) * 0.09
  return grain(shade(t.P[role], 0.96 + fold + (t.face === 'py' ? 0.1 : 0)), t, 0.05)
}

/** Ermine: white fur with the little black tails. */
const ermine = (fur, tail) => t => {
  const cell = 6
  const lx = t.x % cell, ly = (t.y + (Math.floor(t.x / cell) % 2) * 3) % cell
  if (lx === 3 && ly >= 1 && ly <= 3) return t.P[tail]
  if (ly === 1 && (lx === 2 || lx === 4)) return shade(t.P[tail], 1.3)
  return grain(shade(t.P[fur], 1 - noise(t.seed, t.x, 0) * 0.08), t, 0.06)
}

/** Dark rock with glowing cracks running through it (horns, embers). */
const cracked = (rock, hot) => t => {
  const w = Math.sin(t.x * 0.9 + noise(t.seed, t.x >> 2, t.y >> 2) * 4) + Math.sin(t.y * 0.55 - t.x * 0.35)
  if (Math.abs(w) < 0.12) return shade(t.P[hot], 1.25)
  if (Math.abs(w) < 0.3) return mix(t.P[rock], t.P[hot], 0.35)
  return grain(shade(t.P[rock], 0.9 + noise(t.seed, t.x >> 1, t.y >> 1) * 0.22), t, 0.04)
}

/** Fur in strands: streaks along the length, lighter tips, soft noise. */
const pelt = role => t => {
  const { v } = uv(t)
  const strand = noise(t.seed, t.x, t.y >> 2) * 0.22 - 0.11
  return grain(shade(t.P[role], 1.04 + strand - v * 0.1), t, 0.05)
}

/** Belly plates: wide bands with a dark seam between them. */
const plates = role => t => {
  const band = t.y % 4
  return grain(shade(t.P[role], band === 3 ? 0.72 : 1.08 - band * 0.06), t, 0.04)
}

/** Book pages: the paper with lines of writing on top, stacked page edges on the sides. */
const pages = (paper, ink) => t => {
  if (t.face === 'py' || front(t)) {
    if (t.y % 3 === 1 && t.x > 1 && t.x < t.w - 2 && noise(t.seed, t.x >> 1, t.y) > 0.3) return shade(t.P[ink], 1.05)
    return grain(shade(t.P[paper], 1.02), t, 0.03)
  }
  return shade(t.P[paper], t.y % 2 || t.x % 2 ? 0.92 : 0.8)
}

/** Carbon weave for machined parts. */
const carbon = role => t => {
  const cell = ((t.x >> 1) + (t.y >> 1)) % 2
  return grain(shade(t.P[role], cell ? 1.12 : 0.88), t, 0.03)
}

/** A glowing strip, hottest along its middle. */
const neon = role => t => {
  const { u, v } = uv(t)
  const d = t.w >= t.h ? Math.abs(v - 0.5) : Math.abs(u - 0.5)
  return shade(t.P[role], 1.45 - d * 1.1)
}

// ---------------------------------------------------------------- finish

/**
 * The pass every texel goes through after its material (gen.cjs), at the
 * texture's full resolution: light falling from above across each face, a
 * bevel one texel wide (lit top edge, shaded lower edge and sides) and a soft
 * highlight on top faces, so every cube reads as a lit, slightly rounded
 * solid instead of a flat sticker. Glowing cubes get a hot core instead.
 */
function finish(c, { face, x, y, w, h, glow, density }) {
  if (c[3] === 0) return c
  const u = (x + 0.5) / w, v = (y + 0.5) / h
  if (glow) {
    const d = Math.max(Math.abs(u - 0.5), Math.abs(v - 0.5)) * 2
    return shade(c, 1.16 - 0.2 * d)
  }
  let k
  if (face === 'py') k = 1.1 - 0.12 * Math.hypot(u - 0.3, v - 0.3)
  else if (face === 'ny') k = 0.86
  else k = 1.08 - 0.2 * v
  if (density >= 3) {
    if (h >= density && y === 0) k *= 1.14
    else if (h >= density && y === h - 1) k *= 0.8
    if (w >= density && (x === 0 || x === w - 1)) k *= 0.9
  }
  return shade(c, k)
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
  canvas, felt, straw, wood, scales, plating, plastic, lit, eye, hair, leaf, bone, gauze, satin, horn, gel, pumpkin,
  wingScale, ember, grille, scute,
  plume, cutout, gilded, facet, velvet, ermine, cracked, pelt, plates, pages, carbon, neon,
  finish, png,
}
