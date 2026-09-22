import React, { useEffect, useMemo, useState } from 'react'
import {
  X, ArrowRight, Wand2, Loader2, Check, AlertTriangle, Upload, Copy, RotateCcw, Package, Swords,
} from 'lucide-react'
import { notify } from '../../store/notificationStore'
import { LogoMark } from '../../theme/logoVariants'

const api = (window as any).crystal

export interface ProblemFix {
  kind: string
  label: string
  modFile?: string
  modName?: string
  project?: string
  ram?: number
}

export interface DetectedProblem {
  id: string
  group: 'conflict' | 'replace' | 'install' | 'other'
  title: string
  detail: string
  fix: ProblemFix | null
  sides?: [ConflictSide, ConflictSide]
  currentVersion?: string | null
}

interface ConflictSide {
  name: string
  version: string | null
  modFile: string
}

type RowState = 'idle' | 'working' | 'done' | 'failed'

interface Props {
  instanceId: string
  problems: DetectedProblem[]
  errorText: string | null
  account: string | null
  onRelaunch: () => void
  onClose: () => void
  /** A fix changed a setting the page shows (RAM). */
  onRamChanged?: (ram: number) => void
}

/** A short id for this crash, so support can match a screenshot to an uploaded log. */
function crashId(): string {
  return Array.from(crypto.getRandomValues(new Uint8Array(6)), b => b.toString(16).padStart(2, '0')).join('')
}

/**
 * The window a failed launch opens: what Nexora found in the log, grouped
 * into mod conflicts (keep one), mods to replace, mods to install and other
 * problems, each with its own fix and one button for all of them.
 */
export function CrashDialog({ instanceId, problems, errorText, account, onRelaunch, onClose, onRamChanged }: Props) {
  const [state, setState] = useState<Record<string, RowState>>({})
  const [previews, setPreviews] = useState<Record<string, { label?: string; error?: string } | undefined>>({})
  const [logUrl, setLogUrl] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)
  const id = useMemo(crashId, [problems])

  const conflicts = problems.filter(p => p.group === 'conflict')
  const replacements = problems.filter(p => p.group === 'replace')
  const installs = problems.filter(p => p.group === 'install')
  const others = problems.filter(p => p.group === 'other')
  const modProblems = conflicts.length + replacements.length + installs.length > 0

  // Which version a "Replace" would install, looked up while the window is open.
  useEffect(() => {
    for (const p of replacements) {
      if (!p.fix) continue
      api?.previewFix(instanceId, p.fix).then((r: { label?: string; error?: string } | null) =>
        setPreviews(prev => ({ ...prev, [p.id]: r ?? { error: 'Nicht verfügbar' } })))
    }
  }, [problems])

  const setRow = (key: string, value: RowState) => setState(prev => ({ ...prev, [key]: value }))

  async function apply(p: DetectedProblem): Promise<boolean> {
    if (!p.fix || state[p.id] === 'done') return state[p.id] === 'done'
    setRow(p.id, 'working')
    const result = await api?.applyFix(instanceId, p.fix)
    setRow(p.id, result?.ok ? 'done' : 'failed')
    if (!result?.ok) notify({ type: 'error', title: p.fix.modName || 'Autofix', message: result?.message || 'Fehlgeschlagen' })
    else if ((p.fix.kind === 'lower-ram' || p.fix.kind === 'raise-ram') && p.fix.ram) onRamChanged?.(p.fix.ram)
    return !!result?.ok
  }

  async function keep(p: DetectedProblem, keepIndex: 0 | 1) {
    if (!p.sides) return
    const drop = p.sides[keepIndex === 0 ? 1 : 0]
    setRow(p.id, 'working')
    const result = await api?.applyFix(instanceId, { kind: 'disable-mod', label: '', modFile: drop.modFile })
    setRow(p.id, result?.ok ? 'done' : 'failed')
    notify({
      type: result?.ok ? 'success' : 'error',
      title: 'Konflikt',
      message: result?.ok ? `${drop.name} deaktiviert, ${p.sides[keepIndex].name} bleibt.` : result?.message || 'Fehlgeschlagen',
    })
  }

  async function fixAll() {
    let fixed = 0
    for (const p of [...replacements, ...installs, ...others]) {
      if (p.fix && state[p.id] !== 'done' && await apply(p)) fixed++
    }
    notify({ type: fixed > 0 ? 'success' : 'info', title: 'Alles beheben', message: fixed > 0 ? `${fixed} Problem(e) behoben.` : 'Nichts mehr automatisch zu beheben.' })
  }

  async function uploadLog() {
    setUploading(true)
    const result = await api?.uploadCrashLog(instanceId)
    setUploading(false)
    if (result?.ok) {
      setLogUrl(result.url)
      notify({ type: 'success', title: 'Crash-Log hochgeladen', message: 'Link in die Zwischenablage kopiert.' })
    } else {
      notify({ type: 'error', title: 'Upload fehlgeschlagen', message: result?.message || 'Unbekannter Fehler' })
    }
  }

  const fixable = [...replacements, ...installs, ...others].filter(p => p.fix && state[p.id] !== 'done')
  const unresolvedConflicts = conflicts.filter(p => state[p.id] !== 'done').length

  return (
    <div className="fixed inset-0 z-50 bg-black/70 flex items-center justify-center p-6" onClick={onClose} role="dialog" aria-modal="true" aria-label="Start fehlgeschlagen">
      <div className="relative w-full max-w-[520px] max-h-full overflow-y-auto rounded-2xl border border-crystal-border bg-crystal-panel shadow-2xl" onClick={e => e.stopPropagation()}>
        <button onClick={onClose} aria-label="Schließen" className="absolute top-3 right-3 z-10 p-1.5 rounded-md text-white/80 hover:text-white hover:bg-white/10">
          <X size={16} />
        </button>

        {/* Banner */}
        <div className="relative h-28 overflow-hidden rounded-t-2xl bg-gradient-to-br from-crystal-danger/50 via-crystal-accent/25 to-crystal-bg flex items-center justify-center">
          <div className="absolute inset-0 opacity-30" style={{ backgroundImage: 'repeating-linear-gradient(135deg, rgba(255,255,255,0.08) 0 2px, transparent 2px 14px)' }} />
          <div className="relative w-14 h-14 rounded-2xl bg-crystal-bg/80 border border-white/10 flex items-center justify-center shadow-lg">
            <LogoMark variant="facet-hex" size={30} />
          </div>
        </div>

        <div className="px-6 pt-5 pb-4 text-center">
          <h2 className="text-lg font-semibold text-crystal-text">{modProblems ? 'Mods passen nicht zusammen' : 'Start fehlgeschlagen'}</h2>
          <p className="mt-1 text-[13px] text-crystal-muted">
            {problems.length === 0
              ? 'Nexora konnte die Ursache nicht sicher erkennen. Die Fehlermeldung steht unten.'
              : modProblems
                ? 'Einige deiner Mods passen nicht zum Spiel oder nicht zueinander.'
                : 'Nexora hat die Ursache gefunden.'}
          </p>
        </div>

        <div className="px-6 space-y-5">
          {conflicts.length > 0 && (
            <Group icon={<Swords size={12} />} title="Konflikt · eine behalten" tone="danger">
              {conflicts.map(p => {
                const done = state[p.id] === 'done'
                return (
                  <div key={p.id} className="flex items-center gap-2">
                    {p.sides!.map((side, i) => (
                      <React.Fragment key={side.modFile}>
                        {i === 1 && <X size={14} className="text-crystal-danger shrink-0" />}
                        <button
                          disabled={state[p.id] === 'working' || done}
                          onClick={() => keep(p, i as 0 | 1)}
                          title={`${side.name} behalten`}
                          className="flex-1 min-w-0 flex items-center gap-2 px-3 py-2 rounded-lg border border-crystal-border bg-crystal-card text-left hover:border-crystal-accent disabled:opacity-60 disabled:hover:border-crystal-border"
                        >
                          <Package size={13} className="text-crystal-accent shrink-0" />
                          <span className="flex-1 min-w-0 truncate text-[13px] text-crystal-text">{side.name}</span>
                          {side.version && <span className="text-[11px] text-crystal-muted tabular truncate max-w-[70px]">{side.version}</span>}
                        </button>
                      </React.Fragment>
                    ))}
                    {done && <Check size={15} className="text-crystal-success shrink-0" />}
                  </div>
                )
              })}
            </Group>
          )}

          {replacements.length > 0 && (
            <Group title="Ersetzen">
              {replacements.map(p => {
                const preview = previews[p.id]
                return (
                  <FixRow key={p.id} state={state[p.id]} onFix={() => apply(p)} disabled={!!preview?.error}
                    left={<Chip name={p.fix?.modName ?? p.title} version={p.currentVersion ?? null} />}
                    right={
                      preview === undefined
                        ? <span className="flex items-center gap-1.5 text-xs text-crystal-muted"><Loader2 size={12} className="animate-spin" /> Suche…</span>
                        : preview.error
                          ? <span className="text-xs text-crystal-warning truncate">{preview.error}</span>
                          : <Chip name={preview.label ?? ''} version={null} accent />
                    }
                  />
                )
              })}
            </Group>
          )}

          {installs.length > 0 && (
            <Group title="Fehlt">
              {installs.map(p => (
                <FixRow key={p.id} state={state[p.id]} onFix={() => apply(p)}
                  left={<Chip name={p.fix?.modName ?? p.title} version={null} />}
                  right={<span className="text-xs text-crystal-muted">von Modrinth</span>} />
              ))}
            </Group>
          )}

          {others.length > 0 && (
            <Group title="Sonstiges">
              {others.map(p => (
                <div key={p.id} className="flex items-start gap-3 rounded-lg border border-crystal-border bg-crystal-card px-3 py-2.5">
                  <AlertTriangle size={14} className="text-crystal-warning shrink-0 mt-0.5" />
                  <div className="flex-1 min-w-0">
                    <p className="text-[13px] text-crystal-text">{p.title}</p>
                    <p className="text-xs text-crystal-muted mt-0.5">{p.detail}</p>
                  </div>
                  {p.fix && <FixButton state={state[p.id]} label={p.fix.label} onClick={() => apply(p)} />}
                </div>
              ))}
            </Group>
          )}

          {(fixable.length > 0 || unresolvedConflicts > 0) && (
            <div className="flex flex-col items-center gap-2.5">
              {fixable.length > 0 && (
                <button onClick={fixAll} className="crystal-btn-primary text-[13px] px-4">
                  <Wand2 size={14} /> Alles beheben
                </button>
              )}
              {modProblems && (
                <p className="flex items-start gap-1.5 text-xs text-crystal-warning text-center">
                  <AlertTriangle size={13} className="shrink-0 mt-px" />
                  Das kann mehrere Starts brauchen: beheben, neu starten und wiederholen, bis das Spiel läuft.
                </p>
              )}
            </div>
          )}

          <button onClick={onRelaunch} className="w-full crystal-btn-ghost border border-crystal-border text-crystal-text text-[13px]">
            <RotateCcw size={13} /> Neu starten
          </button>

          {errorText && (
            <details className="text-xs text-crystal-muted">
              <summary className="cursor-pointer hover:text-crystal-text select-none">Vollständige Fehlermeldung</summary>
              <pre className="mt-2 p-2.5 rounded-md bg-crystal-bg border border-crystal-border font-mono text-[11px] whitespace-pre-wrap break-all max-h-48 overflow-auto select-text">{errorText}</pre>
            </details>
          )}
        </div>

        <div className="mt-5 px-6 py-4 border-t border-crystal-border space-y-3">
          <div className="flex items-center gap-2 text-xs">
            <span className="text-crystal-muted">Info:</span>
            <button
              onClick={() => { navigator.clipboard.writeText(id); notify({ type: 'success', message: 'ID kopiert' }) }}
              className="flex items-center gap-1.5 px-2 py-1 rounded-md bg-crystal-card border border-crystal-border font-mono text-crystal-accent hover:border-crystal-accent"
              title="Kopieren"
            >
              {id} <Copy size={11} />
            </button>
            {account && (
              <>
                <span className="ml-auto text-crystal-muted">Account:</span>
                <span className="px-2 py-1 rounded-md bg-crystal-card border border-crystal-border text-crystal-text">{account}</span>
              </>
            )}
          </div>
          <div className="flex items-center gap-2">
            <button onClick={uploadLog} disabled={uploading} className="crystal-btn-primary text-xs py-1.5 disabled:opacity-60">
              {uploading ? <Loader2 size={12} className="animate-spin" /> : <Upload size={12} />} Crash-Log hochladen
            </button>
            {logUrl && (
              <button onClick={() => api?.openExternal?.(logUrl)} className="text-xs text-crystal-accent hover:underline truncate">
                {logUrl}
              </button>
            )}
          </div>
          <p className="text-[11px] text-crystal-muted">
            Der Log wird auf mclo.gs veröffentlicht, damit du ihn teilen kannst. Zugangsdaten und dein Windows-Benutzername werden vorher entfernt.
          </p>
        </div>
      </div>
    </div>
  )
}

function Group({ title, icon, tone, children }: { title: string; icon?: React.ReactNode; tone?: 'danger'; children: React.ReactNode }) {
  return (
    <div>
      <p className={`flex items-center justify-center gap-1.5 mb-2 text-[11px] font-semibold uppercase tracking-wider ${tone === 'danger' ? 'text-crystal-danger' : 'text-crystal-muted'}`}>
        {icon}{title}
      </p>
      <div className="space-y-2">{children}</div>
    </div>
  )
}

function Chip({ name, version, accent }: { name: string; version: string | null; accent?: boolean }) {
  return (
    <span className={`flex-1 min-w-0 flex items-center gap-2 px-3 py-2 rounded-lg border bg-crystal-card ${accent ? 'border-crystal-accent/50' : 'border-crystal-border'}`}>
      <Package size={13} className={accent ? 'text-crystal-accent shrink-0' : 'text-crystal-muted shrink-0'} />
      <span className="flex-1 min-w-0 truncate text-[13px] text-crystal-text">{name}</span>
      {version && <span className="text-[11px] text-crystal-muted tabular truncate max-w-[90px]">{version}</span>}
    </span>
  )
}

function FixRow({ left, right, state, onFix, disabled }: {
  left: React.ReactNode; right: React.ReactNode; state?: RowState; onFix: () => void; disabled?: boolean
}) {
  return (
    <div className="flex items-center gap-2">
      <div className="flex-1 min-w-0 flex">{left}</div>
      <ArrowRight size={14} className="text-crystal-muted shrink-0" />
      <div className="flex-1 min-w-0 flex items-center">{right}</div>
      <FixButton state={state} label="Beheben" onClick={onFix} disabled={disabled} />
    </div>
  )
}

function FixButton({ state, label, onClick, disabled }: { state?: RowState; label: string; onClick: () => void; disabled?: boolean }) {
  if (state === 'done') {
    return <span className="shrink-0 flex items-center gap-1 px-2.5 py-1.5 text-xs text-crystal-success"><Check size={13} /> Erledigt</span>
  }
  return (
    <button onClick={onClick} disabled={disabled || state === 'working'}
      className="shrink-0 flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border border-crystal-border bg-crystal-card text-xs text-crystal-text hover:border-crystal-accent disabled:opacity-50 disabled:hover:border-crystal-border">
      {state === 'working' ? <Loader2 size={12} className="animate-spin" /> : <Wand2 size={12} />}
      {state === 'failed' ? 'Nochmal' : label}
    </button>
  )
}
