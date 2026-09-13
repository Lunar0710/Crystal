import React from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import {
  LayoutDashboard, Rocket, Layers,
  Sparkles, Users, Newspaper,
  Settings, Terminal
} from 'lucide-react'

const navItems = [
  { path: '/dashboard', icon: LayoutDashboard, label: 'Dashboard' },
  { path: '/launch',    icon: Rocket,          label: 'Launch' },
  { path: '/instances', icon: Layers,          label: 'Instanzen & Mods' },
  { path: '/cosmetics', icon: Sparkles,        label: 'Cosmetics' },
  { path: '/logs',      icon: Terminal,        label: 'Logs' },
  { path: '/friends',   icon: Users,           label: 'Friends' },
  { path: '/news',      icon: Newspaper,       label: 'News' },
]

export function Sidebar() {
  return (
    <nav className="flex flex-col w-16 bg-crystal-panel border-r border-crystal-border py-3 gap-1 items-center">
      {navItems.map(({ path, icon: Icon, label }) => (
        <NavLink
          key={path}
          to={path}
          title={label}
          className={({ isActive }) =>
            `group relative flex items-center justify-center w-10 h-10 rounded-xl transition-all duration-200 ` +
            (isActive
              ? 'bg-crystal-gradient text-white shadow-glow'
              : 'text-crystal-muted hover:text-crystal-text hover:bg-crystal-card')
          }
        >
          <Icon size={18} />
          <Tooltip label={label} />
        </NavLink>
      ))}

      <div className="flex-1" />

      <NavLink
        to="/settings"
        title="Settings"
        className={({ isActive }) =>
          `group relative flex items-center justify-center w-10 h-10 rounded-xl transition-all duration-200 ` +
          (isActive
            ? 'bg-crystal-gradient text-white shadow-glow'
            : 'text-crystal-muted hover:text-crystal-text hover:bg-crystal-card')
        }
      >
        <Settings size={18} />
        <Tooltip label="Settings" />
      </NavLink>

      <div className="w-8 h-8 rounded-full bg-crystal-gradient flex items-center justify-center text-xs font-bold text-white mt-2 cursor-pointer shadow-crystal">
        C
      </div>
    </nav>
  )
}

function Tooltip({ label }: { label: string }) {
  return (
    <div className="absolute left-full ml-3 px-2 py-1 bg-crystal-card border border-crystal-border rounded-lg text-xs text-crystal-text whitespace-nowrap opacity-0 group-hover:opacity-100 pointer-events-none transition-opacity z-50 shadow-card">
      {label}
    </div>
  )
}
