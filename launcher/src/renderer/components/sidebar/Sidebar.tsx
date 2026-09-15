import React, { useEffect, useState } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router-dom'
import {
  Home, Play, Boxes, Shirt, Users, Newspaper, ScrollText, Settings, type LucideIcon,
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
    ],
  },
  {
    title: 'Profil',
    items: [
      { path: '/cosmetics', icon: Shirt, label: 'Cosmetics' },
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
    <nav className="flex flex-col w-52 shrink-0 bg-crystal-panel border-r border-crystal-border">
      <div className="flex-1 overflow-y-auto px-2.5 pt-4 pb-2 space-y-5">
        {groups.map(group => (
          <div key={group.title}>
            <p className="px-2.5 mb-1 text-[11px] font-medium text-crystal-muted/80">{group.title}</p>
            <div className="space-y-px">
              {group.items.map(item => <Item key={item.path} {...item} />)}
            </div>
          </div>
        ))}
      </div>

      <div className="px-2.5 py-2.5 border-t border-crystal-border space-y-px">
        <Item path="/settings" icon={Settings} label="Einstellungen" />
        <button
          onClick={() => navigate('/launch')}
          className="w-full flex items-center gap-2.5 px-2.5 py-2 rounded-md text-left hover:bg-crystal-card transition-colors"
        >
          <span className="w-6 h-6 rounded bg-crystal-border text-crystal-text text-[11px] font-semibold flex items-center justify-center shrink-0">
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
        `relative flex items-center gap-2.5 px-2.5 py-[7px] rounded-md text-[13px] transition-colors ` +
        (isActive
          ? 'bg-crystal-card text-crystal-text font-medium'
          : 'text-crystal-muted hover:text-crystal-text hover:bg-crystal-card/60')
      }
    >
      {({ isActive }) => (
        <>
          {isActive && <span className="absolute left-0 top-1.5 bottom-1.5 w-[2px] rounded-full bg-crystal-accent" />}
          <Icon size={15} strokeWidth={isActive ? 2 : 1.75} className={isActive ? 'text-crystal-accent' : ''} />
          {label}
        </>
      )}
    </NavLink>
  )
}
