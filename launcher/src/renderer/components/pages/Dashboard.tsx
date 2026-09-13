import React, { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Rocket, Users, Newspaper, Sparkles, Activity,
  Clock, Cpu, MemoryStick, Globe, ChevronRight,
  Zap, Shield, Star
} from 'lucide-react'
import { notify } from '../../store/notificationStore'
import { CrystalWordmark } from '../../theme/CrystalWordmark'

const newsItems = [
  {
    id: 1,
    title: 'Crystal Client 1.0 Released',
    excerpt: 'The first official release of Crystal Client is here. Featuring a brand new HUD system and improved performance.',
    date: '2 hours ago',
    tag: 'Release',
    tagColor: 'bg-crystal-success/20 text-crystal-success',
  },
  {
    id: 2,
    title: 'New Cosmetics: Crystal Wings',
    excerpt: 'Show off your style with the new Crystal Wings cape available in the Crystal Store.',
    date: '1 day ago',
    tag: 'Cosmetics',
    tagColor: 'bg-crystal-accent/20 text-crystal-accent',
  },
  {
    id: 3,
    title: 'Performance Update',
    excerpt: 'Improved rendering pipeline reduces GPU usage by up to 30% on mid-range hardware.',
    date: '3 days ago',
    tag: 'Update',
    tagColor: 'bg-crystal-warning/20 text-crystal-warning',
  },
]

const api = (window as any).crystal

export function Dashboard() {
  const navigate = useNavigate()
  const [instanceCount, setInstanceCount] = useState(0)
  const [modCount, setModCount] = useState(0)
  const [username, setUsername] = useState<string | null>(null)

  useEffect(() => {
    api?.getInstances().then((list: { id: string }[]) => {
      const instances = list || []
      setInstanceCount(instances.length)

      Promise.all(instances.map(i => api?.listContent(i.id, 'mod')))
        .then(results => setModCount(results.flat().filter(Boolean).length))
    })
    api?.getProfile().then((p: { username: string } | null) => setUsername(p?.username ?? null))
  }, [])

  const statCards = [
    { label: 'Instanzen',      value: String(instanceCount),      icon: Globe,    color: 'text-crystal-accent' },
    { label: 'Mods gesamt',    value: String(modCount),           icon: Cpu,      color: 'text-crystal-warning' },
    { label: 'Client-Version', value: '1.0.0',                    icon: Zap,      color: 'text-crystal-success' },
    { label: 'Minecraft',      value: '1.21.11',                  icon: Activity, color: 'text-crystal-accent-2' },
  ]

  return (
    <div className="p-6 space-y-6">
      {/* Hero */}
      <div className="relative crystal-card p-6 overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-br from-crystal-accent/10 via-transparent to-crystal-accent-2/10 pointer-events-none" />
        <div className="relative flex items-center justify-between">
          <div>
            {username && (
              <div className="flex items-center gap-2 mb-1">
                <div className="w-2 h-2 rounded-full bg-crystal-success" />
                <span className="text-crystal-success text-xs font-medium">Angemeldet als {username}</span>
              </div>
            )}
            <h1 className="text-3xl font-bold text-crystal-text mb-1">
              Welcome to <CrystalWordmark size={30} className="text-crystal-text align-middle" />
            </h1>
            <p className="text-crystal-muted text-sm">Crystal Client · Minecraft 1.21.11 · Fabric</p>
          </div>

          <button
            onClick={() => navigate('/launch')}
            className="flex items-center gap-2 px-6 py-3 rounded-xl bg-crystal-gradient text-white font-semibold shadow-glow hover:opacity-90 active:scale-95 transition-all duration-200"
          >
            <Rocket size={18} />
            Play
          </button>
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-4 gap-3">
        {statCards.map(({ label, value, icon: Icon, color }) => (
          <div key={label} className="crystal-card p-4 flex items-center gap-3">
            <div className={`p-2 rounded-lg bg-crystal-border ${color}`}>
              <Icon size={16} />
            </div>
            <div>
              <p className="text-crystal-text font-semibold">{value}</p>
              <p className="text-crystal-muted text-xs">{label}</p>
            </div>
          </div>
        ))}
      </div>

      {/* Main content */}
      <div className="grid grid-cols-3 gap-4">
        {/* News */}
        <div className="col-span-2 space-y-3">
          <div className="flex items-center justify-between">
            <h2 className="text-crystal-text font-semibold flex items-center gap-2">
              <Newspaper size={16} className="text-crystal-accent" />
              Latest News
            </h2>
            <button
              onClick={() => navigate('/news')}
              className="text-crystal-muted hover:text-crystal-accent text-xs flex items-center gap-1 transition-colors"
            >
              See all <ChevronRight size={12} />
            </button>
          </div>
          {newsItems.map(item => (
            <div key={item.id} className="crystal-card p-4 hover:border-crystal-accent/50 transition-colors cursor-pointer">
              <div className="flex items-start justify-between gap-3">
                <div className="flex-1">
                  <div className="flex items-center gap-2 mb-1.5">
                    <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${item.tagColor}`}>
                      {item.tag}
                    </span>
                    <span className="text-crystal-muted text-xs flex items-center gap-1">
                      <Clock size={10} /> {item.date}
                    </span>
                  </div>
                  <h3 className="text-crystal-text font-medium text-sm mb-1">{item.title}</h3>
                  <p className="text-crystal-muted text-xs leading-relaxed">{item.excerpt}</p>
                </div>
              </div>
            </div>
          ))}
        </div>

        {/* Right panel */}
        <div className="space-y-3">
          {/* Quick actions */}
          <div className="crystal-card p-4">
            <h3 className="text-crystal-text font-semibold text-sm mb-3 flex items-center gap-2">
              <Zap size={14} className="text-crystal-accent" /> Quick Actions
            </h3>
            <div className="space-y-2">
              {[
                { label: 'Spiel starten',      path: '/launch',    icon: Rocket },
                { label: 'Instanzen & Mods',   path: '/instances', icon: Globe },
                { label: 'Cosmetics',          path: '/cosmetics', icon: Sparkles },
                { label: 'Logs & Crashes',     path: '/logs',      icon: Cpu },
              ].map(({ label, path, icon: Icon }) => (
                <button
                  key={path}
                  onClick={() => navigate(path)}
                  className="w-full flex items-center justify-between px-3 py-2 rounded-lg bg-crystal-panel hover:bg-crystal-border text-crystal-muted hover:text-crystal-text transition-all text-sm"
                >
                  <span className="flex items-center gap-2">
                    <Icon size={14} /> {label}
                  </span>
                  <ChevronRight size={12} />
                </button>
              ))}
            </div>
          </div>

          {/* Friends — local only until there's a real backend */}
          <div className="crystal-card p-4">
            <h3 className="text-crystal-text font-semibold text-sm mb-3 flex items-center gap-2">
              <Users size={14} className="text-crystal-accent" /> Friends
            </h3>
            <p className="text-crystal-muted text-xs">
              Freundesliste braucht einen Crystal-Account-Server — aktuell nur lokale Vorschau.
            </p>
            <button
              onClick={() => navigate('/friends')}
              className="mt-2 text-crystal-accent text-xs hover:underline"
            >
              Ansehen →
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
