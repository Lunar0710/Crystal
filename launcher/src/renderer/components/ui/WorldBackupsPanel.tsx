import React, { useState } from 'react'
import { Archive, ChevronDown, RotateCcw } from 'lucide-react'
import { notify } from '../../store/notificationStore'

interface WorldInfo {
  name: string
  sizeMb: number
  lastPlayed: number
  backups: { stamp: string; at: number; sizeMb: number }[]
}

const api = (window as any).crystal

function when(ms: number): string {
  return new Date(ms).toLocaleString('de-DE', { dateStyle: 'short', timeStyle: 'short' })
}

function size(mb: number): string {
  return mb >= 1024 ? `${(mb / 1024).toLocaleString('de-DE', { maximumFractionDigits: 1 })} GB` : `${Math.max(1, mb)} MB`
}

/** The instance's singleplayer worlds: back one up, or put an older state back. */
export function WorldBackupsPanel({ instanceId }: { instanceId: string }) {
  const [open, setOpen] = useState(false)
  const [worlds, setWorlds] = useState<WorldInfo[] | null>(null)
  const [busy, setBusy] = useState<string | null>(null)

  async function load() {
    setWorlds((await api?.listWorlds(instanceId)) || [])
  }

  async function toggle() {
    const next = !open
    setOpen(next)
    if (next) await load()
  }

  async function backup(world: string) {
    setBusy(world)
    const r = await api?.backupWorld(instanceId, world)
    setBusy(null)
    notify({ type: r?.ok ? 'success' : 'error', title: 'Welt sichern', message: r?.message ?? 'Fehlgeschlagen.' })
    await load()
  }

  async function restore(world: string, stamp: string) {
    if (!window.confirm(`„${world}“ auf den Stand vom ${stamp.replace('_', ' ')} zurücksetzen? Der jetzige Stand wird vorher gesichert.`)) return
    setBusy(world)
    const r = await api?.restoreWorld(instanceId, world, stamp)
    setBusy(null)
    notify({ type: r?.ok ? 'success' : 'error', title: 'Welt zurücksetzen', message: r?.message ?? 'Fehlgeschlagen.' })
    await load()
  }

  return (
    <section className="crystal-card mb-4">
      <button onClick={toggle} aria-expanded={open} className="w-full flex items-center gap-3 px-4 py-3 text-left">
        <Archive size={15} strokeWidth={1.75} className="text-crystal-muted shrink-0" />
        <span className="flex-1">
          <span className="block text-[13px] text-crystal-text">Welten sichern</span>
          <span className="block text-xs text-crystal-muted">Einzelspieler-Welten kopieren und ältere Stände wiederherstellen.</span>
        </span>
        <ChevronDown size={14} className={`text-crystal-muted transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>
      {open && (
        <div className="border-t border-crystal-border">
          {worlds === null && <p className="px-4 py-3 text-xs text-crystal-muted">Lade…</p>}
          {worlds?.length === 0 && <p className="px-4 py-3 text-xs text-crystal-muted">Diese Instanz hat noch keine Einzelspieler-Welt.</p>}
          <ul className="divide-y divide-crystal-border">
            {worlds?.map(w => (
              <li key={w.name} className="px-4 py-3 space-y-2">
                <div className="flex items-center gap-3">
                  <div className="flex-1 min-w-0">
                    <p className="text-[13px] text-crystal-text truncate">{w.name}</p>
                    <p className="text-xs text-crystal-muted tabular">Zuletzt gespielt {when(w.lastPlayed)}, {size(w.sizeMb)}</p>
                  </div>
                  <button
                    onClick={() => backup(w.name)}
                    disabled={!!busy}
                    className="crystal-btn-ghost border border-crystal-border text-xs px-2.5 py-1 text-crystal-text disabled:opacity-50"
                  >
                    {busy === w.name ? 'Sichere…' : 'Jetzt sichern'}
                  </button>
                </div>
                {w.backups.length > 0 && (
                  <ul className="pl-3 space-y-1">
                    {w.backups.map(b => (
                      <li key={b.stamp} className="flex items-center gap-3 text-xs">
                        <span className="flex-1 text-crystal-muted tabular">Stand vom {when(b.at)}, {size(b.sizeMb)}</span>
                        <button
                          onClick={() => restore(w.name, b.stamp)}
                          disabled={!!busy}
                          className="inline-flex items-center gap-1 text-crystal-muted hover:text-crystal-text disabled:opacity-50"
                        >
                          <RotateCcw size={11} /> Wiederherstellen
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  )
}
