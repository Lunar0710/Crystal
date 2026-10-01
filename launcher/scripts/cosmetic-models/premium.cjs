'use strict'
/**
 * Nexora's premium model cosmetics: the same format as defs.cjs, but painted
 * at four texels per skin pixel (density 4) with the premium materials in
 * lib.cjs, rounded shapes stepped from many cubes, layered feathers and fur,
 * glowing parts and particles (bones with the `drift` animation) that rise or
 * fall around the item. Every design is original.
 */
const L = require('./lib.cjs')

const c = (from, size, mat, extra = {}) => ({ from, size, mat, ...extra })
const glow = { glow: true }
const HI = 4

/** Particle bones: a small glowing cube each, rising (amp > 0) or falling, out of step. */
function particles(prefix, parent, points, mat, { amp = 5, speed = 1.4, size = 0.6 } = {}) {
  return points.map(([x, y, z], i) => ({
    name: `${prefix}${i}`, parent, pivot: [x, y, z], anim: 'drift', amp, speed: speed * (0.85 + (i % 3) * 0.15),
    phase: Math.round(((i * 0.381966) % 1) * 1000) / 1000,
    cubes: [c([x - size / 2, y - size / 2, z - size / 2], [size, size, size], mat, glow)],
  }))
}

// ---------------------------------------------------------------- wings

/**
 * A feather cut out of a thin plane: widening from the quill, a rounded tip,
 * a light shaft, faint barbs, colour from `a` at the root to `b` at the tip.
 * `part` is the stretch of the feather this plane shows (a glowing tip is the
 * last part on a plane of its own), so the outline runs on across both.
 */
function feather(a, b, tipFrom = 0.4, part = [0, 1]) {
  const span = part[1] - part[0]
  const at = (px, py, t) => {
    const d = t.density ?? 4, W = t.w / d, H = t.h / d / span
    const f = part[0] + (py / H)
    let hw = (W / 2) * (0.4 + 0.6 * Math.sin(Math.min(1, f * 1.8) * Math.PI / 2))
    if (f > 0.76) hw *= Math.sqrt(Math.max(0, 1 - ((f - 0.76) / 0.24) ** 2))
    return { f, x: Math.abs(px - W / 2), hw }
  }
  return L.cutout((px, py, t) => { const p = at(px, py, t); return p.x <= p.hw }, (t, px, py) => {
    const { f, x, hw } = at(px, py, t)
    let col = f > tipFrom ? L.mix(t.P[a], t.P[b], Math.min(1, (f - tipFrom) / (1 - tipFrom))) : t.P[a]
    if (x < 0.13) return L.shade(col, 1.25)
    if ((py * 3 + x * 4) % 1.3 < 0.22) col = L.shade(col, 0.93)
    return L.shade(col, 1.05 - (x / Math.max(0.3, hw)) * 0.16 + (L.noise(t.seed, t.x, t.y) - 0.5) * 0.04)
  })
}

/** One primary feather of the phoenix: the vane, then a glowing ember tip. */
const phoenixPrimary = (name, x, rot, len, z) => ({
  name, parent: 'hand', pivot: [x, -0.6, 0], rotation: [0, 0, rot], cubes: [
    c([x - 1.15, -0.6 - len * 0.7, z], [2.3, len * 0.7, 0.12], 'primary'),
    c([x - 1.15, -0.6 - len, z], [2.3, len * 0.3, 0.12], 'tip', glow),
  ],
})

const phoenixWings = {
  id: 'phoenix_wings', name: 'Phönixschwingen', slot: 'wings', anchor: 'wing', density: HI,
  palettes: {
    default: { name: 'Feuer', a: '#ff7a1a', b: '#ffe27a', c: '#b3200f', d: '#fff4c8', e: '#4a120a' },
    frost: { name: 'Frostphönix', a: '#62c8ff', b: '#eafcff', c: '#1f5fc8', d: '#ffffff', e: '#13254f' },
    void: { name: 'Nachtphönix', a: '#9a5cff', b: '#ff8ee0', c: '#3a1670', d: '#ffe0f7', e: '#140a26' },
    gold: { name: 'Sonnenphönix', a: '#f6c544', b: '#fff6c4', c: '#b37a10', d: '#ffffff', e: '#4f3608' },
  },
  mats: {
    arm: L.plume('c', 'a', 0.2),
    cov: feather('c', 'a', 0.1),
    plume: feather('c', 'a', 0.15),
    primary: feather('c', 'a', 0.2, [0, 0.7]),
    tip: L.bright(feather('a', 'b', 0.7, [0.7, 1]), 1.1),
    spark: L.bright(L.plain('d', { rim: 0, grain: 0.02 }), 1.2),
  },
  bones: [
    { name: 'arm', pivot: [0, 0, 0], rotation: [0, 0, 20], cubes: [
      c([-0.2, -1, -0.65], [7.8, 2, 1.3], 'arm'),
      // Coverts, overlapping like shingles, each a hair further back.
      ...[0.1, 1.5, 2.9, 4.3, 5.7].map((x, i) => c([x, -4.2, -0.75 - i * 0.05], [2.2, 3.8, 0.12], 'cov')),
      // Secondaries, longer towards the hand.
      ...[0.4, 1.8, 3.2, 4.6, 6.0].map((x, i) => c([x, -0.6 - (7.2 + i * 0.6), -0.2 - i * 0.05], [2.3, 7.2 + i * 0.6, 0.12], 'plume')),
    ] },
    { name: 'hand', parent: 'arm', pivot: [7.6, 0, 0], rotation: [0, 0, 16], anim: 'fold', cubes: [
      c([7.4, -0.8, -0.55], [6.6, 1.6, 1.1], 'arm'),
      ...[7.5, 9.0, 10.5, 12.0].map((x, i) => c([x, -3.6, -0.75 - i * 0.05], [2.2, 3.4, 0.12], 'cov')),
    ] },
    phoenixPrimary('p1', 8.4, 4, 11, -0.2),
    phoenixPrimary('p2', 10, 11, 12.2, -0.26),
    phoenixPrimary('p3', 11.6, 19, 12.8, -0.32),
    phoenixPrimary('p4', 13.1, 29, 12.2, -0.38),
    phoenixPrimary('p5', 13.9, 42, 10.4, -0.44),
    // Embers rising off the feather tips.
    ...particles('e', 'hand', [[9, -11.5, 0], [12, -12.5, 0.2], [15.5, -9.5, -0.2], [11, -9, 0.3], [14, -12, 0]], 'spark', { amp: 7, speed: 1.6, size: 0.5 }),
  ],
}

/**
 * A butterfly lobe painted on a cut-out plane: a fan from the root corner
 * whose reach `reach(angle)` (skin pixels) shapes the outline; colour runs
 * from `a` at the root to `b` outwards, with dark veins, a dark rim dotted
 * with light, and an eye spot. `down` lobes hang below the root.
 */
function lobe(w, h, down, reach, spot) {
  const at = (px, py) => {
    const x = px, y = down ? py : h - py
    return { r: Math.hypot(x, y), a: Math.atan2(y, x) }
  }
  return L.cutout(
    (px, py) => { const { r, a } = at(px, py); return a >= 0 && a <= Math.PI / 2 + 0.05 && r <= reach(a) },
    (t, px, py) => {
      const { r, a } = at(px, py)
      const f = r / reach(a)
      if (f > 0.9) return Math.round(a * 22) % 3 === 0 && f < 0.97 ? L.shade(t.P.d, 1) : L.shade(t.P.c, 1 + f * 0.1)
      // Veins fanning out from the root.
      const k = (a / (Math.PI / 2)) * 7
      if (r > 1.5 && Math.abs(k - Math.round(k)) < (0.14 / r) * (7 / (Math.PI / 2))) return L.shade(t.P.c, 1.25)
      const sx = Math.cos(spot[0]) * spot[1] * reach(spot[0]), sy = Math.sin(spot[0]) * spot[1] * reach(spot[0])
      const ds = Math.hypot(r * Math.cos(a) - sx, r * Math.sin(a) - sy)
      if (ds < spot[2]) return ds < spot[2] * 0.55 ? L.shade(t.P.d, 1.1) : L.shade(t.P.c, 0.9)
      return L.shade(L.mix(t.P.a, t.P.b, Math.min(1, f * 1.15)), 1.05 - f * 0.1 + (L.noise(t.seed, t.x, t.y) - 0.5) * 0.05)
    },
  )
}

const auroraWings = {
  id: 'aurora_wings', name: 'Polarlicht-Falter', slot: 'wings', anchor: 'wing', density: HI,
  palettes: {
    default: { name: 'Polarlicht', a: '#3fe0c5', b: '#7a5cff', c: '#0d1633', d: '#d4fff6' },
    monarch: { name: 'Monarch', a: '#ffae2b', b: '#d4500f', c: '#1a1210', d: '#fff3cf' },
    moon: { name: 'Mondfalter', a: '#e4f3ff', b: '#9fb4ff', c: '#28304c', d: '#ffffff' },
    rose: { name: 'Rosenfalter', a: '#ffc2dc', b: '#e0457b', c: '#2b1020', d: '#fff0f6' },
  },
  mats: {
    upper: lobe(12, 11, false, a => 7 + 5 * Math.sin(a * 2) ** 0.8, [Math.PI / 4, 0.62, 1.3]),
    lower: lobe(9, 10, true, a => 5.5 + 3 * Math.sin(a * 2) + (Math.abs(a - 1.2) < 0.13 ? 3.2 : 0), [0.75, 0.55, 0.9]),
    spot: L.bright(L.neon('d'), 1.1),
    body: L.pelt('c'),
  },
  bones: [
    { name: 'root', pivot: [0, 0, 0], cubes: [c([-0.4, -3.5, -0.6], [1, 6.5, 1], 'body')] },
    { name: 'upper', parent: 'root', pivot: [0.3, 0, 0], rotation: [0, 0, 6], anim: 'fold', cubes: [
      c([0.3, 0, -0.1], [12, 11, 0.2], 'upper'),
    ] },
    { name: 'lower', parent: 'root', pivot: [0.3, -0.2, 0], rotation: [0, 0, -4], anim: 'fold', cubes: [
      c([0.3, -10.2, -0.05], [9, 10, 0.2], 'lower'),
    ] },
    ...particles('s', 'root', [[7, 8, 0.4], [10, 3, -0.4], [5, -7, 0.3], [8, -3, 0]], 'spot', { amp: 4, speed: 1, size: 0.45 }),
  ],
}

/** A blade of the cyber wings with a glowing strip on both sides. */
const cyberBlade = (name, x, rot, len) => ({
  name, parent: 'hand', pivot: [x, -0.5, 0], rotation: [0, 0, rot], cubes: [
    c([x - 0.8, -0.5 - len, -0.3], [1.6, len, 0.6], 'panel'),
    c([x - 0.25, -0.2 - len, -0.42], [0.5, len - 0.8, 0.12], 'neon', glow),
    c([x - 0.25, -0.2 - len, 0.3], [0.5, len - 0.8, 0.12], 'neon', glow),
  ],
})

const cyberWings = {
  id: 'cyber_wings', name: 'Synthflügel', slot: 'wings', anchor: 'wing', density: HI,
  palettes: {
    default: { name: 'Neon', a: '#2b313d', b: '#3cf0ff', c: '#12151b', d: '#9aa6b8' },
    magenta: { name: 'Synthwave', a: '#2d2236', b: '#ff4fd8', c: '#140f19', d: '#a493b8' },
    gold: { name: 'Goldstahl', a: '#3a3226', b: '#ffc23f', c: '#17130d', d: '#c9b48a' },
    white: { name: 'Weißlicht', a: '#e3e8ef', b: '#3f9dff', c: '#9aa3b0', d: '#ffffff' },
  },
  mats: {
    alloy: L.plating('a'),
    joint: L.gilded('d'),
    panel: L.carbon('a'),
    neon: L.neon('b'),
  },
  bones: [
    { name: 'arm', pivot: [0, 0, 0], rotation: [0, 0, 18], cubes: [
      c([-0.9, -1, -1], [2, 2, 2], 'joint'),
      c([0.6, -0.65, -0.65], [7.4, 1.3, 1.3], 'alloy'),
      c([2, -0.25, -0.75], [4.6, 0.5, 0.1], 'neon', glow),
      c([7.4, -0.9, -0.9], [1.8, 1.8, 1.8], 'joint'),
    ] },
    { name: 'hand', parent: 'arm', pivot: [8.3, 0, 0], rotation: [0, 0, 14], anim: 'fold', cubes: [
      c([8.3, -0.5, -0.5], [6.4, 1, 1], 'alloy'),
      c([9.5, -0.2, -0.6], [4, 0.4, 0.1], 'neon', glow),
    ] },
    cyberBlade('b0', 2.5, -6, 7.5),
    cyberBlade('b1', 5.5, 0, 9.5),
    cyberBlade('b2', 9, 6, 11),
    cyberBlade('b3', 11.5, 16, 11.5),
    cyberBlade('b4', 14, 30, 10.5),
    // Data sparks drifting down off the blade tips.
    ...particles('d', 'hand', [[10, -11, 0], [13, -11.5, 0], [15.5, -9, 0]], 'neon', { amp: -5, speed: 1.8, size: 0.4 }),
  ],
}
// The first blade hangs off the arm, not the hand.
cyberWings.bones.find(b => b.name === 'b0').parent = 'arm'

// ---------------------------------------------------------------- hats

/** The halo ring: `n` segments around y, each its own bone turned to its place. */
function ring(prefix, parent, y, radius, n, thick, mat) {
  const chord = 2 * radius * Math.sin(Math.PI / n) + 0.06
  return Array.from({ length: n }, (_, i) => ({
    name: `${prefix}${i}`, parent, pivot: [0, y, 0], rotation: [0, (360 / n) * i, 0], cubes: [
      c([-chord / 2, y - thick / 2, radius - thick / 2], [chord, thick, thick], mat, glow),
    ],
  }))
}

const celestialHalo = {
  id: 'celestial_halo', name: 'Himmelsreif', slot: 'hat', anchor: 'head', density: HI,
  palettes: {
    default: { name: 'Sonnengold', a: '#ffd35e', b: '#fff6d2', c: '#ffe9a0' },
    moon: { name: 'Mondsilber', a: '#c9dcff', b: '#ffffff', c: '#9fc3ff' },
    rose: { name: 'Rosenlicht', a: '#ff8cc6', b: '#ffe3f1', c: '#ffc0e0' },
    void: { name: 'Sternenleere', a: '#9a6cff', b: '#e3d6ff', c: '#6ef0ff' },
  },
  mats: {
    gold: L.gilded('a'),
    inner: L.neon('b'),
    star: L.neon('c'),
  },
  bones: [
    { name: 'halo', pivot: [0, 8.4, 0], rotation: [-22, 0, 0], anim: 'bob', amp: 0.35, cubes: [] },
    ...ring('o', 'halo', 8.4, 3.7, 16, 0.85, 'gold'),
    { name: 'inner', parent: 'halo', pivot: [0, 8.4, 0], anim: 'spin', speed: 1.6, cubes: [] },
    ...ring('i', 'inner', 8.4, 2.7, 10, 0.3, 'inner'),
    // Three little stars circling the other way.
    { name: 'orbit', parent: 'halo', pivot: [0, 8.4, 0], anim: 'spin', speed: -2.6, cubes: [
      ...[0, 120, 240].flatMap(a => {
        const r = 4.9, x = Math.cos(a * Math.PI / 180) * r, z = Math.sin(a * Math.PI / 180) * r
        return [
          c([x - 0.45, 8.25, z - 0.15], [0.9, 0.3, 0.3], 'star', glow),
          c([x - 0.15, 7.95, z - 0.15], [0.3, 0.9, 0.3], 'star', glow),
        ]
      }),
    ] },
    // Motes of light falling from the ring.
    ...particles('m', 'halo', [[3.5, 8.1, 1], [-3, 8.1, 2], [0.5, 8.1, -3.6], [-2.4, 8.1, -2.6]], 'star', { amp: -5, speed: 1.1, size: 0.35 }),
  ],
}

/** An arch over the crown from one side to the other, stepped round. `axis` 'z' runs front to back. */
function arch(name, axis, mat) {
  const steps = [[3.7, 5.6, 1.4], [2.9, 6.9, 1.2], [1.7, 7.7, 1.2], [0.4, 8.0, 1.0]]
  const cubes = []
  for (const [d, y, h] of steps) {
    for (const s of [1, -1]) {
      const along = s * d - (s > 0 ? 0 : 0.9)
      cubes.push(axis === 'z'
        ? c([-0.45, y, along], [0.9, h, 0.9], mat)
        : c([along, y, -0.45], [0.9, h, 0.9], mat))
    }
  }
  return { name, parent: 'crown', pivot: [0, 6, 0], cubes }
}

const royalCrown = {
  id: 'royal_crown', name: 'Königskrone', slot: 'hat', anchor: 'head', density: HI,
  palettes: {
    default: { name: 'Königsgold', a: '#f2c14e', b: '#8e1b3a', c: '#e0115f', d: '#2f6bff', e: '#f4f1ea', x: '#16161a' },
    imperial: { name: 'Kaiserlich', a: '#f2c14e', b: '#4b1d7a', c: '#1fbf6a', d: '#b06bff', e: '#f4f1ea', x: '#16161a' },
    silver: { name: 'Eiskönig', a: '#dfe5ee', b: '#1d2b5c', c: '#6fd0ff', d: '#d9f6ff', e: '#ffffff', x: '#27324a' },
    obsidian: { name: 'Obsidian', a: '#4a4150', b: '#2a0e12', c: '#ff4d2a', d: '#ffb02e', e: '#d8d2c8', x: '#0c0b0e' },
  },
  mats: {
    gold: L.gilded('a'),
    velvet: L.velvet('b'),
    ruby: L.facet('c'),
    sapphire: L.facet('d'),
    pearl: L.gel('e'),
    ermine: L.ermine('e', 'x'),
  },
  bones: [
    { name: 'crown', pivot: [0, 4, 0], cubes: [
      c([-4.9, 2.9, -4.9], [9.8, 1.1, 9.8], 'ermine'),
      // Gold band.
      c([-4.6, 4, 3.9], [9.2, 1.9, 0.7], 'gold'), c([-4.6, 4, -4.6], [9.2, 1.9, 0.7], 'gold'),
      c([-4.6, 4, -3.9], [0.7, 1.9, 7.8], 'gold'), c([3.9, 4, -3.9], [0.7, 1.9, 7.8], 'gold'),
      // Velvet cap, stepped into a dome.
      c([-3.9, 4.2, -3.9], [7.8, 1.6, 7.8], 'velvet'), c([-3.3, 5.8, -3.3], [6.6, 1.1, 6.6], 'velvet'),
      c([-2.4, 6.9, -2.4], [4.8, 0.7, 4.8], 'velvet'),
      // Points: a tall fleur in the middle of each side, small ones at the corners.
      ...[[0, 4.6], [0, -4.6], [4.6, 0], [-4.6, 0]].flatMap(([x, z]) => {
        const sx = x === 0 ? 1.4 : 0.7, sz = z === 0 ? 1.4 : 0.7
        const px = x === 0 ? -0.7 : x > 0 ? 3.9 : -4.6, pz = z === 0 ? -0.7 : z > 0 ? 3.9 : -4.6
        return [
          c([px, 5.9, pz], [sx, 1.6, sz], 'gold'),
          c([px + (x === 0 ? 0.35 : 0), 7.5, pz + (z === 0 ? 0.35 : 0)], [x === 0 ? 0.7 : 0.7, 0.8, z === 0 ? 0.7 : 0.7], 'gold'),
        ]
      }),
      ...[[-4.6, -4.6], [3.6, -4.6], [-4.6, 3.6], [3.6, 3.6]].map(([x, z]) => c([x, 5.9, z], [1, 0.9, 1], 'gold')),
      ...[[-4.4, -4.4], [3.8, -4.4], [-4.4, 3.8], [3.8, 3.8]].map(([x, z]) => c([x, 6.8, z], [0.6, 0.6, 0.6], 'pearl')),
      // Gems in the band: a big ruby in front, sapphires around.
      c([-0.9, 4.25, 4.55], [1.8, 1.4, 0.4], 'ruby', glow),
      c([-3.2, 4.55, 4.55], [0.9, 0.9, 0.3], 'sapphire', glow), c([2.3, 4.55, 4.55], [0.9, 0.9, 0.3], 'sapphire', glow),
      c([-0.6, 4.4, -4.95], [1.2, 1.1, 0.35], 'sapphire', glow),
      c([-4.95, 4.4, -0.6], [0.35, 1.1, 1.2], 'ruby', glow), c([4.6, 4.4, -0.6], [0.35, 1.1, 1.2], 'ruby', glow),
    ] },
    arch('arch_z', 'z', 'gold'),
    arch('arch_x', 'x', 'gold'),
    // Orb and cross on top, slowly turning.
    { name: 'orb', parent: 'crown', pivot: [0, 9, 0], anim: 'spin', speed: 0.8, cubes: [
      c([-0.85, 8.6, -0.85], [1.7, 1.5, 1.7], 'gold'),
      c([-0.2, 10.1, -0.2], [0.4, 1.5, 0.4], 'gold'),
      c([-0.65, 10.6, -0.2], [1.3, 0.4, 0.4], 'gold'),
      c([-0.35, 9.0, 0.8], [0.7, 0.7, 0.2], 'ruby', glow),
    ] },
  ],
}

/** One horn, curling up and back from the temple, segment by segment, ending in a glowing tip. */
function horn(side) {
  const s = side === 'r' ? -1 : 1
  const segs = [[2.2, 1.9, -12, 26], [1.9, 1.9, -22, 18], [1.6, 1.8, -28, 12], [1.3, 1.7, -32, 8], [1.0, 1.5, -34, 4], [0.7, 1.3, -30, 0]]
  const bones = []
  let y = 3.7, parent = undefined
  segs.forEach(([w, h, rx, rz], i) => {
    const name = `${side}${i}`
    const last = i === segs.length - 1
    bones.push({
      name, ...(parent ? { parent } : {}), pivot: [s * 2.6, y, 1.4], rotation: [rx, 0, s * -rz], cubes: [
        c([s * 2.6 - w / 2, y, 1.4 - w / 2], [w, h, w], last ? 'tip' : 'horn', last ? glow : {}),
      ],
    })
    parent = name
    y += h
  })
  bones.push(...particles(`${side}e`, parent, [[s * 2.6, y + 0.4, 1.4]], 'ember', { amp: 4, speed: 1.3, size: 0.35 }))
  return bones
}

const emberHorns = {
  id: 'ember_horns', name: 'Glutörner', slot: 'hat', anchor: 'head', density: HI,
  palettes: {
    default: { name: 'Lava', a: '#3e2f33', b: '#ff6a1a', c: '#ffd36b' },
    ivory: { name: 'Elfenbein', a: '#e6d9bf', b: '#c79a4a', c: '#fff3cf' },
    frost: { name: 'Gletscher', a: '#cfe6ff', b: '#4fc0ff', c: '#ffffff' },
    void: { name: 'Leere', a: '#1c1430', b: '#b05cff', c: '#f0d4ff' },
  },
  mats: {
    horn: L.cracked('a', 'b'),
    tip: L.neon('c'),
    ember: L.neon('b'),
  },
  bones: [...horn('r'), ...horn('l')],
}

// ---------------------------------------------------------------- masks

/** The kitsune mask's outline on its 8.8 x 9.8 plane (px, py from the top left). */
function maskInside(px, py) {
  const X = px - 4.4, Y = 6.4 - py, ax = Math.abs(X)
  if (px < 0 || py < 0 || px > 8.8 || py > 9.8) return false
  const eye = ((ax - 2) / 1.05) ** 2 + ((Y + 0.45 - (ax - 2) * 0.3) / 0.38) ** 2 < 1
  if (eye) return false
  if (Y <= 4.2 && ax < (Y >= 1 ? 4.3 : 4.3 - (1 - Y) * 0.75)) return true
  return Y > 3.2 && Math.abs(ax - 2.9) < 1.5 * (6.4 - Y) / 3.2
}

const kitsuneMask = {
  id: 'kitsune_mask', name: 'Inari-Maske', slot: 'mask', anchor: 'head', density: HI,
  palettes: {
    default: { name: 'Schrein', a: '#f6f2ea', b: '#d8262f', c: '#d9a63a', d: '#16120f' },
    shadow: { name: 'Schatten', a: '#1d1a22', b: '#8f5cff', c: '#c9ced6', d: '#f2f2f2' },
    jade: { name: 'Jade', a: '#e8f3ea', b: '#1f9a62', c: '#d9b45a', d: '#10201a' },
  },
  mats: {
    // The mask is a cut-out plane: fox face narrowing to the chin, pointed ears,
    // eye holes the wearer's eyes show through, gold rim, red markings.
    face: L.cutout(maskInside, (t, px, py) => {
      const X = px - 4.4, Y = 6.4 - py, ax = Math.abs(X)
      const e = 0.26
      if (!maskInside(px - e, py) || !maskInside(px + e, py) || !maskInside(px, py - e) || !maskInside(px, py + e)) return L.shade(t.P.c, 1.15 - (py / 9.8) * 0.3)
      if (Y > 3.4 && Math.abs(ax - 2.9) < 0.95 * (6.2 - Y) / 3.2) return L.shade(t.P.b, 1.05)
      if (Y > 1.1 && Y < 3.7 && ax < 0.5 - (Y - 1.1) * 0.16) return Y < 1.6 ? L.shade(t.P.c, 1.2) : t.P.b
      if (ax > 1.1 && ax < 3.2 && Math.abs(Y - (1.05 + (ax - 2) * 0.35 - (ax - 2) ** 2 * 0.3)) < 0.13) return t.P.b
      for (let k = 0; k < 2; k++) if (ax > 2.1 && ax < 3.7 && Math.abs(Y - (-1.3 - k * 0.65 - (ax - 2.1) * 0.25)) < 0.12) return t.P.b
      return L.shade(t.P.a, 1.04 - (ax / 4.4) * 0.1 - Math.max(0, -Y) * 0.04 + (L.noise(t.seed, t.x, t.y) - 0.5) * 0.03)
    }),
    snout: L.onFace('pz', t => (t.y >= t.h - 4 && Math.abs(t.x + 0.5 - t.w / 2) < 2.5 ? t.P.d : L.gel('a')(t)), L.gel('a')),
    cord: L.plain('b', { grain: 0.08 }),
    tassel: L.hair('b'),
    bead: L.facet('c'),
  },
  bones: [
    { name: 'mask', pivot: [0, 0, 4.7], cubes: [
      c([-4.4, -3.4, 4.6], [8.8, 9.8, 0.25], 'face'),
      c([-1.2, -2.7, 4.8], [2.4, 1.9, 1.3], 'snout'),
      c([4.5, 0.2, -0.3], [0.35, 0.35, 4.9], 'cord'),
      c([-4.85, 0.2, -0.3], [0.35, 0.35, 4.9], 'cord'),
    ] },
    { name: 'tassel', parent: 'mask', pivot: [4.75, 0.3, 0.4], anim: 'sway', amp: 10, cubes: [
      c([4.5, -0.6, 0.15], [0.5, 0.9, 0.5], 'bead'),
      c([4.45, -3.4, 0.1], [0.6, 2.8, 0.6], 'tassel'),
    ] },
  ],
}

// ---------------------------------------------------------------- backpacks

const arcaneTome = {
  id: 'arcane_tome', name: 'Arkanes Buch', slot: 'backpack', anchor: 'body', density: HI,
  palettes: {
    default: { name: 'Arkan', a: '#4a2a6b', b: '#e3b341', c: '#f3e7c9', d: '#5a3a8a', e: '#b28cff' },
    grimoire: { name: 'Grimoire', a: '#2a0f12', b: '#a7a9b4', c: '#d9cfb4', d: '#3a1418', e: '#ff4d4d' },
    nature: { name: 'Hain', a: '#2f5d3a', b: '#d9c27a', c: '#f1ead2', d: '#24452c', e: '#7dffb0' },
    tide: { name: 'Gezeiten', a: '#163e6b', b: '#cfe3ff', c: '#eef4f8', d: '#1d4f7a', e: '#5ff0ff' },
  },
  mats: {
    cover: L.leather('a', 'b'),
    trim: L.gilded('b'),
    pages: L.pages('c', 'd'),
    rune: L.neon('e'),
  },
  bones: [
    { name: 'book', pivot: [0, -5.5, -6], rotation: [12, 0, 0], anim: 'bob', amp: 0.45, cubes: [
      c([-0.45, -8.6, -6.3], [0.9, 6.2, 0.7], 'cover'),
    ] },
    { name: 'left', parent: 'book', pivot: [0, -5.5, -6], rotation: [0, -24, 0], cubes: [
      c([-4.6, -8.6, -6.25], [4.4, 6.2, 0.35], 'cover'),
      c([-4.4, -8.3, -6.75], [4.2, 5.6, 0.5], 'pages'),
      c([-4.75, -8.75, -6.3], [0.5, 0.5, 0.45], 'trim'), c([-4.75, -2.85, -6.3], [0.5, 0.5, 0.45], 'trim'),
    ] },
    { name: 'right', parent: 'book', pivot: [0, -5.5, -6], rotation: [0, 24, 0], cubes: [
      c([0.2, -8.6, -6.25], [4.4, 6.2, 0.35], 'cover'),
      c([0.2, -8.3, -6.75], [4.2, 5.6, 0.5], 'pages'),
      c([4.25, -8.75, -6.3], [0.5, 0.5, 0.45], 'trim'), c([4.25, -2.85, -6.3], [0.5, 0.5, 0.45], 'trim'),
    ] },
    // Runes circling the book.
    { name: 'runes', parent: 'book', pivot: [0, -5.5, -7], anim: 'spin', speed: 2.2, cubes: [
      c([2.9, -5.8, -7.3], [0.6, 0.6, 0.6], 'rune', glow), c([-3.5, -5.2, -7.3], [0.6, 0.6, 0.6], 'rune', glow),
      c([-0.3, -6.2, -4.1], [0.6, 0.6, 0.6], 'rune', glow), c([-0.3, -4.9, -10.5], [0.6, 0.6, 0.6], 'rune', glow),
    ] },
    ...particles('g', 'book', [[-2, -4, -7.2], [1.8, -4.5, -7.2], [0, -3.5, -7]], 'rune', { amp: 4.5, speed: 1.2, size: 0.35 }),
  ],
}

// ---------------------------------------------------------------- pets

const babyDragon = {
  id: 'baby_dragon', name: 'Drachenjunges', slot: 'pet', anchor: 'pet', density: HI,
  palettes: {
    default: { name: 'Rubin', a: '#c0262d', b: '#f2c38b', c: '#3a1a1a', d: '#ffd23f', e: '#7a1418' },
    emerald: { name: 'Smaragd', a: '#2f9e5b', b: '#e9e3b4', c: '#1d3b2a', d: '#ff6a3a', e: '#1f6b3d' },
    sapphire: { name: 'Saphir', a: '#2f62d6', b: '#cfe3ff', c: '#1b2340', d: '#7affff', e: '#1d3d8a' },
    gold: { name: 'Goldschuppe', a: '#e0a82e', b: '#fff1c4', c: '#5a3d12', d: '#ff3a3a', e: '#b07a16' },
  },
  mats: {
    scales: t => {
      // Overlapping scales, each lit at its top, light seams instead of a dark grid.
      const row = t.y >> 2, x = t.x + (row % 2) * 2, lx = x % 4, ly = t.y % 4
      const d = Math.hypot(lx - 1.5, ly - 0.6) / 2.2
      return L.shade(t.P.a, 1.14 - d * 0.22 - (ly === 3 ? 0.08 : 0) + (L.noise(t.seed, t.x, t.y) - 0.5) * 0.05)
    },
    belly: L.plates('b'),
    horn: L.horn('c'),
    // Scalloped between the fingers, cut out of a thin plane.
    membrane: L.cutout((px, py) => py < 2.4 - 0.9 * Math.abs(Math.sin(px * Math.PI / 1.35)), (t, px, py) =>
      L.shade(L.mix(t.P.e, t.P.a, py / 2.4), 1 + (L.noise(t.seed, t.x, t.y) - 0.5) * 0.08 - (Math.round(px * 1.48) % 2 ? 0 : 0.12))),
    eye: L.neon('d'),
  },
  bones: [
    { name: 'body', pivot: [0, 0, 0], anim: 'bob', amp: 0.5, cubes: [
      c([-1.6, -1.5, -2.3], [3.2, 2.9, 4.4], 'scales'),
      c([-1.2, -1.75, -1.9], [2.4, 0.3, 3.6], 'belly'),
      c([-1.3, -1.2, 2.05], [2.6, 2.3, 0.6], 'belly'),
      ...[[-1.7, 1.1], [0.8, 1.1], [-1.7, -1.8], [0.8, -1.8]].map(([x, z]) => c([x, -2.8, z], [0.9, 1.4, 0.9], 'scales')),
      ...[-1.9, -0.7, 0.5].map(z => c([-0.25, 1.4, z], [0.5, 0.6, 0.7], 'horn')),
    ] },
    { name: 'neck', parent: 'body', pivot: [0, 0.6, 2.1], rotation: [-28, 0, 0], cubes: [
      c([-0.8, 0.2, 1.7], [1.6, 2.5, 1.6], 'scales'),
    ] },
    { name: 'head', parent: 'neck', pivot: [0, 2.7, 2.6], anim: 'look', amp: 22, cubes: [
      c([-1.3, 2.2, 1.8], [2.6, 2.2, 2.6], 'scales'),
      c([-0.9, 2.2, 4.4], [1.8, 1.25, 1.6], 'scales'),
      c([-0.8, 1.65, 2.3], [1.6, 0.6, 3.5], 'belly'),
      c([-1.4, 3.2, 3.3], [0.2, 0.6, 0.75], 'eye', glow),
      c([1.2, 3.2, 3.3], [0.2, 0.6, 0.75], 'eye', glow),
      c([-0.6, 3.1, 5.95], [0.4, 0.3, 0.1], 'horn'), c([0.2, 3.1, 5.95], [0.4, 0.3, 0.1], 'horn'),
    ] },
    { name: 'horn_l', parent: 'head', pivot: [-0.8, 4.3, 2.3], rotation: [-38, 0, 12], cubes: [c([-1.05, 4.3, 2.05], [0.5, 1.6, 0.5], 'horn')] },
    { name: 'horn_r', parent: 'head', pivot: [0.8, 4.3, 2.3], rotation: [-38, 0, -12], cubes: [c([0.55, 4.3, 2.05], [0.5, 1.6, 0.5], 'horn')] },
    { name: 'wing_l', parent: 'body', pivot: [-1.4, 1.2, 0.6], rotation: [0, 0, -30], anim: 'swayz', amp: 24, speed: 2.4, cubes: [
      c([-5.6, 1.0, 0.35], [4.2, 0.5, 0.5], 'horn'),
      c([-5.4, -1.4, 0.45], [4, 2.4, 0.15], 'membrane'),
    ] },
    { name: 'wing_r', parent: 'body', pivot: [1.4, 1.2, 0.6], rotation: [0, 0, 30], anim: 'swayz', amp: -24, speed: 2.4, cubes: [
      c([1.4, 1.0, 0.35], [4.2, 0.5, 0.5], 'horn'),
      c([1.4, -1.4, 0.45], [4, 2.4, 0.15], 'membrane'),
    ] },
    { name: 't1', parent: 'body', pivot: [0, -0.4, -2.3], rotation: [18, 0, 0], anim: 'look', amp: 18, speed: 2, cubes: [
      c([-0.7, -1, -4.6], [1.4, 1.2, 2.3], 'scales'),
    ] },
    { name: 't2', parent: 't1', pivot: [0, -0.5, -4.6], anim: 'look', amp: 26, speed: 2, phase: 0.15, cubes: [
      c([-0.5, -0.9, -6.4], [1, 0.9, 1.8], 'scales'),
    ] },
    { name: 't3', parent: 't2', pivot: [0, -0.5, -6.4], anim: 'look', amp: 30, speed: 2, phase: 0.3, cubes: [
      c([-0.35, -0.8, -7.6], [0.7, 0.6, 1.2], 'scales'),
      c([-0.9, -0.75, -8.4], [1.8, 0.5, 1.0], 'horn'),
    ] },
  ],
}

const spiritFox = {
  id: 'spirit_fox', name: 'Geisterfuchs', slot: 'pet', anchor: 'pet', density: HI,
  palettes: {
    default: { name: 'Kitsune', a: '#f4efe8', b: '#ffffff', c: '#e2342a', d: '#ff7a45', e: '#2a1a1a' },
    ember: { name: 'Glutfuchs', a: '#e8701e', b: '#fff0dc', c: '#2a1a14', d: '#ffd23f', e: '#1a1010' },
    spirit: { name: 'Geist', a: '#9fdcff', b: '#eafcff', c: '#3a6ee8', d: '#7affff', e: '#10203a' },
    night: { name: 'Nachtfuchs', a: '#2e2b3a', b: '#c9c2e0', c: '#8f5cff', d: '#d6a8ff', e: '#0e0c14' },
  },
  mats: {
    pelt: L.pelt('a'),
    fluff: L.fur('b'),
    mark: L.plain('c', { grain: 0.06, rim: 0.1 }),
    dark: L.plain('e', { rim: 0 }),
    eye: L.neon('d'),
    wisp: L.neon('d'),
    head: L.onFace('pz', t => {
      const u = (t.x + 0.5) / t.w, v = (t.y + 0.5) / t.h
      // A flame mark on the forehead.
      if (Math.abs(u - 0.5) < 0.09 - v * 0.15 && v < 0.4) return t.P.c
      return L.pelt('a')(t)
    }, L.pelt('a')),
  },
  bones: [
    { name: 'body', pivot: [0, 0, 0], anim: 'bob', amp: 0.4, cubes: [
      c([-1.4, -1.4, -2.2], [2.8, 2.6, 4.2], 'pelt'),
      c([-1.1, -1.2, 1.8], [2.2, 2.2, 0.8], 'fluff'),
      ...[[-1.4, 0.9], [0.6, 0.9], [-1.4, -1.9], [0.6, -1.9]].map(([x, z]) => c([x, -2.8, z], [0.8, 1.5, 0.8], 'pelt')),
      ...[[-1.45, 0.85], [0.55, 0.85], [-1.45, -1.95], [0.55, -1.95]].map(([x, z]) => c([x, -2.9, z], [0.9, 0.45, 0.9], 'dark')),
    ] },
    { name: 'head', parent: 'body', pivot: [0, 1, 2.2], anim: 'look', amp: 18, speed: 0.8, cubes: [
      c([-1.6, 0.6, 1.9], [3.2, 2.6, 2.6], 'head'),
      c([-2, 0.6, 2.3], [0.4, 1.3, 1.8], 'fluff'), c([1.6, 0.6, 2.3], [0.4, 1.3, 1.8], 'fluff'),
      c([-0.7, 0.7, 4.5], [1.4, 1.1, 1.4], 'fluff'),
      c([-0.3, 1.55, 5.85], [0.6, 0.4, 0.1], 'dark'),
      c([-1.05, 1.9, 4.45], [0.65, 0.45, 0.1], 'eye', glow), c([0.4, 1.9, 4.45], [0.65, 0.45, 0.1], 'eye', glow),
      c([-1.5, 3.2, 2.4], [1.0, 1.7, 0.6], 'pelt'), c([0.5, 3.2, 2.4], [1.0, 1.7, 0.6], 'pelt'),
      c([-1.3, 3.4, 2.95], [0.6, 1.2, 0.1], 'mark'), c([0.7, 3.4, 2.95], [0.6, 1.2, 0.1], 'mark'),
    ] },
    // Three tails fanning out, each swaying at its own pace, glowing at the tip.
    ...[-32, 0, 32].map((ry, i) => ({
      name: `tail${i}`, parent: 'body', pivot: [0, 0.2, -2.2], rotation: [38, ry, 0], anim: 'swayz', amp: 12, speed: 1 + i * 0.25, cubes: [
        c([-0.55, -0.4, -4.8], [1.1, 1.1, 2.6], 'pelt'),
        c([-0.75, -0.6, -6.8], [1.5, 1.5, 2.0], 'fluff'),
        c([-0.55, -0.4, -7.9], [1.1, 1.1, 1.1], 'wisp', glow),
      ],
    })),
    ...particles('w', 'body', [[-1.8, 2.5, -5.8], [0, 3.2, -6.4], [1.8, 2.5, -5.8]], 'wisp', { amp: 4, speed: 1.3, size: 0.4 }),
  ],
}

module.exports = [
  phoenixWings, auroraWings, cyberWings,
  celestialHalo, royalCrown, emberHorns,
  kitsuneMask, arcaneTome, babyDragon, spiritFox,
]
