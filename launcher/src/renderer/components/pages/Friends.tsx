import React, { useState, useEffect } from 'react'
import { Users, UserPlus, Search, Trash2, X } from 'lucide-react'
import { notify } from '../../store/notificationStore'

interface Friend {
  id: string
  username: string
  uuid: string | null
  addedAt: number
}

const api = (window as any).crystal

export function Friends() {
  const [friends, setFriends] = useState<Friend[]>([])
  const [search, setSearch] = useState('')
  const [adding, setAdding] = useState(false)
  const [newName, setNewName] = useState('')
  const [busy, setBusy] = useState(false)

  function refresh() {
    api?.listFriends().then((list: Friend[]) => setFriends(list || []))
  }

  useEffect(refresh, [])

  async function addFriend() {
    const name = newName.trim()
    if (!name) return

    setBusy(true)
    const result = await api?.addFriend(name)
    setBusy(false)

    if (result?.success) {
      notify({ type: 'success', message: `${result.friend.username} hinzugefügt` })
      setNewName('')
      setAdding(false)
      refresh()
    } else {
      notify({ type: 'error', message: result?.error || 'Konnte Freund nicht hinzufügen' })
    }
  }

  async function removeFriend(id: string, username: string) {
    await api?.removeFriend(id)
    notify({ type: 'info', message: `${username} entfernt` })
    refresh()
  }

  const filtered = friends.filter(f => f.username.toLowerCase().includes(search.toLowerCase()))

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Users size={20} className="text-crystal-accent" />
          <h1 className="text-xl font-bold text-crystal-text">Freunde</h1>
          <span className="text-xs text-crystal-muted bg-crystal-border px-2 py-0.5 rounded-full">
            {friends.length}
          </span>
        </div>
        <button onClick={() => setAdding(true)} className="crystal-btn-primary flex items-center gap-1.5 text-sm">
          <UserPlus size={14} /> Freund hinzufügen
        </button>
      </div>

      {adding && (
        <div className="crystal-card p-4 space-y-3 border-crystal-accent/50">
          <div className="flex items-center justify-between">
            <h3 className="text-crystal-text font-medium text-sm">Freund hinzufügen</h3>
            <button onClick={() => setAdding(false)} className="text-crystal-muted hover:text-crystal-text">
              <X size={14} />
            </button>
          </div>
          <input
            autoFocus
            type="text"
            value={newName}
            onChange={e => setNewName(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && addFriend()}
            className="crystal-input w-full"
            placeholder="Minecraft-Username"
          />
          <p className="text-crystal-muted text-xs">
            Der Name wird gegen Mojang geprüft, damit keine Tippfehler in der Liste landen.
          </p>
          <div className="flex gap-2">
            <button onClick={addFriend} disabled={busy} className="crystal-btn-primary text-sm disabled:opacity-60">
              {busy ? 'Prüfe...' : 'Hinzufügen'}
            </button>
            <button onClick={() => setAdding(false)} className="crystal-btn-ghost text-sm">Abbrechen</button>
          </div>
        </div>
      )}

      {friends.length > 0 && (
        <div className="relative">
          <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-crystal-muted" />
          <input
            type="text"
            value={search}
            onChange={e => setSearch(e.target.value)}
            className="crystal-input w-full pl-8"
            placeholder="Freunde durchsuchen..."
          />
        </div>
      )}

      <div className="space-y-2">
        {filtered.map(friend => (
          <div key={friend.id} className="crystal-card p-4 flex items-center gap-3">
            <div className="w-9 h-9 rounded-full bg-crystal-gradient flex items-center justify-center text-white text-sm font-bold shrink-0">
              {friend.username.charAt(0).toUpperCase()}
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-crystal-text font-medium text-sm">{friend.username}</p>
              <p className="text-crystal-muted text-xs">
                {friend.uuid ? 'Verifizierter Account' : 'Nicht verifiziert (offline hinzugefügt)'}
              </p>
            </div>
            <button
              onClick={() => removeFriend(friend.id, friend.username)}
              className="p-2 rounded-lg hover:bg-crystal-border text-crystal-muted hover:text-crystal-danger transition-colors"
            >
              <Trash2 size={14} />
            </button>
          </div>
        ))}

        {friends.length === 0 && !adding && (
          <div className="crystal-card p-8 text-center text-crystal-muted">
            <Users size={32} className="mx-auto mb-2 opacity-30" />
            <p className="text-sm">Noch keine Freunde hinzugefügt.</p>
          </div>
        )}
      </div>

      <p className="text-crystal-muted text-xs">
        Die Liste liegt nur lokal auf diesem Rechner. Online-Status und Chat brauchen einen
        Crystal-Account-Server, den es noch nicht gibt.
      </p>
    </div>
  )
}
