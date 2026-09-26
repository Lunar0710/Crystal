import React, { useEffect, useMemo, useRef, useState } from 'react'
import { UserPlus, Users, X, Check, Send, MessageCircle, UserMinus, RefreshCw, Gamepad2, LogIn, MailPlus } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { notify } from '../../store/notificationStore'
import { Page, PageHeader, EmptyState } from '../ui/Page'

type Status = 'offline' | 'launcher' | 'game'
interface Person { uuid: string; name: string | null }
interface Friend extends Person { status: Status }
interface ChatMessage { id: string; from: string; to: string; name: string; text: string; at: number }
interface Invite { from: Person; server: string; at: number }
type NetState = 'no-server' | 'offline-account' | 'no-account' | 'connecting' | 'online' | 'error'
interface Snapshot {
  state: NetState
  me: { uuid: string; name: string } | null
  friends: Friend[]
  incoming: Person[]
  outgoing: Person[]
  unread: Record<string, number>
}

const api = (window as any).crystal

const STATUS_LABEL: Record<Status, string> = { game: 'Spielt gerade', launcher: 'Im Launcher', offline: 'Offline' }
const STATUS_DOT: Record<Status, string> = { game: 'bg-crystal-success', launcher: 'bg-sky-400', offline: 'bg-crystal-muted/40' }
const STATE_TEXT: Record<Exclude<NetState, 'online'>, string> = {
  'no-server': 'Der Nexora-Server ist gerade nicht erreichbar. Prüf deine Internetverbindung, es wird gleich erneut versucht.',
  'offline-account': 'Freunde und Chat brauchen ein Microsoft-Konto. Mit einem Offline-Konto geht das nicht.',
  'no-account': 'Melde dich an, dann siehst du hier deine Freunde.',
  connecting: 'Verbinde mit dem Nexora-Server…',
  error: 'Der Nexora-Server ist gerade nicht erreichbar. Nexora versucht es gleich wieder.',
}

function Avatar({ name, status, size = 32 }: { name: string | null; status?: Status; size?: number }) {
  return (
    <span className="relative shrink-0 rounded-full bg-crystal-accent/12 ring-1 ring-inset ring-white/10 flex items-center justify-center text-crystal-text font-semibold"
      style={{ width: size, height: size, fontSize: size * 0.38 }}>
      {(name ?? '?').charAt(0).toUpperCase()}
      {status && <span className={`absolute -right-0.5 -bottom-0.5 h-2.5 w-2.5 rounded-full ring-2 ring-crystal-card ${STATUS_DOT[status]}`} aria-hidden="true" />}
    </span>
  )
}

function time(at: number) {
  const d = new Date(at)
  const today = new Date().toDateString() === d.toDateString()
  return today ? d.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })
    : d.toLocaleDateString('de-DE', { day: 'numeric', month: 'short' }) + ', ' + d.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })
}

export function Friends() {
  const [snap, setSnap] = useState<Snapshot | null>(null)
  const [adding, setAdding] = useState(false)
  const [newName, setNewName] = useState('')
  const [open, setOpen] = useState<string | null>(null)
  const [history, setHistory] = useState<Record<string, ChatMessage[]>>({})
  const [draft, setDraft] = useState('')
  const [invites, setInvites] = useState<Invite[]>([])
  const navigate = useNavigate()
  const listEnd = useRef<HTMLDivElement>(null)
  const openRef = useRef<string | null>(null)
  openRef.current = open

  useEffect(() => {
    api?.socialSnapshot?.().then((s: Snapshot) => s && setSnap(s))
    const off = api?.on?.('social:event', (m: any) => {
      switch (m.t) {
        case 'state':
          setSnap(m)
          break
        case 'friends':
          setSnap(s => s && { ...s, friends: m.friends, incoming: m.incoming, outgoing: m.outgoing, unread: m.unread })
          break
        case 'presence':
          setSnap(s => s && { ...s, friends: s.friends.map(f => (f.uuid === m.uuid ? { ...f, status: m.status } : f)) })
          break
        case 'unread':
          setSnap(s => s && { ...s, unread: m.unread })
          break
        case 'chat-history':
          setHistory(h => ({ ...h, [m.with]: m.messages }))
          break
        case 'chat': {
          const msg: ChatMessage = m.message
          setSnap(s => {
            const other = s?.me && msg.from === s.me.uuid ? msg.to : msg.from
            setHistory(h => (h[other] ? { ...h, [other]: [...h[other], msg] } : h))
            // The open conversation is read as it arrives.
            if (other === openRef.current && msg.from === other) api?.socialSend({ t: 'chat-read', with: other })
            return s
          })
          break
        }
        case 'invite':
          setInvites(list => [{ from: m.from, server: m.server, at: m.at }, ...list.filter(x => x.from.uuid !== m.from.uuid)].slice(0, 5))
          break
        case 'invite-sent':
          notify({ type: 'success', message: `Einladung auf ${m.server} verschickt.` })
          break
        case 'friend-request':
          notify({ type: 'info', title: 'Freundschaftsanfrage', message: `${m.from?.name ?? 'Jemand'} möchte dein Freund sein.` })
          break
        case 'friend-accepted':
          notify({ type: 'success', message: `${m.by?.name ?? 'Jemand'} ist jetzt dein Freund.` })
          break
        case 'error':
          notify({ type: 'error', message: m.message })
          break
      }
    })
    return () => { if (typeof off === 'function') off() }
  }, [])

  // Opening a conversation loads its history and marks it read.
  useEffect(() => {
    if (!open) return
    api?.socialSend({ t: 'chat-history', with: open })
    api?.socialSend({ t: 'chat-read', with: open })
    setDraft('')
  }, [open])

  useEffect(() => { listEnd.current?.scrollIntoView({ block: 'end' }) }, [history, open])

  const friends = useMemo(() => {
    const order: Record<Status, number> = { game: 0, launcher: 1, offline: 2 }
    return [...(snap?.friends ?? [])].sort((a, b) => order[a.status] - order[b.status] || (a.name ?? '').localeCompare(b.name ?? ''))
  }, [snap])
  const online = snap?.state === 'online'
  const openFriend = friends.find(f => f.uuid === open) ?? null

  function addFriend() {
    const name = newName.trim()
    if (!name) return
    api?.socialSend({ t: 'friend-add', name })
    notify({ type: 'info', message: `Anfrage an ${name} gesendet.` })
    setNewName('')
    setAdding(false)
  }

  function sendChat() {
    const text = draft.trim()
    if (!text || !open) return
    api?.socialSend({ t: 'chat-send', to: open, text })
    setDraft('')
  }

  const requests = (snap?.incoming.length ?? 0) + (snap?.outgoing.length ?? 0)

  return (
    <Page wide>
      <PageHeader
        title="Freunde"
        description="Sieh, wer online ist, nimm Anfragen an und schreib deinen Freunden, im Launcher und im Spiel."
        actions={online && (
          <button onClick={() => setAdding(a => !a)} className="crystal-btn-primary text-[13px]">
            <UserPlus size={14} /> Freund hinzufügen
          </button>
        )}
      />

      {!online && snap && (
        <div className="crystal-card flex items-center gap-3 px-4 py-3.5 mb-6">
          <Users size={16} className="text-crystal-muted shrink-0" />
          <p className="flex-1 text-[13px] text-crystal-muted">{STATE_TEXT[snap.state as Exclude<NetState, 'online'>]}</p>
          {snap.state === 'error' && (
            <button onClick={() => api?.socialReconnect()} className="crystal-btn-ghost text-[13px]"><RefreshCw size={13} /> Erneut</button>
          )}
        </div>
      )}

      {online && adding && (
        <div className="crystal-card mb-6 px-4 py-3.5">
          <label htmlFor="friend-name" className="crystal-label">Minecraft-Name</label>
          <div className="flex gap-2 mt-1.5">
            <input id="friend-name" autoFocus maxLength={16} value={newName}
              onChange={e => setNewName(e.target.value)} onKeyDown={e => e.key === 'Enter' && addFriend()}
              className="crystal-input flex-1 text-[13px]" placeholder="Name des Spielers" />
            <button onClick={addFriend} disabled={!newName.trim()} className="crystal-btn-primary text-[13px] disabled:opacity-50">Anfrage senden</button>
            <button onClick={() => setAdding(false)} aria-label="Abbrechen" className="crystal-btn-ghost px-2.5"><X size={14} /></button>
          </div>
          <p className="text-xs text-crystal-muted mt-1.5">Ihr seid Freunde, sobald die andere Seite annimmt.</p>
        </div>
      )}

      {online && (
        <div className="grid grid-cols-[minmax(0,300px)_minmax(0,1fr)] gap-6 items-start">
          <div className="space-y-6">
            {invites.length > 0 && (
              <section>
                <h2 className="nexora-display text-[15px] text-crystal-text mb-3 px-0.5">Einladungen</h2>
                <div className="crystal-card divide-y divide-white/[0.06] overflow-hidden">
                  {invites.map(inv => (
                    <div key={inv.from.uuid + inv.at} className="flex items-center gap-3 px-3.5 py-2.5">
                      <Avatar name={inv.from.name} size={28} />
                      <div className="flex-1 min-w-0">
                        <p className="text-[13px] text-crystal-text truncate">{inv.from.name}</p>
                        <p className="text-[11px] text-crystal-muted truncate">lädt dich auf {inv.server} ein</p>
                      </div>
                      <button onClick={() => navigate(`/launch?join=${encodeURIComponent(inv.server)}&autostart=1`)}
                        className="crystal-btn-primary text-[12px] px-3 py-1.5"><LogIn size={12} /> Beitreten</button>
                      <button onClick={() => setInvites(list => list.filter(x => x !== inv))} aria-label="Einladung ausblenden"
                        className="p-1.5 rounded-full text-crystal-muted hover:text-crystal-text hover:bg-white/[0.06] nexora-ease"><X size={13} /></button>
                    </div>
                  ))}
                </div>
              </section>
            )}
            {requests > 0 && (
              <section>
                <h2 className="nexora-display text-[15px] text-crystal-text mb-3 px-0.5">Anfragen</h2>
                <div className="crystal-card divide-y divide-white/[0.06] overflow-hidden">
                  {snap!.incoming.map(p => (
                    <div key={p.uuid} className="flex items-center gap-3 px-3.5 py-2.5">
                      <Avatar name={p.name} size={28} />
                      <p className="flex-1 min-w-0 text-[13px] text-crystal-text truncate">{p.name ?? 'Unbekannt'}</p>
                      <button onClick={() => api?.socialSend({ t: 'friend-accept', uuid: p.uuid })} aria-label={`${p.name} annehmen`}
                        className="p-1.5 rounded-full bg-crystal-accent text-crystal-bg hover:brightness-110 nexora-ease"><Check size={13} /></button>
                      <button onClick={() => api?.socialSend({ t: 'friend-decline', uuid: p.uuid })} aria-label={`${p.name} ablehnen`}
                        className="p-1.5 rounded-full text-crystal-muted hover:text-crystal-text hover:bg-white/[0.06] nexora-ease"><X size={13} /></button>
                    </div>
                  ))}
                  {snap!.outgoing.map(p => (
                    <div key={p.uuid} className="flex items-center gap-3 px-3.5 py-2.5">
                      <Avatar name={p.name} size={28} />
                      <div className="flex-1 min-w-0">
                        <p className="text-[13px] text-crystal-text truncate">{p.name ?? 'Unbekannt'}</p>
                        <p className="text-[11px] text-crystal-muted">Wartet auf Antwort</p>
                      </div>
                      <button onClick={() => api?.socialSend({ t: 'friend-decline', uuid: p.uuid })} aria-label={`Anfrage an ${p.name} zurückziehen`}
                        className="p-1.5 rounded-full text-crystal-muted hover:text-crystal-text hover:bg-white/[0.06] nexora-ease"><X size={13} /></button>
                    </div>
                  ))}
                </div>
              </section>
            )}

            <section>
              <h2 className="nexora-display text-[15px] text-crystal-text mb-3 px-0.5">
                Freunde <span className="text-crystal-muted font-normal">{friends.filter(f => f.status !== 'offline').length} online</span>
              </h2>
              {friends.length === 0 ? (
                <EmptyState icon={<Users size={22} strokeWidth={1.75} />} title="Noch keine Freunde"
                  action={<button onClick={() => setAdding(true)} className="crystal-btn-primary text-[13px]"><UserPlus size={14} /> Freund hinzufügen</button>}>
                  Schick jemandem eine Anfrage mit seinem Minecraft-Namen.
                </EmptyState>
              ) : (
                <div className="crystal-card p-1.5 space-y-0.5">
                  {friends.map(f => {
                    const unread = snap!.unread[f.uuid] ?? 0
                    return (
                      <button key={f.uuid} onClick={() => setOpen(f.uuid)}
                        className={`w-full flex items-center gap-3 px-2.5 py-2 rounded-xl text-left nexora-ease ${open === f.uuid ? 'nexora-nav-active' : 'hover:bg-white/[0.04]'}`}>
                        <Avatar name={f.name} status={f.status} />
                        <span className="flex-1 min-w-0">
                          <span className="block text-[13px] text-crystal-text truncate">{f.name ?? 'Unbekannt'}</span>
                          <span className={`block text-[11px] ${f.status === 'game' ? 'text-crystal-success' : 'text-crystal-muted'}`}>{STATUS_LABEL[f.status]}</span>
                        </span>
                        {unread > 0 && <span className="min-w-[20px] h-5 px-1.5 rounded-full bg-crystal-accent text-crystal-bg text-[11px] font-semibold flex items-center justify-center tabular">{unread}</span>}
                      </button>
                    )
                  })}
                </div>
              )}
            </section>
          </div>

          <section className="crystal-card flex flex-col h-[520px] overflow-hidden">
            {!openFriend ? (
              <div className="flex-1 flex flex-col items-center justify-center text-center px-8">
                <MessageCircle size={26} strokeWidth={1.5} className="text-crystal-muted/70 mb-3" />
                <p className="text-[13px] text-crystal-text font-medium">Wähl einen Freund</p>
                <p className="text-xs text-crystal-muted mt-1 max-w-[40ch]">Nachrichten kommen auch an, wenn er gerade offline ist, und erscheinen im Spiel als Hinweis.</p>
              </div>
            ) : (
              <>
                <header className="flex items-center gap-3 px-4 py-3 border-b border-white/[0.06]">
                  <Avatar name={openFriend.name} status={openFriend.status} />
                  <div className="flex-1 min-w-0">
                    <p className="text-[13.5px] font-medium text-crystal-text truncate">{openFriend.name}</p>
                    <p className="text-[11px] text-crystal-muted flex items-center gap-1">
                      {openFriend.status === 'game' && <Gamepad2 size={11} />}{STATUS_LABEL[openFriend.status]}
                    </p>
                  </div>
                  {openFriend.status !== 'offline' && (
                    <button onClick={() => api?.socialSend({ t: 'invite', to: openFriend.uuid })}
                      className="crystal-btn-ghost text-[12px]" title="Auf den Server einladen, auf dem du gerade spielst"><MailPlus size={13} /> Einladen</button>
                  )}
                  <button onClick={() => { api?.socialSend({ t: 'friend-remove', uuid: openFriend.uuid }); setOpen(null) }}
                    className="crystal-btn-ghost text-[12px]" title="Aus der Freundesliste entfernen"><UserMinus size={13} /> Entfernen</button>
                </header>
                <div className="flex-1 overflow-y-auto px-4 py-4 space-y-2">
                  {(history[openFriend.uuid] ?? []).length === 0 && (
                    <p className="text-center text-xs text-crystal-muted py-8">Noch keine Nachrichten. Sag Hallo.</p>
                  )}
                  {(history[openFriend.uuid] ?? []).map(msg => {
                    const mine = msg.from === snap!.me?.uuid
                    return (
                      <div key={msg.id} className={`flex ${mine ? 'justify-end' : 'justify-start'}`}>
                        <div className={`max-w-[75%] rounded-2xl px-3.5 py-2 text-[13px] leading-snug ${mine ? 'bg-crystal-accent text-crystal-bg rounded-br-md' : 'bg-white/[0.06] text-crystal-text rounded-bl-md'}`}>
                          <p className="whitespace-pre-wrap break-words">{msg.text}</p>
                          <p className={`text-[10px] mt-0.5 ${mine ? 'text-crystal-bg/60' : 'text-crystal-muted'}`}>{time(msg.at)}</p>
                        </div>
                      </div>
                    )
                  })}
                  <div ref={listEnd} />
                </div>
                <div className="flex gap-2 p-3 border-t border-white/[0.06]">
                  <input value={draft} maxLength={500} onChange={e => setDraft(e.target.value)}
                    onKeyDown={e => e.key === 'Enter' && !e.shiftKey && sendChat()}
                    className="crystal-input flex-1 text-[13px]" placeholder={`Nachricht an ${openFriend.name}`} aria-label="Nachricht" />
                  <button onClick={sendChat} disabled={!draft.trim()} className="crystal-btn-primary px-3.5 disabled:opacity-50" aria-label="Senden"><Send size={14} /></button>
                </div>
              </>
            )}
          </section>
        </div>
      )}
    </Page>
  )
}
