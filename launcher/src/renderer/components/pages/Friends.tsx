import React, { useState, useEffect } from 'react'
import { UserPlus, Search, Trash2, Users, X } from 'lucide-react'
import { notify } from '../../store/notificationStore'
import { Page, PageHeader, EmptyState } from '../ui/Page'

interface Friend {
  id: string
  username: string
  uuid: string | null
  addedAt: number
}

const api = (window as any).crystal

export function Friends() {
  const [friends, setFriends] = useState<Friend[] | null>(null)
  // Who is playing with Nexora now; asked again every 30 seconds while the page is open.
  const [presence, setPresence] = useState<{ available: boolean; online: string[] }>({ available: false, online: [] })
  useEffect(() => {
    const ask = () => api?.friendsPresence?.().then((p: { available: boolean; online: string[] } | undefined) => p && setPresence(p))
    ask()
    const timer = setInterval(ask, 30000)
    return () => clearInterval(timer)
  }, [friends])
  const [search, setSearch] = useState('')
  const [adding, setAdding] = useState(false)
  const [newName, setNewName] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function refresh() {
    api?.listFriends().then((list: Friend[]) => setFriends(list || []))
  }

  useEffect(refresh, [])

  async function addFriend() {
    const name = newName.trim()
    if (!name) return
    setBusy(true)
    setError(null)
    const result = await api?.addFriend(name)
    setBusy(false)
    if (result?.success) {
      notify({ type: 'success', message: `${result.friend.username} hinzugefügt` })
      setNewName('')
      setAdding(false)
      refresh()
    } else {
      setError(result?.error || 'Diesen Spieler gibt es nicht.')
    }
  }

  async function removeFriend(id: string) {
    await api?.removeFriend(id)
    refresh()
  }

  const list = friends ?? []
  const filtered = list.filter(f => f.username.toLowerCase().includes(search.toLowerCase()))

  return (
    <Page>
      <PageHeader
        title="Freunde"
        description={presence.available
          ? 'Deine Liste liegt nur auf diesem PC. Wer gerade mit Nexora spielt, siehst du am grünen Punkt.'
          : 'Deine Liste liegt nur auf diesem PC. Den Online-Status gibt es, sobald ein Nexora-Server eingestellt ist.'}
        actions={
          <button onClick={() => { setAdding(true); setError(null) }} disabled={adding} className="crystal-btn-primary text-[13px] disabled:opacity-60">
            <UserPlus size={14} /> Hinzufügen
          </button>
        }
      />

      {adding && (
        <div className="crystal-card mb-5">
          <div className="flex items-center justify-between px-4 pt-3.5">
            <h2 className="text-[13px] font-semibold text-crystal-text">Freund hinzufügen</h2>
            <button onClick={() => setAdding(false)} aria-label="Abbrechen" className="p-1 rounded text-crystal-muted hover:text-crystal-text"><X size={14} /></button>
          </div>
          <div className="px-4 pt-3 pb-4 space-y-1.5">
            <label htmlFor="friend-name" className="crystal-label">Spielername</label>
            <div className="flex gap-2">
              <input
                id="friend-name"
                autoFocus
                maxLength={16}
                value={newName}
                onChange={e => { setNewName(e.target.value); setError(null) }}
                onKeyDown={e => e.key === 'Enter' && addFriend()}
                aria-invalid={!!error}
                className={`crystal-input flex-1 text-[13px] ${error ? 'border-crystal-danger/70' : ''}`}
              />
              <button onClick={addFriend} disabled={busy || !newName.trim()} className="crystal-btn-primary text-[13px] disabled:opacity-50">
                {busy ? 'Prüfe…' : 'Hinzufügen'}
              </button>
            </div>
            {error
              ? <p className="text-xs text-crystal-danger">{error}</p>
              : <p className="text-xs text-crystal-muted">Der Name wird bei Mojang geprüft, damit sich keine Tippfehler einschleichen.</p>}
          </div>
        </div>
      )}

      {list.length > 3 && (
        <div className="relative mb-3">
          <Search size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-crystal-muted" />
          <input
            value={search}
            onChange={e => setSearch(e.target.value)}
            className="crystal-input w-full pl-8 text-[13px]"
            placeholder="Freunde filtern"
            aria-label="Freunde filtern"
          />
        </div>
      )}

      {friends && list.length === 0 && !adding && (
        <EmptyState
          icon={<Users size={22} strokeWidth={1.75} />}
          title="Noch niemand in der Liste"
          action={<button onClick={() => setAdding(true)} className="crystal-btn-primary text-[13px]"><UserPlus size={14} /> Freund hinzufügen</button>}
        />
      )}

      {filtered.length > 0 && (
        <div className="crystal-card divide-y divide-crystal-border overflow-hidden">
          {filtered.map(friend => (
            <div key={friend.id} className="group flex items-center gap-3 px-4 py-2.5">
              <span className="relative w-8 h-8 rounded-md bg-crystal-panel border border-crystal-border flex items-center justify-center text-xs font-semibold text-crystal-text shrink-0">
                {friend.username.charAt(0).toUpperCase()}
                {presence.online.includes(friend.id) && (
                  <span className="absolute -right-0.5 -bottom-0.5 h-2.5 w-2.5 rounded-full bg-crystal-success ring-2 ring-crystal-card" aria-hidden="true" />
                )}
              </span>
              <div className="flex-1 min-w-0">
                <p className="text-[13px] text-crystal-text truncate">{friend.username}</p>
                <p className="text-xs text-crystal-muted">
                  Seit {new Date(friend.addedAt).toLocaleDateString('de-DE', { day: 'numeric', month: 'short', year: 'numeric' })}
                  {!friend.uuid && ', nicht verifiziert'}
                  {presence.online.includes(friend.id) && <span className="text-crystal-success">, spielt gerade mit Nexora</span>}
                </p>
              </div>
              <button
                onClick={() => removeFriend(friend.id)}
                aria-label={`${friend.username} entfernen`}
                className="p-1.5 rounded-md text-crystal-muted opacity-0 group-hover:opacity-100 focus:opacity-100 hover:text-crystal-danger hover:bg-crystal-border/50 transition"
              >
                <Trash2 size={13} strokeWidth={1.75} />
              </button>
            </div>
          ))}
        </div>
      )}

      {list.length > 0 && filtered.length === 0 && (
        <p className="text-center text-xs text-crystal-muted py-6">Niemand heißt so.</p>
      )}
    </Page>
  )
}
