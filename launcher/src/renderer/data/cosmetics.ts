import { RankId } from './ranks'

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
]

const MASKS: CosmeticDef[] = [
  { id: 'mk-visor',    name: 'Visor',     slot: 'mask', color: '#22d3ee', secondary: '#0e7490' },
  { id: 'mk-shades',   name: 'Shades',    slot: 'mask', color: '#18181b', secondary: '#3f3f46' },
  { id: 'mk-oni',      name: 'Oni',       slot: 'mask', color: '#dc2626', secondary: '#fef2f2', requiredRank: 'crystal_plus' },
  { id: 'mk-kitsune',  name: 'Kitsune',   slot: 'mask', color: '#f8fafc', secondary: '#dc2626', requiredRank: 'crystal_plus' },
  { id: 'mk-scarf',    name: 'Mundschutz',slot: 'mask', color: '#0f172a', secondary: '#475569' },
  { id: 'mk-neon',     name: 'Neon Grid', slot: 'mask', color: '#a855f7', secondary: '#22d3ee', requiredRank: 'developer' },
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
]

const BACKPACKS: CosmeticDef[] = [
  { id: 'bp-hiker',   name: 'Hiker',     slot: 'backpack', color: '#65a30d', secondary: '#3f6212' },
  { id: 'bp-jet',     name: 'Jetpack',   slot: 'backpack', color: '#94a3b8', secondary: '#f97316', requiredRank: 'crystal_plus' },
  { id: 'bp-satchel', name: 'Satchel',   slot: 'backpack', color: '#92400e', secondary: '#451a03' },
  { id: 'bp-crystal', name: 'Crystal',   slot: 'backpack', color: '#7c6af5', secondary: '#5b8af5', requiredRank: 'crystal_plus' },
  { id: 'bp-turtle',  name: 'Panzer',    slot: 'backpack', color: '#15803d', secondary: '#052e16' },
  { id: 'bp-cube',    name: 'Cube',      slot: 'backpack', color: '#e11d48', secondary: '#4c0519', requiredRank: 'developer' },
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
