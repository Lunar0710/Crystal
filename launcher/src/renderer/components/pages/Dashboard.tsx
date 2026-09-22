import React, { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Play, Plus, ChevronRight, Boxes } from 'lucide-react'
import { Page, PageHeader, EmptyState } from '../ui/Page'
import { useReleases, relativeDate } from '../../hooks/useReleases'

interface Instance {
  id: string
  name: string
  version: string
  loader: string
  useCrystalClient: boolean
  createdAt: number
}

const api = (window as any).crystal

export function Dashboard() {
  const navigate = useNavigate()
  const [instances, setInstances] = useState<Instance[] | null>(null)
  const [modCounts, setModCounts] = useState<Record<string, number>>({})
  const [username, setUsername] = useState<string | null>(null)
  const [clientVersion, setClientVersion] = useState<string | null>(null)
  const news = useReleases()

  useEffect(() => {
    api?.getVersionInfo().then((v: { client: string | null }) => setClientVersion(v?.client ?? null))
    api?.getProfile().then((p: { username: string } | null) => setUsername(p?.username ?? null))
    api?.getInstances().then(async (list: Instance[]) => {
      const all = list || []
      setInstances(all)
      const counts = await Promise.all(all.map(i => api?.listContent(i.id, 'mod')))
      setModCounts(Object.fromEntries(all.map((i, idx) => [i.id, (counts[idx] || []).length])))
    })
  }, [])

  const playInstance = (id?: string) => navigate(id ? `/launch?instance=${id}` : '/launch')

  return (
    <Page wide>
      <PageHeader
        title={username ? `Hallo, ${username}` : 'Willkommen bei Nexora'}
        description={`Minecraft 1.8.9 bis 26.2${clientVersion ? `, Nexora Client ${clientVersion}` : ''}.`}
        actions={
          <button onClick={() => playInstance()} className="crystal-btn-primary px-5">
            <Play size={14} fill="currentColor" /> Spielen
          </button>
        }
      />

      <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)] gap-8">
        <section>
          <div className="flex items-baseline justify-between mb-2 px-0.5">
            <h2 className="text-[13px] font-semibold text-crystal-text">Deine Instanzen</h2>
            <button onClick={() => navigate('/instances')} className="text-xs text-crystal-muted hover:text-crystal-text">
              Verwalten
            </button>
          </div>

          {instances === null && (
            <div className="crystal-card divide-y divide-crystal-border">
              {[0, 1, 2].map(i => (
                <div key={i} className="px-4 py-3.5 animate-pulse">
                  <div className="h-3.5 w-40 rounded bg-crystal-border mb-1.5" />
                  <div className="h-3 w-24 rounded bg-crystal-border/60" />
                </div>
              ))}
            </div>
          )}

          {instances?.length === 0 && (
            <EmptyState
              icon={<Boxes size={22} strokeWidth={1.75} />}
              title="Noch keine Instanz"
              action={
                <button onClick={() => navigate('/instances')} className="crystal-btn-primary text-[13px]">
                  <Plus size={14} /> Instanz anlegen
                </button>
              }
            >
              Eine Instanz ist ein eigener Minecraft-Ordner mit eigenen Mods, Welten und Einstellungen.
            </EmptyState>
          )}

          {instances && instances.length > 0 && (
            <div className="crystal-card divide-y divide-crystal-border overflow-hidden">
              {instances.map(inst => (
                <button
                  key={inst.id}
                  onClick={() => playInstance(inst.id)}
                  className="group w-full flex items-center gap-3.5 px-4 py-3 text-left hover:bg-crystal-panel/60 transition-colors"
                >
                  <span className="w-8 h-8 rounded-md bg-crystal-panel border border-crystal-border flex items-center justify-center text-xs font-semibold text-crystal-text shrink-0">
                    {inst.name.charAt(0).toUpperCase()}
                  </span>
                  <span className="flex-1 min-w-0">
                    <span className="block text-[13px] font-medium text-crystal-text truncate">{inst.name}</span>
                    <span className="block text-xs text-crystal-muted tabular">
                      {inst.version}, {inst.useCrystalClient ? 'Nexora Client' : 'Vanilla mit Mods'}
                      {modCounts[inst.id] !== undefined && `, ${modCounts[inst.id]} ${modCounts[inst.id] === 1 ? 'Mod' : 'Mods'}`}
                    </span>
                  </span>
                  <span className="flex items-center gap-1 text-xs text-crystal-muted opacity-0 group-hover:opacity-100 transition-opacity">
                    <Play size={12} fill="currentColor" /> Starten
                  </span>
                </button>
              ))}
            </div>
          )}
        </section>

        <section>
          <div className="flex items-baseline justify-between mb-2 px-0.5">
            <h2 className="text-[13px] font-semibold text-crystal-text">Neuigkeiten</h2>
            <button onClick={() => navigate('/news')} className="text-xs text-crystal-muted hover:text-crystal-text">
              Alle anzeigen
            </button>
          </div>

          {news.loading && <div className="h-24 rounded-[10px] bg-crystal-card animate-pulse" />}

          {!news.loading && news.items.length === 0 && (
            <p className="text-xs text-crystal-muted px-0.5">
              {news.ok ? 'Noch keine Versionen veröffentlicht.' : 'Konnte GitHub gerade nicht erreichen.'}
            </p>
          )}

          {!news.loading && news.items.length > 0 && (
            <ul className="space-y-1">
              {news.items.slice(0, 4).map(r => (
                <li key={r.id}>
                  <button
                    onClick={() => navigate('/news')}
                    className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-left hover:bg-crystal-card transition-colors"
                  >
                    <span className="font-mono text-xs text-crystal-muted w-14 shrink-0">{r.tag}</span>
                    <span className="flex-1 min-w-0 text-[13px] text-crystal-text truncate">{r.title}</span>
                    <span className="text-xs text-crystal-muted shrink-0">{relativeDate(r.publishedAt)}</span>
                    <ChevronRight size={13} className="text-crystal-muted shrink-0" />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </Page>
  )
}
