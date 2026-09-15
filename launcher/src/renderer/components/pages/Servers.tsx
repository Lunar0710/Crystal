import React, { useCallback, useEffect, useState } from 'react'
import { Server as ServerIcon, Plus, RefreshCw, Trash2, Play, Users, Wifi, WifiOff } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { Page, PageHeader, EmptyState } from '../ui/Page'
import { notify } from '../../store/notificationStore'

interface FavoriteServer {
  id: string
  name: string
  address: string
}

interface ServerStatus {
  online: boolean
  motd?: string
  playersOnline?: number
  playersMax?: number
  version?: string
  favicon?: string
  pingMs?: number
  error?: string
}

const api = (window as any).crystal

/** Green under 80 ms, amber under 180, red above, like the bars in the multiplayer menu. */
function pingTone(ms: number) {
  if (ms < 80) return 'text-emerald-400'
  if (ms < 180) return 'text-amber-400'
  return 'text-red-400'
}

export function Servers() {
  const navigate = useNavigate()
  const [servers, setServers] = useState<FavoriteServer[]>([])
  const [status, setStatus] = useState<Record<string, ServerStatus | 'loading'>>({})
  const [name, setName] = useState('')
  const [address, setAddress] = useState('')

  const pingAll = useCallback((list: FavoriteServer[]) => {
    for (const s of list) {
      setStatus(prev => ({ ...prev, [s.id]: 'loading' }))
      api?.pingServer(s.address).then((result: ServerStatus) => setStatus(prev => ({ ...prev, [s.id]: result })))
    }
  }, [])

  const refresh = useCallback(async () => {
    const list: FavoriteServer[] = (await api?.listServers()) || []
    setServers(list)
    pingAll(list)
  }, [pingAll])

  useEffect(() => { refresh() }, [refresh])

  async function add(e: React.FormEvent) {
    e.preventDefault()
    const server: FavoriteServer | null = await api?.addServer(name, address)
    if (!server) {
      notify({ type: 'error', message: 'Die Adresse sieht nicht wie ein Server aus, zum Beispiel "play.beispiel.de" oder "1.2.3.4:25565".' })
      return
    }
    setName('')
    setAddress('')
    const list = [...servers, server]
    setServers(list)
    pingAll([server])
  }

  async function remove(id: string) {
    await api?.removeServer(id)
    setServers(prev => prev.filter(s => s.id !== id))
  }

  return (
    <Page>
      <PageHeader
        title="Server"
        description="Deine Lieblingsserver mit Ping und Spielerzahl. Beitreten startet das Spiel direkt auf dem Server."
        actions={
          <button onClick={refresh} className="crystal-btn-ghost text-[13px] inline-flex items-center gap-1.5">
            <RefreshCw size={14} /> Aktualisieren
          </button>
        }
      />

      <form onSubmit={add} className="crystal-card p-3 mb-5 flex flex-wrap gap-2 items-center">
        <input
          id="server-name"
          value={name}
          onChange={e => setName(e.target.value)}
          placeholder="Name (optional)"
          className="crystal-input text-[13px] flex-1 min-w-[140px]"
          maxLength={40}
        />
        <input
          id="server-address"
          value={address}
          onChange={e => setAddress(e.target.value)}
          placeholder="Adresse, z. B. play.beispiel.de"
          className="crystal-input text-[13px] flex-[2] min-w-[200px]"
          required
        />
        <button type="submit" className="crystal-btn-primary text-[13px] inline-flex items-center gap-1.5">
          <Plus size={14} /> Hinzufügen
        </button>
      </form>

      {servers.length === 0 ? (
        <EmptyState icon={<ServerIcon size={26} />} title="Noch keine Server">
          Füg oben einen Server hinzu, dann siehst du hier Ping und Spieler.
        </EmptyState>
      ) : (
        <div className="space-y-2">
          {servers.map(server => {
            const st = status[server.id]
            const loading = st === 'loading' || st === undefined
            const info = loading ? null : st as ServerStatus
            return (
              <div key={server.id} className="crystal-card p-3 flex items-center gap-3">
                <div className="w-12 h-12 rounded-md bg-crystal-panel shrink-0 overflow-hidden flex items-center justify-center">
                  {info?.favicon
                    ? <img src={info.favicon} alt="" className="w-full h-full [image-rendering:pixelated]" />
                    : <ServerIcon size={20} className="text-crystal-muted" />}
                </div>

                <div className="min-w-0 flex-1">
                  <div className="flex items-baseline gap-2 min-w-0">
                    <p className="text-[13px] font-semibold text-crystal-text truncate">{server.name}</p>
                    <p className="text-[11px] text-crystal-muted truncate">{server.address}</p>
                  </div>
                  <p className="text-[12px] text-crystal-muted truncate mt-0.5">
                    {loading ? 'Wird abgefragt...' : info?.online ? (info.motd?.split('\n')[0] || info.version || 'Online') : info?.error || 'Offline'}
                  </p>
                </div>

                <div className="flex flex-col items-end gap-0.5 shrink-0 text-[12px] tabular-nums">
                  {info?.online ? (
                    <>
                      <span className="inline-flex items-center gap-1 text-crystal-text">
                        <Users size={12} className="text-crystal-muted" />
                        {(info.playersOnline ?? 0).toLocaleString('de-DE')} / {(info.playersMax ?? 0).toLocaleString('de-DE')}
                      </span>
                      <span className={`inline-flex items-center gap-1 ${pingTone(info.pingMs ?? 999)}`}>
                        <Wifi size={12} /> {info.pingMs} ms
                      </span>
                    </>
                  ) : !loading ? (
                    <span className="inline-flex items-center gap-1 text-red-400"><WifiOff size={12} /> Offline</span>
                  ) : null}
                </div>

                <button
                  onClick={() => navigate(`/launch?join=${encodeURIComponent(server.address)}`)}
                  className="crystal-btn-primary text-[13px] inline-flex items-center gap-1.5 shrink-0"
                >
                  <Play size={13} /> Beitreten
                </button>
                <button
                  onClick={() => remove(server.id)}
                  className="text-crystal-muted hover:text-red-400 p-1.5 shrink-0"
                  title="Entfernen"
                  aria-label={`${server.name} entfernen`}
                >
                  <Trash2 size={14} />
                </button>
              </div>
            )
          })}
        </div>
      )}
    </Page>
  )
}
