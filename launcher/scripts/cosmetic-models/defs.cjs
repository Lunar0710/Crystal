'use strict'
/**
 * Nexora's 3D model cosmetics. Every one is an original design, built from
 * cubes in bones the way Blockbench models are, and painted by the materials
 * in lib.cjs. gen.cjs turns this file into model JSON + textures for the game
 * and the launcher.
 *
 * Coordinates are skin pixels, y up, +z towards the face, the same spaces the
 * block cosmetics use (cosmeticShapes.ts):
 *   head  - origin at the centre of the head, the head spans -4..4.
 *   body  - origin at the neck; the body spans y 0..-12, its back is z = -2.
 *   wing  - one right wing in its own hinge space, x pointing away from the
 *           body. The game and the preview mirror it for the left wing and
 *           flap both with the player's movement.
 *
 * A bone: { name, parent?, pivot:[x,y,z], rotation?:[x,y,z] degrees (applied
 * X, then Y, then Z), anim?, amp?, speed?, cubes }. Cube corners are absolute
 * (not relative to the bone), like Bedrock geometry.
 *
 * Animations the renderers know (both play them the same way):
 *   sway    - swings around x, more when moving (tails, flaps, straps)
 *   swayz   - swings around z
 *   spin    - turns around y, `speed` degrees per tick
 *   bob     - floats up and down by `amp` pixels
 *   bounce  - hops with the walk cycle (backpacks)
 *   fold    - wing tip that trails the flap
 *   flicker - flame that stretches and shrinks
 */
const L = require('./lib.cjs')

const c = (from, size, mat, extra = {}) => ({ from, size, mat, ...extra })
const glow = { glow: true }

// ---------------------------------------------------------------- hats

const wizardHat = {
  id: 'wizard_hat', name: 'Sternenhut', slot: 'hat', anchor: 'head',
  palettes: {
    default: { name: 'Mitternacht', a: '#2e3192', b: '#f2c14e', c: '#fff4c2', d: '#1b1d5c' },
    crimson: { name: 'Karmesin', a: '#8f1d2c', b: '#e8b04a', c: '#fff0c9', d: '#5a0f1a' },
    forest: { name: 'Waldläufer', a: '#2f5d3a', b: '#d9c27a', c: '#f6f1c7', d: '#1b3a22' },
    frost: { name: 'Frost', a: '#dfe9f5', b: '#6aa6d8', c: '#ffffff', d: '#9fb7d4' },
  },
  mats: {
    felt: L.stars('a', 'b'),
    band: L.stripes('b', 'd', 3, 'x', 1),
    brim: L.plain('d', { grain: 0.06 }),
    star: L.bright(L.gem('c')),
  },
  bones: [
    { name: 'brim', pivot: [0, 3, 0], cubes: [
      c([-6.5, 3, -6.5], [13, 0.75, 13], 'brim'),
      c([-4.5, 3, -4.5], [9, 2, 9], 'band'),
    ] },
    { name: 'cone', parent: 'brim', pivot: [0, 5, 0], rotation: [-4, 0, 0], cubes: [
      c([-3.75, 5, -3.75], [7.5, 2.5, 7.5], 'felt'),
      c([-3, 7.5, -3], [6, 2.5, 6], 'felt'),
    ] },
    { name: 'upper', parent: 'cone', pivot: [0, 10, 0], rotation: [-12, 0, -14], cubes: [
      c([-2.2, 10, -2.2], [4.4, 2.5, 4.4], 'felt'),
      c([-1.4, 12.5, -1.4], [2.8, 2, 2.8], 'felt'),
    ] },
    { name: 'tip', parent: 'upper', pivot: [0, 14.5, 0], rotation: [-8, 0, -26], anim: 'sway', amp: 9, cubes: [
      c([-0.75, 14.5, -0.75], [1.5, 2.2, 1.5], 'felt'),
      c([-0.9, 16.6, -0.9], [1.8, 1.8, 1.8], 'star', glow),
    ] },
  ],
}

const crystalCrown = {
  id: 'crystal_crown', name: 'Kristallkrone', slot: 'hat', anchor: 'head',
  palettes: {
    default: { name: 'Gold & Aqua', a: '#e3b341', b: '#5ee7f0', c: '#b07a1c' },
    royal: { name: 'Königlich', a: '#e3b341', b: '#c03b5c', c: '#9b6a17' },
    silver: { name: 'Silber & Amethyst', a: '#c9ced6', b: '#b07cf2', c: '#7d848f' },
    obsidian: { name: 'Obsidian', a: '#2a2533', b: '#ff5f3a', c: '#17131d' },
  },
  mats: {
    gold: L.metal('a'),
    rim: L.stripes('a', 'c', 2, 'x', 1),
    gem: L.gem('b'),
  },
  bones: [
    { name: 'ring', pivot: [0, 4, 0], cubes: [
      c([-4.6, 3.4, 4], [9.2, 2, 0.6], 'gold'),
      c([-4.6, 3.4, -4.6], [9.2, 2, 0.6], 'gold'),
      c([-4.6, 3.4, -4], [0.6, 2, 8], 'gold'),
      c([4, 3.4, -4], [0.6, 2, 8], 'gold'),
      c([-4.8, 3.2, 4.2], [9.6, 0.5, 0.6], 'rim'),
      c([-4.8, 3.2, -4.8], [9.6, 0.5, 0.6], 'rim'),
      // Points: a tall one in the middle of each side, short ones at the corners.
      c([-1, 5.4, 4], [2, 2.6, 0.6], 'gold'), c([-0.5, 8, 4], [1, 0.8, 0.6], 'gold'),
      c([-1, 5.4, -4.6], [2, 2.6, 0.6], 'gold'),
      c([-4.6, 5.4, -1], [0.6, 2.6, 2], 'gold'), c([4, 5.4, -1], [0.6, 2.6, 2], 'gold'),
      c([-4.6, 5.4, 3.4], [1.2, 1.4, 1.2], 'gold'), c([3.4, 5.4, 3.4], [1.2, 1.4, 1.2], 'gold'),
      c([-4.6, 5.4, -4.6], [1.2, 1.4, 1.2], 'gold'), c([3.4, 5.4, -4.6], [1.2, 1.4, 1.2], 'gold'),
      // Gems.
      c([-0.8, 3.8, 4.5], [1.6, 1.4, 0.4], 'gem', glow),
      c([-0.6, 6, 4.5], [1.2, 1.2, 0.3], 'gem', glow),
      c([-4.9, 3.8, -0.6], [0.4, 1.2, 1.2], 'gem', glow), c([4.5, 3.8, -0.6], [0.4, 1.2, 1.2], 'gem', glow),
      c([-0.6, 3.8, -4.9], [1.2, 1.2, 0.4], 'gem', glow),
    ] },
    // A crystal floating over the crown, turning slowly.
    { name: 'floater', parent: 'ring', pivot: [0, 11, 0], rotation: [0, 45, 0], anim: 'spin', speed: 2.2, cubes: [
      c([-1, 10, -1], [2, 2, 2], 'gem', glow),
      c([-0.6, 12, -0.6], [1.2, 1, 1.2], 'gem', glow),
      c([-0.6, 9, -0.6], [1.2, 1, 1.2], 'gem', glow),
    ] },
  ],
}

const aviatorCap = {
  id: 'aviator_cap', name: 'Fliegerkappe', slot: 'hat', anchor: 'head',
  palettes: {
    default: { name: 'Sattelbraun', a: '#7a4a26', b: '#efe3c8', c: '#3b3f46', d: '#79c7e6' },
    night: { name: 'Nachtflug', a: '#2c2f36', b: '#c9c4b8', c: '#8b8f96', d: '#e0a64a' },
    olive: { name: 'Oliv', a: '#5b6136', b: '#e8dcb0', c: '#3a2d22', d: '#88e0b4' },
  },
  mats: {
    leather: L.leather('a', 'b'),
    fur: L.fur('b'),
    strap: L.stripes('c', 'a', 3, 'x', 2),
    lens: L.glass('d', 'c'),
    frame: L.metal('c'),
  },
  bones: [
    { name: 'shell', pivot: [0, 4, 0], cubes: [
      c([-4.45, 1.2, -4.45], [8.9, 3.45, 8.9], 'leather'),
      c([-4.55, 1.0, 4.1], [9.1, 0.7, 0.6], 'fur'),
      // Goggles on the forehead.
      c([-4.6, 2.1, -4.6], [9.2, 0.8, 9.2], 'strap'),
      c([-3.4, 1.7, 4.3], [2.9, 1.9, 0.7], 'frame'),
      c([0.5, 1.7, 4.3], [2.9, 1.9, 0.7], 'frame'),
      c([-3.1, 1.95, 4.75], [2.3, 1.4, 0.4], 'lens'),
      c([0.8, 1.95, 4.75], [2.3, 1.4, 0.4], 'lens'),
      c([-0.5, 2.3, 4.5], [1, 0.6, 0.4], 'frame'),
    ] },
    { name: 'flap_r', parent: 'shell', pivot: [-4.5, 1.5, 0], anim: 'swayz', amp: 6, cubes: [
      c([-4.75, -2.6, -1.8], [0.5, 4.1, 3.6], 'leather'),
      c([-4.85, -3.1, -1.4], [0.6, 0.8, 2.8], 'fur'),
    ] },
    { name: 'flap_l', parent: 'shell', pivot: [4.5, 1.5, 0], anim: 'swayz', amp: -6, cubes: [
      c([4.25, -2.6, -1.8], [0.5, 4.1, 3.6], 'leather'),
      c([4.25, -3.1, -1.4], [0.6, 0.8, 2.8], 'fur'),
    ] },
  ],
}

const toadstool = {
  id: 'toadstool_cap', name: 'Fliegenpilz', slot: 'hat', anchor: 'head',
  palettes: {
    default: { name: 'Klassisch', a: '#d7322b', b: '#fbf3e4', c: '#e8d5b0' },
    violet: { name: 'Zauberpilz', a: '#7d3ccf', b: '#f5e7ff', c: '#d9c6ee' },
    glow: { name: 'Leuchtpilz', a: '#1f7a8c', b: '#b8ffea', c: '#cde8e0' },
  },
  mats: {
    cap: L.spots('a', 'b', 0.045, 1.5),
    gills: L.stripes('c', 'b', 2, 'x', 1),
    stem: L.plain('b', { grain: 0.1 }),
  },
  bones: [
    { name: 'cap', pivot: [0, 4, 0], cubes: [
      c([-5.6, 3.6, -5.6], [11.2, 0.6, 11.2], 'gills'),
      c([-5.5, 4.2, -5.5], [11, 2, 11], 'cap'),
      c([-4.5, 6.2, -4.5], [9, 1.5, 9], 'cap'),
      c([-3, 7.7, -3], [6, 1, 6], 'cap'),
    ] },
    // A small mushroom sprouting from the side, bobbing.
    { name: 'sprout', parent: 'cap', pivot: [4, 6.2, 2.5], rotation: [0, 0, -16], anim: 'bob', amp: 0.25, cubes: [
      c([3.6, 6.2, 2.1], [0.8, 1.6, 0.8], 'stem'),
      c([2.8, 7.8, 1.3], [2.4, 0.9, 2.4], 'cap'),
    ] },
  ],
}

const frogBeanie = {
  id: 'frog_beanie', name: 'Froschmütze', slot: 'hat', anchor: 'head',
  palettes: {
    default: { name: 'Teichgrün', a: '#5caa3f', b: '#3e7d2a', c: '#f4f1e4', d: '#1c1c1c', e: '#f29bb2' },
    tree: { name: 'Laubfrosch', a: '#9ad14b', b: '#6ea12e', c: '#fbf7e6', d: '#1c1c1c', e: '#f7b267' },
    poison: { name: 'Pfeilgift', a: '#2d7ad6', b: '#1d4f94', c: '#fbe3a0', d: '#101010', e: '#ffd23f' },
  },
  mats: {
    wool: L.knit('a'),
    rib: L.knit('b'),
    eye: L.onFace('pz', t => {
      // Pupil in the middle of the front face, with a catchlight.
      const cx = t.w / 2, cy = t.h / 2
      const dx = t.x + 0.5 - cx, dy = t.y + 0.5 - cy
      if (dx * dx + dy * dy < (t.w * t.w) / 10) return (t.x === Math.floor(cx) - 1 && t.y === Math.floor(cy) - 1) ? t.P.c : t.P.d
      return L.plain('c', { rim: 0.2 })(t)
    }, L.plain('c', { rim: 0.2 })),
    lid: L.knit('a'),
    cheek: L.plain('e', { rim: 0 }),
  },
  bones: [
    { name: 'beanie', pivot: [0, 4, 0], cubes: [
      c([-4.6, 1.6, -4.6], [9.2, 1.2, 9.2], 'rib'),
      c([-4.45, 2.8, -4.45], [8.9, 2.2, 8.9], 'wool'),
      c([-3.9, 5, -3.9], [7.8, 0.9, 7.8], 'wool'),
      c([-4.6, 1.9, 4.2], [1.2, 0.7, 0.5], 'cheek'),
      c([3.4, 1.9, 4.2], [1.2, 0.7, 0.5], 'cheek'),
    ] },
    { name: 'eyes', parent: 'beanie', pivot: [0, 5.5, 1.5], anim: 'bob', amp: 0.12, cubes: [
      c([-3.7, 5.4, 0.4], [2.6, 2.3, 2.6], 'eye'),
      c([1.1, 5.4, 0.4], [2.6, 2.3, 2.6], 'eye'),
      c([-3.8, 7.3, 0.3], [2.8, 0.5, 2.8], 'lid'),
      c([1.0, 7.3, 0.3], [2.8, 0.5, 2.8], 'lid'),
    ] },
  ],
}

const neonHeadset = {
  id: 'neon_headset', name: 'Neon-Headset', slot: 'hat', anchor: 'head',
  palettes: {
    default: { name: 'Cyber', a: '#1d1f26', b: '#3cf0ff', c: '#3a3d48' },
    magenta: { name: 'Synthwave', a: '#221a2c', b: '#ff4fd8', c: '#453656' },
    lime: { name: 'Toxic', a: '#1a1f1a', b: '#9dff4a', c: '#3a4638' },
    snow: { name: 'Weiß', a: '#eceef2', b: '#58a6ff', c: '#b8bec9' },
  },
  mats: {
    shell: L.plain('a', { grain: 0.05, rim: 0.25 }),
    pad: L.leather('c'),
    neon: L.bright(L.plain('b', { grain: 0.04, rim: 0 }), 1.2),
    band: L.stripes('a', 'c', 4, 'x', 3),
  },
  bones: [
    { name: 'band', pivot: [0, 4, 0], cubes: [
      c([-4.3, 4.3, -1], [8.6, 0.9, 2], 'band'),
      c([-5, 0.4, -0.7], [0.8, 4.4, 1.4], 'shell'),
      c([4.2, 0.4, -0.7], [0.8, 4.4, 1.4], 'shell'),
      c([-3.6, 4.2, -0.8], [7.2, 0.3, 1.6], 'pad'),
      // Ear cups with a glowing ring outside.
      c([-5.6, -2.4, -1.9], [1.5, 3.8, 3.8], 'shell'),
      c([4.1, -2.4, -1.9], [1.5, 3.8, 3.8], 'shell'),
      c([-5.9, -1.7, -1.2], [0.3, 2.4, 2.4], 'neon', glow),
      c([5.6, -1.7, -1.2], [0.3, 2.4, 2.4], 'neon', glow),
      c([-1.5, 5.1, -0.4], [3, 0.3, 0.8], 'neon', glow),
    ] },
    { name: 'mic', parent: 'band', pivot: [-5.2, -1.2, 0.5], rotation: [0, -28, 0], anim: 'sway', amp: 2, cubes: [
      c([-5.45, -1.45, 0.5], [0.5, 0.5, 4.2], 'shell'),
      c([-5.6, -1.6, 4.6], [0.8, 0.8, 0.9], 'neon', glow),
    ] },
  ],
}

const propellerCap = {
  id: 'propeller_cap', name: 'Propellermütze', slot: 'hat', anchor: 'head',
  palettes: {
    default: { name: 'Jahrmarkt', a: '#e8412c', b: '#2f7de1', c: '#f7d038', d: '#48b04a' },
    pastel: { name: 'Pastell', a: '#f7a8c4', b: '#9ad0f5', c: '#fff1a8', d: '#b8e6b0' },
    mono: { name: 'Schwarzweiß', a: '#23252b', b: '#eceef2', c: '#c9ced6', d: '#6b717c' },
  },
  mats: {
    quarters: t => {
      // Four coloured segments around the cap, like a classic beanie.
      const roles = { pz: 'a', px: 'b', nz: 'a', nx: 'b', py: null, ny: 'c' }
      let role = roles[t.face]
      if (t.face === 'py') role = (t.x < t.w / 2) === (t.y < t.h / 2) ? 'a' : 'b'
      return L.plain(role, { rim: 0.14 })(t)
    },
    visor: L.plain('d', { grain: 0.05 }),
    post: L.metal('c'),
    blade: L.stripes('c', 'a', 6, 'x', 3),
  },
  bones: [
    { name: 'cap', pivot: [0, 4, 0], cubes: [
      c([-4.45, 2.6, -4.45], [8.9, 2.6, 8.9], 'quarters'),
      c([-3.6, 5.2, -3.6], [7.2, 0.8, 7.2], 'quarters'),
      c([-3.6, 2.4, 4.2], [7.2, 0.5, 2.8], 'visor'),
      c([-0.4, 6, -0.4], [0.8, 1.3, 0.8], 'post'),
    ] },
    { name: 'prop', parent: 'cap', pivot: [0, 7.3, 0], anim: 'spin', speed: 24, cubes: [
      c([-4.8, 7.3, -0.7], [9.6, 0.35, 1.4], 'blade'),
      c([-0.55, 7.2, -0.55], [1.1, 0.6, 1.1], 'post'),
    ] },
  ],
}

// ---------------------------------------------------------------- wings

const seraphWings = {
  id: 'seraph_wings', name: 'Seraph', slot: 'wings', anchor: 'wing',
  palettes: {
    default: { name: 'Himmlisch', a: '#f6f4ee', b: '#e2c26a', c: '#c9c3b4' },
    dusk: { name: 'Dämmerung', a: '#e9d9ff', b: '#9a6bff', c: '#b7a3d9' },
    fallen: { name: 'Gefallen', a: '#2b2a30', b: '#b0203a', c: '#15141a' },
  },
  mats: {
    bone: L.plain('c', { grain: 0.1 }),
    cover: L.feather('a', 'c', 0.8),
    feather: L.feather('a', 'b', 0.62),
  },
  bones: [
    { name: 'arm', pivot: [0, 0, 0], rotation: [0, 0, 22], cubes: [
      c([0, -1, -0.5], [7, 2, 1], 'bone'),
      c([0.4, -4.4, -0.45], [6.4, 3.6, 0.9], 'cover'),
      c([1, -8.5, -0.35], [1.7, 7.4, 0.5], 'feather'),
      c([2.8, -9.4, -0.35], [1.7, 8.2, 0.5], 'feather'),
      c([4.6, -10.2, -0.35], [1.7, 9, 0.5], 'feather'),
    ] },
    { name: 'hand', parent: 'arm', pivot: [7, 0, 0], rotation: [0, 0, 14], anim: 'fold', cubes: [
      c([7, -0.8, -0.45], [6.5, 1.6, 0.9], 'bone'),
      c([7.2, -3.8, -0.4], [6, 3, 0.8], 'cover'),
    ] },
    { name: 'p1', parent: 'hand', pivot: [7.6, -1, 0], rotation: [0, 0, 6], cubes: [c([6.8, -11.6, -0.3], [1.8, 10.6, 0.5], 'feather')] },
    { name: 'p2', parent: 'hand', pivot: [9.4, -1, 0], rotation: [0, 0, 12], cubes: [c([8.6, -12.4, -0.3], [1.8, 11.4, 0.5], 'feather')] },
    { name: 'p3', parent: 'hand', pivot: [11.2, -1, 0], rotation: [0, 0, 20], cubes: [c([10.4, -12.6, -0.3], [1.8, 11.6, 0.5], 'feather')] },
    { name: 'p4', parent: 'hand', pivot: [13, -0.5, 0], rotation: [0, 0, 30], cubes: [c([12.2, -11.6, -0.3], [1.8, 11.1, 0.5], 'feather')] },
    { name: 'p5', parent: 'hand', pivot: [13.4, 0, 0], rotation: [0, 0, 44], cubes: [c([12.6, -9.6, -0.3], [1.7, 9.6, 0.5], 'feather')] },
  ],
}

const dragonWings = {
  id: 'dragon_wings', name: 'Drachenschwinge', slot: 'wings', anchor: 'wing',
  palettes: {
    default: { name: 'Glut', a: '#8f2323', b: '#3a1414', c: '#2a2a2e', d: '#e8c9a0' },
    emerald: { name: 'Smaragd', a: '#2f7a4a', b: '#15361f', c: '#233026', d: '#e6e0c0' },
    abyss: { name: 'Abgrund', a: '#2b2f6b', b: '#10122e', c: '#1b1b24', d: '#b6c2ff' },
  },
  mats: {
    bone: L.plain('c', { grain: 0.12, rim: 0.25 }),
    skin: L.membrane('a', 'b'),
    claw: L.plain('d', { rim: 0 }),
  },
  bones: [
    { name: 'arm', pivot: [0, 0, 0], rotation: [0, 0, 12], cubes: [
      c([0, -0.7, -0.7], [6.5, 1.4, 1.4], 'bone'),
      c([0.5, -5.5, -0.15], [6, 5, 0.3], 'skin'),
    ] },
    { name: 'hand', parent: 'arm', pivot: [6.5, 0, 0], rotation: [0, 0, 18], anim: 'fold', cubes: [
      c([6.5, -0.55, -0.55], [5, 1.1, 1.1], 'bone'),
      c([11.1, 0.4, -0.3], [0.6, 1.4, 0.6], 'claw'),
    ] },
    { name: 'f1', parent: 'hand', pivot: [11.3, 0, 0], rotation: [0, 0, -30], cubes: [
      c([11.3, -0.3, -0.3], [8.5, 0.6, 0.6], 'bone'),
      c([11.3, -6, -0.1], [8.2, 5.7, 0.2], 'skin'),
    ] },
    { name: 'f2', parent: 'hand', pivot: [11.3, 0, 0], rotation: [0, 0, -62], cubes: [
      c([11.3, -0.3, -0.3], [9.5, 0.6, 0.6], 'bone'),
      c([11.3, -5.2, -0.1], [9.2, 4.9, 0.2], 'skin'),
    ] },
    { name: 'f3', parent: 'hand', pivot: [11.3, 0, 0], rotation: [0, 0, -95], cubes: [
      c([11.3, -0.3, -0.3], [8, 0.6, 0.6], 'bone'),
      c([11.3, -4.4, -0.1], [7.6, 4.1, 0.2], 'skin'),
    ] },
  ],
}

const prismWings = {
  id: 'prism_wings', name: 'Prisma', slot: 'wings', anchor: 'wing',
  palettes: {
    default: { name: 'Aquamarin', a: '#63e6f2', b: '#c9fbff', c: '#2b8fb3' },
    rose: { name: 'Rosenquarz', a: '#f59ac2', b: '#ffe3f0', c: '#b8527f' },
    amber: { name: 'Bernstein', a: '#f5a623', b: '#ffe6b0', c: '#a8620a' },
    void: { name: 'Leere', a: '#7a4cf0', b: '#d9c9ff', c: '#3b1f8a' },
  },
  mats: {
    shard: L.gem('a'),
    core: L.gem('b'),
    root: L.metal('c'),
  },
  bones: [
    { name: 'root', pivot: [0, 0, 0], cubes: [c([0, -1, -0.6], [2, 2, 1.2], 'root')] },
    { name: 's1', parent: 'root', pivot: [1.5, 0, 0], rotation: [0, 0, 32], anim: 'fold', cubes: [
      c([1.5, -1.5, -0.35], [12, 3, 0.7], 'shard'),
      c([13.5, -1, -0.3], [2.4, 2, 0.6], 'core', glow),
    ] },
    { name: 's2', parent: 'root', pivot: [1.5, -0.5, 0], rotation: [0, 0, 6], cubes: [
      c([1.5, -1.3, -0.3], [10, 2.6, 0.6], 'shard'),
      c([11.5, -0.9, -0.25], [2, 1.8, 0.5], 'core', glow),
    ] },
    { name: 's3', parent: 'root', pivot: [1.5, -1, 0], rotation: [0, 0, -24], cubes: [
      c([1.5, -1.1, -0.3], [8, 2.2, 0.6], 'shard'),
      c([9.5, -0.8, -0.25], [1.6, 1.6, 0.5], 'core', glow),
    ] },
    { name: 's4', parent: 'root', pivot: [1.5, -1.5, 0], rotation: [0, 0, -52], cubes: [
      c([1.5, -0.9, -0.25], [5.5, 1.8, 0.5], 'shard'),
    ] },
    { name: 'spark', parent: 's1', pivot: [9, 3.5, 0], anim: 'bob', amp: 0.6, cubes: [
      c([8.6, 3.1, -0.3], [0.8, 0.8, 0.6], 'core', glow),
    ] },
  ],
}

// ---------------------------------------------------------------- backpacks

const expeditionPack = {
  id: 'expedition_pack', name: 'Expedition', slot: 'backpack', anchor: 'body',
  palettes: {
    default: { name: 'Bergsteiger', a: '#c2572b', b: '#3d4a3c', c: '#d9c9a3', d: '#8a8f96' },
    arctic: { name: 'Arktis', a: '#e6ecf2', b: '#2e5b88', c: '#f2c14e', d: '#9aa4b0' },
    forest: { name: 'Förster', a: '#4f6b3a', b: '#2d2a24', c: '#c9b27a', d: '#7a7f86' },
  },
  mats: {
    canvas: L.plain('a', { grain: 0.12 }),
    trim: L.leather('b'),
    roll: L.stripes('c', 'b', 4, 'x', 3),
    buckle: L.metal('d'),
  },
  bones: [
    { name: 'bag', pivot: [0, -6, -2], anim: 'bounce', amp: 0.35, cubes: [
      c([-3.5, -10.6, -5.6], [7, 8.6, 3.6], 'canvas'),
      c([-3.7, -3.4, -5.8], [7.4, 1.6, 4], 'trim'),
      c([-2.5, -9.8, -6.4], [5, 3.6, 0.8], 'canvas'),
      c([-2.6, -7.2, -6.5], [5.2, 0.8, 1], 'trim'),
      c([-0.5, -7.6, -6.6], [1, 1, 0.3], 'buckle'),
      c([-4.6, -2, -4.9], [9.2, 2, 2], 'roll'),
      c([3.5, -9.4, -4.6], [1.3, 3.4, 1.3], 'buckle'),
      c([-4.8, -9, -4.6], [1.3, 2.6, 1.3], 'trim'),
    ] },
    // Shoulder straps over the front of the body.
    { name: 'straps', pivot: [0, 0, 0], cubes: [
      c([-3, -0.3, -2.1], [1.1, 0.5, 4.3], 'trim'),
      c([1.9, -0.3, -2.1], [1.1, 0.5, 4.3], 'trim'),
      c([-3, -7.4, 2.0], [1.1, 7.2, 0.35], 'trim'),
      c([1.9, -7.4, 2.0], [1.1, 7.2, 0.35], 'trim'),
      c([-3.1, -4.4, 2.15], [1.3, 0.8, 0.35], 'buckle'),
      c([1.8, -4.4, 2.15], [1.3, 0.8, 0.35], 'buckle'),
    ] },
  ],
}

const jetpack = {
  id: 'jetpack_mk2', name: 'Jetpack Mk2', slot: 'backpack', anchor: 'body',
  palettes: {
    default: { name: 'Stahl', a: '#9aa3ad', b: '#e0602a', c: '#ffd35a', d: '#3b4048' },
    rocket: { name: 'Rakete', a: '#e8ebef', b: '#c8202e', c: '#6fd3ff', d: '#2b2f36' },
    stealth: { name: 'Tarnkappe', a: '#2c3036', b: '#5a6470', c: '#b16cff', d: '#15181c' },
  },
  mats: {
    tank: L.metal('a'),
    stripe: L.stripes('b', 'a', 5, 'y', 2),
    dark: L.plain('d', { grain: 0.08 }),
    lamp: L.plain('c', { rim: 0 }),
    fire: L.flame('c', 'b'),
  },
  bones: [
    { name: 'frame', pivot: [0, -6, -2], anim: 'bounce', amp: 0.2, cubes: [
      c([-3.9, -9.4, -5.4], [3.2, 7.6, 3.2], 'stripe'),
      c([0.7, -9.4, -5.4], [3.2, 7.6, 3.2], 'stripe'),
      c([-3.6, -1.8, -5.1], [2.6, 0.9, 2.6], 'tank'),
      c([1.0, -1.8, -5.1], [2.6, 0.9, 2.6], 'tank'),
      c([-1.1, -8.4, -4.2], [2.2, 6, 2.2], 'dark'),
      c([-0.5, -4.2, -4.5], [1, 1, 0.4], 'lamp', glow),
      c([-3.5, -10.9, -5], [2.4, 1.5, 2.4], 'dark'),
      c([1.1, -10.9, -5], [2.4, 1.5, 2.4], 'dark'),
    ] },
    { name: 'flame_r', parent: 'frame', pivot: [-2.3, -10.9, -3.8], anim: 'flicker', cubes: [
      c([-3, -14.2, -4.5], [1.4, 3.3, 1.4], 'fire', glow),
    ] },
    { name: 'flame_l', parent: 'frame', pivot: [2.3, -10.9, -3.8], anim: 'flicker', speed: 1.3, cubes: [
      c([1.6, -14.2, -4.5], [1.4, 3.3, 1.4], 'fire', glow),
    ] },
  ],
}

const catPack = {
  id: 'cat_pack', name: 'Plüschkatze', slot: 'backpack', anchor: 'body',
  palettes: {
    default: { name: 'Rotkater', a: '#e88b3a', b: '#fff1e0', c: '#2a1d16', d: '#f29bb2' },
    tux: { name: 'Frack', a: '#26262b', b: '#f2f2f2', c: '#8fd14f', d: '#f29bb2' },
    grey: { name: 'Nebel', a: '#9aa0ab', b: '#e7e9ee', c: '#3a6ea5', d: '#e8a0b4' },
  },
  mats: {
    fur: L.fur('a'),
    belly: L.fur('b'),
    // The face looks backwards, away from the player: it's on the back (nz) face.
    head: L.onFace('nz', t => {
      const eyeY = Math.floor(t.h * 0.38), eyeL = Math.floor(t.w * 0.28), eyeR = t.w - 1 - Math.floor(t.w * 0.28)
      if (t.y >= eyeY && t.y <= eyeY + 1 && (t.x === eyeL || t.x === eyeL - 1 || t.x === eyeR || t.x === eyeR + 1)) {
        return (t.y === eyeY && (t.x === eyeL - 1 || t.x === eyeR)) ? t.P.b : t.P.c
      }
      const mid = t.w / 2
      if (t.y === eyeY + 3 && Math.abs(t.x + 0.5 - mid) < 1) return t.P.d
      if (t.y === eyeY + 4 && Math.abs(t.x + 0.5 - mid) >= 1 && Math.abs(t.x + 0.5 - mid) < 2.5) return t.P.c
      if (t.y > eyeY + 2 && Math.abs(t.x + 0.5 - mid) < t.w * 0.3) return L.fur('b')(t)
      return L.fur('a')(t)
    }, L.fur('a')),
    ear: L.onFace('nz', L.plain('d', { rim: 0.1 }), L.fur('a')),
  },
  bones: [
    { name: 'body', pivot: [0, -6, -2], anim: 'bounce', amp: 0.35, cubes: [
      c([-3, -10.4, -5.2], [6, 5.6, 3.2], 'fur'),
      c([-2, -10, -5.5], [4, 4.2, 0.4], 'belly'),
      c([-3.3, -5.2, -5.6], [6.6, 5, 3.6], 'head'),
      c([-3.2, -0.2, -4.4], [1.8, 1.8, 1.2], 'ear'),
      c([1.4, -0.2, -4.4], [1.8, 1.8, 1.2], 'ear'),
      c([-3.6, -11, -4.8], [1.6, 1, 1.8], 'belly'),
      c([2, -11, -4.8], [1.6, 1, 1.8], 'belly'),
      // Paws hugging the shoulders.
      c([-4.4, -2.6, -2.8], [1.3, 1.3, 3.2], 'fur'),
      c([3.1, -2.6, -2.8], [1.3, 1.3, 3.2], 'fur'),
    ] },
    { name: 'tail', parent: 'body', pivot: [2.4, -9.6, -4.6], rotation: [0, 0, -30], anim: 'swayz', amp: 14, cubes: [
      c([2.1, -9.9, -5], [4.2, 0.9, 0.9], 'fur'),
      c([5.8, -9.9, -5], [1.2, 0.9, 0.9], 'belly'),
    ] },
  ],
}

// ---------------------------------------------------------------- bandanas

const ninjaBand = {
  id: 'ninja_band', name: 'Schattenband', slot: 'bandana', anchor: 'head',
  palettes: {
    default: { name: 'Nacht', a: '#1f2430', b: '#c9ced6', c: '#e0433a' },
    crimson: { name: 'Blutmond', a: '#a4161a', b: '#e0c16a', c: '#1b1b1b' },
    sky: { name: 'Himmel', a: '#2d6fd6', b: '#e8ebef', c: '#f7d038' },
  },
  mats: {
    cloth: L.plain('a', { grain: 0.1, rim: 0.2 }),
    plate: L.onFace('pz', t => {
      // Engraved crystal mark in the middle of the plate.
      const cx = (t.w - 1) / 2, cy = (t.h - 1) / 2
      const d = Math.abs(t.x - cx) / (t.w * 0.22) + Math.abs(t.y - cy) / (t.h * 0.34)
      if (d < 1 && d > 0.55) return t.P.c
      return L.metal('b')(t)
    }, L.metal('b')),
  },
  bones: [
    { name: 'band', pivot: [0, 1.5, 0], cubes: [
      c([-4.35, 0.8, -4.35], [8.7, 1.7, 8.7], 'cloth'),
      c([-2, 0.6, 4.3], [4, 2.1, 0.35], 'plate'),
      c([-0.8, 0.7, -4.95], [1.6, 1.8, 0.7], 'cloth'),
    ] },
    { name: 'tail_a', parent: 'band', pivot: [-0.3, 1.6, -5], rotation: [28, 0, 8], anim: 'sway', amp: 14, cubes: [
      c([-0.9, -4.2, -5.2], [1.1, 5.8, 0.25], 'cloth'),
    ] },
    { name: 'tail_b', parent: 'band', pivot: [0.3, 1.6, -5], rotation: [20, 0, -12], anim: 'sway', amp: 11, speed: 1.2, cubes: [
      c([-0.1, -3.4, -5.2], [1.1, 5, 0.25], 'cloth'),
    ] },
  ],
}

const paisleyBandana = {
  id: 'paisley_bandana', name: 'Paisley', slot: 'bandana', anchor: 'head',
  palettes: {
    default: { name: 'Rot', a: '#b3202c', b: '#f4efe6' },
    navy: { name: 'Marine', a: '#1f3a6b', b: '#f4efe6' },
    black: { name: 'Schwarz', a: '#1d1d21', b: '#e9e2d0' },
    mint: { name: 'Minze', a: '#3fbf9b', b: '#123a31' },
  },
  mats: {
    cloth: L.print('a', 'b'),
  },
  bones: [
    { name: 'cloth', pivot: [0, 3, 0], cubes: [
      c([-4.4, 1.6, -4.4], [8.8, 2.6, 8.8], 'cloth'),
      c([-4.25, 4.2, -4.25], [8.5, 0.35, 8.5], 'cloth'),
      c([-1, 1.4, -5], [2, 1.9, 0.8], 'cloth'),
    ] },
    { name: 'ends', parent: 'cloth', pivot: [0, 2, -5], rotation: [34, 0, 0], anim: 'sway', amp: 9, cubes: [
      c([-1.8, -1.6, -5.3], [1.5, 3.4, 0.3], 'cloth'),
      c([0.3, -1.2, -5.3], [1.5, 3, 0.3], 'cloth'),
    ] },
  ],
}

module.exports = [
  wizardHat, crystalCrown, aviatorCap, toadstool, frogBeanie, neonHeadset, propellerCap,
  seraphWings, dragonWings, prismWings,
  expeditionPack, jetpack, catPack,
  ninjaBand, paisleyBandana,
]
