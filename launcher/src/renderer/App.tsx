import React, { useEffect } from 'react'
import { EquippedCosmetics, syncLoadoutToGame } from './data/cosmetics'
import { fillCapeCache } from './data/capeCache'
import { Routes, Route, Navigate, useNavigate } from 'react-router-dom'
import { Sidebar } from './components/sidebar/Sidebar'
import { TitleBar } from './components/ui/TitleBar'
import { Dashboard } from './components/pages/Dashboard'
import { Launch } from './components/pages/Launch'
import { Instances } from './components/pages/Instances'
import { Cosmetics } from './components/pages/Cosmetics'
import { Logs } from './components/pages/Logs'
import { Screenshots } from './components/pages/Screenshots'
import { Stats } from './components/pages/Stats'
import { Servers } from './components/pages/Servers'
import { Friends } from './components/pages/Friends'
import { News } from './components/pages/News'
import { Settings } from './components/pages/Settings'
import { NotificationContainer } from './components/ui/Notifications'
import { UpdateBanner } from './components/ui/UpdateBanner'

export default function App() {
  const navigate = useNavigate()
  // A desktop shortcut: open the start page on that instance and start it.
  useEffect(() => (window as any).crystal?.on('app:quickLaunch', (id: string) => {
    navigate(`/launch?instance=${encodeURIComponent(id)}&autostart=1`)
  }), [navigate])

  // Equipped cosmetics reach the game even if the Cosmetics page is never opened.
  useEffect(() => {
    const api = (window as any).crystal
    api?.getLoadout().then((l: EquippedCosmetics | null) => {
      if (!l) return
      syncLoadoutToGame(l)
      // Which built-in cape you wear, for other Nexora players (uploaded ones stay private).
      api?.syncCapeId(l.cape?.startsWith('builtin:') ? l.cape.slice('builtin:'.length) : null)
    })
    // Pictures of every built-in cape for showing other players' capes; once
    // per launcher version, a little after start so it doesn't slow it down.
    const timer = setTimeout(() => { fillCapeCache().catch(() => {}) }, 8000)
    return () => clearTimeout(timer)
  }, [])

  return (
    <div className="relative flex flex-col h-full bg-crystal-bg">
      <TitleBar />
      <div className="flex flex-1 overflow-hidden">
        <Sidebar />
        <main className="flex-1 overflow-y-auto overflow-x-hidden" id="main">
          <Routes>
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/launch" element={<Launch />} />
            <Route path="/instances" element={<Instances />} />
            <Route path="/servers" element={<Servers />} />
            <Route path="/cosmetics" element={<Cosmetics />} />
            <Route path="/logs" element={<Logs />} />
            <Route path="/screenshots" element={<Screenshots />} />
            <Route path="/stats" element={<Stats />} />
            <Route path="/friends" element={<Friends />} />
            <Route path="/news" element={<News />} />
            <Route path="/settings" element={<Settings />} />
          </Routes>
        </main>
      </div>
      <NotificationContainer />
      <UpdateBanner />
    </div>
  )
}
