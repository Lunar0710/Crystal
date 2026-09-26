import React, { useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { Play, Plus, ChevronDown, Users, Server, SquareTerminal, Shirt, Check } from 'lucide-react'
import { useReleases, relativeDate } from '../../hooks/useReleases'
import { PanoramaBackdrop } from '../ui/PanoramaBackdrop'
import { SkinPreview3D } from '../ui/SkinPreview3D'
import { useRunningGames } from '../ui/RunningGamesPanel'
import { notify } from '../../store/notificationStore'

interface Instance {
  id: string
  name: string
  version: string
  loader: string
  useCrystalClient: boolean
  gameDir: string
  createdAt: number
}
interface FavoriteServer { id: string; name: string; address: string }
interface ServerStatus { online: boolean; playersOnline?: number; favicon?: string }

const api = (window as any).crystal

/**
 * The play page, laid out like Feather's: the chosen version on its own
 * panorama with a switch button, a full-width launch bar under it, news and
 * your servers side by side, and your 3D skin in the panel on the right.
 */
export function Dashboard() {
  const navigate = useNavigate()
  const [instances, setInstances] = useState<Instance[] | null>(null)
  const [instanceId, setInstanceId] = useState('')
  const [pickerOpen, setPickerOpen] = useState(false)
  const [username, setUsername] = useState<string | null>(null)
  const [skin, setSkin] = useState<{ url: string | null; slim: boolean }>({ url: null, slim: false })
  const [servers, setServers] = useState<FavoriteServer[]>([])
  const [status, setStatus] = useState<Record<string, ServerStatus>>({})
  const pickerRef = useRef<HTMLDivElement | null>(null)
  const [progress, setProgress] = useState<{ step: string; percent: number } | null>(null)

  useEffect(() => {
    const unsubs = [
      api?.on('launch:progress', (data: { step: string; percent: number }) => setProgress(data)),
      api?.on('launch:started', () => setProgress(null)),
      api?.on('launch:error', (msg: string) => {
        setProgress(null)
        // The start page has the crash help (which mod, what to do).
        navigate(`/launch?instance=${encodeURIComponent(instanceIdRef.current)}`)
        notify({ type: 'error', title: 'Start fehlgeschlagen', message: String(msg).slice(0, 200) })
      }),
    ]
    return () => unsubs.forEach(u => u?.())
  }, [])
  const news = useReleases()
  const running = useRunningGames()

  useEffect(() => {
    Promise.all([api?.getInstances(), api?.getSetting('lastInstance')]).then(([list, last]: [Instance[], string | undefined]) => {
      const all = list || []
      setInstances(all)
      setInstanceId(last && all.some(i => i.id === last) ? last : all[0]?.id ?? '')
    })
    api?.getProfile().then((p: { username: string } | null) => {
      const name = p?.username ?? null
      setUsername(name)
      if (name) api?.fetchSkin(name).then((r: { success: boolean; dataUrl?: string; slim?: boolean } | null) => {
        if (r?.success) setSkin({ url: r.dataUrl ?? null, slim: !!r.slim })
      })
    })
    api?.listServers().then((list: FavoriteServer[]) => {
      const first = (list || []).slice(0, 5)
      setServers(first)
      first.forEach(s => api?.pingServer(s.address).then((st: ServerStatus) => setStatus(prev => ({ ...prev, [s.id]: st }))))
    })
  }, [])

  useEffect(() => {
    if (!pickerOpen) return
    const close = (e: MouseEvent) => { if (!pickerRef.current?.contains(e.target as Node)) setPickerOpen(false) }
    window.addEventListener('mousedown', close)
    return () => window.removeEventListener('mousedown', close)
  }, [pickerOpen])

  const instance = instances?.find(i => i.id === instanceId) ?? null
  const instanceIdRef = useRef('')
  instanceIdRef.current = instanceId
  const isRunning = !!instance && running.some(g => g.instanceId === instance.id)

  const choose = (id: string) => {
    setInstanceId(id)
    setPickerOpen(false)
    api?.setSetting('lastInstance', id)
  }
  // Started right here; the bar shows the steps. Without an account the start page asks for one.
  const launch = async (join?: string) => {
    if (!instance) { navigate('/instances'); return }
    const account = await api?.getProfile()
    if (!account) { navigate(`/launch?instance=${encodeURIComponent(instance.id)}`); return }
    const [saved, mem] = await Promise.all([api?.getSetting('maxRam'), api?.getSystemMemory()])
    setProgress({ step: 'Vorbereiten', percent: 0 })
    api?.setSetting('lastInstance', instance.id)
    await api?.launchGame({
      version: instance.version,
      loader: instance.loader,
      gameDir: instance.gameDir,
      maxRam: saved || mem?.suggestedMb || 4096,
      instanceId: instance.id,
      injectCrystal: instance.useCrystalClient,
      joinServer: join,
    })
  }

  const launchLabel = progress ? `${progress.step.replace(/\.+$/, '')} ${Math.round(progress.percent)} %`
    : !instance ? 'Instanz anlegen' : isRunning ? 'Läuft' : `${instance.useCrystalClient ? 'Nexora' : 'Minecraft'} starten`

  return (
    <div className="h-full px-5 pb-4 pt-1 grid grid-cols-[minmax(0,1fr)_290px] gap-4 min-h-0">
      <div className="flex flex-col gap-3 min-h-0">
        {/* The version, on its own panorama. */}
        <section className="relative h-[240px] shrink-0 overflow-hidden rounded-lg bg-crystal-panel">
          <PanoramaBackdrop version={instance?.version} shade="from-black/55 via-black/35 to-black/55" faceSize={420} />
          <div className="relative h-full flex flex-col items-center justify-center gap-3 text-center">
            <p className="text-[19px] font-semibold text-white drop-shadow">
              {instance ? `Minecraft ${instance.version}` : 'Noch keine Instanz'}
            </p>
            <div className="relative" ref={pickerRef}>
              <button
                onClick={() => instances?.length ? setPickerOpen(o => !o) : navigate('/instances')}
                className="flex items-center gap-2 h-10 px-6 rounded-md bg-black/90 text-[13px] font-semibold text-white hover:bg-black transition-colors"
              >
                {instance ? instance.name : 'Instanz anlegen'}
                {!!instances?.length && <ChevronDown size={14} className="text-crystal-muted" />}
              </button>
              {pickerOpen && instances && (
                <div className="absolute left-1/2 -translate-x-1/2 top-[calc(100%+6px)] z-40 w-64 max-h-64 overflow-y-auto rounded-lg border border-crystal-border bg-[#111113] p-1 text-left shadow-xl">
                  {instances.map(i => (
                    <button key={i.id} onClick={() => choose(i.id)}
                      className="w-full flex items-center gap-2.5 px-3 py-2 rounded-md text-[13px] hover:bg-white/[0.05]">
                      <span className="flex-1 min-w-0">
                        <span className="block truncate text-crystal-text">{i.name}</span>
                        <span className="block text-[11px] text-crystal-muted">{i.version}{i.useCrystalClient ? ', Nexora' : ''}</span>
                      </span>
                      {i.id === instanceId && <Check size={14} className="text-crystal-accent" />}
                    </button>
                  ))}
                  <button onClick={() => navigate('/instances')}
                    className="w-full flex items-center gap-2 px-3 py-2 rounded-md text-[13px] text-crystal-muted hover:bg-white/[0.05] hover:text-crystal-text">
                    <Plus size={14} /> Neue Instanz
                  </button>
                </div>
              )}
            </div>
          </div>
          {/* Quick buttons on the right edge, as in Feather. */}
          <div className="absolute right-3 top-1/2 -translate-y-1/2 flex flex-col gap-2">
            <EdgeButton label="Freunde" onClick={() => navigate('/friends')}><Users size={17} /></EdgeButton>
            <EdgeButton label="Server" onClick={() => navigate('/servers')}><Server size={17} /></EdgeButton>
            <EdgeButton label="Logs" onClick={() => api?.openLogsWindow?.()}><SquareTerminal size={17} /></EdgeButton>
          </div>
        </section>

        <button
          onClick={() => launch()}
          disabled={isRunning || !!progress}
          className="relative overflow-hidden h-12 shrink-0 rounded-lg bg-crystal-accent text-white text-[14px] font-bold uppercase tracking-[0.06em] flex items-center justify-center gap-2.5 hover:brightness-110 disabled:opacity-60 disabled:hover:brightness-100 transition"
        >
          {/* While starting, the bar fills like Feather's. */}
          {progress && <span className="absolute inset-y-0 left-0 bg-white/15 transition-[width] duration-300" style={{ width: `${Math.max(3, progress.percent)}%` }} />}
          {instance ? <Play size={16} fill="currentColor" /> : <Plus size={16} />}
          {launchLabel}
        </button>

        <div className="grid grid-cols-2 gap-3 flex-1 min-h-0">
          <Card title="Neuigkeiten von Nexora" action={<button onClick={() => navigate('/news')} className="text-[11px] font-bold uppercase tracking-wide text-crystal-muted hover:text-crystal-text">Alle</button>}>
            {news.loading && <div className="h-20 rounded-md bg-crystal-card animate-pulse" />}
            {!news.loading && news.items.length === 0 && (
              <p className="text-xs text-crystal-muted">{news.ok ? 'Noch keine Versionen veröffentlicht.' : 'Konnte GitHub gerade nicht erreichen.'}</p>
            )}
            {news.items.slice(0, 1).map(r => (
              <button key={r.id} onClick={() => navigate('/news')} className="relative w-full flex-1 min-h-[96px] overflow-hidden rounded-md text-left">
                <PanoramaBackdrop version={instance?.version} shade="from-black/85 via-black/60 to-black/30" faceSize={240} />
                <span className="absolute right-2 top-2 rounded bg-emerald-500 px-1.5 py-0.5 text-[10px] font-bold uppercase text-black">Neu</span>
                <span className="relative block p-3">
                  <span className="block text-[11px] text-crystal-muted">{r.tag}, {relativeDate(r.publishedAt)}</span>
                  <span className="mt-1 block text-[15px] font-bold text-white leading-snug">{r.title}</span>
                </span>
              </button>
            ))}
          </Card>

          <Card title="Deine Server" action={<button onClick={() => navigate('/servers')} className="text-[11px] font-bold uppercase tracking-wide text-crystal-muted hover:text-crystal-text">Alle</button>}>
            <ul className="space-y-1 overflow-y-auto min-h-0">
              {servers.map(s => {
                const st = status[s.id]
                return (
                  <li key={s.id} className="group flex items-center gap-2.5 h-9 px-2 rounded-md hover:bg-white/[0.04]">
                    {st?.favicon
                      ? <img src={st.favicon} alt="" className="w-5 h-5 rounded-sm" style={{ imageRendering: 'pixelated' }} />
                      : <span className="w-5 h-5 rounded-sm bg-crystal-card" />}
                    <span className="flex-1 min-w-0 truncate text-[13px] text-crystal-text">{s.name}</span>
                    <span className="group-hover:hidden flex items-center gap-1.5 text-[11px] font-semibold text-crystal-muted tabular">
                      <span className={`w-2 h-2 rounded-full ${st?.online ? 'bg-emerald-500' : st ? 'bg-crystal-danger' : 'bg-crystal-border'}`} />
                      {st?.online ? st.playersOnline ?? 0 : st ? 'offline' : '…'}
                    </span>
                    <button onClick={() => launch(s.address)} className="hidden group-hover:block h-7 px-3 rounded bg-crystal-accent text-[11px] font-bold text-white">
                      Verbinden
                    </button>
                  </li>
                )
              })}
              {servers.length === 0 && <li className="text-xs text-crystal-muted">Noch keine Server gespeichert.</li>}
            </ul>
          </Card>
        </div>
      </div>

      {/* Where Feather shows an ad: your own look. */}
      <aside className="relative rounded-lg bg-crystal-panel overflow-hidden flex flex-col">
        <div className="flex-1 flex items-center justify-center min-h-0">
          <SkinPreview3D skinDataUrl={skin.url} slim={skin.slim} width={260} height={360} />
        </div>
        <div className="p-3 border-t border-white/[0.05] flex items-center gap-2">
          <span className="flex-1 min-w-0 truncate text-[13px] font-semibold text-crystal-text">{username ?? 'Nicht angemeldet'}</span>
          <button onClick={() => navigate('/cosmetics')} className="flex items-center gap-1.5 h-8 px-3 rounded-md bg-crystal-accent text-[12px] font-bold text-white hover:brightness-110">
            <Shirt size={13} /> Cosmetics
          </button>
        </div>
      </aside>
    </div>
  )
}

function Card({ title, action, children }: { title: string; action?: React.ReactNode; children: React.ReactNode }) {
  return (
    <section className="rounded-lg bg-crystal-panel p-3.5 flex flex-col gap-2.5 min-h-0">
      <div className="flex items-center justify-between">
        <h2 className="text-[12px] font-bold uppercase tracking-[0.04em] text-crystal-text">{title}</h2>
        {action}
      </div>
      {children}
    </section>
  )
}

function EdgeButton({ label, onClick, children }: { label: string; onClick: () => void; children: React.ReactNode }) {
  return (
    <button onClick={onClick} aria-label={label} title={label}
      className="w-9 h-9 flex items-center justify-center rounded-md bg-black/45 text-white/85 hover:bg-black/75 hover:text-white transition-colors">
      {children}
    </button>
  )
}
