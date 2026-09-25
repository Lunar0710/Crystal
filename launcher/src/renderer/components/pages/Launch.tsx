import React, { useState, useEffect, useRef } from 'react'
import { Play, ChevronDown, Plus, ExternalLink, X, FlaskConical, CheckCircle2, RotateCcw, AlertTriangle, Boxes, MonitorSmartphone } from 'lucide-react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { notify } from '../../store/notificationStore'
import { LoginPanel } from '../ui/LoginPanel'
import { CrashDialog, type DetectedProblem } from '../ui/CrashDialog'
import { Page, PageHeader, EmptyState } from '../ui/Page'
import { RunningGamesPanel, useRunningGames } from '../ui/RunningGamesPanel'
import { GroupLaunch } from '../ui/GroupLaunch'
import { PerfDoctorPanel } from '../ui/PerfDoctorPanel'

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
  accountUuid?: string
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
  const autostartDone = useRef(false)
  // Whether login and the account list have been looked at, so a shortcut can tell "no account" from "not loaded yet".
  const [accountsChecked, setAccountsChecked] = useState(false)
  const runningGames = useRunningGames()
  const [accounts, setAccounts] = useState<Profile[]>([])

  function refreshInstances() {
    Promise.all([api?.getInstances(), api?.getSetting('lastInstance')]).then(([list, last]: [Instance[], string | undefined]) => {
      const all = list || []
      setInstances(all)
      const requested = searchParams.get('instance')
      // A link asks for one; otherwise the one played last, so a start is one click.
      setInstanceId(current =>
        all.some(i => i.id === current) ? current
          : requested && all.some(i => i.id === requested) ? requested
          : last && all.some(i => i.id === last) ? last
          : all[0]?.id ?? '')
    })
  }

  // Playtime per instance (by name, as the statistics keep it), shown in the picker.
  const [playtime, setPlaytime] = useState<Record<string, number>>({})
  useEffect(() => {
    api?.getStatsSummary?.().then((s: { byInstance?: { name: string; ms: number }[] } | null) => {
      setPlaytime(Object.fromEntries((s?.byInstance ?? []).map(r => [r.name, r.ms])))
    })
  }, [])
  const hours = (ms: number) => ms >= 3_600_000 ? `${Math.round(ms / 3_600_000)} Std.` : `${Math.max(1, Math.round(ms / 60_000))} Min.`

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

    Promise.all([
      api?.listAccounts().then((list: Profile[]) => setAccounts(list || [])),
      api?.getProfile().then(async (p: Profile | null) => {
        if (p) { setProfile(p); return }
        const auto: Profile | null = await api?.autoLogin()
        if (auto) setProfile(auto)
      }),
    ]).finally(() => setAccountsChecked(true))

    // Subscribed once for the page's lifetime rather than per click: a second
    // Play press no longer stacks duplicate listeners, and a crash that happens
    // after the game was already running still reaches the autofix panel.
    const unsubs = [
      api?.on('launch:progress', (data: { step: string; percent: number }) => setProgress(data)),
      // The game still starts; this only says why it got less RAM than set.
      api?.on('launch:notice', (message: string) => notify({ type: 'warning', title: 'Wenig freier Speicher', message })),
      api?.on('launch:started', () => {
        notify({ type: 'success', title: 'Nexora', message: 'Minecraft wurde gestartet. Du kannst jetzt eine weitere Instanz mit einem anderen Konto starten.' })
        setLaunching(false)
        setProgress(null)
      }),
      api?.on('launch:error', async (msg: string, fromInstance?: string) => {
        setLaunching(false)
        setProgress(null)
        setLastError(msg)
        // With several games running, a crash can come from one started earlier.
        if (fromInstance) launchedInstanceRef.current = fromInstance
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
    setProgress({ step: 'Teste Nexora...', percent: 0 })

    const result = await api?.tryWithCrystal(instance.id)

    setTrying(false)
    setProgress(null)
    refreshInstances()

    if (result) {
      setTryStatus({ ok: result.success, message: result.message })
      notify({
        type: result.success ? 'success' : 'warning',
        title: 'Try with Nexora',
        message: result.message,
      })
    }
  }

  async function launch() {
    if (!profile && !ownAccount) {
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
    api?.setSetting('lastInstance', instance.id)

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

  // A shortcut or link can ask for another instance while the page is open.
  useEffect(() => {
    const requested = searchParams.get('instance')
    if (requested && instances.some(i => i.id === requested)) setInstanceId(requested)
  }, [searchParams, instances])

  // Opened from a desktop shortcut: start the instance once everything is loaded.
  useEffect(() => {
    // Cleared once handled, which also re-arms it for the next shortcut click.
    if (searchParams.get('autostart') !== '1') { autostartDone.current = false; return }
    if (autostartDone.current || !accountsChecked) return
    if (!instance || instance.id !== searchParams.get('instance')) return
    autostartDone.current = true
    const next = new URLSearchParams(searchParams)
    next.delete('autostart')
    setSearchParams(next, { replace: true })
    if (runningGames.some(g => g.instanceId === instance.id)) return
    // launch() itself says what is missing (no account) instead of waiting for a login.
    launch()
  })
  // One game per instance; the button says so instead of starting it twice.
  const instanceRunning = !!instance && runningGames.some(g => g.instanceId === instance.id)
  // The account this start plays on: the instance's own, or the active one.
  const ownAccount = instance?.accountUuid ? accounts.find(a => a.uuid === instance.accountUuid) : undefined
  const launchAccount = ownAccount ?? profile
  const accountBusy = launchAccount ? runningGames.find(g => g.accountUuid === launchAccount.uuid) : undefined

  async function setInstanceAccount(uuid: string) {
    if (!instance) return
    await api?.updateInstance(instance.id, { accountUuid: uuid })
    refreshInstances()
  }
  const playLabel = launching ? 'Startet…' : instanceRunning ? 'Läuft' : 'Spielen'

  return (
    <Page>
      <PageHeader title="Starten" description="Wähle Konto und Instanz, dann kann es losgehen." />

      {/* The start banner: version and play button on the Nexora backdrop, so
          the thing you came for is the first thing on the page. */}
      <section className="relative mb-5 overflow-hidden rounded-[18px] border border-crystal-border bg-crystal-card">
        <div className="nexora-backdrop absolute inset-0" aria-hidden="true" />
        <div className="relative flex flex-wrap items-end gap-4 p-6">
          <div className="min-w-0 flex-1">
            <p className="text-[11px] font-semibold uppercase tracking-[0.1em] text-crystal-muted">
              {instance ? (instance.useCrystalClient ? 'Nexora Client' : 'Vanilla mit Mods') : 'Keine Instanz'}
            </p>
            <p className="mt-1 text-[30px] font-semibold leading-none tracking-tight text-crystal-text tabular">
              {instance ? instance.version : '—'}
            </p>
            <p className="mt-2 text-[13px] text-crystal-muted truncate">
              {instance ? instance.name : 'Lege eine Instanz an, um zu spielen.'}
            </p>
          </div>
          <button
            onClick={launch}
            disabled={busy || !instance || instanceRunning}
            className="crystal-btn-primary px-7 py-3 text-[15px] disabled:opacity-60"
          >
            <Play size={16} />
            {playLabel}
          </button>
        </div>
      </section>

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
        <RunningGamesPanel games={runningGames} />

        <section>
          <h2 className="text-[13px] font-semibold text-crystal-text mb-2 px-0.5">Konto</h2>
          <LoginPanel profile={profile} onProfileChange={p => { setProfile(p); api?.listAccounts().then((list: Profile[]) => setAccounts(list || [])) }} />
          {accountBusy && !launching && !instanceRunning && (
            <p className="mt-2 px-0.5 text-xs text-crystal-warning">
              {launchAccount?.username} spielt schon in „{accountBusy.instanceName}“. Wähle für diese Instanz ein anderes Konto, unten bei der Instanz oder hier.
            </p>
          )}
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
                    <option key={i.id} value={i.id}>{i.name}{playtime[i.name] ? `, ${hours(playtime[i.name])} gespielt` : ''}</option>
                  ))}
                </select>
                <ChevronDown size={14} className="absolute right-3 top-1/2 -translate-y-1/2 text-crystal-muted pointer-events-none" />
              </div>

              {instance && (
                <>
                <label className="flex items-center gap-3 text-xs">
                  <span className="text-crystal-muted shrink-0 w-24">Startet als</span>
                  <span className="relative flex-1">
                    <select
                      value={ownAccount ? ownAccount.uuid : ''}
                      onChange={e => setInstanceAccount(e.target.value)}
                      disabled={busy || instanceRunning}
                      className="crystal-input w-full appearance-none pr-9 cursor-pointer text-[13px]"
                    >
                      <option value="">Aktives Konto{profile ? ` (${profile.username})` : ''}</option>
                      {accounts.map(a => (
                        <option key={a.uuid} value={a.uuid}>
                          Immer {a.username}{a.type === 'offline' ? ' (offline)' : ''}
                        </option>
                      ))}
                    </select>
                    <ChevronDown size={14} className="absolute right-3 top-1/2 -translate-y-1/2 text-crystal-muted pointer-events-none" />
                  </span>
                </label>
                <dl className="grid grid-cols-3 gap-3 text-xs">
                  <div>
                    <dt className="text-crystal-muted">Version</dt>
                    <dd className="text-crystal-text mt-0.5 tabular">{instance.version}</dd>
                  </div>
                  <div>
                    <dt className="text-crystal-muted">Modus</dt>
                    <dd className="text-crystal-text mt-0.5">{instance.useCrystalClient ? 'Nexora Client' : 'Vanilla mit Mods'}</dd>
                  </div>
                  <div>
                    <dt className="text-crystal-muted">Arbeitsspeicher</dt>
                    <dd className="text-crystal-text mt-0.5 tabular">{(maxRam / 1024).toLocaleString('de-DE', { maximumFractionDigits: 1 })} GB</dd>
                  </div>
                </dl>
                </>
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
                <button onClick={launch} disabled={busy || !instance || instanceRunning} className="crystal-btn-primary flex-1 py-2.5 text-[14px] disabled:opacity-60">
                  <Play size={15} fill="currentColor" />
                  {playLabel}
                </button>
                <button
                  onClick={tryWithCrystal}
                  disabled={busy || !instance}
                  title="Startet die Instanz einmal mit Nexora. Klappt es nicht, wird automatisch alles zurückgesetzt."
                  className="crystal-btn-ghost border border-crystal-border text-crystal-text disabled:opacity-60"
                >
                  <FlaskConical size={14} strokeWidth={1.75} />
                  {trying ? 'Teste…' : 'Mit Nexora testen'}
                </button>
                {navigator.userAgent.includes('Windows') && (
                  <button
                    onClick={async () => {
                      const r = await api?.createInstanceShortcut(instance!.id)
                      notify({ type: r?.ok ? 'success' : 'error', title: 'Desktop-Verknüpfung', message: r?.message ?? 'Fehlgeschlagen.' })
                    }}
                    disabled={!instance}
                    title="Legt ein Symbol auf den Desktop, das diese Instanz direkt startet"
                    aria-label="Desktop-Verknüpfung anlegen"
                    className="crystal-btn-ghost border border-crystal-border text-crystal-text disabled:opacity-60"
                  >
                    <MonitorSmartphone size={14} strokeWidth={1.75} />
                  </button>
                )}
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

        {instance && <PerfDoctorPanel instanceId={instance.id} running={instanceRunning} onRamChanged={setMaxRam} />}

        <GroupLaunch instances={instances} accounts={accounts} profile={profile} running={runningGames} maxRam={maxRam} disabled={busy} />

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
            Startet eine .exe oder .jar direkt. Nexora verändert deren Dateien nicht, die Einstellungen oben gelten dafür nicht.
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
