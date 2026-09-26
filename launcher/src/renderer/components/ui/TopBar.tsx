import React, { useEffect, useRef, useState } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router-dom'
import {
  Gamepad2, Boxes, Shirt, Server, Users, LayoutGrid, Settings, SquareTerminal, Minus, Square, X,
  BarChart3, Swords, Sparkles, Images, Newspaper, type LucideIcon,
} from 'lucide-react'
import { CrystalWordmark } from '../../theme/CrystalWordmark'
import { RankBadge } from './RankBadge'
import { PlayerHead } from './PlayerHead'
import type { RankId } from '../../data/ranks'
import { LOGO_CHANGED } from './TitleBar'

const api = (window as any).crystal
const isMac = api?.platform === 'darwin'
const noDrag = { WebkitAppRegion: 'no-drag' } as React.CSSProperties

/** The four places you go most, as tabs in the middle (Feather's layout). */
const TABS: { path: string; icon: LucideIcon; label: string; also?: string[] }[] = [
  { path: '/dashboard', icon: Gamepad2, label: 'Spielen', also: ['/launch'] },
  { path: '/instances', icon: Boxes, label: 'Mods' },
  { path: '/cosmetics', icon: Shirt, label: 'Cosmetics' },
  { path: '/servers', icon: Server, label: 'Server' },
]

/** Everything else, behind the grid button. */
const MORE: { path: string; icon: LucideIcon; label: string }[] = [
  { path: '/stats', icon: BarChart3, label: 'Statistik' },
  { path: '/fights', icon: Swords, label: 'Kämpfe' },
  { path: '/wrapped', icon: Sparkles, label: 'Rückblick' },
  { path: '/screenshots', icon: Images, label: 'Screenshots' },
  { path: '/news', icon: Newspaper, label: 'Neuigkeiten' },
]

/**
 * Title bar and navigation in one, the way Feather's launcher is laid out:
 * logo on the left, the main tabs in the middle with a red line under the
 * open one, the account, then square icon buttons and the window controls.
 * The bar itself drags the window; everything clickable opts out.
 */
export function TopBar() {
  const location = useLocation()
  const navigate = useNavigate()
  const [username, setUsername] = useState<string | null>(null)
  const [rank, setRank] = useState<RankId | null>(null)
  const [logo, setLogo] = useState<string | null>(null)
  const [moreOpen, setMoreOpen] = useState(false)
  const moreRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    api?.getProfile().then((p: { username: string } | null) => setUsername(p?.username ?? null))
    api?.getRank().then((r: RankId | null) => setRank(r ?? null)).catch(() => setRank(null))
    setMoreOpen(false)
  }, [location.pathname])

  useEffect(() => {
    const load = () => api?.getLogo?.().then((l: string | null) => setLogo(l ?? null))
    load()
    window.addEventListener(LOGO_CHANGED, load)
    return () => window.removeEventListener(LOGO_CHANGED, load)
  }, [])

  useEffect(() => {
    if (!moreOpen) return
    const close = (e: MouseEvent) => { if (!moreRef.current?.contains(e.target as Node)) setMoreOpen(false) }
    window.addEventListener('mousedown', close)
    return () => window.removeEventListener('mousedown', close)
  }, [moreOpen])

  const moreActive = MORE.some(m => location.pathname.startsWith(m.path))

  return (
    <header
      className={`grid grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-4 h-[60px] shrink-0 pr-3 select-none ${isMac ? 'pl-[84px]' : 'pl-5'}`}
      style={{ WebkitAppRegion: 'drag' } as React.CSSProperties}
      onDoubleClick={isMac ? () => api?.maximize() : undefined}
    >
      <button onClick={() => navigate('/dashboard')} style={noDrag} className="flex items-center shrink-0" aria-label="Zur Übersicht">
        {logo
          ? <img src={logo} alt="Logo" className="h-6 max-w-[170px] object-contain" draggable={false} />
          : <CrystalWordmark size={15} className="text-crystal-text" />}
      </button>

      <div className="flex justify-center min-w-0">
        {/* Its own grid column: the tabs can never run under the logo or the account. */}
        <nav style={noDrag} className="flex items-center gap-0.5 min-w-0 overflow-hidden rounded-lg bg-[#1c1d1f] p-1">
          {TABS.map(tab => {
            const active = location.pathname.startsWith(tab.path) || !!tab.also?.some(p => location.pathname.startsWith(p))
            return (
              <NavLink
                key={tab.path}
                to={tab.path}
                className={`relative flex items-center gap-2 px-3 h-9 rounded-md whitespace-nowrap text-[13px] font-semibold transition-colors ${
                  active ? 'text-crystal-text' : 'text-[#7d7d7f] hover:text-crystal-text'
                }`}
              >
                <tab.icon size={15} strokeWidth={2} />
                {tab.label}
                {active && <span className="absolute left-1/2 -translate-x-1/2 bottom-0.5 h-[3px] w-7 rounded-full bg-crystal-accent" />}
              </NavLink>
            )
          })}
        </nav>
      </div>

      <div className="flex items-center gap-3">
      <button
        onClick={() => navigate('/launch')}
        style={noDrag}
        className="flex items-center gap-2.5 h-11 pl-1.5 pr-3 rounded-lg bg-[#131415] hover:bg-[#1a1b1d] transition-colors"
        title={username ? 'Konto wechseln' : 'Anmelden'}
      >
        <PlayerHead name={username} size={32} />
        <span className="text-left leading-tight">
          <span className="block text-[10px] text-crystal-muted">{username ? 'Angemeldet als' : 'Nicht angemeldet'}</span>
          <span className="flex items-center gap-1.5 text-[13px] font-semibold text-crystal-text">
            {username ?? 'Anmelden'}
            {username && rank && rank !== 'member' && <RankBadge rank={rank} size="sm" />}
          </span>
        </span>
      </button>

      <div className="flex items-center gap-1.5" style={noDrag}>
        <IconButton label="Freunde" active={location.pathname.startsWith('/friends')} onClick={() => navigate('/friends')}><Users size={17} /></IconButton>
        <div className="relative" ref={moreRef}>
          <IconButton label="Mehr" active={moreActive || moreOpen} onClick={() => setMoreOpen(o => !o)}><LayoutGrid size={17} /></IconButton>
          {moreOpen && (
            <div className="absolute right-0 top-[calc(100%+6px)] z-50 w-48 rounded-lg border border-crystal-border bg-[#111113] p-1 shadow-xl">
              {MORE.map(item => (
                <button
                  key={item.path}
                  onClick={() => navigate(item.path)}
                  className={`w-full flex items-center gap-2.5 px-3 py-2 rounded-md text-[13px] text-left transition-colors ${
                    location.pathname.startsWith(item.path) ? 'bg-crystal-accent/15 text-crystal-text' : 'text-crystal-muted hover:bg-white/[0.05] hover:text-crystal-text'
                  }`}
                >
                  <item.icon size={15} /> {item.label}
                </button>
              ))}
            </div>
          )}
        </div>
        <IconButton label="Einstellungen" active={location.pathname.startsWith('/settings')} onClick={() => navigate('/settings')}><Settings size={17} /></IconButton>
        <IconButton label="Logs (eigenes Fenster)" onClick={() => api?.openLogsWindow?.()}><SquareTerminal size={17} /></IconButton>
        {!isMac && (
          <>
            <span className="w-px h-6 bg-white/[0.08] mx-1" />
            <IconButton label="Minimieren" onClick={() => api?.minimize()}><Minus size={16} /></IconButton>
            <IconButton label="Maximieren" onClick={() => api?.maximize()}><Square size={13} /></IconButton>
            <button
              onClick={() => api?.close()}
              aria-label="Schließen"
              title="Schließen"
              className="w-9 h-9 flex items-center justify-center rounded-md bg-[#3a1614] text-crystal-accent hover:bg-crystal-accent hover:text-white transition-colors"
            >
              <X size={17} strokeWidth={2.2} />
            </button>
          </>
        )}
      </div>
      </div>
    </header>
  )
}

function IconButton({ label, active, onClick, children }: { label: string; active?: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      onClick={onClick}
      aria-label={label}
      title={label}
      className={`w-9 h-9 flex items-center justify-center rounded-md transition-colors ${
        active ? 'bg-crystal-accent/20 text-crystal-text' : 'bg-[#131415] text-[#b5b5b8] hover:bg-[#1f2022] hover:text-crystal-text'
      }`}
    >
      {children}
    </button>
  )
}
