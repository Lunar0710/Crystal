import { RankId } from './ranks'
import { shapeFor, type ShapeBox } from './cosmeticShapes'
import { MODEL_ITEM_DATA, BLOCK_MODELS } from './cosmeticModels.generated'
import { BLOCK_ITEMS } from './blockItems'

export type CosmeticSlot = 'cape' | 'hat' | 'bandana' | 'mask' | 'wings' | 'backpack' | 'aura' | 'pet'

export interface CosmeticDef {
  id: string
  name: string
  slot: CosmeticSlot
  /** Primary colour — drives both the picker swatch and the 3D mesh. */
  color: string
  secondary?: string
  /** Rough silhouette hint for the 3D mesh (per-slot meaning). */
  variant?: string
  /** Omitted = free for everyone. */
  requiredRank?: RankId
  /** A 3D model cosmetic (cosmeticModels.ts) instead of plain boxes. */
  model?: string
  /** Colour variants to choose from; the first is the default. */
  variants?: CosmeticVariant[]
  /** Shown with a "Neu" badge in the grid. */
  isNew?: boolean
  /** Model items: plain boxes per variant for games or servers without model support. */
  fallback?: Record<string, ShapeBox[]>
}

export interface CosmeticVariant {
  id: string
  name: string
  color: string
  secondary?: string
}

/** Which colour variant each item wears, by item id. Items not listed wear their first. */
export type CosmeticVariants = Record<string, string>

export const SLOTS: { id: CosmeticSlot; label: string }[] = [
  { id: 'cape', label: 'Capes' },
  { id: 'hat', label: 'Hüte' },
  { id: 'bandana', label: 'Bandanas' },
  { id: 'mask', label: 'Masken' },
  { id: 'wings', label: 'Wings' },
  { id: 'backpack', label: 'Rucksäcke' },
  { id: 'aura', label: 'Auren' },
  { id: 'pet', label: 'Pets' },
]

export type NonCapeSlot = Exclude<CosmeticSlot, 'cape'>

/**
 * The 3D model cosmetics (defs.cjs), with their colour variants. They sit in
 * front of the block items of their slot.
 */
const MODEL_ITEMS: CosmeticDef[] = MODEL_ITEM_DATA.map(m => ({
  id: `md-${m.model.replace(/_/g, '-')}`,
  name: m.name,
  slot: m.slot,
  color: m.variants[0].color,
  secondary: m.variants[0].secondary,
  requiredRank: 'crystal_plus' as RankId,
  model: m.model,
  variants: m.variants,
  fallback: m.fallback,
  isNew: true,
}))

const modelsFor = (slot: NonCapeSlot) => MODEL_ITEMS.filter(m => m.slot === slot)

/**
 * Block items with the textured model gen.cjs made of their boxes. Their boxes
 * (shapeFor) stay the fallback for games and servers without the model.
 */
const blocks = (slot: NonCapeSlot) => BLOCK_ITEMS[slot].map(d => (BLOCK_MODELS[d.id] ? { ...d, model: BLOCK_MODELS[d.id] } : d))

export const COSMETICS_BY_SLOT: Record<NonCapeSlot, CosmeticDef[]> = {
  hat: [...modelsFor('hat'), ...blocks('hat')],
  bandana: [...modelsFor('bandana'), ...blocks('bandana')],
  mask: [...modelsFor('mask'), ...blocks('mask')],
  wings: [...modelsFor('wings'), ...blocks('wings')],
  backpack: [...modelsFor('backpack'), ...blocks('backpack')],
  aura: blocks('aura'),
  pet: [...modelsFor('pet'), ...blocks('pet')],
}

export type EquippedCosmetics = Record<CosmeticSlot, string | null>

export const EMPTY_LOADOUT: EquippedCosmetics = {
  cape: null, hat: null, bandana: null, mask: null, wings: null, backpack: null, aura: null, pet: null,
}

export function findCosmetic(slot: NonCapeSlot, id: string | null): CosmeticDef | null {
  if (!id) return null
  return COSMETICS_BY_SLOT[slot].find(c => c.id === id) ?? null
}

/** The variant an item wears: the chosen one if it still exists, else its first. */
export function variantOf(def: CosmeticDef, variants: CosmeticVariants | undefined): CosmeticVariant | null {
  if (!def.variants?.length) return null
  const chosen = variants?.[def.id]
  return def.variants.find(v => v.id === chosen) ?? def.variants[0]
}

/** The item as it looks in its chosen variant: colours swapped in, the rest the same. */
export function resolveCosmetic(def: CosmeticDef | null, variants: CosmeticVariants | undefined): CosmeticDef | null {
  if (!def) return null
  const v = variantOf(def, variants)
  if (!v || (v.color === def.color && v.secondary === def.secondary)) return def
  return { ...def, color: v.color, secondary: v.secondary ?? def.secondary }
}

/** One slot's item the way loadout.json, catalog.json and the Nexora server carry it. */
export function gameItem(def: CosmeticDef, variants: CosmeticVariants | undefined) {
  const resolved = resolveCosmetic(def, variants)!
  const variant = variantOf(def, variants)
  if (def.model) {
    const skin = variant?.id ?? 'default'
    return {
      id: def.id,
      color: resolved.color,
      secondary: resolved.secondary,
      // Auras keep their motion style; the model names their particle sprite.
      variant: def.slot === 'aura' ? def.variant ?? null : null,
      plusOnly: !!def.requiredRank,
      anchor: def.slot === 'wings' ? 'wing' : def.slot === 'backpack' ? 'body' : def.slot === 'pet' ? 'pet' : def.slot === 'aura' ? null : 'head',
      // Older games and servers only know boxes: they draw these instead.
      boxes: def.fallback?.[skin] ?? def.fallback?.default ?? shapeFor(resolved)?.boxes ?? [],
      model: def.model,
      skin,
      vid: skin,
    }
  }
  const shape = shapeFor(resolved)
  return {
    id: def.id,
    color: resolved.color,
    secondary: resolved.secondary,
    variant: def.variant,
    plusOnly: !!def.requiredRank,
    // The exact boxes the preview draws, so the game renders the same shape.
    anchor: shape?.anchor ?? null,
    boxes: shape?.boxes ?? [],
    // Which colour variant this is, so the in-game menu shows it chosen.
    ...(variant ? { vid: variant.id } : {}),
  }
}

/**
 * Sends hats, masks, wings… to the in-game client (cosmetics/loadout.json).
 * Rank-locked items are flagged so the client hides them if the rank runs out.
 * Called at launcher start and whenever the loadout changes, so equipped items
 * show in-game without having to open the Cosmetics page first.
 */
export function syncLoadoutToGame(loadout: Partial<EquippedCosmetics>, variants?: CosmeticVariants) {
  const items: Record<string, unknown> = {}
  for (const slot of Object.keys(COSMETICS_BY_SLOT) as NonCapeSlot[]) {
    const def = findCosmetic(slot, loadout[slot] ?? null)
    items[slot] = def ? gameItem(def, variants) : null
  }
  ;(window as any).crystal?.syncLoadout(items)
}

// ---------------------------------------------------------------- emotes

export interface EmoteDef {
  /** The name the game and the Nexora server use (Emote.java). */
  id: string
  name: string
  description: string
  isNew?: boolean
}

export const EMOTES: EmoteDef[] = [
  { id: 'WAVE', name: 'Winken', description: 'Hallo sagen' },
  { id: 'CHEER', name: 'Jubeln', description: 'Beide Arme hoch' },
  { id: 'CLAP', name: 'Klatschen', description: 'Applaus' },
  { id: 'DANCE', name: 'Tanzen', description: 'Läuft, bis du dich bewegst' },
  { id: 'BOW', name: 'Verbeugen', description: 'Nach einem guten Kampf' },
  { id: 'FACEPALM', name: 'Facepalm', description: 'Wenn es wieder schiefging' },
  { id: 'POINT', name: 'Zeigen', description: 'Da drüben!' },
  { id: 'SALUTE', name: 'Salutieren', description: 'Hand an die Stirn', isNew: true },
  { id: 'SHRUG', name: 'Schulterzucken', description: 'Keine Ahnung', isNew: true },
  { id: 'THINK', name: 'Nachdenken', description: 'Hand am Kinn', isNew: true },
]

/** Emotes on the in-game wheel, in order. At most this many. */
export const MAX_WHEEL_EMOTES = 8
export const DEFAULT_WHEEL: string[] = ['WAVE', 'CHEER', 'CLAP', 'DANCE', 'BOW', 'FACEPALM', 'POINT', 'SALUTE']

// ---------------------------------------------------------------- in-game menu

/**
 * Everything the in-game Cosmetics menu (CosmeticsScreen.java) offers, written
 * to cosmetics/catalog.json: every item already resolved to what the game
 * draws, for each of its variants, whether it's locked for this player, and
 * the saved outfits. The menu then equips things without the launcher.
 */
export function buildGameCatalog(opts: {
  canUse: (def: CosmeticDef) => boolean
  capes: { id: string; name: string; locked: boolean; category: string }[]
  outfits: { name: string; loadout: Partial<EquippedCosmetics>; variants?: CosmeticVariants }[]
}) {
  const slots = (Object.keys(COSMETICS_BY_SLOT) as NonCapeSlot[]).map(slot => ({
    slot,
    items: COSMETICS_BY_SLOT[slot].map(def => {
      const variants: CosmeticVariant[] = def.variants?.length ? def.variants : [{ id: 'default', name: 'Standard', color: def.color, secondary: def.secondary }]
      return {
        id: def.id,
        name: def.name,
        locked: !opts.canUse(def),
        plus: !!def.requiredRank,
        isNew: !!def.isNew,
        model: def.model ?? null,
        variants: variants.map(v => ({
          id: v.id,
          name: v.name,
          color: v.color,
          secondary: v.secondary ?? v.color,
          item: gameItem(def, { [def.id]: v.id }),
        })),
      }
    }),
  }))
  return {
    version: 1,
    slots,
    capes: opts.capes,
    emotes: EMOTES.map(e => ({ id: e.id, name: e.name })),
    outfits: opts.outfits,
  }
}
