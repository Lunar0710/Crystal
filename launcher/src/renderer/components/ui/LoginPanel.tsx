import React, { useState, useEffect, useRef } from 'react'
import { LogIn, ChevronDown, Plus, X, Check } from 'lucide-react'
import { notify } from '../../store/notificationStore'
import { RankBadge } from './RankBadge'
import { RankId } from '../../data/ranks'

interface Profile {
  username: string
  uuid: string
  type: 'microsoft' | 'offline'
}

const api = (window as any).crystal

export function LoginPanel({ profile, onProfileChange }: { profile: Profile | null; onProfileChange: (p: Profile | null) => void }) {
  const [loading, setLoading] = useState(false)
  const [offlineName, setOfflineName] = useState('')
  const [showOffline, setShowOffline] = useState(false)
  const [rank, setRank] = useState<RankId>('member')
  const [accounts, setAccounts] = useState<Profile[]>([])
  const [open, setOpen] = useState(false)
  const [switching, setSwitching] = useState<string | null>(null)
  const menuRef = useRef<HTMLDivElement>(null)

  function refreshAccounts() {
    api?.listAccounts().then((list: Profile[]) => setAccounts(list || []))
  }

  useEffect(() => {
    refreshAccounts()
    if (profile) api?.getRank().then(setRank)
  }, [profile])

  useEffect(() => {
    if (!open) return
    const close = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) setOpen(false)
    }
    window.addEventListener('mousedown', close)
    return () => window.removeEventListener('mousedown', close)
  }, [open])

  async function addMicrosoft() {
    setLoading(true)
    setOpen(false)
    const result = await api?.loginMicrosoft()
    setLoading(false)
    if (result) {
      onProfileChange(result)
      refreshAccounts()
      notify({ type: 'success', message: `${result.username} hinzugefügt` })
    } else {
      notify({ type: 'error', title: 'Anmeldung abgebrochen', message: 'Die Microsoft-Anmeldung wurde nicht abgeschlossen.' })
    }
  }

  async function addOffline() {
    const name = offlineName.trim()
    if (!name) return
    const result = await api?.loginOffline(name)
    onProfileChange(result)
    setOfflineName('')
    setShowOffline(false)
    refreshAccounts()
  }

  async function switchTo(uuid: string) {
    if (uuid === profile?.uuid) return setOpen(false)
    setSwitching(uuid)
    const result = await api?.switchAccount(uuid)
    setSwitching(null)
    setOpen(false)
    if (result) onProfileChange(result)
  }

  async function remove(uuid: string, e: React.MouseEvent) {
    e.stopPropagation()
    await api?.removeAccount(uuid)
    const next = await api?.getProfile()
    onProfileChange(next || null)
    refreshAccounts()
  }

  if (!profile) {
    return (
      <div className="crystal-card p-4 space-y-3">
        <div>
          <h3 className="text-crystal-text text-sm font-medium">Anmelden</h3>
          <p className="text-crystal-muted text-xs mt-0.5">Du kannst später weitere Konten hinzufügen und zwischen ihnen wechseln.</p>
        </div>

        <button
          onClick={addMicrosoft}
          disabled={loading}
          className="crystal-btn-primary w-full flex items-center justify-center gap-2 text-sm disabled:opacity-60"
        >
          <LogIn size={14} />
          {loading ? 'Warte auf Microsoft…' : 'Mit Microsoft anmelden'}
        </button>

        {!showOffline ? (
          <button onClick={() => setShowOffline(true)} className="w-full text-crystal-muted hover:text-crystal-text text-xs transition-colors">
            Offline spielen
          </button>
        ) : (
          <OfflineForm value={offlineName} onChange={setOfflineName} onSubmit={addOffline} />
        )}
      </div>
    )
  }

  return (
    <div className="relative" ref={menuRef}>
      <button
        onClick={() => setOpen(o => !o)}
        className="crystal-card w-full p-3 flex items-center gap-3 text-left hover:border-crystal-accent/40 transition-colors"
      >
        <Avatar name={profile.username} />
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1.5">
            <span className="text-crystal-text text-sm font-medium truncate">{profile.username}</span>
            <RankBadge rank={rank} />
          </div>
          <span className="text-crystal-muted text-xs">
            {profile.type === 'microsoft' ? 'Microsoft' : 'Offline'}
            {accounts.length > 1 && ` · ${accounts.length} Konten`}
          </span>
        </div>
        <ChevronDown size={14} className={`text-crystal-muted transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>

      {open && (
        <div className="absolute z-20 left-0 right-0 mt-1 rounded-lg border border-crystal-border bg-crystal-panel shadow-xl overflow-hidden">
          <div className="max-h-64 overflow-y-auto py-1">
            {accounts.map(acc => (
              <div
                key={acc.uuid}
                onClick={() => switchTo(acc.uuid)}
                className="group flex items-center gap-3 px-3 py-2 cursor-pointer hover:bg-crystal-card"
              >
                <Avatar name={acc.username} small />
                <div className="flex-1 min-w-0">
                  <p className="text-sm text-crystal-text truncate">{acc.username}</p>
                  <p className="text-[11px] text-crystal-muted">{acc.type === 'microsoft' ? 'Microsoft' : 'Offline'}</p>
                </div>
                {switching === acc.uuid && <span className="text-xs text-crystal-muted">…</span>}
                {acc.uuid === profile.uuid && <Check size={14} className="text-crystal-accent" />}
                <button
                  onClick={e => remove(acc.uuid, e)}
                  title="Konto entfernen"
                  className="p-1 rounded text-crystal-muted opacity-0 group-hover:opacity-100 hover:text-crystal-danger transition-opacity"
                >
                  <X size={12} />
                </button>
              </div>
            ))}
          </div>

          <div className="border-t border-crystal-border p-2 space-y-1">
            <button
              onClick={addMicrosoft}
              disabled={loading}
              className="w-full flex items-center gap-2 px-2 py-1.5 rounded text-sm text-crystal-text hover:bg-crystal-card disabled:opacity-60"
            >
              <Plus size={14} className="text-crystal-muted" />
              {loading ? 'Warte auf Microsoft…' : 'Microsoft-Konto hinzufügen'}
            </button>
            {!showOffline ? (
              <button
                onClick={() => setShowOffline(true)}
                className="w-full flex items-center gap-2 px-2 py-1.5 rounded text-sm text-crystal-muted hover:text-crystal-text hover:bg-crystal-card"
              >
                <Plus size={14} />
                Offline-Konto hinzufügen
              </button>
            ) : (
              <div className="px-1 pt-1">
                <OfflineForm value={offlineName} onChange={setOfflineName} onSubmit={addOffline} />
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

function Avatar({ name, small }: { name: string; small?: boolean }) {
  const size = small ? 'w-7 h-7 text-[11px]' : 'w-9 h-9 text-xs'
  return (
    <div className={`${size} rounded-md bg-crystal-border text-crystal-text font-semibold flex items-center justify-center shrink-0`}>
      {name.charAt(0).toUpperCase()}
    </div>
  )
}

function OfflineForm({ value, onChange, onSubmit }: { value: string; onChange: (v: string) => void; onSubmit: () => void }) {
  return (
    <div className="flex gap-2">
      <input
        autoFocus
        type="text"
        value={value}
        maxLength={16}
        onChange={e => onChange(e.target.value)}
        onKeyDown={e => e.key === 'Enter' && onSubmit()}
        className="crystal-input flex-1 text-sm"
        placeholder="Spielername"
      />
      <button onClick={onSubmit} disabled={!value.trim()} className="crystal-btn-primary text-sm px-3 disabled:opacity-50">
        Hinzufügen
      </button>
    </div>
  )
}
