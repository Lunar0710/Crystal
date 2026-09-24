import React, { useEffect, useState } from 'react'
import { Gauge, CheckCircle2 } from 'lucide-react'
import { notify } from '../../store/notificationStore'

interface PerfFinding {
  id: string
  level: 'high' | 'medium' | 'info'
  title: string
  detail: string
  fix: { kind: string; label: string } | null
}

const api = (window as any).crystal

const DOT: Record<PerfFinding['level'], string> = {
  high: 'bg-crystal-danger',
  medium: 'bg-crystal-warning',
  info: 'bg-crystal-muted',
}

/**
 * FPS-Doktor: checks one instance for what costs frames (mods doing the same
 * job twice, heavy mods, costly Nexora modules, RAM set too high or low) and
 * fixes each with a click.
 */
export function PerfDoctorPanel({ instanceId, running, onRamChanged }: {
  instanceId: string
  running: boolean
  onRamChanged: (ram: number) => void
}) {
  const [findings, setFindings] = useState<PerfFinding[] | null>(null)
  const [checking, setChecking] = useState(false)
  const [fixing, setFixing] = useState<string | null>(null)

  // A different instance has its own findings.
  useEffect(() => setFindings(null), [instanceId])

  async function check() {
    setChecking(true)
    setFindings((await api?.analyzePerformance(instanceId)) || [])
    setChecking(false)
  }

  async function apply(finding: PerfFinding) {
    if (!finding.fix) return
    setFixing(finding.id)
    const result = await api?.applyPerformanceFix(instanceId, finding.fix)
    setFixing(null)
    notify({ type: result?.ok ? 'success' : 'error', title: 'FPS-Doktor', message: result?.message ?? 'Fehlgeschlagen.' })
    if (result?.ok && finding.fix.kind === 'set-ram') onRamChanged((finding.fix as { ram?: number }).ram ?? 4096)
    if (result?.ok) check()
  }

  return (
    <section>
      <div className="flex items-baseline justify-between mb-2 px-0.5">
        <h2 className="flex items-center gap-2 text-[13px] font-semibold text-crystal-text">
          <Gauge size={14} strokeWidth={1.75} className="text-crystal-muted" /> FPS-Doktor
        </h2>
        <button onClick={check} disabled={checking} className="text-xs text-crystal-muted hover:text-crystal-text disabled:opacity-60">
          {checking ? 'Prüfe…' : findings ? 'Neu prüfen' : 'Instanz prüfen'}
        </button>
      </div>
      {findings === null ? (
        <p className="px-0.5 text-xs text-crystal-muted">
          Sucht in dieser Instanz nach Mods, die doppelt arbeiten, schweren Mods, teuren Nexora-Modulen und falschem Arbeitsspeicher.
        </p>
      ) : findings.length === 0 ? (
        <div className="crystal-card flex items-center gap-2.5 px-4 py-3 text-[13px] text-crystal-text">
          <CheckCircle2 size={15} className="text-crystal-success shrink-0" /> Nichts gefunden, was spürbar FPS kostet.
        </div>
      ) : (
        <ul className="crystal-card divide-y divide-crystal-border">
          {findings.map(f => (
            <li key={f.id} className="flex items-start gap-3 px-4 py-3">
              <span className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${DOT[f.level]}`} aria-hidden="true" />
              <div className="min-w-0 flex-1">
                <p className="text-[13px] text-crystal-text">{f.title}</p>
                <p className="mt-0.5 text-xs text-crystal-muted">{f.detail}</p>
              </div>
              {f.fix && (
                <button
                  onClick={() => apply(f)}
                  disabled={!!fixing || (running && f.fix.kind !== 'set-ram')}
                  title={running && f.fix.kind !== 'set-ram' ? 'Geht erst, wenn die Instanz geschlossen ist.' : undefined}
                  className="crystal-btn-ghost border border-crystal-border text-xs px-2.5 py-1 text-crystal-text shrink-0 disabled:opacity-50"
                >
                  {fixing === f.id ? '…' : f.fix.label}
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
