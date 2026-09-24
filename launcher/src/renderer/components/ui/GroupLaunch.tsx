import React, { useEffect, useState } from 'react'
import { Layers, Play } from 'lucide-react'
import { notify } from '../../store/notificationStore'
import type { RunningGame } from './RunningGamesPanel'

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
}

const api = (window as any).crystal

/**
 * Starts several instances in one go, each on its own account (the one set
 * on the instance, else the active one). They start one after another: two
 * launches downloading and unpacking at once only slow each other down.
 */
export function GroupLaunch({ instances, accounts, profile, running, maxRam, disabled }: {
  instances: Instance[]
  accounts: Profile[]
  profile: Profile | null
  running: RunningGame[]
  maxRam: number
  disabled: boolean
}) {
  const [open, setOpen] = useState(false)
  const [picked, setPicked] = useState<string[]>([])
  const [status, setStatus] = useState<string | null>(null)

  useEffect(() => {
    api?.getSetting('groupLaunch').then((ids: string[] | undefined) => Array.isArray(ids) && setPicked(ids))
  }, [])

  const accountOf = (i: Instance) =>
    (i.accountUuid && accounts.find(a => a.uuid === i.accountUuid)) || profile
  const isRunning = (i: Instance) => running.some(g => g.instanceId === i.id)

  function toggle(id: string) {
    setPicked(current => {
      const next = current.includes(id) ? current.filter(x => x !== id) : [...current, id]
      api?.setSetting('groupLaunch', next)
      return next
    })
  }

  async function start() {
    const chosen = instances.filter(i => picked.includes(i.id) && !isRunning(i))
    if (chosen.length === 0) return
    // An account can only be online once, so every instance needs its own.
    const seen = new Map<string, string>()
    for (const i of chosen) {
      const account = accountOf(i)
      if (!account) {
        notify({ type: 'error', title: 'Kein Konto', message: `"${i.name}" hat kein Konto. Melde dich an oder gib der Instanz eins.` })
        return
      }
      const busy = running.find(g => g.accountUuid === account.uuid)
      const twice = seen.get(account.uuid)
      if (busy || twice) {
        notify({
          type: 'error',
          title: 'Ein Konto zweimal',
          message: `${account.username} spielt schon in "${busy?.instanceName ?? twice}". Gib "${i.name}" unter "Startet als" ein eigenes Konto.`,
        })
        return
      }
      seen.set(account.uuid, i.name)
    }

    for (const [n, i] of chosen.entries()) {
      setStatus(`Starte ${i.name} (${n + 1} von ${chosen.length})…`)
      const ok = await api?.launchGame({
        version: i.version,
        loader: i.loader,
        gameDir: i.gameDir,
        maxRam,
        instanceId: i.id,
        injectCrystal: i.useCrystalClient,
      })
      if (!ok) {
        setStatus(null)
        notify({ type: 'error', title: 'Gruppen-Start angehalten', message: `"${i.name}" ist nicht gestartet, die übrigen Instanzen bleiben aus.` })
        return
      }
    }
    setStatus(null)
    notify({ type: 'success', title: 'Gruppen-Start', message: `${chosen.length} Instanzen laufen.` })
  }

  if (instances.length < 2) return null
  const startable = instances.filter(i => picked.includes(i.id) && !isRunning(i)).length

  return (
    <section>
      <button
        onClick={() => setOpen(o => !o)}
        aria-expanded={open}
        className="flex items-center gap-2 px-0.5 mb-2 text-[13px] font-semibold text-crystal-text"
      >
        <Layers size={14} strokeWidth={1.75} className="text-crystal-muted" />
        Mehrere auf einmal starten
        <span className="text-xs font-normal text-crystal-muted">{open ? 'ausblenden' : 'einblenden'}</span>
      </button>
      {open && (
        <div className="crystal-card p-4 space-y-3">
          <p className="text-xs text-crystal-muted">
            Hake die Instanzen an, die zusammen laufen sollen. Jede startet mit ihrem Konto aus „Startet als“, also braucht jede ein eigenes.
          </p>
          <ul className="space-y-1.5">
            {instances.map(i => {
              const account = accountOf(i)
              const runningNow = isRunning(i)
              return (
                <li key={i.id}>
                  <label className={`flex items-center gap-3 text-[13px] ${runningNow ? 'opacity-60' : 'cursor-pointer'}`}>
                    <input
                      type="checkbox"
                      checked={picked.includes(i.id)}
                      onChange={() => toggle(i.id)}
                      disabled={runningNow || !!status}
                      className="accent-[rgb(var(--c-accent))]"
                    />
                    <span className="flex-1 min-w-0 truncate text-crystal-text">{i.name}</span>
                    <span className="text-xs text-crystal-muted truncate">
                      {runningNow ? 'läuft schon' : account ? `als ${account.username}` : 'kein Konto'}
                    </span>
                  </label>
                </li>
              )
            })}
          </ul>
          <button
            onClick={start}
            disabled={disabled || !!status || startable === 0}
            className="crystal-btn-primary w-full py-2 text-[13px] disabled:opacity-60"
          >
            <Play size={14} fill="currentColor" />
            {status ?? (startable > 0 ? `${startable} Instanzen starten` : 'Instanzen auswählen')}
          </button>
        </div>
      )}
    </section>
  )
}
