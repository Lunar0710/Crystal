import React, { useEffect, useState } from 'react'
import { Layers, Plus, Trash2, Check } from 'lucide-react'
import { notify } from '../../store/notificationStore'

const api = (window as any).crystal

interface ModProfiles {
  active: string | null
  profiles: { name: string; mods: number }[]
}

/**
 * Named sets of enabled mods for one instance ("PvP", "Bauen", ...). Picking a
 * profile enables exactly its mods and disables the rest; the Nexora jar is
 * left to the launcher.
 */
export function ModProfileBar({ instanceId, onChanged }: { instanceId: string; onChanged: () => void }) {
  const [data, setData] = useState<ModProfiles>({ active: null, profiles: [] })
  const [naming, setNaming] = useState(false)
  const [name, setName] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api?.listModProfiles?.(instanceId).then((d: ModProfiles) => d && setData(d))
  }, [instanceId])

  async function apply(profile: string) {
    setBusy(true)
    const next = await api?.applyModProfile(instanceId, profile)
    setBusy(false)
    if (!next) return notify({ type: 'error', message: 'Profil konnte nicht geladen werden' })
    setData(next)
    onChanged()
    notify({ type: 'success', message: `Profil „${profile}“ aktiv` })
  }

  async function save() {
    const trimmed = name.trim()
    if (!trimmed) return
    const next = await api?.saveModProfile(instanceId, trimmed)
    if (!next) return notify({ type: 'error', message: 'Name ungültig (1 bis 32 Zeichen)' })
    setData(next)
    setNaming(false)
    setName('')
    notify({ type: 'success', message: `Aktive Mods als „${trimmed}“ gespeichert` })
  }

  async function remove(profile: string) {
    const next = await api?.deleteModProfile(instanceId, profile)
    if (next) setData(next)
  }

  return (
    <div className="crystal-card px-3 py-2.5 flex flex-wrap items-center gap-2">
      <span className="inline-flex items-center gap-1.5 text-xs text-crystal-muted mr-1">
        <Layers size={13} strokeWidth={1.75} /> Mod-Profile
      </span>

      {data.profiles.length === 0 && !naming && (
        <span className="text-xs text-crystal-muted">Speichere die gerade aktiven Mods als Profil, z. B. „PvP“ oder „Bauen“.</span>
      )}

      {data.profiles.map(p => {
        const active = p.name === data.active
        return (
          <span key={p.name} className={`group inline-flex items-center rounded-md border text-xs ${
            active ? 'border-crystal-accent bg-crystal-accent/10 text-crystal-text' : 'border-crystal-border text-crystal-muted'
          }`}>
            <button
              disabled={busy || active}
              onClick={() => apply(p.name)}
              title={`${p.mods} Mods`}
              className="inline-flex items-center gap-1 pl-2 pr-1.5 py-1 hover:text-crystal-text disabled:cursor-default"
            >
              {active && <Check size={12} />}
              {p.name}
              <span className="tabular opacity-60">{p.mods}</span>
            </button>
            <button
              onClick={() => remove(p.name)}
              aria-label={`Profil ${p.name} löschen`}
              className="pr-1.5 py-1 opacity-0 group-hover:opacity-100 focus:opacity-100 hover:text-crystal-danger"
            >
              <Trash2 size={11} />
            </button>
          </span>
        )
      })}

      <div className="ml-auto">
        {naming ? (
          <form onSubmit={e => { e.preventDefault(); save() }} className="flex items-center gap-1.5">
            <input
              autoFocus
              value={name}
              maxLength={32}
              onChange={e => setName(e.target.value)}
              onKeyDown={e => { if (e.key === 'Escape') setNaming(false) }}
              className="crystal-input text-xs py-1 w-36"
              placeholder="Profilname"
              aria-label="Profilname"
            />
            <button type="submit" className="crystal-btn-primary text-xs py-1">Speichern</button>
            <button type="button" onClick={() => setNaming(false)} className="crystal-btn-ghost text-xs py-1">Abbrechen</button>
          </form>
        ) : (
          <button onClick={() => { setName(data.active ?? ''); setNaming(true) }} className="crystal-btn-ghost border border-crystal-border text-crystal-text text-xs py-1">
            <Plus size={12} /> Aktive Mods speichern
          </button>
        )}
      </div>
    </div>
  )
}
