import React, { useState, useEffect, useRef } from 'react'
import { Play, ChevronDown, Plus, ExternalLink, X, FlaskConical, CheckCircle2, RotateCcw, AlertTriangle, Boxes } from 'lucide-react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { notify } from '../../store/notificationStore'
import { LoginPanel } from '../ui/LoginPanel'
import { CrashDialog, type DetectedProblem } from '../ui/CrashDialog'
import { Page, PageHeader, EmptyState } from '../ui/Page'

interface ExternalClient {
  id: string
  name: string
  executablePath: string
}

interface Instance {
  id: string
  name: string
  version: string
  loader: string
  gameDir: string
  useCrystalClient: boolean
}

interface Profile {
  username: string
  uuid: string
  type: 'microsoft' | 'offline'
}

const api = (window as any).crystal
const DEFAULT_RAM = 4096

export function Launch() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  // Set by the Server page's "Beitreten": the game starts straight onto that server.
  const joinServer = searchParams.get('join')
  const [instances, setInstances] = useState<Instance[]>([])
  const [instanceId, setInstanceId] = useState<string>('')
  const [externalClients, setExternalClients] = useState<ExternalClient[]>([])
  const [externalId, setExternalId] = useState<string | null>(null)
  const [maxRam, setMaxRam] = useState(DEFAULT_RAM)
  const [profile, setProfile] = useState<Profile | null>(null)
  const [launching, setLaunching] = useState(false)
  const [progress, setProgress] = useState<{ step: string; percent: number } | null>(null)
  const [trying, setTrying] = useState(false)
  const [tryStatus, setTryStatus] = useState<{ ok: boolean; message: string } | null>(null)
  const [problems, setProblems] = useState<DetectedProblem[] | null>(null)
  const [lastError, setLastError] = useState<string | null>(null)
  const [crashOpen, setCrashOpen] = useState(false)
  const launchedInstanceRef = useRef<string | null>(null)

  function refreshInstances() {
    api?.getInstances().then((list: Instance[]) => {
      const all = list || []
      setInstances(all)
      const requested = searchParams.get('instance')
      setInstanceId(current =>
        all.some(i => i.id === current) ? current
          : requested && all.some(i => i.id === requested) ? requested
          : all[0]?.id ?? '')
    })
  }

  function refreshExternalClients() {
    api?.listExternalClients().then((list: ExternalClient[]) => setExternalClients(list || []))
  }

  useEffect(() => {
    refreshInstances()
    refreshExternalClients()

    // RAM lives in Settings now — the launch screen just reads it.
    Promise.all([api?.getSetting('maxRam'), api?.getSystemMemory()]).then(([saved, mem]: [number | undefined, { suggestedMb: number } | undefined]) => {
      setMaxRam(saved || mem?.suggestedMb || DEFAULT_RAM)
    })

    api?.getProfile().then((p: Profile | null) => {
      if (p) setProfile(p)
      else api?.autoLogin().then((auto: Profile | null) => auto && setProfile(auto))
    })

    // Subscribed once for the page's lifetime rather than per click: a second
    // Play press no longer stacks duplicate listeners, and a crash that happens
    // after the game was already running still reaches the autofix panel.
    const unsubs = [
      api?.on('launch:progress', (data: { step: string; percent: number }) => setProgress(data)),
      // The game still starts; this only says why it got less RAM than set.
      api?.on('launch:notice', (message: string) => notify({ type: 'warning', title: 'Wenig freier Speicher', message })),
      api?.on('launch:started', () => {
        notify({ type: 'success', title: 'Crystal', message: 'Minecraft wurde gestartet' })
        setLaunching(false)
        setProgress(null)
      }),
      api?.on('launch:error', async (msg: string) => {
        setLaunching(false)
        setProgress(null)
        setLastError(msg)
        const target = launchedInstanceRef.current
        const found: DetectedProblem[] = target ? (await api?.analyzeFailure(target, msg)) || [] : []
        setProblems(found)
        setCrashOpen(true)
      }),
    ]
    return () => unsubs.forEach(u => u?.())
  }, [])

  const instance = instances.find(i => i.id === instanceId) ?? null

  async function addExternalClient() {
    const added = await api?.addExternalClient()
    if (added) {
      refreshExternalClients()
      notify({ type: 'success', message: `${added.name} hinzugefügt` })
    }
  }

  async function removeExternalClient(id: string, e: React.MouseEvent) {
    e.stopPropagation()
    await api?.removeExternalClient(id)
    if (externalId === id) setExternalId(null)
    refreshExternalClients()
  }

  async function launchExternal(id: string) {
    const ok = await api?.launchExternalClient(id)
    if (ok) notify({ type: 'success', message: 'Client gestartet' })
    else notify({ type: 'error', message: 'Client konnte nicht gestartet werden. Liegt die Datei noch am selben Ort?' })
  }

  // One attempt only: the backend reverts the instance itself if it fails,
  // so there is deliberately no retry loop here.
  async function tryWithCrystal() {
    if (!instance) return
    setTrying(true)
    setTryStatus(null)
    setProgress({ step: 'Teste Crystal...', percent: 0 })

    const result = await api?.tryWithCrystal(instance.id)

    setTrying(false)
    setProgress(null)
    refreshInstances()

    if (result) {
      setTryStatus({ ok: result.success, message: result.message })
      notify({
        type: result.success ? 'success' : 'warning',
        title: 'Try with Crystal',
        message: result.message,
      })
    }
  }

  async function launch() {
    if (!profile) {
      notify({ type: 'error', title: 'Nicht angemeldet', message: 'Bitte zuerst anmelden.' })
      return
    }
    if (!instance) {
      notify({ type: 'error', title: 'Keine Instanz', message: 'Lege zuerst eine Instanz an.' })
      return
    }

    setLaunching(true)
    setProgress({ step: 'Vorbereiten...', percent: 0 })
    setProblems(null)
    setLastError(null)
    setCrashOpen(false)
    launchedInstanceRef.current = instance.id

    await api?.launchGame({
      version: instance.version,
      loader: instance.loader,
      gameDir: instance.gameDir,
      maxRam,
      instanceId: instance.id,
      injectCrystal: instance.useCrystalClient,
      joinServer: joinServer || undefined,
    })
  }

  const busy = launching || trying

  return (
    <Page>
      <PageHeader title="Starten" description="Wähle Konto und Instanz, dann kann es losgehen." />

      {joinServer && (
        <div className="crystal-card mb-5 px-4 py-3 flex items-center gap-3">
          <Play size={15} className="text-crystal-accent shrink-0" />
          <p className="text-[13px] text-crystal-text flex-1 min-w-0 truncate">
            Nach dem Start direkt auf <span className="font-semibold">{joinServer}</span>
          </p>
          <button
            onClick={() => { const next = new URLSearchParams(searchParams); next.delete('join'); setSearchParams(next) }}
            className="text-crystal-muted hover:text-crystal-text"
            aria-label="Server-Beitritt abbrechen"
            title="Nicht direkt beitreten"
          >
            <X size={15} />
          </button>
        </div>
      )}

      <div className="space-y-6">
        <section>
          <h2 className="text-[13px] font-semibold text-crystal-text mb-2 px-0.5">Konto</h2>
          <LoginPanel profile={profile} onProfileChange={setProfile} />
        </section>

        <section>
          <div className="flex items-baseline justify-between mb-2 px-0.5">
            <h2 className="text-[13px] font-semibold text-crystal-text">Instanz</h2>
            <button onClick={() => navigate('/instances')} className="text-xs text-crystal-muted hover:text-crystal-text">
              Verwalten
            </button>
          </div>

          {instances.length === 0 ? (
            <EmptyState
              icon={<Boxes size={22} strokeWidth={1.75} />}
              title="Noch keine Instanz"
              action={<button onClick={() => navigate('/instances')} className="crystal-btn-primary text-[13px]"><Plus size={14} /> Instanz anlegen</button>}
            >
              Lege zuerst eine Instanz an. Mods, Welten und Einstellungen liegen dann getrennt voneinander.
            </EmptyState>
          ) : (
            <div className="crystal-card p-4 space-y-4">
              <div className="relative">
                <select
                  value={instanceId}
                  onChange={e => { setInstanceId(e.target.value); setProblems(null); setTryStatus(null) }}
                  disabled={busy}
                  className="crystal-input w-full appearance-none pr-9 cursor-pointer text-[13px]"
                >
                  {instances.map(i => (
                    <option key={i.id} value={i.id}>{i.name}</option>
                  ))}
                </select>
                <ChevronDown size={14} className="absolute right-3 top-1/2 -translate-y-1/2 text-crystal-muted pointer-events-none" />
              </div>

              {instance && (
                <dl className="grid grid-cols-3 gap-3 text-xs">
                  <div>
                    <dt className="text-crystal-muted">Version</dt>
                    <dd className="text-crystal-text mt-0.5 tabular">{instance.version}</dd>
                  </div>
                  <div>
                    <dt className="text-crystal-muted">Modus</dt>
                    <dd className="text-crystal-text mt-0.5">{instance.useCrystalClient ? 'Crystal Client' : 'Vanilla mit Mods'}</dd>
                  </div>
                  <div>
                    <dt className="text-crystal-muted">Arbeitsspeicher</dt>
                    <dd className="text-crystal-text mt-0.5 tabular">{(maxRam / 1024).toLocaleString('de-DE', { maximumFractionDigits: 1 })} GB</dd>
                  </div>
                </dl>
              )}

              {progress && (
                <div className="space-y-1.5" aria-live="polite">
                  <div className="flex justify-between text-xs">
                    <span className="text-crystal-muted truncate">{progress.step}</span>
                    <span className="text-crystal-text tabular">{progress.percent}%</span>
                  </div>
                  <div className="h-1 bg-crystal-border rounded-full overflow-hidden">
                    <div className="h-full bg-crystal-accent rounded-full transition-[width] duration-300" style={{ width: `${progress.percent}%` }} />
                  </div>
                </div>
              )}

              <div className="flex gap-2">
                <button onClick={launch} disabled={busy || !instance} className="crystal-btn-primary flex-1 py-2.5 text-[14px] disabled:opacity-60">
                  <Play size={15} fill="currentColor" />
                  {launching ? 'Startet…' : 'Spielen'}
                </button>
                <button
                  onClick={tryWithCrystal}
                  disabled={busy || !instance}
                  title="Startet die Instanz einmal mit Crystal. Klappt es nicht, wird automatisch alles zurückgesetzt."
                  className="crystal-btn-ghost border border-crystal-border text-crystal-text disabled:opacity-60"
                >
                  <FlaskConical size={14} strokeWidth={1.75} />
                  {trying ? 'Teste…' : 'Mit Crystal testen'}
                </button>
              </div>

              {tryStatus && (
                <div className={`flex items-start gap-2.5 p-3 rounded-lg text-xs border ${
                  tryStatus.ok ? 'border-crystal-success/30 bg-crystal-success/[0.07]' : 'border-crystal-warning/30 bg-crystal-warning/[0.07]'
                }`}>
                  {tryStatus.ok
                    ? <CheckCircle2 size={14} className="text-crystal-success shrink-0 mt-px" />
                    : <RotateCcw size={14} className="text-crystal-warning shrink-0 mt-px" />}
                  <span className="text-crystal-text">{tryStatus.message}</span>
                </div>
              )}
            </div>
          )}
        </section>

        {problems !== null && lastError && launchedInstanceRef.current && (
          <section aria-live="polite">
            <button onClick={() => setCrashOpen(true)} className="w-full crystal-card flex items-center gap-3 px-4 py-3 text-left hover:border-crystal-danger/60">
              <AlertTriangle size={15} strokeWidth={1.75} className="text-crystal-danger shrink-0" />
              <span className="flex-1 min-w-0">
                <span className="block text-[13px] text-crystal-text">Start fehlgeschlagen</span>
                <span className="block text-xs text-crystal-muted">
                  {problems.length === 0 ? 'Fehlermeldung ansehen' : `${problems.length} Problem(e) gefunden, ansehen und beheben`}
                </span>
              </span>
            </button>
          </section>
        )}
        {crashOpen && launchedInstanceRef.current && (
          <CrashDialog
            instanceId={launchedInstanceRef.current}
            problems={problems ?? []}
            errorText={lastError}
            account={profile?.username ?? null}
            onClose={() => setCrashOpen(false)}
            onRelaunch={() => { setCrashOpen(false); launch() }}
            onRamChanged={setMaxRam}
          />
        )}

        <section>
          <h2 className="text-[13px] font-semibold text-crystal-text mb-0.5 px-0.5">Andere Clients</h2>
          <p className="text-xs text-crystal-muted mb-2 px-0.5">
            Startet eine .exe oder .jar direkt. Crystal verändert deren Dateien nicht, die Einstellungen oben gelten dafür nicht.
          </p>
          <div className="flex flex-wrap gap-2">
            {externalClients.map(c => (
              <div key={c.id} className="group flex items-center rounded-lg border border-crystal-border bg-crystal-card overflow-hidden">
                <button
                  onClick={() => launchExternal(c.id)}
                  title={c.executablePath}
                  className="flex items-center gap-1.5 pl-3 pr-2 py-1.5 text-[13px] text-crystal-text hover:bg-crystal-panel transition-colors"
                >
                  <ExternalLink size={12} className="text-crystal-muted" /> {c.name}
                </button>
                <button
                  onClick={e => removeExternalClient(c.id, e)}
                  aria-label={`${c.name} entfernen`}
                  className="px-2 py-1.5 text-crystal-muted hover:text-crystal-danger hover:bg-crystal-panel transition-colors"
                >
                  <X size={12} />
                </button>
              </div>
            ))}
            <button
              onClick={addExternalClient}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[13px] text-crystal-muted border border-dashed border-crystal-border hover:text-crystal-text hover:border-crystal-muted transition-colors"
            >
              <Plus size={13} /> Client hinzufügen
            </button>
          </div>
        </section>
      </div>
    </Page>
  )
}
