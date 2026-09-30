'use strict'
/**
 * Nexora's block cosmetics (blockItems.ts + cosmeticShapes.ts) as textured
 * models. Every box becomes a cube of the same size and place, painted with a
 * material that fits the item (knitted wool, felt, straw, wood, feathers,
 * metal plating, fur...) in that box's colour, once per colour variant. The
 * result goes through the same pipeline as the designed model items (gen.cjs),
 * so the launcher and the game draw them the same way, with real surfaces
 * instead of flat colours. The boxes themselves stay the fallback for games
 * and servers that predate model cosmetics.
 *
 * Auras have no boxes: each gets a pixel-art particle sprite (AURA_SPRITES)
 * that the launcher preview and the game draw their particles with.
 */
const fs = require('fs')
const os = require('os')
const path = require('path')
const { execFileSync } = require('child_process')
const L = require('./lib.cjs')

const LAUNCHER = path.resolve(__dirname, '..', '..')

/** Loads blockItems.ts and cosmeticShapes.ts, bundled for node with esbuild. */
function loadSources() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'nexora-blocks-'))
  const entry = path.join(dir, 'entry.ts')
  const out = path.join(dir, 'bundle.cjs')
  const data = path.join(LAUNCHER, 'src', 'renderer', 'data').replace(/\\/g, '/')
  fs.writeFileSync(entry, `export { BLOCK_ITEMS } from '${data}/blockItems'\nexport { shapeFor } from '${data}/cosmeticShapes'\n`)
  execFileSync(process.execPath, [require.resolve('esbuild/bin/esbuild', { paths: [LAUNCHER] }), entry, '--bundle', '--platform=node',
    '--format=cjs', `--outfile=${out}`, '--log-level=warning'], { stdio: 'inherit', cwd: LAUNCHER })
  const mod = require(out)
  fs.rmSync(dir, { recursive: true, force: true })
  return mod
}

// ---------------------------------------------------------------- looks
//
// Per item style: the material for boxes in the item's main colour (c), in
// its second colour (a) and in any other fixed colour (x). Each is a function
// of the cube's own palette role, so every variant paints in its colours.
// `b` in a palette is always the variant's second colour.

const M = {
  knit: r => L.knit(r), felt: r => L.felt(r), canvas: r => L.canvas(r), straw: r => L.straw(r), wood: r => L.wood(r),
  metal: r => L.metal(r), plating: r => L.plating(r), plastic: r => L.plastic(r), leather: r => L.leather(r),
  fur: r => L.fur(r), hair: r => L.hair(r), leaf: r => L.leaf(r), bone: r => L.bone(r), gauze: r => L.gauze(r),
  satin: r => L.satin(r), horn: r => L.horn(r), gel: r => L.gel(r), pumpkin: r => L.pumpkin(r), gem: r => L.gem(r),
  glass: r => L.glass(r), scales: r => L.scales(r), scute: r => L.scute(r), grille: r => L.grille(r), lit: r => L.lit(r),
  ember: r => L.ember(r), plain: r => L.plain(r, { grain: 0.1, rim: 0.2 }),
  print: r => L.print(r, 'b'), stars: r => L.stars(r, 'b'), spots: r => L.spots(r, 'b', 0.05, 1.3),
  stripes: r => L.stripes(r, 'b', 4, 'x', 2), feather: r => L.feather(r, 'b', 0.6), membrane: r => L.membrane(r, 'b'),
  wingScale: r => L.wingScale(r, 'b'), stripesY: r => L.stripes(r, 'b', 3, 'y', 1),
}

/** c: main colour, a: second colour, x: other boxes, g: glowing boxes (default gem or lit panel). */
const S = (c, a = c, x = 'plain', g = null) => ({ c, a, x, g })

const STYLES = {
  hat: {
    beanie: S('knit', 'knit'), cap: S('canvas', 'canvas'), crown: S('metal', 'gem'), tophat: S('felt', 'satin'),
    straw: S('straw', 'straw', 'canvas'), halo: S('metal', 'metal', 'plain', 'metal'), horns: S('horn', 'horn'), antenna: S('metal', 'metal'),
    ears: S('fur', 'fur'), bunny: S('fur', 'fur'), headphones: S('plastic', 'lit'), wizard: S('stars', 'gem'),
    flowers: S('satin', 'satin', 'leaf'), propeller: S('canvas', 'canvas', 'plastic'), viking: S('plating', 'leather', 'horn'),
    party: S('stars', 'stripes'), pirate: S('felt', 'metal'), chef: S('canvas', 'canvas'), cowboy: S('leather', 'leather'),
    helmet: S('plastic', 'lit'), mohawk: S('hair', 'hair'), wreath: S('leaf', 'leaf'), santa: S('felt', 'fur'),
    pumpkin: S('pumpkin', 'pumpkin', 'leaf'), graduation: S('felt', 'satin'), fedora: S('felt', 'satin'),
    samurai: S('plating', 'metal'), sombrero: S('straw', 'stripes'), bucket: S('canvas', 'canvas'),
    jester: S('felt', 'felt', 'metal'), unicorn: S('gem', 'gem'), default: S('knit', 'knit'),
  },
  bandana: {
    ninja: S('canvas', 'canvas'), headband: S('knit', 'stripesY'), knot: S('print', 'print'), bow: S('satin', 'satin'),
    wrap: S('gauze', 'gauze'), default: S('print', 'print'),
  },
  mask: {
    visor: S('lit', 'metal'), shades: S('glass', 'metal'), oni: S('plastic', 'plastic', 'plain'), kitsune: S('plastic', 'plastic', 'plain'),
    scarf: S('knit', 'knit'), neon: S('plastic', 'lit'), glasses: S('metal', 'glass'), pixel: S('plain', 'plain'),
    monocle: S('metal', 'glass'), gas: S('plating', 'plastic'), bandit: S('print', 'print'), eyepatch: S('leather', 'leather'),
    plague: S('leather', 'glass'), hockey: S('plastic', 'plastic'), clown: S('gel', 'plain'), goggles: S('leather', 'metal'),
    mustache: S('hair', 'hair'), vr: S('plastic', 'lit', 'canvas'), default: S('canvas', 'canvas'),
  },
  wings: {
    feather: S('feather', 'feather'), bat: S('membrane', 'bone'), flame: S('ember', 'ember'), insect: S('wingScale', 'wingScale'),
    butterfly: S('wingScale', 'wingScale'), mecha: S('plating', 'lit'), shard: S('gem', 'gem'), dragon: S('scales', 'membrane'),
    phoenix: S('ember', 'ember'), fairy: S('gem', 'gem'), leaf: S('leaf', 'leaf'), bone: S('bone', 'bone'), ice: S('gem', 'gem'),
    moth: S('wingScale', 'fur'), default: S('feather', 'feather'),
  },
  backpack: {
    pack: S('canvas', 'leather'), jetpack: S('plating', 'metal'), shell: S('scute', 'scute'), guitar: S('wood', 'wood'),
    katana: S('satin', 'metal'), quiver: S('leather', 'feather'), boombox: S('plastic', 'grille'), shield: S('wood', 'metal'),
    chest: S('wood', 'metal'), lantern: S('metal', 'wood'), rod: S('wood', 'wood', 'plastic'), default: S('canvas', 'leather'),
  },
  pet: {
    fox: S('fur', 'fur'), slime: S('gel', 'gel'), pumpkin: S('pumpkin', 'pumpkin', 'leaf'), ghost: S('gel', 'gel'),
    drone: S('plating', 'plastic'), bee: S('fur', 'gem'), dragon: S('scales', 'membrane'), axolotl: S('gel', 'gel'),
    penguin: S('feather', 'fur'), robot: S('plating', 'metal'), cat: S('fur', 'fur'), default: S('fur', 'fur'),
  },
}

/** Items whose boxes spin as one (a halo turning, propeller blades). */
const SPIN = {
  'ht-halo': { pivot: [0, 8, 0], speed: 1.5, all: true },
  'ht-propeller': { pivot: [0, 7.7, 0], speed: 14, select: b => Math.abs(b.y - 7.7) < 0.2 },
}

const DARK = new Set(['#141414', '#000000', '#1c1917', '#18181b'])
const round = v => Math.round(v * 1000) / 1000

/** The material key for one box. */
function lookFor(def, box, defaultColour) {
  const style = (STYLES[def.slot] ?? {})[def.variant ?? 'default'] ?? (STYLES[def.slot] ?? {}).default ?? S('plain')
  const small = [box.w, box.h, box.d].filter(v => v <= 1).length >= 2
  if (box.glow && style.g) return style.g
  if (box.glow) return small || def.slot === 'hat' || def.slot === 'wings' ? (style.c === 'ember' ? 'ember' : 'gem') : 'lit'
  if (DARK.has(defaultColour.toLowerCase()) && small) return 'eye'
  if (defaultColour.toLowerCase() === def.color.toLowerCase()) return style.c
  if (def.secondary && defaultColour.toLowerCase() === def.secondary.toLowerCase()) return style.a
  return style.x
}

const MAT_FOR = key => (key === 'eye' ? r => L.eye(r) : M[key] ?? M.plain)

function variantsOf(def) {
  return def.variants?.length ? def.variants : [{ id: 'default', name: 'Standard', color: def.color, secondary: def.secondary }]
}

/** One block item as a model def for gen.cjs, or null (auras). */
function blockDef(def, shapeFor) {
  if (def.slot === 'aura') return null
  const vars = variantsOf(def)
  const shapes = vars.map(v => shapeFor({ ...def, color: v.color, secondary: v.secondary ?? def.secondary }))
  const first = shapes[0]
  if (!first || !first.boxes.length) return null
  for (const s of shapes) {
    if (s.boxes.length !== first.boxes.length || s.boxes.some((b, i) => b.x !== first.boxes[i].x || b.w !== first.boxes[i].w)) {
      throw new Error(`${def.id}: variants change the shape`)
    }
  }
  const id = def.id.replace(/-/g, '_')
  const spin = SPIN[def.id]
  const mats = {}
  const palettes = {}
  vars.forEach((v, vi) => {
    const p = { name: v.name, a: v.color, b: v.secondary ?? def.secondary ?? v.color }
    shapes[vi].boxes.forEach((b, i) => { p[`k${i}`] = b.color })
    palettes[v.id] = p
  })
  const main = [], rotated = [], spinning = []
  first.boxes.forEach((b, i) => {
    const key = lookFor(def, b, b.color)
    mats[`m${i}`] = MAT_FOR(key)(`k${i}`)
    const cube = { from: [b.x - b.w / 2, b.y - b.h / 2, b.z - b.d / 2].map(round), size: [b.w, b.h, b.d].map(round), mat: `m${i}` }
    if (b.glow) cube.glow = true
    if (spin && (spin.all || spin.select(b))) spinning.push({ b, cube })
    else if (b.rz) rotated.push({ b, cube })
    else main.push(cube)
  })
  const bones = [{ name: 'main', pivot: [0, 0, 0], cubes: main }]
  rotated.forEach(({ b, cube }, i) => {
    bones.push({ name: `r${i}`, parent: 'main', pivot: [b.x, b.y, b.z].map(round), rotation: [0, 0, round(b.rz * 180 / Math.PI)], cubes: [cube] })
  })
  if (spinning.length) {
    // Rotated boxes inside a spinning group (none today) would lose their tilt.
    bones.push({ name: 'spin', parent: 'main', pivot: spin.pivot, anim: 'spin', speed: spin.speed, cubes: spinning.map(s => s.cube) })
  }
  return {
    id, name: def.name, slot: def.slot, anchor: first.anchor,
    // Pets are small: twice the texels, so eyes and fur keep their detail.
    density: def.slot === 'pet' ? 4 : 2,
    palettes, mats, bones,
  }
}

// ---------------------------------------------------------------- aura sprites
//
// '#' main colour, 'o' second colour, 'w' near white. Edges are lit on the
// top left and shaded on the bottom right automatically.

const SPRITES = {
  flake: [
    '.....#.....', '...#.#.#...', '....#w#....', '.#...#...#.', '..#..o..#..', '###oowoo###',
    '..#..o..#..', '.#...#...#.', '....#w#....', '...#.#.#...', '.....#.....'],
  flame: [
    '.....#.....', '....##.....', '....###....', '...####.#..', '..##o##.#..', '..#oo####..',
    '.##oww###..', '.#ooww#o#..', '.#owwwwo#..', '..#owwo#...', '...####....'],
  drop: [
    '.....#.....', '.....#.....', '....###....', '....###....', '...#####...', '..##w####..',
    '..#ww####..', '.##w######.', '.####o####.', '..##ooo##..', '...#####...'],
  rune: [
    '.....#.....', '....#o#....', '...#o.o#...', '..#o...o#..', '.#o..#..o#.', '#o..#w#..o#',
    '.#o..#..o#.', '..#o...o#..', '...#o.o#...', '....#o#....', '.....#.....'],
  petal: [
    '...........', '....##.....', '...####....', '..##w###...', '..#ww####..', '.###w####..',
    '.####o####.', '..###oo###.', '...######..', '....####...', '......#....'],
  wisp: [
    '...####....', '..#oooo#...', '.#oo##oo#..', '.#o#..#o#..', '.#o#.#oo#..', '.#oo#oo#...',
    '..#ooo#....', '...###.#...', '.......#...', '........#..', '...........'],
  sun: [
    '.....#.....', '.....#.....', '..#..#..#..', '...#ooo#...', '....owo....', '####owo####',
    '....owo....', '...#ooo#...', '..#..#..#..', '.....#.....', '.....#.....'],
  orb: [
    '...#####...', '..##ooo##..', '.##o...o##.', '.#o..#..o#.', '.#o.#w#.o#.', '.#o..#..o#.',
    '.##o...o##.', '..##ooo##..', '...#####...', '...........', '...........'],
  heart: [
    '...........', '.###...###.', '#####.#####', '##ww#######', '#ww########', '###########',
    '.#########.', '..#######..', '...#####...', '....###....', '.....#.....'],
  star: [
    '.....#.....', '....###....', '....#w#....', '###########', '.####w####.', '..#######..',
    '..###.###..', '.##.....##.', '.#.......#.', '...........', '...........'],
  spark: [
    '.....#.....', '.....#.....', '.....#.....', '....#w#....', '...#www#...', '###wwwww###',
    '...#www#...', '....#w#....', '.....#.....', '.....#.....', '.....#.....'],
  bubble: [
    '...#####...', '..#.....#..', '.#.ww....#.', '#.ww......#', '#.w.......#', '#.........#',
    '#.........#', '#........o#', '.#......o#.', '..#.....#..', '...#####...'],
  bolt: [
    '......###..', '.....###...', '....###....', '...###.....', '..#######..', '....www#...',
    '....###....', '...###.....', '..##.......', '.##........', '.#.........'],
  note: [
    '.....##....', '.....#o#...', '.....#.o#..', '.....#..#..', '.....#.....', '.....#.....',
    '..####.....', '.#ww##.....', '.#w###.....', '..###......', '...........'],
  leaf: [
    '........##.', '......####.', '....#####o.', '...####o##.', '..###o###..', '.##wo###...',
    '.#wo###....', '.#o##......', '..o#.......', '.o.........', 'o..........'],
}

const AURA_SPRITE = {
  'au-frost': 'flake', 'au-flame': 'flame', 'au-toxic': 'drop', 'au-arcane': 'rune', 'au-blossom': 'petal',
  'au-shadow': 'wisp', 'au-gold': 'sun', 'au-void': 'orb', 'au-hearts': 'heart', 'au-stars': 'star',
  'au-spark': 'spark', 'au-bubbles': 'bubble', 'au-bolts': 'bolt', 'au-notes': 'note', 'au-leaves': 'leaf', 'au-autumn': 'leaf',
}

/** A 16×16 PNG of an aura's particle in its colours. */
function auraSprite(def) {
  const rows = SPRITES[AURA_SPRITE[def.id] ?? 'spark']
  let main = L.hex(def.color)
  // A dark aura (Shadow) still needs a readable particle: lift it towards grey-violet.
  if (main[0] * 0.3 + main[1] * 0.59 + main[2] * 0.11 < 80) main = L.mix(main, [150, 145, 175, 255], 0.45)
  const second = def.secondary ? L.hex(def.secondary) : L.shade(main, 1.45)
  const white = L.mix(main, [255, 255, 255, 255], 0.82)
  const size = 16, off = 2
  const px = new Uint8Array(size * size * 4)
  const at = (x, y) => (y >= 0 && y < rows.length && x >= 0 && x < rows[y].length ? rows[y][x] : '.')
  for (let y = 0; y < rows.length; y++) {
    for (let x = 0; x < rows[y].length; x++) {
      const ch = at(x, y)
      if (ch === '.') continue
      let c = ch === 'o' ? second : ch === 'w' ? white : main
      if (ch === '#') {
        if (at(x - 1, y) === '.' || at(x, y - 1) === '.') c = L.shade(c, 1.3)
        else if (at(x + 1, y) === '.' || at(x, y + 1) === '.') c = L.shade(c, 0.72)
      }
      const o = ((y + off + 1) * size + (x + off)) * 4
      px[o] = c[0]; px[o + 1] = c[1]; px[o + 2] = c[2]; px[o + 3] = 255
    }
  }
  return L.png(size, size, px)
}

/** Block model defs, aura sprites, and which item uses which model. */
function blockModels() {
  const { BLOCK_ITEMS, shapeFor } = loadSources()
  const defs = [], sprites = {}, byItem = {}
  for (const list of Object.values(BLOCK_ITEMS)) {
    for (const def of list) {
      if (def.slot === 'aura') {
        const id = def.id.replace(/-/g, '_')
        sprites[id] = auraSprite(def)
        byItem[def.id] = id
        continue
      }
      const d = blockDef(def, shapeFor)
      if (!d) continue
      defs.push(d)
      byItem[def.id] = d.id
    }
  }
  return { defs, sprites, byItem }
}

module.exports = { blockModels }
