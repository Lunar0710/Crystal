import { CosmeticDef } from './cosmetics'

/**
 * The one shape definition for every hat, bandana, mask, backpack and wing.
 * The launcher's 3D preview and the in-game client both draw exactly these
 * boxes, so what you see on the Cosmetics page is what you get in-game.
 * (Before, both sides had their own hand-written geometry and drifted apart:
 * every mask looked the same in-game, wings were barely visible.)
 *
 * Coordinates are skin pixels:
 *  - head anchor: origin at the centre of the head, y up, +z towards the face.
 *    The head spans -4..4 on every axis.
 *  - body anchor: origin at the neck, y up (the body spans 0..-12), +z = front,
 *    so the back of the body is at z = -2.
 *  - wing anchor: one wing in its own hinge space, x pointing away from the body.
 *    The renderer mirrors it for the other side and animates the flap.
 */

export type ShapeAnchor = 'head' | 'body' | 'wing' | 'pet'

/**
 * Where a pet floats: beside the right shoulder, a little above it, in body
 * space (origin at the neck, y up). Preview and game both place it here and
 * bob it up and down.
 */
export const PET_POS = { x: -10, y: 3, z: 0 }

export interface ShapeBox {
  /** centre */
  x: number; y: number; z: number
  /** size */
  w: number; h: number; d: number
  color: string
  /** Rendered at full brightness in-game (halos, neon, flames). */
  glow?: boolean
  /** Rotation around z in radians, applied at the box centre (wing feathers). */
  rz?: number
}

export interface CosmeticShape {
  anchor: ShapeAnchor
  boxes: ShapeBox[]
}

const HEAD_TOP = 4
const FACE = 4
const BACK = -2

export function shapeFor(def: CosmeticDef): CosmeticShape | null {
  const c = def.color
  const a = def.secondary ?? def.color
  const b = (x: number, y: number, z: number, w: number, h: number, d: number, color = c, extra: Partial<ShapeBox> = {}): ShapeBox =>
    ({ x, y, z, w, h, d, color, ...extra })

  switch (def.slot) {
    case 'hat': return { anchor: 'head', boxes: hat(def.variant, c, a, b) }
    case 'mask': return { anchor: 'head', boxes: mask(def.variant, c, a, b) }
    case 'bandana': return { anchor: 'head', boxes: bandana(def.variant, c, a, b) }
    case 'backpack': return { anchor: 'body', boxes: backpack(def.variant, c, a, b) }
    case 'wings': return { anchor: 'wing', boxes: wing(def.variant, c, a, b) }
    case 'pet': return { anchor: 'pet', boxes: pet(def.variant, c, a, b) }
    default: return null
  }
}

type B = (x: number, y: number, z: number, w: number, h: number, d: number, color?: string, extra?: Partial<ShapeBox>) => ShapeBox

const EYE = '#141414'

/** Pets in their own space: centre of the pet, y up, +z where it looks. About 5 pixels big. */
function pet(variant: string | undefined, c: string, a: string, b: B): ShapeBox[] {
  const eyes = (y: number, z: number, gap = 0.65) => [b(-gap, y, z, 0.5, 0.5, 0.1, EYE), b(gap, y, z, 0.5, 0.5, 0.1, EYE)]
  const legs = (y: number) => [b(-0.8, y, 0.8, 0.7, 0.9, 0.7), b(0.8, y, 0.8, 0.7, 0.9, 0.7), b(-0.8, y, -2, 0.7, 0.9, 0.7), b(0.8, y, -2, 0.7, 0.9, 0.7)]
  switch (variant) {
    case 'fox': return [
      b(0, -0.8, -0.6, 2.6, 2.2, 4), b(0, -1.3, 1.1, 1.8, 1.1, 0.5, a),
      b(0, 0.9, 1.8, 2.8, 2.4, 2.4), b(0, 0.4, 3.3, 1.2, 1, 0.8, a),
      b(-0.9, 2.6, 1.6, 0.8, 1.2, 0.5), b(0.9, 2.6, 1.6, 0.8, 1.2, 0.5),
      ...eyes(1.2, 3.05), ...legs(-2.3),
      b(0, -0.5, -3.3, 1.4, 1.4, 2.2), b(0, -0.5, -4.7, 1.2, 1.2, 0.6, a),
    ]
    case 'slime': return [
      b(0, 0, 0, 4, 4, 4), b(-1.4, 1.4, 0, 0.8, 0.8, 4.1, a), ...eyes(0.5, 2.05, 0.9), b(0.4, -0.7, 2.05, 0.6, 0.4, 0.1, EYE),
    ]
    case 'ghost': return [
      b(0, 0.4, 0, 3.4, 3.6, 3, c, { glow: true }),
      b(-1.15, -1.8, 0, 1.1, 0.8, 3, c, { glow: true }), b(0.9, -1.7, 0, 1.3, 0.6, 3, c, { glow: true }),
      ...eyes(1, 1.55, 0.7), b(0, 0, 1.55, 0.7, 0.8, 0.1, EYE),
    ]
    case 'drone': return [
      b(0, 0, 0, 2.4, 1.2, 2.4, a), b(0, 0, 0, 5.4, 0.4, 0.4), b(0, 0, 0, 0.4, 0.4, 5.4),
      b(-2.7, 0.5, 0, 1.6, 0.2, 1.6), b(2.7, 0.5, 0, 1.6, 0.2, 1.6), b(0, 0.5, -2.7, 1.6, 0.2, 1.6), b(0, 0.5, 2.7, 1.6, 0.2, 1.6),
      b(0, 0, 1.25, 0.8, 0.8, 0.1, EYE), b(0, -0.8, 0.9, 0.5, 0.4, 0.5, '#ef4444', { glow: true }),
    ]
    case 'bee': return [
      b(0, 0, -1.2, 2.4, 2.4, 1, c), b(0, 0, -0.2, 2.4, 2.4, 1, EYE), b(0, 0, 0.8, 2.4, 2.4, 1, c),
      b(0, 0, 1.9, 2, 2, 1.2, EYE), b(0, 0, -2, 0.4, 0.4, 0.6, EYE),
      b(-1.4, 1.6, 0, 1.6, 0.2, 2, a, { rz: 0.4 }), b(1.4, 1.6, 0, 1.6, 0.2, 2, a, { rz: -0.4 }),
    ]
    // Cat, and the default.
    default: return [
      b(0, -0.8, -0.6, 2.6, 2.2, 4), b(0, 0.9, 1.8, 2.8, 2.4, 2.4),
      b(-0.9, 2.5, 1.6, 0.8, 0.8, 0.6), b(0.9, 2.5, 1.6, 0.8, 0.8, 0.6),
      ...eyes(1.1, 3.05), b(0, 0.5, 3.05, 0.4, 0.3, 0.1, a), ...legs(-2.3),
      b(0, -0.4, -2.9, 0.6, 0.6, 1.2), b(0, 0.4, -3.4, 0.6, 1.4, 0.6),
    ]
  }
}

function bandana(variant: string | undefined, c: string, a: string, b: B): ShapeBox[] {
  const band = b(0, 3.2, 0, 8.7, 1.8, 8.7)
  switch (variant) {
    // Long tails down the back.
    // Big bow on top of the head.
    case 'bow': return [
      b(0, 4.3, 1, 1.6, 1.4, 1.4, a), b(-2, 4.6, 1, 2.8, 2.4, 1, c, { rz: 0.35 }), b(2, 4.6, 1, 2.8, 2.4, 1, c, { rz: -0.35 }),
      b(-1.2, 3.6, 1, 0.8, 1.6, 0.8, c, { rz: -0.4 }), b(1.2, 3.6, 1, 0.8, 1.6, 0.8, c, { rz: 0.4 }),
    ]
    // Bandage round the head, one loose end at the back.
    case 'wrap': return [
      b(0, 2.2, 0, 8.7, 1.2, 8.7), b(0, 0.9, 0, 8.6, 0.8, 8.6, a, { rz: 0.08 }),
      b(1.4, 1.6, -4.8, 1.2, 3.4, 0.4, c, { rz: 0.3 }),
    ]
    case 'ninja': return [band, b(1.4, 0.4, -5.1, 1.4, 6.5, 0.8, a), b(-1.2, 1.4, -5.1, 1.2, 4.5, 0.8, a), b(0, 3.2, -4.8, 2, 2, 1.6, a)]
    // Thin sweatband, nothing hanging off.
    case 'headband': return [b(0, 3.4, 0, 8.8, 1.1, 8.8), b(0, 3.4, FACE + 0.5, 3, 1.1, 0.6, a)]
    // Knot on the side, like a pirate's.
    case 'knot': return [b(0, 3.4, 0, 8.8, 2.4, 8.8), b(4.9, 2.6, -1, 1.8, 1.8, 1.8, a), b(5.4, 1, -1.6, 0.9, 3, 0.9, a)]
    default: return [band, b(0, 3.2, -5, 1.4, 1.4, 2.2, a), b(0.8, 2.2, -5.6, 1, 2.4, 0.6, a)]
  }
}

function hat(variant: string | undefined, c: string, a: string, b: B): ShapeBox[] {
  const T = HEAD_TOP
  switch (variant) {
    case 'crown': {
      const boxes = [b(0, T + 0.8, 0, 8.8, 1.6, 8.8)]
      for (const [x, z] of [[-3.6, -3.6], [0, -3.6], [3.6, -3.6], [-3.6, 0], [3.6, 0], [-3.6, 3.6], [0, 3.6], [3.6, 3.6]]) {
        boxes.push(b(x, T + 2.4, z, 1.4, 1.8, 1.4))
      }
      boxes.push(b(0, T + 1, FACE + 0.5, 1.4, 1.4, 0.6, a, { glow: true }))
      return boxes
    }
    // Santa hat: fur band, cone bending to one side, bobble at the tip.
    case 'santa': return [
      b(0, T + 0.8, 0, 9, 1.6, 9, a), b(0, T + 2.8, 0, 7.4, 2.6, 7.4), b(0.8, T + 4.8, -0.6, 5.2, 2, 5.2),
      b(2.4, T + 6.2, -1.6, 3, 1.8, 3), b(4, T + 6.6, -2.6, 2, 2, 2, a),
    ]
    // Mortarboard: skull cap, flat square board, tassel off one corner.
    case 'graduation': return [
      b(0, T + 1, 0, 8.6, 2, 8.6), b(0, T + 2.3, 0, 12, 0.6, 12),
      b(0, T + 2.8, 0, 1, 0.5, 1, a), b(5.4, T + 1, 5.4, 0.5, 3, 0.5, a), b(5.4, T - 0.8, 5.4, 1, 1, 1, a),
    ]
    // Fedora: pinched crown, narrow brim dipping at the front.
    case 'fedora': return [
      b(0, T + 0.4, 0, 11, 0.6, 11), b(0, T + 0.2, FACE + 1.4, 9, 0.5, 2.4),
      b(0, T + 2.4, 0, 7.8, 3.4, 7.8), b(0, T + 1.2, 0, 8, 1, 8, a), b(0, T + 4.2, 0, 5, 0.5, 7.8),
    ]
    // Kabuto: bowl, flared neck guard, crescent crest on the brow.
    case 'samurai': return [
      b(0, T + 1.4, 0, 9.4, 3, 9.4), b(0, T - 1.4, -4.2, 11, 3, 1.4), b(-5, T - 1, 0, 1.2, 3, 8), b(5, T - 1, 0, 1.2, 3, 8),
      b(-2.2, T + 4, FACE + 0.6, 1, 4, 0.6, a, { rz: 0.5 }), b(2.2, T + 4, FACE + 0.6, 1, 4, 0.6, a, { rz: -0.5 }),
      b(0, T + 1.8, FACE + 0.8, 1.8, 1.8, 0.6, a),
    ]
    // Sombrero: very wide brim with a raised rim, tall crown.
    case 'sombrero': return [
      b(0, T + 0.4, 0, 18, 0.6, 18), b(0, T + 0.9, 8.8, 18, 1, 0.6, a), b(0, T + 0.9, -8.8, 18, 1, 0.6, a),
      b(8.8, T + 0.9, 0, 0.6, 1, 18, a), b(-8.8, T + 0.9, 0, 0.6, 1, 18, a),
      b(0, T + 3.2, 0, 7, 5, 7), b(0, T + 5.9, 0, 5, 0.8, 5), b(0, T + 1.3, 0, 7.4, 0.8, 7.4, a),
    ]
    // Bucket hat: soft crown, brim sloping down all round.
    case 'bucket': return [
      b(0, T + 1.8, 0, 8.8, 3.6, 8.8), b(0, T + 3.8, 0, 7.6, 0.6, 7.6),
      b(0, T - 0.1, FACE + 1.1, 10.8, 0.6, 2.2, a), b(0, T - 0.1, -FACE - 1.1, 10.8, 0.6, 2.2, a),
      b(FACE + 1.1, T - 0.1, 0, 2.2, 0.6, 8.8, a), b(-FACE - 1.1, T - 0.1, 0, 2.2, 0.6, 8.8, a),
    ]
    // Jester: three floppy points with bells.
    case 'jester': return [
      b(0, T + 0.8, 0, 9, 1.6, 9),
      b(-4.2, T + 2.6, 0, 2.6, 3, 2.6, c, { rz: 0.7 }), b(-6.4, T + 2.2, 0, 1.2, 1.2, 1.2, a, { glow: true }),
      b(0, T + 3.8, 0, 2.6, 5, 2.6, a), b(0, T + 6.6, 0, 1.2, 1.2, 1.2, c, { glow: true }),
      b(4.2, T + 2.6, 0, 2.6, 3, 2.6, c, { rz: -0.7 }), b(6.4, T + 2.2, 0, 1.2, 1.2, 1.2, a, { glow: true }),
    ]
    // Unicorn horn: spiralled cone of shrinking rings on the brow.
    case 'unicorn': return [
      b(0, T + 0.6, 2.4, 2.4, 1.2, 2.4, a), b(0, T + 1.8, 2.8, 1.9, 1.4, 1.9), b(0, T + 3.2, 3.2, 1.4, 1.4, 1.4, a),
      b(0, T + 4.5, 3.6, 0.9, 1.4, 0.9), b(0, T + 5.6, 3.9, 0.5, 1, 0.5, c, { glow: true }),
    ]
    case 'tophat': return [b(0, T + 0.4, 0, 11.5, 0.8, 11.5), b(0, T + 3.8, 0, 7.4, 6, 7.4), b(0, T + 1.4, 0, 7.6, 1.2, 7.6, a)]
    case 'straw': return [b(0, T + 0.4, 0, 13, 0.7, 13), b(0, T + 2.1, 0, 8, 2.8, 8, a), b(0, T + 1.1, 0, 8.2, 0.7, 8.2, '#b45309')]
    case 'cap': return [b(0, T + 1.6, 0, 8.7, 3.2, 8.7), b(0, T + 0.3, FACE + 1.8, 8, 0.6, 3.8, a), b(0, T + 3.3, 0, 1.2, 0.5, 1.2, a)]
    case 'halo': {
      const boxes: ShapeBox[] = []
      for (let i = 0; i < 16; i++) {
        const t = (i / 16) * Math.PI * 2
        boxes.push(b(Math.cos(t) * 3.8, T + 4, Math.sin(t) * 3.8, 1.2, 0.8, 1.2, c, { glow: true }))
      }
      return boxes
    }
    case 'horns': return [1, -1].flatMap(s => [
      b(s * 3, T + 1, 0, 2.2, 2, 2.2), b(s * 3.6, T + 2.8, 0, 1.6, 1.8, 1.6), b(s * 4.2, T + 4.3, 0, 0.9, 1.4, 0.9, a),
    ])
    case 'antenna': return [b(0, T + 2.2, 0, 0.6, 4.5, 0.6, a), b(0, T + 5, 0, 2, 2, 2, c, { glow: true })]
    // Tricorn: wide brim with the sides turned up, badge on the front.
    case 'pirate': return [
      b(0, T + 0.5, 0, 12.5, 0.8, 10.5), b(0, T + 2.2, 0, 7.6, 3.4, 7.6),
      b(-5.6, T + 1.8, 0, 1.2, 2.6, 8.5, a), b(5.6, T + 1.8, 0, 1.2, 2.6, 8.5, a),
      b(0, T + 2.4, FACE + 1.3, 2.2, 2.2, 0.6, a),
    ]
    // Chef's toque: band plus the puffed top.
    case 'chef': return [
      b(0, T + 1, 0, 8.8, 2, 8.8, a), b(0, T + 4.6, 0, 9.6, 5.2, 9.6),
      b(0, T + 7.4, 0, 8.4, 1.6, 8.4),
    ]
    case 'cowboy': return [
      b(0, T + 0.5, 0, 13.5, 0.8, 11), b(0, T + 2.8, 0, 7.4, 4.4, 7.4),
      b(0, T + 1.3, 0, 7.8, 1, 7.8, a), b(0, T + 5.2, 0, 3.4, 0.8, 7.4, a),
    ]
    // Space helmet: dome around the head with a dark visor.
    case 'helmet': return [
      b(0, T - 1.6, 0, 10.4, 10.4, 10.4), b(0, T - 1.2, FACE + 1.4, 7.4, 5.4, 1.4, a, { glow: true }),
      b(0, T + 4.2, 0, 4.4, 1.6, 4.4, a), b(0, T - 5.6, 0, 11, 1.2, 11, a),
    ]
    // Mohawk: a row of spikes from front to back.
    case 'mohawk': return [-3, -1.5, 0, 1.5, 3].map((z, i) =>
      b(0, T + 1.6 + (i === 2 ? 1.4 : i === 1 || i === 3 ? 0.8 : 0), z, 1.4, 3.2 + (i === 2 ? 2.4 : i === 1 || i === 3 ? 1.4 : 0), 1.4, i % 2 ? a : c))
    // Leaf wreath: leaves lying on the head instead of a floating ring.
    case 'wreath': {
      const boxes: ShapeBox[] = []
      for (let i = 0; i < 12; i++) {
        const t = (i / 12) * Math.PI * 2
        boxes.push(b(Math.cos(t) * 4.2, T + 0.9, Math.sin(t) * 4.2, 1.8, 1.2, 1.8, i % 2 ? a : c, { rz: t }))
      }
      return boxes
    }
    case 'ears': return [1, -1].flatMap(s => [
      b(s * 2.8, T + 1.5, 0, 2.6, 3, 1.2), b(s * 2.8, T + 1.3, 0.35, 1.4, 1.8, 0.6, a),
    ])
    case 'bunny': return [1, -1].flatMap(s => [
      b(s * 1.9, T + 3.5, -0.5, 1.8, 7, 1.2), b(s * 1.9, T + 3.8, -0.05, 0.9, 5, 0.5, a),
    ])
    case 'headphones': return [
      b(0, T + 0.6, 0, 9.4, 1, 2),
      ...[1, -1].flatMap(s => [b(s * 4.6, T - 1.4, 0, 0.8, 3.4, 1.4), b(s * 4.9, -0.2, 0, 1.6, 3.4, 3.4), b(s * 5.75, -0.2, 0, 0.2, 2.2, 2.2, a, { glow: true })]),
    ]
    case 'wizard': return [
      b(0, T + 0.3, 0, 11, 0.6, 11), b(0, T + 1.9, 0, 7, 2.6, 7), b(0, T + 4.3, -0.4, 5.4, 2.4, 5.4),
      b(0, T + 6.5, -1, 3.8, 2.2, 3.8), b(0, T + 8.4, -1.8, 2.2, 2, 2.2), b(0, T + 9.9, -2.6, 1, 1.4, 1),
      b(0, T + 2.2, 3.55, 1.4, 1.4, 0.2, a, { glow: true }), b(0, T + 0.9, 0, 7.2, 0.6, 7.2, a),
    ]
    case 'flowers': {
      const boxes: ShapeBox[] = []
      for (let i = 0; i < 10; i++) {
        const t = (i / 10) * Math.PI * 2
        boxes.push(b(Math.cos(t) * 4.4, T + 0.3, Math.sin(t) * 4.4, 1.6, 1.4, 1.6, i % 2 ? a : c))
        boxes.push(b(Math.cos(t + 0.3) * 4.5, T - 0.2, Math.sin(t + 0.3) * 4.5, 1, 0.6, 1, '#16a34a'))
      }
      return boxes
    }
    case 'propeller': return [
      b(0, T + 1, 0, 8.7, 2, 8.7), b(0, T + 0.2, FACE + 1.4, 7.5, 0.5, 3, a),
      b(0, T + 2.8, 0, 0.5, 1.6, 0.5, '#e5e7eb'),
      b(0, T + 3.7, 0, 7, 0.3, 1, '#ef4444'), b(0, T + 3.75, 0, 1, 0.3, 7, '#facc15'),
    ]
    case 'viking': return [
      b(0, T + 1.4, 0, 9, 3, 9), b(0, T - 0.3, 0, 9.2, 0.8, 9.2, a), b(0, T + 1.4, FACE + 0.55, 1, 3, 0.2, a),
      ...[1, -1].flatMap(s => [b(s * 5.4, T + 1.8, 0, 2.2, 1.4, 1.4, '#f5f5f4'), b(s * 6.3, T + 3.2, 0, 1.2, 2, 1.2, '#f5f5f4'), b(s * 6.6, T + 4.6, 0, 0.7, 1.2, 0.7, '#e7e5e4')]),
    ]
    case 'party': return [
      b(1.2, T + 1, 0, 5, 2, 5), b(1.2, T + 2.8, 0, 3.6, 1.8, 3.6, a), b(1.2, T + 4.4, 0, 2.2, 1.6, 2.2),
      b(1.2, T + 5.7, 0, 1, 1, 1, '#fde047', { glow: true }),
    ]
    default: return [b(0, T + 1.6, 0, 8.8, 4, 8.8), b(0, T - 0.6, 0, 9, 1.4, 9, a), b(0, T + 3.9, 0, 1.8, 1.2, 1.8, a)]
  }
}

function mask(variant: string | undefined, c: string, a: string, b: B): ShapeBox[] {
  const F = FACE + 0.3
  switch (variant) {
    case 'visor': return [b(0, 0.7, F, 8.6, 1.8, 0.6, c, { glow: true }), b(0, 1.8, F, 8.8, 0.4, 0.7, a), b(0, -0.4, F, 8.8, 0.4, 0.7, a)]
    // Hockey mask: pale plate with breathing holes and red chevrons.
    case 'hockey': {
      const boxes = [b(0, 0, F, 8.2, 8, 0.6)]
      for (const [x, y] of [[-2, -1.4], [0, -1.4], [2, -1.4], [-1, -2.8], [1, -2.8], [0, 2.8]]) boxes.push(b(x, y, F + 0.2, 0.8, 0.8, 0.4, '#1c1917'))
      boxes.push(b(-2, 1, F + 0.2, 2.2, 1.2, 0.4, '#1c1917'), b(2, 1, F + 0.2, 2.2, 1.2, 0.4, '#1c1917'))
      boxes.push(b(-2.4, 3, F + 0.25, 1.4, 0.5, 0.3, a, { rz: 0.5 }), b(2.4, 3, F + 0.25, 1.4, 0.5, 0.3, a, { rz: -0.5 }))
      return boxes
    }
    // Clown: round red nose and painted cheeks.
    case 'clown': return [
      b(0, -0.2, F + 0.8, 2, 2, 2, c, { glow: true }),
      b(-2.6, -1.4, F, 1.6, 1.2, 0.3, a), b(2.6, -1.4, F, 1.6, 1.2, 0.3, a),
      b(0, -2.6, F, 3.4, 0.6, 0.3, c),
    ]
    // Brass goggles pushed up on the forehead, lenses catching the light.
    case 'goggles': return [
      b(0, 2.2, F - 0.1, 8.8, 1, 0.5, a), b(-4.4, 2.2, 0, 0.4, 1, 8.8, a), b(4.4, 2.2, 0, 0.4, 1, 8.8, a),
      b(-2, 2.4, F + 0.4, 2.8, 2.8, 1), b(2, 2.4, F + 0.4, 2.8, 2.8, 1),
      b(-2, 2.4, F + 0.95, 1.8, 1.8, 0.2, '#93c5fd', { glow: true }), b(2, 2.4, F + 0.95, 1.8, 1.8, 0.2, '#93c5fd', { glow: true }),
    ]
    // Curled moustache.
    case 'mustache': return [
      b(-1.3, -1.4, F, 2.4, 1, 0.5), b(1.3, -1.4, F, 2.4, 1, 0.5),
      b(-3, -1, F, 1.4, 0.8, 0.5, c, { rz: -0.6 }), b(3, -1, F, 1.4, 0.8, 0.5, c, { rz: 0.6 }),
      b(-3.7, -0.3, F, 0.7, 0.7, 0.5, a), b(3.7, -0.3, F, 0.7, 0.7, 0.5, a),
    ]
    // VR headset: deep front block with a lit strip, strap round the head.
    case 'vr': return [
      b(0, 1, F + 1, 8.4, 3.2, 2.4), b(0, 1, F + 2.25, 6, 0.6, 0.2, a, { glow: true }),
      b(0, 1.4, 0, 8.8, 1, 8.8, '#27272a'),
    ]
    case 'shades': return [
      b(-2, 0.7, F, 3, 1.6, 0.5), b(2, 0.7, F, 3, 1.6, 0.5), b(0, 1.2, F, 2, 0.5, 0.5, a),
      b(-4.3, 1.2, 2.2, 0.4, 0.5, 4.2, a), b(4.3, 1.2, 2.2, 0.4, 0.5, 4.2, a),
    ]
    case 'oni': return [
      b(0, 0, F, 8.4, 8.2, 0.6), b(-2, 1.2, F + 0.35, 1.8, 0.8, 0.2, '#fef08a', { glow: true }), b(2, 1.2, F + 0.35, 1.8, 0.8, 0.2, '#fef08a', { glow: true }),
      b(-2, 2.3, F + 0.35, 2.2, 0.6, 0.2, '#000000'), b(2, 2.3, F + 0.35, 2.2, 0.6, 0.2, '#000000'),
      b(0, -2.4, F + 0.35, 5, 0.6, 0.2, '#000000'), b(-1.6, -2.9, F + 0.35, 0.8, 1.2, 0.2, a), b(1.6, -2.9, F + 0.35, 0.8, 1.2, 0.2, a),
      b(-2.8, 4.8, 0, 1.2, 2, 1.2, a), b(2.8, 4.8, 0, 1.2, 2, 1.2, a),
    ]
    case 'kitsune': return [
      b(0, 0, F, 8.4, 8.2, 0.6), b(-2, 1, F + 0.35, 2, 0.6, 0.2, a), b(2, 1, F + 0.35, 2, 0.6, 0.2, a),
      b(-2.6, -1.2, F + 0.35, 2.2, 0.4, 0.2, a), b(2.6, -1.2, F + 0.35, 2.2, 0.4, 0.2, a), b(0, -2.4, F + 0.5, 1, 0.8, 0.6, '#18181b'),
      b(-2.8, 5, 0.5, 1.8, 2.4, 0.8), b(2.8, 5, 0.5, 1.8, 2.4, 0.8), b(-2.8, 5, 0.95, 0.8, 1.4, 0.2, a), b(2.8, 5, 0.95, 0.8, 1.4, 0.2, a),
    ]
    case 'scarf': return [b(0, -2.6, 0, 8.8, 3.2, 8.8), b(0, -2.6, 0, 8.9, 0.6, 8.9, a), b(-2, -5, -4.6, 1.4, 3, 0.6, a)]
    case 'neon': return [
      b(0, 0.8, F, 8.6, 2.4, 0.4),
      ...[-3, -1, 1, 3].map(x => b(x, 0.8, F + 0.25, 0.4, 2.4, 0.2, a, { glow: true })),
      b(0, 0.8, F + 0.25, 8.6, 0.4, 0.2, a, { glow: true }),
    ]
    case 'glasses': return [
      ...[-2, 2].flatMap(x => [b(x, 1.9, F, 3, 0.4, 0.4), b(x, -0.1, F, 3, 0.4, 0.4), b(x - 1.3, 0.9, F, 0.4, 2, 0.4), b(x + 1.3, 0.9, F, 0.4, 2, 0.4), b(x, 0.9, F - 0.1, 2.2, 1.6, 0.1, a)]),
      b(0, 1.2, F, 1.4, 0.4, 0.4), b(-4.3, 1.2, 2.2, 0.4, 0.4, 4.2), b(4.3, 1.2, 2.2, 0.4, 0.4, 4.2),
    ]
    case 'pixel': return [
      b(0, 1.6, F, 8.4, 0.6, 0.5, '#000000'), b(-2.2, 0.8, F, 3, 1.2, 0.5, '#000000'), b(2.2, 0.8, F, 3, 1.2, 0.5, '#000000'),
      b(-3.2, 1.1, F + 0.3, 0.6, 0.6, 0.2, '#ffffff'), b(1.2, 1.1, F + 0.3, 0.6, 0.6, 0.2, '#ffffff'),
    ]
    // Gas mask: plate over the face with two filters.
    case 'gas': return [
      b(0, -0.4, FACE + 0.8, 8.4, 6.4, 1.6), b(-2.2, -2.6, FACE + 1.6, 2.6, 2.6, 2.6, a), b(2.2, -2.6, FACE + 1.6, 2.6, 2.6, 2.6, a),
      b(0, 1.2, FACE + 1.7, 5.6, 2, 0.6, a, { glow: true }), b(0, -0.4, 0, 9, 1.4, 8.6, a),
    ]
    // Kerchief pulled over the mouth, knot at the back.
    case 'bandit': return [
      b(0, -2.2, FACE + 0.5, 8.6, 4.4, 1), b(0, -2.2, 0, 8.8, 4.2, 8.8), b(0, -1.6, -5, 1.6, 1.6, 2, a),
    ]
    case 'eyepatch': return [
      b(-2, 0.8, FACE + 0.6, 3.4, 3, 0.8), b(0, 1.6, 0, 8.8, 0.7, 8.8, a),
    ]
    // Plague doctor: long beak and round eyes.
    case 'plague': return [
      b(0, -0.6, FACE + 1.2, 3.4, 3.4, 2.6), b(0, -1.2, FACE + 3, 2.4, 2.4, 2.4), b(0, -1.8, FACE + 4.4, 1.4, 1.4, 1.6),
      b(-2.4, 1.4, FACE + 0.7, 2.4, 2.4, 0.8, a), b(2.4, 1.4, FACE + 0.7, 2.4, 2.4, 0.8, a),
    ]
    case 'monocle': return [
      b(2, 2.1, F, 2.2, 0.35, 0.35), b(2, -0.2, F, 2.2, 0.35, 0.35), b(0.9, 0.95, F, 0.35, 2.3, 0.35), b(3.1, 0.95, F, 0.35, 2.3, 0.35),
      b(2, 0.95, F - 0.1, 1.9, 2, 0.1, a), b(3.2, -2.2, F, 0.2, 3.8, 0.2, c),
    ]
    default: return [b(0, -0.6, F, 8.2, 3.2, 0.6), b(0, -1.8, F + 0.3, 8.2, 0.6, 0.3, a)]
  }
}

function backpack(variant: string | undefined, c: string, a: string, b: B): ShapeBox[] {
  const Z = BACK - 1.6
  const straps = [1, -1].map(s => b(s * 2.6, -4.5, BACK + 0.35, 1, 7, 0.4, a))
  switch (variant) {
    // Round shield slung on the back, boss in the middle.
    case 'shield': {
      const boxes = [b(0, -5, Z + 0.6, 9, 9, 0.6), b(0, -5, Z + 0.2, 1.8, 1.8, 0.6, a, { glow: true })]
      for (const [x, y] of [[0, -0.9], [0, -9.1], [-4.1, -5], [4.1, -5]]) boxes.push(b(x, y, Z + 0.35, 1, 1, 0.4, a))
      return [...boxes, ...straps]
    }
    // Treasure chest: lid, lock and metal bands.
    case 'chest': return [
      b(0, -6, Z - 0.2, 7, 4.6, 3.6), b(0, -3.2, Z - 0.2, 7.2, 1.4, 3.8, a),
      b(0, -4.2, Z - 2.05, 1.2, 1.6, 0.3, '#fde047', { glow: true }),
      b(-2.8, -5.2, Z - 2.05, 0.5, 6, 0.3, a), b(2.8, -5.2, Z - 2.05, 0.5, 6, 0.3, a), ...straps,
    ]
    // Lantern hanging off a pole over the shoulder.
    case 'lantern': return [
      b(-1.5, -4, Z + 0.6, 0.6, 14, 0.6, a, { rz: -0.35 }), b(1.2, 3.2, Z + 0.6, 2.6, 0.5, 0.6, a),
      b(2.4, 1.6, Z + 0.6, 2, 2.6, 2), b(2.4, 1.6, Z + 0.6, 1.4, 2, 1.4, '#fbbf24', { glow: true }),
      b(2.4, 3, Z + 0.6, 1.2, 0.4, 1.2, a),
    ]
    // Fishing rod over the shoulder, line and a red float.
    case 'rod': return [
      b(0, -3, Z + 0.6, 0.5, 17, 0.5, a, { rz: 0.55 }), b(-2.4, -5.8, Z + 0.6, 1.2, 1.2, 1.2, c),
      b(4.6, 3.6, Z + 0.6, 0.15, 5, 0.15, '#e5e7eb'), b(4.6, 1, Z + 0.6, 0.9, 1.2, 0.9, '#ef4444'),
    ]
    case 'jetpack': return [
      b(-1.8, -5, Z, 3, 7.5, 3), b(1.8, -5, Z, 3, 7.5, 3), b(0, -3, Z + 0.5, 1.4, 3, 2, a),
      b(-1.8, -9.4, Z, 2, 1.4, 2, '#f97316', { glow: true }), b(1.8, -9.4, Z, 2, 1.4, 2, '#f97316', { glow: true }), ...straps,
    ]
    // Katana strapped diagonally across the back.
    case 'katana': return [
      b(0, -5, Z + 0.6, 1, 15, 1, a, { rz: 0.6 }), b(-2.6, -1.4, Z + 0.6, 0.9, 4, 0.9, '#1c1917', { rz: 0.6 }),
      b(-1.4, -2.6, Z + 0.6, 2.6, 0.7, 1.2, c, { rz: 0.6 }), ...straps,
    ]
    // Quiver with arrows sticking out.
    case 'quiver': return [
      b(1.6, -5.5, Z, 3.4, 8, 3.4), b(1.6, -1.4, Z, 3.8, 1, 3.8, a),
      ...[-0.8, 0, 0.8].map(o => b(1.6 + o, 1.6, Z + o * 0.4, 0.5, 5, 0.5, a)),
      ...straps,
    ]
    // Boombox with two speakers.
    case 'boombox': return [
      b(0, -5, Z - 0.4, 9, 6, 3.4), b(-2.4, -5, Z - 2, 3, 3, 0.6, a), b(2.4, -5, Z - 2, 3, 3, 0.6, a),
      b(0, -2.6, Z - 2, 4.4, 1, 0.6, a, { glow: true }), ...straps,
    ]
    case 'guitar': return [
      b(0.8, -8.5, BACK - 1, 5.5, 5, 1.4), b(-0.3, -3, BACK - 1, 1.2, 8, 1), b(-1, 1.4, BACK - 1, 2, 1.8, 1.2, a),
      b(0.8, -8, BACK - 0.2, 1.6, 1.6, 0.2, '#000000'), b(2.2, -4, BACK + 0.3, 0.6, 11, 0.3, a, { rz: 0.5 }),
    ]
    case 'shell': return [b(0, -6, BACK - 1.4, 8, 10, 2.6), b(0, -6, BACK - 2.8, 6, 7, 0.4, a), b(0, -6, BACK - 3.05, 2, 2, 0.3, c)]
    default: return [b(0, -5.5, Z, 7, 8.5, 3), b(0, -7, BACK - 3.3, 5, 3.2, 1, a), b(0, -1.5, Z, 6, 1.2, 3.2, a), ...straps]
  }
}

/** Right wing, hinge at the origin, extending along +x. Feathers fan from up-and-out to down-and-out. */
function wing(variant: string | undefined, c: string, a: string, b: B): ShapeBox[] {
  const fan = (lengths: number[], thickness: number, start: number, step: number, glow = false) =>
    lengths.map((len, i) => {
      const angle = start - i * step
      return b(Math.cos(angle) * len / 2, Math.sin(angle) * len / 2, -i * 0.15, len, thickness - i * 0.15, 0.7, i % 2 ? a : c, { rz: angle, glow })
    })
  switch (variant) {
    // Leaf wings: broad leaves, each with a lighter vein.
    case 'leaf': return [
      b(5.5, 3.2, 0, 10, 3.6, 0.35, c, { rz: 0.45 }), b(5.4, 3.2, 0.2, 9, 0.4, 0.2, a, { rz: 0.45 }),
      b(6, -0.2, 0, 11, 3.6, 0.35, c, { rz: 0.05 }), b(5.9, -0.2, 0.2, 10, 0.4, 0.2, a, { rz: 0.05 }),
      b(5, -3.6, 0, 9, 3.2, 0.35, c, { rz: -0.35 }), b(4.9, -3.6, 0.2, 8, 0.4, 0.2, a, { rz: -0.35 }),
    ]
    // Bone wings: bare struts and knuckles, no membrane.
    case 'bone': return [
      ...fan([15, 13, 10.5, 8], 0.7, 0.55, 0.3),
      b(0.8, 0, 0.1, 2, 2, 1.2, a), b(7.4, 3.2, 0.1, 1.2, 1.2, 1, a), b(6.6, -0.8, 0.1, 1.2, 1.2, 1, a),
    ]
    // Ice wings: pale lit shards, uneven lengths.
    case 'ice': return [
      ...fan([13, 15, 12, 9, 6], 1.3, 0.62, 0.26, true),
      b(3, 1, 0.2, 5, 5, 0.3, a, { rz: 0.8, glow: true }),
    ]
    // Moth: two soft pairs with eye spots.
    case 'moth': return [
      b(5.2, 2.6, 0, 9.5, 7, 0.4, c, { rz: 0.25 }), b(4, -3.6, 0, 7, 5.2, 0.4, c, { rz: -0.4 }),
      b(6.4, 3.2, 0.25, 2.4, 2.4, 0.2, a), b(6.4, 3.2, 0.35, 1, 1, 0.2, '#18181b'),
      b(4.6, -4, 0.25, 1.8, 1.8, 0.2, a),
    ]
    case 'bat': return [
      ...fan([15, 13.5, 11, 8], 0.8, 0.5, 0.28),
      b(6, -1.5, 0.1, 11, 7, 0.3, c, { rz: 0.1 }),
    ]
    case 'insect':
    case 'butterfly': return [
      b(5.5, 3, 0, 10, 8, 0.4, c, { rz: 0.35 }), b(4.5, -4.2, 0, 8, 6, 0.4, c, { rz: -0.3 }),
      b(8.5, 5.4, 0.05, 3, 1, 0.4, a, { rz: 0.35 }), b(7.2, -6.4, 0.05, 2.6, 1, 0.4, a, { rz: -0.3 }),
    ]
    case 'mecha': return [
      b(5.5, 1, 0, 11, 3.4, 1.2, c, { rz: 0.25 }), b(4.5, -3, 0, 8, 2.6, 1, c, { rz: -0.2 }),
      b(5, 1, 0.7, 9, 0.7, 0.3, a, { rz: 0.25, glow: true }), b(4.5, -3, 0.6, 6.5, 0.6, 0.3, a, { rz: -0.2, glow: true }),
    ]
    case 'flame': return fan([15, 14, 12.5, 10.5, 8.5], 2.4, 0.55, 0.2, true)
    case 'shard': return fan([15, 12.5, 10], 1.8, 0.5, 0.3, true)
    // Dragon: leathery panels between long ribs.
    case 'dragon': return [
      ...fan([16, 13.5, 11, 8.5], 0.9, 0.55, 0.3),
      b(6.5, -2, 0.15, 12, 8, 0.3, a, { rz: 0.12 }),
      b(7.6, 4.2, -0.1, 3, 1.2, 0.6, a, { rz: 0.5 }),
    ]
    // Phoenix: feathers of fire, all glowing, longest at the top.
    case 'phoenix': return [
      ...fan([16, 14, 12, 10, 8], 2.2, 0.6, 0.22, true),
      b(4, 5.5, 0.2, 5, 1.4, 0.5, a, { rz: 0.7, glow: true }),
    ]
    // Fairy: two small round wings, faintly lit.
    case 'fairy': return [
      b(3.6, 2.6, 0, 7, 5.5, 0.35, c, { rz: 0.4, glow: true }),
      b(3, -2.4, 0, 5.5, 4, 0.35, a, { rz: -0.35, glow: true }),
      b(1.2, 0.2, 0, 1.2, 6, 0.5, a),
    ]
    default: return fan([16, 15, 13.5, 11.5, 9.5, 7.5], 2.2, 0.6, 0.17)
  }
}
