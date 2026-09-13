import React, { useState, useEffect } from 'react'
import { User, LogIn, LogOut } from 'lucide-react'
import { notify } from '../../store/notificationStore'
import { RankBadge } from './RankBadge'
import { RankId } from '../../data/ranks'

interface Profile {
  username: string
  uuid: string
  type: 'microsoft' | 'offline'
}

export function LoginPanel({ profile, onProfileChange }: { profile: Profile | null; onProfileChange: (p: Profile | null) => void }) {
  const [loading, setLoading] = useState(false)
  const [offlineName, setOfflineName] = useState('')
  const [showOffline, setShowOffline] = useState(false)
  const [rank, setRank] = useState<RankId>('member')
  const api = (window as any).crystal

  useEffect(() => {
    if (profile) api?.getRank().then(setRank)
  }, [profile])

  async function loginMicrosoft() {
    setLoading(true)
    const result = await api?.loginMicrosoft()
    setLoading(false)
    if (result) {
      onProfileChange(result)
      notify({ type: 'success', title: 'Angemeldet', message: `Willkommen, ${result.username}!` })
    } else {
      notify({ type: 'error', title: 'Login fehlgeschlagen', message: 'Microsoft-Anmeldung wurde abgebrochen oder ist fehlgeschlagen.' })
    }
  }

  async function loginOffline() {
    if (!offlineName.trim()) return
    const result = await api?.loginOffline(offlineName.trim())
    onProfileChange(result)
    setShowOffline(false)
    notify({ type: 'info', title: 'Offline-Modus', message: `Angemeldet als ${offlineName} (offline)` })
  }

  async function logout() {
    await api?.logout()
    onProfileChange(null)
    notify({ type: 'info', message: 'Abgemeldet' })
  }

  if (profile) {
    return (
      <div className="crystal-card p-3 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-full bg-crystal-gradient flex items-center justify-center text-white text-xs font-bold">
            {profile.username.charAt(0).toUpperCase()}
          </div>
          <div>
            <div className="flex items-center gap-1.5">
              <p className="text-crystal-text text-sm font-medium">{profile.username}</p>
              <RankBadge rank={rank} />
            </div>
            <p className="text-crystal-muted text-xs">{profile.type === 'microsoft' ? 'Microsoft-Konto' : 'Offline'}</p>
          </div>
        </div>
        <button onClick={logout} className="p-2 rounded-lg hover:bg-crystal-border text-crystal-muted hover:text-crystal-danger transition-colors">
          <LogOut size={14} />
        </button>
      </div>
    )
  }

  return (
    <div className="crystal-card p-4 space-y-3">
      <div className="flex items-center gap-2">
        <User size={16} className="text-crystal-accent" />
        <h3 className="text-crystal-text font-medium text-sm">Nicht angemeldet</h3>
      </div>

      <button
        onClick={loginMicrosoft}
        disabled={loading}
        className="w-full flex items-center justify-center gap-2 py-2.5 rounded-lg bg-crystal-gradient text-white text-sm font-medium hover:opacity-90 active:scale-95 transition-all disabled:opacity-60"
      >
        <LogIn size={14} />
        {loading ? 'Anmelden...' : 'Mit Microsoft anmelden'}
      </button>

      {!showOffline ? (
        <button onClick={() => setShowOffline(true)} className="w-full text-crystal-muted hover:text-crystal-text text-xs transition-colors">
          Oder im Offline-Modus spielen
        </button>
      ) : (
        <div className="flex gap-2">
          <input
            autoFocus
            type="text"
            value={offlineName}
            onChange={e => setOfflineName(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && loginOffline()}
            className="crystal-input flex-1 text-sm"
            placeholder="Username"
          />
          <button onClick={loginOffline} className="crystal-btn-primary text-sm px-3">OK</button>
        </div>
      )}
    </div>
  )
}
