import React, { useState, useEffect } from 'react'
import { Rocket, ChevronDown, Plus, ExternalLink, Trash2, Settings2, Blocks, FlaskConical, CheckCircle2, RotateCcw } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { notify } from '../../store/notificationStore'
import { LoginPanel } from '../ui/LoginPanel'
import { CrystalWordmark } from '../../theme/CrystalWordmark'

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

  function refreshInstances() {
    api?.getInstances().then((list: Instance[]) => {
      const all = list || []
      setInstances(all)
      setInstanceId(current => (all.some(i => i.id === current) ? current : all[0]?.id ?? ''))
    })
  }

  function refreshExternalClients() {
    api?.listExternalClients().then((list: ExternalClient[]) => setExternalClients(list || []))
  }

  useEffect(() => {
    refreshInstances()
    refreshExternalClients()

    // RAM lives in Settings now — the launch screen just reads it.
    api?.getSetting('maxRam').then((v: number | undefined) => setMaxRam(v || DEFAULT_RAM))

    api?.getProfile().then((p: Profile | null) => {
      if (p) setProfile(p)
      else api?.autoLogin().then((auto: Profile | null) => auto && setProfile(auto))
    })
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
    else notify({ type: 'error', message: 'Konnte Client nicht starten — Datei fehlt?' })
  }

  // One attempt only: the backend reverts the instance itself if it fails,
  // so there is deliberately no retry loop here.
  async function tryWithCrystal() {
    if (!instance) return
    setTrying(true)
    setTryStatus(null)
    setProgress({ step: 'Teste Crystal...', percent: 0 })

    api?.on('launch:progress', (data: { step: string; percent: number }) => setProgress(data))

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

    api?.on('launch:progress', (data: { step: string; percent: number }) => setProgress(data))
    api?.on('launch:error', (msg: string) => {
      notify({ type: 'error', title: 'Launch Error', message: msg })
      setLaunching(false)
      setProgress(null)
    })
    api?.on('launch:started', () => {
      notify({ type: 'success', title: 'Crystal', message: 'Minecraft wurde gestartet' })
      setLaunching(false)
      setProgress(null)
    })

    await api?.launchGame({
      version: instance.version,
      loader: instance.loader,
      gameDir: instance.gameDir,
      maxRam,
      instanceId: instance.id,
      injectCrystal: instance.useCrystalClient,
    })
  }

  return (
    <div className="p-6 max-w-2xl mx-auto space-y-4">
      <div className="flex items-center gap-2 mb-2">
        <Rocket size={20} className="text-crystal-accent" />
        <h1 className="text-xl font-bold text-crystal-text">Launch</h1>
      </div>

      <LoginPanel profile={profile} onProfileChange={setProfile} />

      {/* Instance picker */}
      <div className="crystal-card p-5 space-y-4">
        <div>
          <div className="flex items-center justify-between mb-1.5">
            <label className="text-crystal-muted text-xs font-medium uppercase tracking-wide">Instanz</label>
            <button
              onClick={() => navigate('/instances')}
              className="text-crystal-accent text-xs hover:underline flex items-center gap-1"
            >
              <Settings2 size={11} /> Verwalten
            </button>
          </div>

          {instances.length === 0 ? (
            <div className="flex items-center justify-between gap-3 p-3 rounded-lg bg-crystal-panel border border-dashed border-crystal-border">
              <span className="text-crystal-muted text-sm">Noch keine Instanz vorhanden.</span>
              <button onClick={() => navigate('/instances')} className="crystal-btn-primary text-xs px-3 py-1.5">
                Instanz anlegen
              </button>
            </div>
          ) : (
            <div className="relative">
              <select
                value={instanceId}
                onChange={e => setInstanceId(e.target.value)}
                className="crystal-input w-full appearance-none pr-8 cursor-pointer"
              >
                {instances.map(i => (
                  <option key={i.id} value={i.id}>
                    {i.name} — {i.version} ({i.useCrystalClient ? 'Crystal Client' : 'Vanilla + Mods'})
                  </option>
                ))}
              </select>
              <ChevronDown size={14} className="absolute right-3 top-1/2 -translate-y-1/2 text-crystal-muted pointer-events-none" />
            </div>
          )}
        </div>

        {instance && (
          <div className="flex items-center gap-2 text-xs text-crystal-muted">
            {instance.useCrystalClient ? (
              <>
                <CrystalWordmark size={11} className="text-crystal-text" />
                <span>· Module aktiv · {instance.loader} · {maxRam} MB RAM</span>
              </>
            ) : (
              <>
                <Blocks size={12} />
                <span>Vanilla + Mods · {instance.loader} · {maxRam} MB RAM</span>
              </>
            )}
          </div>
        )}

        {progress && (
          <div className="space-y-1.5">
            <div className="flex justify-between text-xs">
              <span className="text-crystal-muted">{progress.step}</span>
              <span className="text-crystal-accent">{progress.percent}%</span>
            </div>
            <div className="h-1.5 bg-crystal-border rounded-full overflow-hidden">
              <div
                className="h-full bg-crystal-gradient rounded-full transition-all duration-300"
                style={{ width: `${progress.percent}%` }}
              />
            </div>
          </div>
        )}

        <div className="grid grid-cols-[1fr_auto] gap-2">
          <button
            onClick={launch}
            disabled={launching || trying || !instance}
            className="flex items-center justify-center gap-2 py-3 rounded-xl bg-crystal-gradient text-white font-semibold shadow-glow hover:opacity-90 active:scale-95 transition-all duration-200 disabled:opacity-60"
          >
            <Rocket size={18} className={launching ? 'animate-pulse' : ''} />
            {launching ? 'Startet...' : 'Play'}
          </button>

          <button
            onClick={tryWithCrystal}
            disabled={launching || trying || !instance}
            title="Einmaliger Testlauf mit Crystal — bei Fehlschlag wird automatisch zurückgesetzt"
            className="flex items-center justify-center gap-2 px-4 py-3 rounded-xl border border-crystal-accent/50 text-crystal-accent font-semibold hover:bg-crystal-accent/10 active:scale-95 transition-all duration-200 disabled:opacity-60"
          >
            <FlaskConical size={16} className={trying ? 'animate-pulse' : ''} />
            {trying ? 'Teste...' : 'Try with Crystal'}
          </button>
        </div>

        {tryStatus && (
          <div
            className={`flex items-start gap-2 p-3 rounded-lg text-xs border ${
              tryStatus.ok
                ? 'bg-crystal-success/10 border-crystal-success/30 text-crystal-text'
                : 'bg-crystal-warning/10 border-crystal-warning/30 text-crystal-text'
            }`}
          >
            {tryStatus.ok
              ? <CheckCircle2 size={14} className="text-crystal-success shrink-0 mt-0.5" />
              : <RotateCcw size={14} className="text-crystal-warning shrink-0 mt-0.5" />}
            <span>{tryStatus.message}</span>
          </div>
        )}
      </div>

      {/* External clients stay separate — Crystal never touches their files. */}
      <div className="crystal-card p-4 space-y-2">
        <span className="text-crystal-muted text-xs font-medium uppercase tracking-wide">
          Andere Clients
        </span>
        <div className="flex flex-wrap gap-2">
          {externalClients.map(c => (
            <button
              key={c.id}
              onClick={() => launchExternal(c.id)}
              title={c.executablePath}
              className="group flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium border bg-crystal-panel text-crystal-muted border-crystal-border hover:text-crystal-text transition-all"
            >
              <ExternalLink size={12} /> {c.name}
              <Trash2
                size={12}
                onClick={e => removeExternalClient(c.id, e)}
                className="opacity-0 group-hover:opacity-70 hover:!opacity-100 transition-opacity"
              />
            </button>
          ))}
          <button
            onClick={addExternalClient}
            className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-sm text-crystal-muted border border-dashed border-crystal-border hover:text-crystal-accent hover:border-crystal-accent transition-colors"
          >
            <Plus size={14} /> Meteor, Feather, eigene .exe/.jar...
          </button>
        </div>
        <p className="text-crystal-muted text-xs">
          Startet die gewählte Datei direkt — getrennt von Crystal, die Einstellungen oben gelten dafür nicht.
        </p>
      </div>
    </div>
  )
}
