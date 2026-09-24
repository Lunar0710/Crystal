import { RankId } from './ranks'
import { shapeFor } from './cosmeticShapes'

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
}

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

const HATS: CosmeticDef[] = [
  { id: 'ht-beanie',   name: 'Beanie',     slot: 'hat', color: '#ef4444', secondary: '#7f1d1d', variant: 'beanie', requiredRank: 'crystal_plus' },
  { id: 'ht-cap',      name: 'Cap',        slot: 'hat', color: '#2563eb', secondary: '#1e3a8a', variant: 'cap', requiredRank: 'crystal_plus' },
  { id: 'ht-crown',    name: 'Krone',      slot: 'hat', color: '#fbbf24', secondary: '#b45309', variant: 'crown', requiredRank: 'crystal_plus' },
  { id: 'ht-tophat',   name: 'Zylinder',   slot: 'hat', color: '#18181b', secondary: '#3f3f46', variant: 'tophat', requiredRank: 'crystal_plus' },
  { id: 'ht-straw',    name: 'Strohhut',   slot: 'hat', color: '#eab308', secondary: '#a16207', variant: 'straw', requiredRank: 'crystal_plus' },
  { id: 'ht-halo',     name: 'Heiligensch.', slot: 'hat', color: '#fde68a', secondary: '#fbbf24', variant: 'halo', requiredRank: 'crystal_plus' },
  { id: 'ht-horns',    name: 'Hörner',     slot: 'hat', color: '#7f1d1d', secondary: '#450a0a', variant: 'horns', requiredRank: 'developer' },
  { id: 'ht-antenna',  name: 'Antenne',    slot: 'hat', color: '#22d3ee', secondary: '#0e7490', variant: 'antenna', requiredRank: 'developer' },
  { id: 'ht-catears',  name: 'Katzenohren', slot: 'hat', color: '#27272a', secondary: '#f9a8d4', variant: 'ears', requiredRank: 'crystal_plus' },
  { id: 'ht-foxears',  name: 'Fuchsohren', slot: 'hat', color: '#ea580c', secondary: '#fff7ed', variant: 'ears', requiredRank: 'crystal_plus' },
  { id: 'ht-bunny',    name: 'Hasenohren', slot: 'hat', color: '#f5f5f4', secondary: '#fda4af', variant: 'bunny', requiredRank: 'crystal_plus' },
  { id: 'ht-headset',  name: 'Kopfhörer',  slot: 'hat', color: '#18181b', secondary: '#22d3ee', variant: 'headphones', requiredRank: 'crystal_plus' },
  { id: 'ht-wizard',   name: 'Zauberhut',  slot: 'hat', color: '#4c1d95', secondary: '#fde047', variant: 'wizard', requiredRank: 'crystal_plus' },
  { id: 'ht-flowers',  name: 'Blumenkranz', slot: 'hat', color: '#f472b6', secondary: '#fde047', variant: 'flowers', requiredRank: 'crystal_plus' },
  { id: 'ht-propeller', name: 'Propeller', slot: 'hat', color: '#2563eb', secondary: '#facc15', variant: 'propeller', requiredRank: 'crystal_plus' },
  { id: 'ht-viking',   name: 'Wikinger',   slot: 'hat', color: '#78716c', secondary: '#a16207', variant: 'viking', requiredRank: 'crystal_plus' },
  { id: 'ht-party',    name: 'Partyhut',   slot: 'hat', color: '#8b5cf6', secondary: '#22d3ee', variant: 'party', requiredRank: 'crystal_plus' },
  { id: 'ht-tricorn',  name: 'Dreispitz',   slot: 'hat', color: '#1f2937', secondary: '#d1d5db', variant: 'pirate', requiredRank: 'crystal_plus' },
  { id: 'ht-chef',     name: 'Kochmütze',   slot: 'hat', color: '#f8fafc', secondary: '#e2e8f0', variant: 'chef', requiredRank: 'crystal_plus' },
  { id: 'ht-cowboy',   name: 'Cowboyhut',   slot: 'hat', color: '#92400e', secondary: '#451a03', variant: 'cowboy', requiredRank: 'crystal_plus' },
  { id: 'ht-helmet',   name: 'Raumhelm',    slot: 'hat', color: '#e5e7eb', secondary: '#38bdf8', variant: 'helmet', requiredRank: 'crystal_plus' },
  { id: 'ht-mohawk',   name: 'Irokese',     slot: 'hat', color: '#22d3ee', secondary: '#0e7490', variant: 'mohawk', requiredRank: 'crystal_plus' },
  { id: 'ht-wreath',   name: 'Blätterkranz', slot: 'hat', color: '#4ade80', secondary: '#15803d', variant: 'wreath', requiredRank: 'crystal_plus' },
  { id: 'ht-santa', name: 'Weihnachtsmütze', slot: 'hat', color: '#c0392b', secondary: '#f5f5f4', variant: 'santa', requiredRank: 'crystal_plus' },
  { id: 'ht-graduation', name: 'Doktorhut', slot: 'hat', color: '#1c1c1e', secondary: '#d4a843', variant: 'graduation', requiredRank: 'crystal_plus' },
  { id: 'ht-fedora', name: 'Fedora', slot: 'hat', color: '#3b3530', secondary: '#1c1917', variant: 'fedora', requiredRank: 'crystal_plus' },
  { id: 'ht-samurai', name: 'Kabuto', slot: 'hat', color: '#2b2b2e', secondary: '#c9a24a', variant: 'samurai', requiredRank: 'crystal_plus' },
  { id: 'ht-sombrero', name: 'Sombrero', slot: 'hat', color: '#d9b36c', secondary: '#b4412f', variant: 'sombrero', requiredRank: 'crystal_plus' },
  { id: 'ht-bucket', name: 'Fischerhut', slot: 'hat', color: '#5d6b4f', secondary: '#4a553f', variant: 'bucket', requiredRank: 'crystal_plus' },
  { id: 'ht-jester', name: 'Narrenkappe', slot: 'hat', color: '#6d2a8a', secondary: '#e0b83a', variant: 'jester', requiredRank: 'crystal_plus' },
  { id: 'ht-unicorn', name: 'Einhorn', slot: 'hat', color: '#f4e7ff', secondary: '#e9c46a', variant: 'unicorn', requiredRank: 'crystal_plus' },
]

const BANDANAS: CosmeticDef[] = [
  { id: 'bd-crystal',  name: 'Nexora',  slot: 'bandana', color: '#f0f0f0', secondary: '#8a8a8c', requiredRank: 'crystal_plus' },
  { id: 'bd-blossom',  name: 'Blossom',  slot: 'bandana', color: '#f56ba0', secondary: '#f5a3c7', requiredRank: 'crystal_plus' },
  { id: 'bd-ember',    name: 'Ember',    slot: 'bandana', color: '#f5455b', secondary: '#f59e0b', requiredRank: 'crystal_plus' },
  { id: 'bd-mint',     name: 'Mint',     slot: 'bandana', color: '#34d399', secondary: '#5bf5c9', requiredRank: 'crystal_plus' },
  { id: 'bd-void',     name: 'Void',     slot: 'bandana', color: '#27272a', secondary: '#52525b', requiredRank: 'crystal_plus' },
  { id: 'bd-gold',     name: 'Gold',     slot: 'bandana', color: '#e8b85c', secondary: '#fef3c7', requiredRank: 'crystal_plus' },
  { id: 'bd-sakura',   name: 'Sakura',   slot: 'bandana', color: '#fda4af', secondary: '#be123c', requiredRank: 'crystal_plus' },
  { id: 'bd-ink',      name: 'Ink',      slot: 'bandana', color: '#f8fafc', secondary: '#0f172a', requiredRank: 'crystal_plus' },
  { id: 'bd-ocean',    name: 'Ocean',    slot: 'bandana', color: '#0ea5e9', secondary: '#e0f2fe', requiredRank: 'crystal_plus' },
  { id: 'bd-camo',     name: 'Camo',     slot: 'bandana', color: '#4d7c0f', secondary: '#365314', requiredRank: 'crystal_plus' },
  { id: 'bd-ninja',    name: 'Ninja-Band',  slot: 'bandana', color: '#18181b', secondary: '#dc2626', variant: 'ninja', requiredRank: 'crystal_plus' },
  { id: 'bd-headband', name: 'Stirnband',   slot: 'bandana', color: '#f1f5f9', secondary: '#3b82f6', variant: 'headband', requiredRank: 'crystal_plus' },
  { id: 'bd-knot',     name: 'Piratentuch', slot: 'bandana', color: '#b91c1c', secondary: '#7f1d1d', variant: 'knot', requiredRank: 'crystal_plus' },
  { id: 'bd-bow', name: 'Schleife', slot: 'bandana', color: '#e2566f', secondary: '#b83250', variant: 'bow', requiredRank: 'crystal_plus' },
  { id: 'bd-wrap', name: 'Verband', slot: 'bandana', color: '#ece8e1', secondary: '#c9c2b6', variant: 'wrap', requiredRank: 'crystal_plus' },
]

const MASKS: CosmeticDef[] = [
  { id: 'mk-visor',    name: 'Visor',     slot: 'mask', color: '#22d3ee', secondary: '#0e7490', variant: 'visor', requiredRank: 'crystal_plus' },
  { id: 'mk-shades',   name: 'Shades',    slot: 'mask', color: '#18181b', secondary: '#3f3f46', variant: 'shades', requiredRank: 'crystal_plus' },
  { id: 'mk-oni',      name: 'Oni',       slot: 'mask', color: '#dc2626', secondary: '#fef2f2', requiredRank: 'crystal_plus', variant: 'oni' },
  { id: 'mk-kitsune',  name: 'Kitsune',   slot: 'mask', color: '#f8fafc', secondary: '#dc2626', requiredRank: 'crystal_plus', variant: 'kitsune' },
  { id: 'mk-scarf',    name: 'Schal',     slot: 'mask', color: '#0f172a', secondary: '#475569', variant: 'scarf', requiredRank: 'crystal_plus' },
  { id: 'mk-neon',     name: 'Neon Grid', slot: 'mask', color: '#a855f7', secondary: '#22d3ee', requiredRank: 'developer', variant: 'neon' },
  { id: 'mk-glasses',  name: 'Brille',    slot: 'mask', color: '#18181b', secondary: '#bae6fd', variant: 'glasses', requiredRank: 'crystal_plus' },
  { id: 'mk-pixel',    name: 'Pixel Shades', slot: 'mask', color: '#000000', secondary: '#ffffff', variant: 'pixel', requiredRank: 'crystal_plus' },
  { id: 'mk-monocle',  name: 'Monokel',   slot: 'mask', color: '#d4a017', secondary: '#e0f2fe', variant: 'monocle', requiredRank: 'crystal_plus' },
  { id: 'mk-gas',      name: 'Gasmaske',    slot: 'mask', color: '#3f3f46', secondary: '#84cc16', variant: 'gas', requiredRank: 'crystal_plus' },
  { id: 'mk-bandit',   name: 'Banditentuch', slot: 'mask', color: '#b91c1c', secondary: '#7f1d1d', variant: 'bandit', requiredRank: 'crystal_plus' },
  { id: 'mk-eyepatch', name: 'Augenklappe', slot: 'mask', color: '#18181b', secondary: '#27272a', variant: 'eyepatch', requiredRank: 'crystal_plus' },
  { id: 'mk-plague',   name: 'Pestmaske',   slot: 'mask', color: '#1c1917', secondary: '#ca8a04', variant: 'plague', requiredRank: 'crystal_plus' },
  { id: 'mk-hockey', name: 'Hockeymaske', slot: 'mask', color: '#eeeae2', secondary: '#c0392b', variant: 'hockey', requiredRank: 'crystal_plus' },
  { id: 'mk-clown', name: 'Clownsnase', slot: 'mask', color: '#d63a2f', secondary: '#f2a7a0', variant: 'clown', requiredRank: 'crystal_plus' },
  { id: 'mk-goggles', name: 'Fliegerbrille', slot: 'mask', color: '#6b4a2b', secondary: '#b58a3c', variant: 'goggles', requiredRank: 'crystal_plus' },
  { id: 'mk-mustache', name: 'Schnurrbart', slot: 'mask', color: '#2a1d14', secondary: '#3a2a1e', variant: 'mustache', requiredRank: 'crystal_plus' },
  { id: 'mk-vr', name: 'VR-Brille', slot: 'mask', color: '#e6e6e8', secondary: '#ffffff', variant: 'vr', requiredRank: 'crystal_plus' },
]

const WINGS: CosmeticDef[] = [
  { id: 'wg-crystal', name: 'Nexora',  slot: 'wings', color: '#e8e8ea', secondary: '#9a9a9f', variant: 'shard', requiredRank: 'crystal_plus' },
  { id: 'wg-angel',   name: 'Angel',    slot: 'wings', color: '#f8fafc', secondary: '#cbd5e1', variant: 'feather', requiredRank: 'crystal_plus' },
  { id: 'wg-raven',   name: 'Raven',    slot: 'wings', color: '#18181b', secondary: '#3f3f46', variant: 'bat', requiredRank: 'crystal_plus' },
  { id: 'wg-phoenix', name: 'Phoenix',  slot: 'wings', color: '#ef4444', secondary: '#f59e0b', requiredRank: 'crystal_plus', variant: 'flame' },
  { id: 'wg-fae',     name: 'Fae',      slot: 'wings', color: '#2dd4bf', secondary: '#a3e635', requiredRank: 'crystal_plus', variant: 'insect' },
  { id: 'wg-dusk',    name: 'Dusk',     slot: 'wings', color: '#8b5cf6', secondary: '#ec4899', requiredRank: 'crystal_plus', variant: 'feather' },
  { id: 'wg-mecha',   name: 'Mecha',    slot: 'wings', color: '#64748b', secondary: '#22d3ee', requiredRank: 'developer', variant: 'mecha' },
  { id: 'wg-monarch', name: 'Monarch',  slot: 'wings', color: '#f97316', secondary: '#18181b', requiredRank: 'developer', variant: 'butterfly' },
  { id: 'wg-dragon',  name: 'Drache',   slot: 'wings', color: '#166534', secondary: '#4ade80', variant: 'bat', requiredRank: 'crystal_plus' },
  { id: 'wg-frost',   name: 'Frost',    slot: 'wings', color: '#bae6fd', secondary: '#38bdf8', variant: 'shard', requiredRank: 'crystal_plus' },
  { id: 'wg-sakura',  name: 'Sakura',   slot: 'wings', color: '#fbcfe8', secondary: '#db2777', requiredRank: 'crystal_plus', variant: 'butterfly' },
  { id: 'wg-dragon',   name: 'Drachenflügel', slot: 'wings', color: '#7f1d1d', secondary: '#dc2626', variant: 'dragon', requiredRank: 'crystal_plus' },
  { id: 'wg-phoenix',  name: 'Phönix',      slot: 'wings', color: '#f97316', secondary: '#fde047', variant: 'phoenix', requiredRank: 'crystal_plus' },
  { id: 'wg-fairy',    name: 'Feenflügel',  slot: 'wings', color: '#a5f3fc', secondary: '#f0abfc', variant: 'fairy', requiredRank: 'crystal_plus' },
  { id: 'wg-leaf', name: 'Blattflügel', slot: 'wings', color: '#4f7a3a', secondary: '#9cc27a', variant: 'leaf', requiredRank: 'crystal_plus' },
  { id: 'wg-bone', name: 'Knochenflügel', slot: 'wings', color: '#e8e2d4', secondary: '#bfb6a3', variant: 'bone', requiredRank: 'crystal_plus' },
  { id: 'wg-ice', name: 'Eisflügel', slot: 'wings', color: '#cfeaf5', secondary: '#ffffff', variant: 'ice', requiredRank: 'crystal_plus' },
  { id: 'wg-moth', name: 'Mottenflügel', slot: 'wings', color: '#b8a58a', secondary: '#e8dcc6', variant: 'moth', requiredRank: 'crystal_plus' },
]

const BACKPACKS: CosmeticDef[] = [
  { id: 'bp-hiker',   name: 'Hiker',     slot: 'backpack', color: '#65a30d', secondary: '#3f6212', variant: 'pack', requiredRank: 'crystal_plus' },
  { id: 'bp-jet',     name: 'Jetpack',   slot: 'backpack', color: '#94a3b8', secondary: '#f97316', requiredRank: 'crystal_plus', variant: 'jetpack' },
  { id: 'bp-satchel', name: 'Satchel',   slot: 'backpack', color: '#92400e', secondary: '#451a03', requiredRank: 'crystal_plus' },
  { id: 'bp-crystal', name: 'Nexora',   slot: 'backpack', color: '#e8e8ea', secondary: '#9a9a9f', requiredRank: 'crystal_plus' },
  { id: 'bp-turtle',  name: 'Panzer',    slot: 'backpack', color: '#15803d', secondary: '#052e16', variant: 'shell', requiredRank: 'crystal_plus' },
  { id: 'bp-cube',    name: 'Cube',      slot: 'backpack', color: '#e11d48', secondary: '#4c0519', requiredRank: 'developer' },
  { id: 'bp-guitar',  name: 'Gitarre',   slot: 'backpack', color: '#b45309', secondary: '#292524', variant: 'guitar', requiredRank: 'crystal_plus' },
  { id: 'bp-katana',   name: 'Katana',      slot: 'backpack', color: '#dc2626', secondary: '#e5e7eb', variant: 'katana', requiredRank: 'crystal_plus' },
  { id: 'bp-quiver',   name: 'Köcher',      slot: 'backpack', color: '#92400e', secondary: '#f5f5f4', variant: 'quiver', requiredRank: 'crystal_plus' },
  { id: 'bp-boombox',  name: 'Ghettoblaster', slot: 'backpack', color: '#27272a', secondary: '#f59e0b', variant: 'boombox', requiredRank: 'crystal_plus' },
  { id: 'bp-shield', name: 'Rundschild', slot: 'backpack', color: '#6b4a2b', secondary: '#b8b8bc', variant: 'shield', requiredRank: 'crystal_plus' },
  { id: 'bp-chest', name: 'Schatztruhe', slot: 'backpack', color: '#7a4e2a', secondary: '#3a3a3c', variant: 'chest', requiredRank: 'crystal_plus' },
  { id: 'bp-lantern', name: 'Laterne', slot: 'backpack', color: '#2b2b2e', secondary: '#6b4a2b', variant: 'lantern', requiredRank: 'crystal_plus' },
  { id: 'bp-rod', name: 'Angel', slot: 'backpack', color: '#8a6a44', secondary: '#5c4630', variant: 'rod', requiredRank: 'crystal_plus' },
]

const AURAS: CosmeticDef[] = [
  { id: 'au-frost',   name: 'Frost',    slot: 'aura', color: '#38bdf8', variant: 'snow', requiredRank: 'crystal_plus' },
  { id: 'au-flame',   name: 'Flame',    slot: 'aura', color: '#f97316', variant: 'rising', requiredRank: 'crystal_plus' },
  { id: 'au-toxic',   name: 'Toxic',    slot: 'aura', color: '#a3e635', variant: 'orbit', requiredRank: 'crystal_plus' },
  { id: 'au-arcane',  name: 'Arcane',   slot: 'aura', color: '#a855f7', requiredRank: 'crystal_plus', variant: 'ring' },
  { id: 'au-blossom', name: 'Blossom',  slot: 'aura', color: '#ec4899', requiredRank: 'crystal_plus', variant: 'petals' },
  { id: 'au-shadow',  name: 'Shadow',   slot: 'aura', color: '#3f3f46', variant: 'sphere', requiredRank: 'crystal_plus' },
  { id: 'au-gold',    name: 'Radiance', slot: 'aura', color: '#fbbf24', requiredRank: 'crystal_plus', variant: 'ring' },
  { id: 'au-void',    name: 'Void',     slot: 'aura', color: '#6d28d9', requiredRank: 'developer', variant: 'storm' },
  { id: 'au-hearts',  name: 'Herzen',   slot: 'aura', color: '#f43f5e', variant: 'orbit', requiredRank: 'crystal_plus' },
  { id: 'au-stars',   name: 'Sterne',   slot: 'aura', color: '#fde047', variant: 'sphere', requiredRank: 'crystal_plus' },
  { id: 'au-spark',   name: 'Funken',   slot: 'aura', color: '#22d3ee', requiredRank: 'crystal_plus', variant: 'storm' },
  { id: 'au-bubbles', name: 'Blasen',    slot: 'aura', color: '#67e8f9', secondary: '#a5f3fc', variant: 'bubbles', requiredRank: 'crystal_plus' },
  { id: 'au-bolts',   name: 'Blitze',    slot: 'aura', color: '#fde047', secondary: '#f8fafc', variant: 'bolts', requiredRank: 'crystal_plus' },
  { id: 'au-notes',   name: 'Noten',     slot: 'aura', color: '#f0abfc', secondary: '#c084fc', variant: 'notes', requiredRank: 'crystal_plus' },
  { id: 'au-leaves',  name: 'Blätter',   slot: 'aura', color: '#86efac', secondary: '#65a30d', variant: 'leaves', requiredRank: 'crystal_plus' },
]

// A small companion that floats beside your shoulder.
const PETS: CosmeticDef[] = [
  { id: 'pt-cat',      name: 'Katze',          slot: 'pet', color: '#a1a1aa', secondary: '#f9a8d4', variant: 'cat', requiredRank: 'crystal_plus' },
  { id: 'pt-blackcat', name: 'Schwarze Katze', slot: 'pet', color: '#27272a', secondary: '#f9a8d4', variant: 'cat', requiredRank: 'crystal_plus' },
  { id: 'pt-fox',      name: 'Fuchs',          slot: 'pet', color: '#ea580c', secondary: '#fafaf9', variant: 'fox', requiredRank: 'crystal_plus' },
  { id: 'pt-slime',    name: 'Slime',          slot: 'pet', color: '#65a30d', secondary: '#a3e635', variant: 'slime', requiredRank: 'crystal_plus' },
  { id: 'pt-ghost',    name: 'Geist',          slot: 'pet', color: '#e4e4e7', secondary: '#e4e4e7', variant: 'ghost', requiredRank: 'crystal_plus' },
  { id: 'pt-drone',    name: 'Drohne',         slot: 'pet', color: '#52525b', secondary: '#d4d4d8', variant: 'drone', requiredRank: 'crystal_plus' },
  { id: 'pt-bee',      name: 'Biene',          slot: 'pet', color: '#facc15', secondary: '#f5f5f5', variant: 'bee', requiredRank: 'crystal_plus' },
]

export type NonCapeSlot = Exclude<CosmeticSlot, 'cape'>

export const COSMETICS_BY_SLOT: Record<NonCapeSlot, CosmeticDef[]> = {
  hat: HATS,
  bandana: BANDANAS,
  mask: MASKS,
  wings: WINGS,
  backpack: BACKPACKS,
  aura: AURAS,
  pet: PETS,
}

export type EquippedCosmetics = Record<CosmeticSlot, string | null>

export const EMPTY_LOADOUT: EquippedCosmetics = {
  cape: null, hat: null, bandana: null, mask: null, wings: null, backpack: null, aura: null, pet: null,
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
