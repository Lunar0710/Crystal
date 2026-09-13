import React from 'react'
import { Crown, Shield, ShieldCheck, Code, Video, Gem } from 'lucide-react'
import { RANKS, RankId } from '../../data/ranks'

const ICONS = { crown: Crown, shield: Shield, 'shield-check': ShieldCheck, code: Code, video: Video, gem: Gem }

export function RankBadge({ rank, size = 'sm' }: { rank?: RankId | null; size?: 'sm' | 'md' }) {
  if (!rank || rank === 'member') return null
  const def = RANKS[rank]
  const Icon = def.icon ? ICONS[def.icon] : null
  const pad = size === 'sm' ? 'px-1.5 py-0.5 text-[10px] gap-0.5' : 'px-2 py-1 text-xs gap-1'

  return (
    <span
      className={`inline-flex items-center ${pad} rounded-full font-semibold text-white shrink-0`}
      style={{ background: `linear-gradient(135deg, ${def.colors[0]}, ${def.colors[1]})` }}
    >
      {Icon && <Icon size={size === 'sm' ? 10 : 12} />}
      {def.label}
    </span>
  )
}
