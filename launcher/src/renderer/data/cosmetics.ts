import { RankId } from './ranks'
import { shapeFor } from './cosmeticShapes'

export type CosmeticSlot = 'cape' | 'hat' | 'bandana' | 'mask' | 'wings' | 'backpack' | 'aura'

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
}

export const SLOTS: { id: CosmeticSlot; label: string }[] = [
  { id: 'cape', label: 'Capes' },
  { id: 'hat', label: 'Hüte' },
  { id: 'bandana', label: 'Bandanas' },
  { id: 'mask', label: 'Masken' },
  { id: 'wings', label: 'Wings' },
  { id: 'backpack', label: 'Rucksäcke' },
  { id: 'aura', label: 'Auren' },
]

const HATS: CosmeticDef[] = [
  { id: 'ht-beanie',   name: 'Beanie',     slot: 'hat', color: '#ef4444', secondary: '#7f1d1d', variant: 'beanie' },
  { id: 'ht-cap',      name: 'Cap',        slot: 'hat', color: '#2563eb', secondary: '#1e3a8a', variant: 'cap' },
  { id: 'ht-crown',    name: 'Krone',      slot: 'hat', color: '#fbbf24', secondary: '#b45309', variant: 'crown', requiredRank: 'crystal_plus' },
  { id: 'ht-tophat',   name: 'Zylinder',   slot: 'hat', color: '#18181b', secondary: '#3f3f46', variant: 'tophat', requiredRank: 'crystal_plus' },
  { id: 'ht-straw',    name: 'Strohhut',   slot: 'hat', color: '#eab308', secondary: '#a16207', variant: 'straw' },
  { id: 'ht-halo',     name: 'Heiligensch.', slot: 'hat', color: '#fde68a', secondary: '#fbbf24', variant: 'halo', requiredRank: 'crystal_plus' },
  { id: 'ht-horns',    name: 'Hörner',     slot: 'hat', color: '#7f1d1d', secondary: '#450a0a', variant: 'horns', requiredRank: 'developer' },
  { id: 'ht-antenna',  name: 'Antenne',    slot: 'hat', color: '#22d3ee', secondary: '#0e7490', variant: 'antenna', requiredRank: 'developer' },
  { id: 'ht-catears',  name: 'Katzenohren', slot: 'hat', color: '#27272a', secondary: '#f9a8d4', variant: 'ears' },
  { id: 'ht-foxears',  name: 'Fuchsohren', slot: 'hat', color: '#ea580c', secondary: '#fff7ed', variant: 'ears' },
  { id: 'ht-bunny',    name: 'Hasenohren', slot: 'hat', color: '#f5f5f4', secondary: '#fda4af', variant: 'bunny' },
  { id: 'ht-headset',  name: 'Kopfhörer',  slot: 'hat', color: '#18181b', secondary: '#22d3ee', variant: 'headphones' },
  { id: 'ht-wizard',   name: 'Zauberhut',  slot: 'hat', color: '#4c1d95', secondary: '#fde047', variant: 'wizard', requiredRank: 'crystal_plus' },
  { id: 'ht-flowers',  name: 'Blumenkranz', slot: 'hat', color: '#f472b6', secondary: '#fde047', variant: 'flowers' },
  { id: 'ht-propeller', name: 'Propeller', slot: 'hat', color: '#2563eb', secondary: '#facc15', variant: 'propeller' },
  { id: 'ht-viking',   name: 'Wikinger',   slot: 'hat', color: '#78716c', secondary: '#a16207', variant: 'viking', requiredRank: 'crystal_plus' },
  { id: 'ht-party',    name: 'Partyhut',   slot: 'hat', color: '#8b5cf6', secondary: '#22d3ee', variant: 'party' },
]

const BANDANAS: CosmeticDef[] = [
  { id: 'bd-crystal',  name: 'Crystal',  slot: 'bandana', color: '#5b8af5', secondary: '#7c6af5' },
  { id: 'bd-blossom',  name: 'Blossom',  slot: 'bandana', color: '#f56ba0', secondary: '#f5a3c7' },
  { id: 'bd-ember',    name: 'Ember',    slot: 'bandana', color: '#f5455b', secondary: '#f59e0b' },
  { id: 'bd-mint',     name: 'Mint',     slot: 'bandana', color: '#34d399', secondary: '#5bf5c9' },
  { id: 'bd-void',     name: 'Void',     slot: 'bandana', color: '#27272a', secondary: '#52525b' },
  { id: 'bd-gold',     name: 'Gold',     slot: 'bandana', color: '#e8b85c', secondary: '#fef3c7' },
  { id: 'bd-sakura',   name: 'Sakura',   slot: 'bandana', color: '#fda4af', secondary: '#be123c', requiredRank: 'crystal_plus' },
  { id: 'bd-ink',      name: 'Ink',      slot: 'bandana', color: '#f8fafc', secondary: '#0f172a', requiredRank: 'crystal_plus' },
  { id: 'bd-ocean',    name: 'Ocean',    slot: 'bandana', color: '#0ea5e9', secondary: '#e0f2fe' },
  { id: 'bd-camo',     name: 'Camo',     slot: 'bandana', color: '#4d7c0f', secondary: '#365314' },
]

const MASKS: CosmeticDef[] = [
  { id: 'mk-visor',    name: 'Visor',     slot: 'mask', color: '#22d3ee', secondary: '#0e7490', variant: 'visor' },
  { id: 'mk-shades',   name: 'Shades',    slot: 'mask', color: '#18181b', secondary: '#3f3f46', variant: 'shades' },
  { id: 'mk-oni',      name: 'Oni',       slot: 'mask', color: '#dc2626', secondary: '#fef2f2', requiredRank: 'crystal_plus', variant: 'oni' },
  { id: 'mk-kitsune',  name: 'Kitsune',   slot: 'mask', color: '#f8fafc', secondary: '#dc2626', requiredRank: 'crystal_plus', variant: 'kitsune' },
  { id: 'mk-scarf',    name: 'Schal',     slot: 'mask', color: '#0f172a', secondary: '#475569', variant: 'scarf' },
  { id: 'mk-neon',     name: 'Neon Grid', slot: 'mask', color: '#a855f7', secondary: '#22d3ee', requiredRank: 'developer', variant: 'neon' },
  { id: 'mk-glasses',  name: 'Brille',    slot: 'mask', color: '#18181b', secondary: '#bae6fd', variant: 'glasses' },
  { id: 'mk-pixel',    name: 'Pixel Shades', slot: 'mask', color: '#000000', secondary: '#ffffff', variant: 'pixel' },
  { id: 'mk-monocle',  name: 'Monokel',   slot: 'mask', color: '#d4a017', secondary: '#e0f2fe', variant: 'monocle', requiredRank: 'crystal_plus' },
]

const WINGS: CosmeticDef[] = [
  { id: 'wg-crystal', name: 'Crystal',  slot: 'wings', color: '#7c6af5', secondary: '#5b8af5', variant: 'shard' },
  { id: 'wg-angel',   name: 'Angel',    slot: 'wings', color: '#f8fafc', secondary: '#cbd5e1', variant: 'feather' },
  { id: 'wg-raven',   name: 'Raven',    slot: 'wings', color: '#18181b', secondary: '#3f3f46', variant: 'bat' },
  { id: 'wg-phoenix', name: 'Phoenix',  slot: 'wings', color: '#ef4444', secondary: '#f59e0b', requiredRank: 'crystal_plus', variant: 'flame' },
  { id: 'wg-fae',     name: 'Fae',      slot: 'wings', color: '#2dd4bf', secondary: '#a3e635', requiredRank: 'crystal_plus', variant: 'insect' },
  { id: 'wg-dusk',    name: 'Dusk',     slot: 'wings', color: '#8b5cf6', secondary: '#ec4899', requiredRank: 'crystal_plus', variant: 'feather' },
  { id: 'wg-mecha',   name: 'Mecha',    slot: 'wings', color: '#64748b', secondary: '#22d3ee', requiredRank: 'developer', variant: 'mecha' },
  { id: 'wg-monarch', name: 'Monarch',  slot: 'wings', color: '#f97316', secondary: '#18181b', requiredRank: 'developer', variant: 'butterfly' },
  { id: 'wg-dragon',  name: 'Drache',   slot: 'wings', color: '#166534', secondary: '#4ade80', variant: 'bat' },
  { id: 'wg-frost',   name: 'Frost',    slot: 'wings', color: '#bae6fd', secondary: '#38bdf8', variant: 'shard' },
  { id: 'wg-sakura',  name: 'Sakura',   slot: 'wings', color: '#fbcfe8', secondary: '#db2777', requiredRank: 'crystal_plus', variant: 'butterfly' },
]

const BACKPACKS: CosmeticDef[] = [
  { id: 'bp-hiker',   name: 'Hiker',     slot: 'backpack', color: '#65a30d', secondary: '#3f6212', variant: 'pack' },
  { id: 'bp-jet',     name: 'Jetpack',   slot: 'backpack', color: '#94a3b8', secondary: '#f97316', requiredRank: 'crystal_plus', variant: 'jetpack' },
  { id: 'bp-satchel', name: 'Satchel',   slot: 'backpack', color: '#92400e', secondary: '#451a03' },
  { id: 'bp-crystal', name: 'Crystal',   slot: 'backpack', color: '#7c6af5', secondary: '#5b8af5', requiredRank: 'crystal_plus' },
  { id: 'bp-turtle',  name: 'Panzer',    slot: 'backpack', color: '#15803d', secondary: '#052e16', variant: 'shell' },
  { id: 'bp-cube',    name: 'Cube',      slot: 'backpack', color: '#e11d48', secondary: '#4c0519', requiredRank: 'developer' },
  { id: 'bp-guitar',  name: 'Gitarre',   slot: 'backpack', color: '#b45309', secondary: '#292524', variant: 'guitar' },
]

const AURAS: CosmeticDef[] = [
  { id: 'au-frost',   name: 'Frost',    slot: 'aura', color: '#38bdf8', variant: 'snow' },
  { id: 'au-flame',   name: 'Flame',    slot: 'aura', color: '#f97316', variant: 'rising' },
  { id: 'au-toxic',   name: 'Toxic',    slot: 'aura', color: '#a3e635', variant: 'orbit' },
  { id: 'au-arcane',  name: 'Arcane',   slot: 'aura', color: '#a855f7', requiredRank: 'crystal_plus', variant: 'ring' },
  { id: 'au-blossom', name: 'Blossom',  slot: 'aura', color: '#ec4899', requiredRank: 'crystal_plus', variant: 'petals' },
  { id: 'au-shadow',  name: 'Shadow',   slot: 'aura', color: '#3f3f46', variant: 'sphere' },
  { id: 'au-gold',    name: 'Radiance', slot: 'aura', color: '#fbbf24', requiredRank: 'crystal_plus', variant: 'ring' },
  { id: 'au-void',    name: 'Void',     slot: 'aura', color: '#6d28d9', requiredRank: 'developer', variant: 'storm' },
  { id: 'au-hearts',  name: 'Herzen',   slot: 'aura', color: '#f43f5e', variant: 'orbit' },
  { id: 'au-stars',   name: 'Sterne',   slot: 'aura', color: '#fde047', variant: 'sphere' },
  { id: 'au-spark',   name: 'Funken',   slot: 'aura', color: '#22d3ee', requiredRank: 'crystal_plus', variant: 'storm' },
]

export type NonCapeSlot = Exclude<CosmeticSlot, 'cape'>

export const COSMETICS_BY_SLOT: Record<NonCapeSlot, CosmeticDef[]> = {
  hat: HATS,
  bandana: BANDANAS,
  mask: MASKS,
  wings: WINGS,
  backpack: BACKPACKS,
  aura: AURAS,
}

export type EquippedCosmetics = Record<CosmeticSlot, string | null>

export const EMPTY_LOADOUT: EquippedCosmetics = {
  cape: null, hat: null, bandana: null, mask: null, wings: null, backpack: null, aura: null,
}

export function findCosmetic(slot: NonCapeSlot, id: string | null): CosmeticDef | null {
  if (!id) return null
  return COSMETICS_BY_SLOT[slot].find(c => c.id === id) ?? null
}

/**
 * Sends hats, masks, wings… to the in-game client (cosmetics/loadout.json).
 * Rank-locked items are flagged so the client hides them if the rank runs out.
 * Called at launcher start and whenever the loadout changes, so equipped items
 * show in-game without having to open the Cosmetics page first.
 */
export function syncLoadoutToGame(loadout: Partial<EquippedCosmetics>) {
  const items: Record<string, unknown> = {}
  for (const slot of Object.keys(COSMETICS_BY_SLOT) as NonCapeSlot[]) {
    const def = findCosmetic(slot, loadout[slot] ?? null)
    if (!def) { items[slot] = null; continue }
    const shape = shapeFor(def)
    items[slot] = {
      color: def.color,
      secondary: def.secondary,
      variant: def.variant,
      plusOnly: !!def.requiredRank,
      // The exact boxes the preview draws, so the game renders the same shape.
      anchor: shape?.anchor ?? null,
      boxes: shape?.boxes ?? [],
    }
  }
  ;(window as any).crystal?.syncLoadout(items)
}
