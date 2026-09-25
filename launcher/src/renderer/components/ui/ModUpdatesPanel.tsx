import React, { useEffect, useState } from 'react'
import { RefreshCw } from 'lucide-react'
import { notify } from '../../store/notificationStore'

interface ModUpdate {
  fileName: string
  versionId: string
  versionNumber: string
  beta: boolean
}

const api = (window as any).crystal

/**
 * Newer versions of the instance's mods on Modrinth, and one click to take
 * them. Betas are listed but only taken along when asked for.
 */
export function ModUpdatesPanel({ instanceId, onUpdated }: { instanceId: string; onUpdated: () => void }) {
  const [updates, setUpdates] = useState<ModUpdate[] | null>(null)
  const [withBetas, setWithBetas] = useState(false)
  const [working, setWorking] = useState(false)
  const [open, setOpen] = useState(false)

  const check = () => api?.checkModUpdates?.(instanceId).then((u: ModUpdate[]) => setUpdates(u || []))
  useEffect(() => { setUpdates(null); check() }, [instanceId])

  if (!updates || updates.length === 0) return null
  const chosen = updates.filter(u => withBetas || !u.beta)

  async function updateAll() {
    setWorking(true)
    const result = await api?.updateMods(instanceId, chosen)
    setWorking(false)
    if (result?.error) notify({ type: 'error', title: 'Mods aktualisieren', message: result.error })
    else notify({
      type: result?.failed?.length ? 'warning' : 'success',
      title: 'Mods aktualisieren',
      message: `${result?.updated ?? 0} aktualisiert${result?.failed?.length ? `, ${result.failed.length} fehlgeschlagen` : ''}.`,
    })
    onUpdated()
    check()
  }

  const releases = updates.filter(u => !u.beta).length
  return (
    <section className="crystal-card mb-4 px-4 py-3">
      <div className="flex items-center gap-3">
        <RefreshCw size={15} strokeWidth={1.75} className="text-crystal-accent shrink-0" />
        <div className="flex-1 min-w-0">
          <p className="text-[13px] text-crystal-text">
            {updates.length === 1 ? '1 Mod-Update verfügbar' : `${updates.length} Mod-Updates verfügbar`}
            {releases < updates.length && <span className="text-crystal-muted">, davon {updates.length - releases} Beta</span>}
          </p>
          <button onClick={() => setOpen(o => !o)} className="text-xs text-crystal-muted hover:text-crystal-text">
            {open ? 'Liste ausblenden' : 'Welche?'}
          </button>
        </div>
        {releases < updates.length && (
          <label className="flex items-center gap-1.5 text-xs text-crystal-muted cursor-pointer">
            <input type="checkbox" checked={withBetas} onChange={e => setWithBetas(e.target.checked)} className="accent-[rgb(var(--c-accent))]" />
            Betas mitnehmen
          </label>
        )}
        <button
          onClick={updateAll}
          disabled={working || chosen.length === 0}
          className="crystal-btn-primary text-[13px] disabled:opacity-60"
        >
          {working ? 'Aktualisiere…' : chosen.length === updates.length ? 'Alle aktualisieren' : `${chosen.length} aktualisieren`}
        </button>
      </div>
      {open && (
        <ul className="mt-2 space-y-0.5 text-xs">
          {updates.map(u => (
            <li key={u.fileName} className="flex gap-2">
              <span className="flex-1 truncate text-crystal-muted">{u.fileName}</span>
              <span className="text-crystal-text tabular">{u.versionNumber}{u.beta && <span className="text-crystal-warning"> Beta</span>}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
