import React from 'react'
import { Crown, Shield, ShieldCheck, Code, Video, Gem } from 'lucide-react'
import { RANKS, RankId } from '../../data/ranks'

const ICONS = { crown: Crown, shield: Shield, 'shield-check': ShieldCheck, code: Code, video: Video, gem: Gem }

/** Tinted label in the rank's own colour rather than a glossy gradient pill. */
export function RankBadge({ rank, size = 'sm' }: { rank?: RankId | null; size?: 'sm' | 'md' }) {
  if (!rank || rank === 'member') return null
  const def = RANKS[rank]
  const Icon = def.icon ? ICONS[def.icon] : null
  const color = def.colors[0]
  const pad = size === 'sm' ? 'px-1.5 py-px text-[10.5px] gap-1' : 'px-2 py-0.5 text-xs gap-1'

  return (
    <span
      className={`inline-flex items-center ${pad} rounded font-medium shrink-0 leading-[1.5]`}
      style={{ color, background: `${color}1f`, boxShadow: `inset 0 0 0 1px ${color}40` }}
    >
      {Icon && <Icon size={size === 'sm' ? 10 : 12} strokeWidth={2} />}
      {def.label}
    </span>
  )
}
