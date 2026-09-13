import React from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { Sidebar } from './components/sidebar/Sidebar'
import { TitleBar } from './components/ui/TitleBar'
import { Dashboard } from './components/pages/Dashboard'
import { Launch } from './components/pages/Launch'
import { Instances } from './components/pages/Instances'
import { Cosmetics } from './components/pages/Cosmetics'
import { Logs } from './components/pages/Logs'
import { Friends } from './components/pages/Friends'
import { News } from './components/pages/News'
import { Settings } from './components/pages/Settings'
import { NotificationContainer } from './components/ui/Notifications'
import { UpdateBanner } from './components/ui/UpdateBanner'

export default function App() {
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
            <Route path="/cosmetics" element={<Cosmetics />} />
            <Route path="/logs" element={<Logs />} />
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
