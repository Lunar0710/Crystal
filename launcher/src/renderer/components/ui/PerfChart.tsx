import React, { useMemo, useState } from 'react'

export interface PerfPoint {
  start: number
  instanceName: string
  version: string
  perf: { avgFps?: number; lowFps?: number; nexora?: string; cpuAvg?: number; ramAvgMb?: number; ramPeakMb?: number }
}

const W = 640
const H = 180
const PAD = { top: 12, right: 12, bottom: 22, left: 36 }

function niceMax(v: number): number {
  const step = v > 400 ? 100 : v > 150 ? 50 : 20
  return Math.max(step, Math.ceil(v / step) * step)
}

/**
 * Frame rate per session: the average as a solid line in the accent colour,
 * the 1% low dashed in grey, so the two differ by more than colour. A thin
 * rule marks where the Nexora version changed, which is what shows whether an
 * update cost frames. Hovering a session shows all of its numbers.
 */
export function PerfChart({ points }: { points: PerfPoint[] }) {
  const [hover, setHover] = useState<number | null>(null)
  const max = niceMax(Math.max(...points.map(p => p.perf.avgFps ?? 0), 1))
  const innerW = W - PAD.left - PAD.right
  const innerH = H - PAD.top - PAD.bottom
  const x = (i: number) => PAD.left + (points.length === 1 ? innerW / 2 : (i / (points.length - 1)) * innerW)
  const y = (fps: number) => PAD.top + innerH - (fps / max) * innerH

  const line = (key: 'avgFps' | 'lowFps') => points
    .map((p, i) => p.perf[key] === undefined ? null : `${x(i).toFixed(1)},${y(p.perf[key]!).toFixed(1)}`)
    .filter(Boolean)
    .join(' ')

  // Where the Nexora version differs from the session before.
  const versionChanges = useMemo(() => points
    .map((p, i) => ({ i, v: p.perf.nexora }))
    .filter(({ i, v }) => i > 0 && v && points[i - 1].perf.nexora && v !== points[i - 1].perf.nexora), [points])

  const active = hover === null ? null : points[hover]

  function onMove(e: React.MouseEvent<SVGSVGElement>) {
    const box = e.currentTarget.getBoundingClientRect()
    const px = ((e.clientX - box.left) / box.width) * W
    const i = points.length === 1 ? 0 : Math.round(((px - PAD.left) / innerW) * (points.length - 1))
    setHover(Math.max(0, Math.min(points.length - 1, i)))
  }

  return (
    <div className="crystal-card p-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2 mb-3">
        <h2 className="text-[13px] font-semibold text-crystal-text">Leistung pro Sitzung</h2>
        <div className="flex items-center gap-4 text-xs text-crystal-muted">
          <span className="flex items-center gap-1.5">
            <svg width="16" height="4" aria-hidden="true"><line x1="0" y1="2" x2="16" y2="2" className="stroke-crystal-accent" strokeWidth="2" strokeLinecap="round" /></svg>
            Durchschnitt
          </span>
          <span className="flex items-center gap-1.5">
            <svg width="16" height="4" aria-hidden="true"><line x1="0" y1="2" x2="16" y2="2" className="stroke-crystal-muted" strokeWidth="2" strokeDasharray="3 3" /></svg>
            1%-Low
          </span>
        </div>
      </div>

      <div className="relative">
        <svg
          viewBox={`0 0 ${W} ${H}`}
          className="w-full h-auto overflow-visible"
          role="img"
          aria-label={`FPS der letzten ${points.length} Sitzungen`}
          onMouseMove={onMove}
          onMouseLeave={() => setHover(null)}
        >
          {[0, 0.5, 1].map(f => (
            <g key={f}>
              <line x1={PAD.left} x2={W - PAD.right} y1={y(max * f)} y2={y(max * f)}
                className="stroke-crystal-border" strokeWidth="1" strokeDasharray={f === 0 ? undefined : '2 4'} />
              <text x={PAD.left - 6} y={y(max * f) + 3} textAnchor="end" className="fill-crystal-muted text-[10px] tabular">
                {Math.round(max * f)}
              </text>
            </g>
          ))}

          {versionChanges.map(({ i, v }) => (
            <g key={i}>
              <line x1={x(i - 0.5)} x2={x(i - 0.5)} y1={PAD.top} y2={PAD.top + innerH} className="stroke-crystal-muted" strokeWidth="1" opacity="0.5" />
              <text x={x(i - 0.5) + 4} y={PAD.top + 9} className="fill-crystal-muted text-[10px]">{v}</text>
            </g>
          ))}

          <polyline points={line('lowFps')} fill="none" className="stroke-crystal-muted" strokeWidth="2" strokeDasharray="4 4" strokeLinejoin="round" />
          <polyline points={line('avgFps')} fill="none" className="stroke-crystal-accent" strokeWidth="2" strokeLinejoin="round" strokeLinecap="round" />

          {hover !== null && (
            <line x1={x(hover)} x2={x(hover)} y1={PAD.top} y2={PAD.top + innerH} className="stroke-crystal-text" strokeWidth="1" opacity="0.3" />
          )}
          {points.map((p, i) => p.perf.avgFps !== undefined && (
            <circle key={i} cx={x(i)} cy={y(p.perf.avgFps)} r={hover === i ? 4.5 : 3}
              className="fill-crystal-accent stroke-crystal-card" strokeWidth="2" />
          ))}

          <text x={PAD.left} y={H - 4} className="fill-crystal-muted text-[10px]">
            {new Date(points[0].start).toLocaleDateString('de-DE')}
          </text>
          <text x={W - PAD.right} y={H - 4} textAnchor="end" className="fill-crystal-muted text-[10px]">Letzte Sitzung</text>
        </svg>

        {active && hover !== null && (
          <div
            className="pointer-events-none absolute top-0 z-10 w-52 rounded-lg border border-crystal-border bg-crystal-panel px-3 py-2 text-xs shadow-lg"
            style={{ left: `${Math.min(70, Math.max(0, (x(hover) / W) * 100 - 15))}%` }}
          >
            <p className="text-crystal-text font-medium truncate">{active.instanceName || 'Instanz'}</p>
            <p className="text-crystal-muted tabular">
              {new Date(active.start).toLocaleString('de-DE', { dateStyle: 'short', timeStyle: 'short' })}
              {active.perf.nexora ? `, Nexora ${active.perf.nexora}` : ''}
            </p>
            <dl className="mt-1.5 grid grid-cols-2 gap-x-3 gap-y-0.5 tabular">
              <dt className="text-crystal-muted">Durchschnitt</dt><dd className="text-crystal-text text-right">{active.perf.avgFps ?? '–'} FPS</dd>
              <dt className="text-crystal-muted">1%-Low</dt><dd className="text-crystal-text text-right">{active.perf.lowFps ?? '–'} FPS</dd>
              {active.perf.cpuAvg !== undefined && <><dt className="text-crystal-muted">CPU</dt><dd className="text-crystal-text text-right">{Math.round(active.perf.cpuAvg)} %</dd></>}
              {active.perf.ramAvgMb !== undefined && <><dt className="text-crystal-muted">RAM</dt><dd className="text-crystal-text text-right">{(active.perf.ramAvgMb / 1024).toFixed(1)} GB</dd></>}
            </dl>
          </div>
        )}
      </div>

      <table className="sr-only">
        <caption>FPS pro Sitzung</caption>
        <thead><tr><th>Beginn</th><th>Instanz</th><th>Nexora</th><th>Durchschnitt</th><th>1%-Low</th></tr></thead>
        <tbody>
          {points.map(p => (
            <tr key={p.start}>
              <td>{new Date(p.start).toLocaleString('de-DE')}</td><td>{p.instanceName}</td><td>{p.perf.nexora ?? ''}</td>
              <td>{p.perf.avgFps ?? ''}</td><td>{p.perf.lowFps ?? ''}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
