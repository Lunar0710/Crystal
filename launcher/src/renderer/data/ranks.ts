export type RankId =
  | 'owner'
  | 'co_owner'
  | 'admin'
  | 'staff'
  | 'developer'
  | 'media'
  | 'crystal_plus'
  | 'member'

export interface RankDef {
  id: RankId
  label: string
  colors: [string, string]
  icon: 'crown' | 'shield' | 'shield-check' | 'code' | 'video' | 'gem' | null
  priority: number // lower = displayed first / higher authority
}

export const RANKS: Record<RankId, RankDef> = {
  owner:        { id: 'owner',        label: 'Owner',      colors: ['#ffd54d', '#ff7a45'], icon: 'crown',        priority: 0 },
  co_owner:     { id: 'co_owner',     label: 'Co-Owner',   colors: ['#ffd54d', '#f5455b'], icon: 'crown',        priority: 1 },
  admin:        { id: 'admin',        label: 'Admin',      colors: ['#f5455b', '#f57c3d'], icon: 'shield',       priority: 2 },
  staff:        { id: 'staff',        label: 'Staff',      colors: ['#34d399', '#5bf5c9'], icon: 'shield-check', priority: 3 },
  developer:    { id: 'developer',    label: 'Developer',  colors: ['#a35bf5', '#7c3df5'], icon: 'code',         priority: 4 },
  media:        { id: 'media',        label: 'Media',      colors: ['#f56ba0', '#f5455b'], icon: 'video',        priority: 5 },
  crystal_plus: { id: 'crystal_plus', label: 'Crystal+',   colors: ['#5b8af5', '#7c6af5'], icon: 'gem',          priority: 6 },
  member:       { id: 'member',       label: 'Member',     colors: ['#6b7280', '#4b5563'], icon: null,           priority: 9 },
}

// Ranks that unlock Crystal+ perks (premium cosmetics, priority support, etc).
// Staff/dev/owner ranks get perks for free as part of the role. Media is a
// visible title, not a perk grant, so it's deliberately left out of this list.
export const PERK_RANKS: RankId[] = ['owner', 'co_owner', 'admin', 'staff', 'developer', 'crystal_plus']

export function hasPerks(rank: RankId | undefined | null): boolean {
  return !!rank && PERK_RANKS.includes(rank)
}

/**
 * Whether `rank` is at least as high as `required`. Ranks are ordered by
 * priority (owner = 0 … member = 9), so every staff rank automatically
 * unlocks whatever Crystal+ unlocks.
 */
export function meetsRank(rank: RankId | undefined | null, required: RankId): boolean {
  if (!rank) return false
  return RANKS[rank].priority <= RANKS[required].priority
}

/** Short label for a locked item, e.g. "Crystal+" or "Team". */
export function lockLabel(required: RankId): string {
  return required === 'crystal_plus' ? 'Crystal+' : 'Team'
}

export const RANK_ORDER: RankId[] = ['owner', 'co_owner', 'admin', 'staff', 'developer', 'media', 'crystal_plus', 'member']
