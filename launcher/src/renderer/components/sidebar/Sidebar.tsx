import React, { useEffect, useState } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router-dom'
import {
  Home, Play, Boxes, Shirt, Users, Newspaper, ScrollText, Settings, Images, Server, BarChart3, Swords, Sparkles, type LucideIcon,
} from 'lucide-react'
import { RankBadge } from '../ui/RankBadge'
import type { RankId } from '../../data/ranks'

interface NavItem {
  path: string
  icon: LucideIcon
  label: string
}

const groups: { title: string; items: NavItem[] }[] = [
  {
    title: 'Spielen',
    items: [
      { path: '/dashboard', icon: Home,  label: 'Übersicht' },
      { path: '/launch',    icon: Play,  label: 'Starten' },
      { path: '/instances', icon: Boxes, label: 'Instanzen' },
      { path: '/servers',   icon: Server, label: 'Server' },
    ],
  },
  {
    title: 'Profil',
    items: [
      { path: '/cosmetics', icon: Shirt, label: 'Cosmetics' },
      { path: '/screenshots', icon: Images, label: 'Screenshots' },
      { path: '/stats', icon: BarChart3, label: 'Statistik' },
      { path: '/fights', icon: Swords, label: 'Kämpfe' },
      { path: '/wrapped', icon: Sparkles, label: 'Rückblick' },
      { path: '/friends',   icon: Users, label: 'Freunde' },
    ],
  },
  {
    title: 'Mehr',
    items: [
      { path: '/news', icon: Newspaper,  label: 'Neuigkeiten' },
      { path: '/logs', icon: ScrollText, label: 'Logs' },
    ],
  },
]

const api = (window as any).crystal

export function Sidebar() {
  const location = useLocation()
  const navigate = useNavigate()
  const [username, setUsername] = useState<string | null>(null)
  const [rank, setRank] = useState<RankId | null>(null)

  // Re-read on navigation: logging in or switching accounts happens on other
  // pages, and the chip should follow without a reload.
  useEffect(() => {
    api?.getProfile().then((p: { username: string } | null) => setUsername(p?.username ?? null))
    api?.getRank().then((r: RankId | null) => setRank(r ?? null)).catch(() => setRank(null))
  }, [location.pathname])

  return (
    <nav className="flex flex-col w-56 shrink-0 bg-crystal-panel/55 border-r border-white/[0.06]">
      <div className="flex-1 overflow-y-auto px-2.5 pt-4 pb-2 space-y-5">
        {groups.map(group => (
          <div key={group.title}>
            <p className="px-3 mb-1.5 text-[10px] font-medium uppercase tracking-[0.16em] text-crystal-muted/60">{group.title}</p>
            <div className="space-y-0.5">
              {group.items.map(item => <Item key={item.path} {...item} />)}
            </div>
          </div>
        ))}
      </div>

      <div className="px-2.5 py-2.5 border-t border-white/[0.06] space-y-px">
        <Item path="/settings" icon={Settings} label="Einstellungen" />
        <button
          onClick={() => navigate('/launch')}
          className="w-full flex items-center gap-2.5 px-2.5 py-2 rounded-xl text-left hover:bg-white/[0.04] nexora-ease"
        >
          <span className="w-7 h-7 rounded-full bg-crystal-accent/15 ring-1 ring-inset ring-crystal-accent/30 text-crystal-text text-[11px] font-semibold flex items-center justify-center shrink-0">
            {username ? username.charAt(0).toUpperCase() : '?'}
          </span>
          <span className="min-w-0 flex-1">
            <span className="flex items-center gap-1.5 min-w-0">
              <span className="text-[13px] text-crystal-text truncate">{username ?? 'Nicht angemeldet'}</span>
              {username && <RankBadge rank={rank} size="sm" />}
            </span>
            <span className="block text-[11px] text-crystal-muted">{username ? 'Konto wechseln' : 'Anmelden'}</span>
          </span>
        </button>
      </div>
    </nav>
  )
}

function Item({ path, icon: Icon, label }: NavItem) {
  return (
    <NavLink
      to={path}
      className={({ isActive }) =>
        `relative flex items-center gap-3 px-3 py-2 rounded-full text-[13px] nexora-ease ` +
        (isActive
          ? 'nexora-nav-active text-crystal-text font-medium'
          : 'text-crystal-muted hover:text-crystal-text hover:bg-white/[0.04]')
      }
    >
      {({ isActive }) => (
        <>
          <Icon size={16} strokeWidth={isActive ? 2.1 : 1.75} className={isActive ? 'text-crystal-accent' : ''} />
          {label}
        </>
      )}
    </NavLink>
  )
}
