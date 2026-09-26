import React, { useEffect, useMemo, useState, useCallback } from 'react'
import { Images, Copy, FolderOpen, Trash2, X, ChevronLeft, ChevronRight, RefreshCw } from 'lucide-react'
import { Page, PageHeader, EmptyState } from '../ui/Page'
import { notify } from '../../store/notificationStore'

interface Screenshot {
  instanceId: string
  instanceName: string
  fileName: string
  takenAt: number
  sizeBytes: number
}

const api = (window as any).crystal

const key = (s: Screenshot) => `${s.instanceId}/${s.fileName}`

function fmtDate(ms: number) {
  return new Date(ms).toLocaleString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

export function Screenshots() {
  const [shots, setShots] = useState<Screenshot[]>([])
  const [loaded, setLoaded] = useState(false)
  const [thumbs, setThumbs] = useState<Record<string, string>>({})
  const [filter, setFilter] = useState<string>('all')
  const [open, setOpen] = useState<number | null>(null)
  const [full, setFull] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    const list: Screenshot[] = (await api?.listScreenshots()) || []
    setShots(list)
    setLoaded(true)
  }, [])

  useEffect(() => { refresh() }, [refresh])

  const instances = useMemo(() => {
    const map = new Map<string, string>()
    for (const s of shots) map.set(s.instanceId, s.instanceName)
    return [...map.entries()]
  }, [shots])

  const visible = useMemo(() => filter === 'all' ? shots : shots.filter(s => s.instanceId === filter), [shots, filter])

  // Thumbnails load one after another so a big folder doesn't stall the app.
  useEffect(() => {
    let cancelled = false
    ;(async () => {
      for (const s of visible) {
        if (cancelled) return
        if (thumbs[key(s)]) continue
        const url = await api?.screenshotThumb(s.instanceId, s.fileName)
        if (!cancelled && url) setThumbs(prev => ({ ...prev, [key(s)]: url }))
      }
    })()
    return () => { cancelled = true }
  }, [visible])

  const current = open !== null ? visible[open] : null

  useEffect(() => {
    setFull(null)
    if (!current) return
    let cancelled = false
    api?.screenshotFull(current.instanceId, current.fileName).then((url: string | null) => { if (!cancelled) setFull(url) })
    return () => { cancelled = true }
  }, [current?.instanceId, current?.fileName])

  useEffect(() => {
    if (open === null) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(null)
      if (e.key === 'ArrowRight') setOpen(i => (i === null ? i : Math.min(visible.length - 1, i + 1)))
      if (e.key === 'ArrowLeft') setOpen(i => (i === null ? i : Math.max(0, i - 1)))
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, visible.length])

  async function copy(s: Screenshot) {
    const ok = await api?.copyScreenshot(s.instanceId, s.fileName)
    notify(ok ? { type: 'success', message: 'Screenshot in die Zwischenablage kopiert' } : { type: 'error', message: 'Kopieren hat nicht geklappt' })
  }

  async function remove(s: Screenshot) {
    const ok = await api?.removeScreenshot(s.instanceId, s.fileName)
    if (!ok) {
      notify({ type: 'error', message: 'Löschen hat nicht geklappt' })
      return
    }
    notify({ type: 'success', message: 'In den Papierkorb verschoben' })
    const idx = visible.findIndex(v => key(v) === key(s))
    const next = shots.filter(v => key(v) !== key(s))
    setShots(next)
    const nextVisible = filter === 'all' ? next : next.filter(v => v.instanceId === filter)
    setOpen(nextVisible.length === 0 ? null : Math.min(idx, nextVisible.length - 1))
  }

  return (
    <Page wide>
      <PageHeader
        title="Screenshots"
        description="Alle Screenshots aus allen Instanzen an einem Ort. F2 im Spiel macht einen neuen."
        actions={
          <button onClick={refresh} className="crystal-btn-ghost text-[13px] inline-flex items-center gap-1.5">
            <RefreshCw size={14} /> Aktualisieren
          </button>
        }
      />

      {instances.length > 1 && (
        <div className="flex flex-wrap gap-1.5 mb-4">
          <Chip active={filter === 'all'} onClick={() => setFilter('all')}>Alle ({shots.length})</Chip>
          {instances.map(([id, name]) => (
            <Chip key={id} active={filter === id} onClick={() => setFilter(id)}>
              {name} ({shots.filter(s => s.instanceId === id).length})
            </Chip>
          ))}
        </div>
      )}

      {loaded && visible.length === 0 ? (
        <EmptyState icon={<Images size={28} />} title="Noch keine Screenshots">
          Drück im Spiel F2, dann taucht der Screenshot hier auf.
        </EmptyState>
      ) : (
        <div className="grid grid-cols-[repeat(auto-fill,minmax(210px,1fr))] gap-3">
          {visible.map((s, i) => (
            <button
              key={key(s)}
              onClick={() => setOpen(i)}
              className="group text-left rounded-[10px] overflow-hidden border border-crystal-border bg-crystal-card hover:border-crystal-accent/60 transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-crystal-accent"
            >
              <div className="aspect-video bg-crystal-panel">
                {thumbs[key(s)] && <img src={thumbs[key(s)]} alt="" className="w-full h-full object-cover" />}
              </div>
              <div className="px-2.5 py-2">
                <p className="text-[12px] text-crystal-text truncate">{fmtDate(s.takenAt)}</p>
                <p className="text-[11px] text-crystal-muted truncate">{s.instanceName}</p>
              </div>
            </button>
          ))}
        </div>
      )}

      {current && (
        <div className="fixed inset-0 z-50 bg-black/85 flex flex-col" onClick={() => setOpen(null)}>
          <div className="flex items-center gap-2 px-4 py-3" onClick={e => e.stopPropagation()}>
            <div className="min-w-0 flex-1">
              <p className="text-[13px] text-white truncate">{current.fileName}</p>
              <p className="text-[11px] text-white/60">{current.instanceName} · {fmtDate(current.takenAt)} · {(current.sizeBytes / 1024 / 1024).toFixed(1)} MB</p>
            </div>
            <ViewerButton onClick={() => copy(current)} icon={<Copy size={15} />} label="Kopieren" />
            <ViewerButton onClick={() => api?.showScreenshot(current.instanceId, current.fileName)} icon={<FolderOpen size={15} />} label="Im Ordner zeigen" />
            <ViewerButton onClick={() => remove(current)} icon={<Trash2 size={15} />} label="Löschen" danger />
            <ViewerButton onClick={() => setOpen(null)} icon={<X size={16} />} label="Schließen" />
          </div>
          <div className="flex-1 min-h-0 flex items-center justify-center gap-3 px-4 pb-4">
            <NavButton disabled={open === 0} onClick={e => { e.stopPropagation(); setOpen(i => Math.max(0, (i ?? 0) - 1)) }}><ChevronLeft size={22} /></NavButton>
            <div className="flex-1 min-w-0 h-full flex items-center justify-center" onClick={e => e.stopPropagation()}>
              {full
                ? <img src={full} alt={current.fileName} className="max-w-full max-h-full object-contain rounded-md" />
                : thumbs[key(current)] && <img src={thumbs[key(current)]} alt="" className="max-w-full max-h-full object-contain rounded-md opacity-70" />}
            </div>
            <NavButton disabled={open === visible.length - 1} onClick={e => { e.stopPropagation(); setOpen(i => Math.min(visible.length - 1, (i ?? 0) + 1)) }}><ChevronRight size={22} /></NavButton>
          </div>
        </div>
      )}
    </Page>
  )
}

function Chip({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      onClick={onClick}
      className={`px-2.5 py-1 rounded-md text-[12px] transition-colors ${active ? 'bg-crystal-accent text-on-accent' : 'bg-crystal-card text-crystal-muted hover:text-crystal-text'}`}
    >
      {children}
    </button>
  )
}

function ViewerButton({ onClick, icon, label, danger }: { onClick: () => void; icon: React.ReactNode; label: string; danger?: boolean }) {
  return (
    <button
      onClick={onClick}
      title={label}
      aria-label={label}
      className={`inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-md text-[12px] text-white/85 hover:text-white ${danger ? 'hover:bg-red-500/30' : 'hover:bg-white/10'}`}
    >
      {icon}<span>{label}</span>
    </button>
  )
}

function NavButton({ disabled, onClick, children }: { disabled: boolean; onClick: (e: React.MouseEvent) => void; children: React.ReactNode }) {
  return (
    <button
      disabled={disabled}
      onClick={onClick}
      className="shrink-0 w-10 h-10 rounded-full flex items-center justify-center text-white/80 hover:bg-white/10 disabled:opacity-20 disabled:hover:bg-transparent"
    >
      {children}
    </button>
  )
}
