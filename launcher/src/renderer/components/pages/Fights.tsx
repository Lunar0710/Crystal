import React, { useEffect, useMemo, useRef, useState } from 'react'
import { Swords, Play, Pause } from 'lucide-react'
import { Page, PageHeader, EmptyState } from '../ui/Page'

const api = (window as any).crystal

interface FightSummary {
  instanceId: string
  instanceName: string
  file: string
  start: number
  opponent: string
  won: boolean
  hits: number
  swings: number
  longestCombo: number
  hitsTaken: number
  durationMs: number
  server: string
}

/** me x,y,z,yaw,hp, opponent x,y,z,yaw,hp, flags (1 hit landed, 2 hit taken). One per tick. */
type Frame = number[]

interface Fight extends FightSummary {
  frames: Frame[]
}

const TICKS_PER_SECOND = 20

function seconds(ms: number): string {
  const s = Math.round(ms / 1000)
  return s >= 60 ? `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')} min` : `${s} s`
}

function accuracy(f: FightSummary): number {
  return f.swings === 0 ? 0 : Math.round((100 * f.hits) / f.swings)
}

/** The recorded fights, and a replay of one seen from above. */
export function Fights() {
  const [list, setList] = useState<FightSummary[] | null>(null)
  const [selected, setSelected] = useState<FightSummary | null>(null)
  const [fight, setFight] = useState<Fight | null>(null)

  useEffect(() => {
    api?.listFights?.().then((l: FightSummary[]) => {
      setList(l || [])
      if (l?.length) setSelected(l[0])
    })
  }, [])

  useEffect(() => {
    if (!selected) return
    setFight(null)
    api?.readFight(selected.instanceId, selected.file).then((f: Fight | null) => {
      if (f && Array.isArray(f.frames)) setFight({ ...selected, frames: f.frames })
    })
  }, [selected])

  return (
    <Page wide>
      <PageHeader title="Kämpfe" description="Jeder Kampf mit Nexora wird aufgezeichnet. Hier siehst du ihn von oben noch einmal, mit Treffern und Lebensverlauf." />
      {list === null && <div className="h-40 rounded-[10px] bg-crystal-card animate-pulse" />}
      {list && list.length === 0 && (
        <EmptyState icon={<Swords size={22} strokeWidth={1.75} />} title="Noch keine Kämpfe">
          <p className="text-xs text-crystal-muted">Sobald du mit Nexora 1.6 jemanden angreifst, landet die Runde hier.</p>
        </EmptyState>
      )}
      {list && list.length > 0 && (() => {
        // The record over every fight on this PC.
        const won = list.filter(f => f.won).length
        const hits = list.reduce((n, f) => n + f.hits, 0), swings = list.reduce((n, f) => n + f.swings, 0)
        return (
          <dl className="grid grid-cols-3 gap-3 mb-4">
            <div className="crystal-card px-4 py-3"><dt className="text-xs text-crystal-muted">Kämpfe</dt><dd className="mt-1 text-xl font-semibold text-crystal-text tabular">{list.length}</dd></div>
            <div className="crystal-card px-4 py-3"><dt className="text-xs text-crystal-muted">Siege</dt><dd className="mt-1 text-xl font-semibold text-crystal-text tabular">{won} <span className="text-sm font-normal text-crystal-muted">({Math.round((100 * won) / list.length)} %)</span></dd></div>
            <div className="crystal-card px-4 py-3"><dt className="text-xs text-crystal-muted">Trefferquote</dt><dd className="mt-1 text-xl font-semibold text-crystal-text tabular">{swings ? Math.round((100 * hits) / swings) : 0} %</dd></div>
          </dl>
        )
      })()}
      {list && list.length > 0 && (
        <div className="grid gap-4 lg:grid-cols-[260px_1fr]">
          <ul className="crystal-card divide-y divide-crystal-border self-start max-h-[70vh] overflow-y-auto">
            {list.map(f => {
              const active = selected?.file === f.file && selected?.instanceId === f.instanceId
              return (
                <li key={f.instanceId + f.file}>
                  <button
                    onClick={() => setSelected(f)}
                    aria-current={active}
                    className={`w-full px-3 py-2.5 text-left transition-colors ${active ? 'bg-crystal-panel' : 'hover:bg-crystal-panel/60'}`}
                  >
                    <p className="text-[13px] text-crystal-text truncate">
                      <span className={f.won ? 'text-crystal-success' : 'text-crystal-muted'}>{f.won ? 'Sieg' : 'Kampf'}</span> gegen {f.opponent}
                    </p>
                    <p className="text-xs text-crystal-muted tabular truncate">
                      {new Date(f.start).toLocaleString('de-DE', { dateStyle: 'short', timeStyle: 'short' })}, {f.hits}/{f.swings} Treffer
                    </p>
                  </button>
                </li>
              )
            })}
          </ul>
          <div className="min-w-0">
            {selected && !fight && <div className="h-80 rounded-[10px] bg-crystal-card animate-pulse" />}
            {fight && <Replay fight={fight} />}
          </div>
        </div>
      )}
    </Page>
  )
}

function Replay({ fight }: { fight: Fight }) {
  const frames = fight.frames
  const [tick, setTick] = useState(0)
  const [playing, setPlaying] = useState(true)
  const [speed, setSpeed] = useState(1)
  const last = useRef(0)

  useEffect(() => { setTick(0); setPlaying(true) }, [fight])

  // Advances through the ticks at 20 per second times the chosen speed.
  useEffect(() => {
    if (!playing) return
    let raf = 0
    last.current = performance.now()
    const step = (now: number) => {
      // The frame's timestamp is when the frame began, which can be a little
      // before the performance.now() taken when playing started.
      const dt = Math.max(0, (now - last.current) / 1000)
      last.current = now
      setTick(t => {
        const next = t + dt * TICKS_PER_SECOND * speed
        if (next >= frames.length - 1) { setPlaying(false); return frames.length - 1 }
        return next
      })
      raf = requestAnimationFrame(step)
    }
    raf = requestAnimationFrame(step)
    return () => cancelAnimationFrame(raf)
  }, [playing, speed, frames.length])

  // Arena bounds from both paths, with a margin, kept square so distances look right.
  const view = useMemo(() => {
    let minX = Infinity, maxX = -Infinity, minZ = Infinity, maxZ = -Infinity
    for (const f of frames) {
      for (const [x, z] of [[f[0], f[2]], [f[5], f[7]]]) {
        minX = Math.min(minX, x); maxX = Math.max(maxX, x); minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z)
      }
    }
    const size = Math.max(maxX - minX, maxZ - minZ, 8) + 4
    const cx = (minX + maxX) / 2, cz = (minZ + maxZ) / 2
    return { x0: cx - size / 2, z0: cz - size / 2, size }
  }, [frames])

  const W = 400
  const px = (x: number) => ((x - view.x0) / view.size) * W
  const pz = (z: number) => ((z - view.z0) / view.size) * W
  const path = (xi: number, zi: number) => frames.map(f => `${px(f[xi]).toFixed(1)},${pz(f[zi]).toFixed(1)}`).join(' ')

  const i = Number.isFinite(tick) ? Math.max(0, Math.min(frames.length - 1, Math.floor(tick))) : 0
  const now = frames[i]
  const facing = (x: number, z: number, yaw: number) => {
    const r = (yaw * Math.PI) / 180
    return { x2: px(x) + -Math.sin(r) * 14, y2: pz(z) + Math.cos(r) * 14 }
  }
  const me = facing(now[0], now[2], now[3])
  const op = facing(now[5], now[7], now[8])
  const maxHp = Math.max(20, ...frames.map(f => Math.max(f[4], f[9])))

  const H = 70
  const hpLine = (idx: number) => frames.map((f, t) =>
    `${((t / Math.max(1, frames.length - 1)) * W).toFixed(1)},${(H - (f[idx] / maxHp) * H).toFixed(1)}`).join(' ')

  return (
    <div className="crystal-card p-4 space-y-3">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-[15px] font-semibold text-crystal-text">
          {fight.won ? 'Sieg' : 'Kampf'} gegen {fight.opponent}
        </h2>
        <p className="text-xs text-crystal-muted">
          {new Date(fight.start).toLocaleString('de-DE', { dateStyle: 'medium', timeStyle: 'short' })} auf {fight.server}, {fight.instanceName}
        </p>
      </div>
      <dl className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
        <div><dt className="text-crystal-muted">Treffer</dt><dd className="text-crystal-text tabular">{fight.hits}/{fight.swings} ({accuracy(fight)} %)</dd></div>
        <div><dt className="text-crystal-muted">Beste Combo</dt><dd className="text-crystal-text tabular">{fight.longestCombo}</dd></div>
        <div><dt className="text-crystal-muted">Getroffen worden</dt><dd className="text-crystal-text tabular">{fight.hitsTaken}×</dd></div>
        <div><dt className="text-crystal-muted">Dauer</dt><dd className="text-crystal-text tabular">{seconds(fight.durationMs)}</dd></div>
      </dl>

      <svg viewBox={`0 0 ${W} ${W}`} className="w-full max-w-[480px] mx-auto block rounded-lg bg-crystal-bg" role="img"
        aria-label={`Draufsicht des Kampfes gegen ${fight.opponent}`}>
        <polyline points={path(5, 7)} fill="none" className="stroke-crystal-muted" strokeWidth="1.5" strokeDasharray="3 3" opacity="0.6" />
        <polyline points={path(0, 2)} fill="none" className="stroke-crystal-accent" strokeWidth="1.5" opacity="0.6" />
        {frames.map((f, t) => (f[10] & 1) ? (
          <circle key={'h' + t} cx={px(f[5])} cy={pz(f[7])} r="3" className="fill-crystal-accent" opacity={t <= i ? 0.9 : 0.25} />
        ) : null)}
        {frames.map((f, t) => (f[10] & 2) ? (
          <circle key={'t' + t} cx={px(f[0])} cy={pz(f[2])} r="3" className="fill-crystal-danger" opacity={t <= i ? 0.9 : 0.25} />
        ) : null)}
        <line x1={px(now[5])} y1={pz(now[7])} {...op} className="stroke-crystal-muted" strokeWidth="2" />
        <circle cx={px(now[5])} cy={pz(now[7])} r="6" className="fill-crystal-card stroke-crystal-muted" strokeWidth="2" />
        <line x1={px(now[0])} y1={pz(now[2])} {...me} className="stroke-crystal-accent" strokeWidth="2" />
        <circle cx={px(now[0])} cy={pz(now[2])} r="6" className="fill-crystal-accent stroke-crystal-card" strokeWidth="2" />
      </svg>

      <div className="flex flex-wrap items-center justify-center gap-4 text-xs text-crystal-muted">
        <span className="flex items-center gap-1.5"><span className="h-2.5 w-2.5 rounded-full bg-crystal-accent" /> Du</span>
        <span className="flex items-center gap-1.5"><span className="h-2.5 w-2.5 rounded-full border-2 border-crystal-muted" /> {fight.opponent}</span>
        <span className="flex items-center gap-1.5"><span className="h-2 w-2 rounded-full bg-crystal-accent" /> dein Treffer</span>
        <span className="flex items-center gap-1.5"><span className="h-2 w-2 rounded-full bg-crystal-danger" /> du getroffen</span>
      </div>

      <div>
        <div className="flex justify-between text-xs text-crystal-muted mb-1">
          <span>Leben</span>
          <span className="tabular">Du {now[4].toFixed(1)}, {fight.opponent} {now[9].toFixed(1)}</span>
        </div>
        <svg viewBox={`0 0 ${W} ${H}`} className="w-full h-16" preserveAspectRatio="none" aria-hidden="true">
          <polyline points={hpLine(9)} fill="none" className="stroke-crystal-muted" strokeWidth="1.5" strokeDasharray="3 3" vectorEffect="non-scaling-stroke" />
          <polyline points={hpLine(4)} fill="none" className="stroke-crystal-accent" strokeWidth="1.5" vectorEffect="non-scaling-stroke" />
          <line x1={(i / Math.max(1, frames.length - 1)) * W} x2={(i / Math.max(1, frames.length - 1)) * W} y1="0" y2={H}
            className="stroke-crystal-text" strokeWidth="1" opacity="0.4" vectorEffect="non-scaling-stroke" />
        </svg>
      </div>

      <div className="flex items-center gap-3">
        <button
          onClick={() => { if (i >= frames.length - 1) setTick(0); setPlaying(p => !p) }}
          aria-label={playing ? 'Anhalten' : 'Abspielen'}
          className="crystal-btn-ghost border border-crystal-border px-2.5 py-1.5 text-crystal-text"
        >
          {playing ? <Pause size={14} /> : <Play size={14} />}
        </button>
        <input
          type="range" min={0} max={frames.length - 1} step={1} value={i}
          onChange={e => { setPlaying(false); setTick(Number(e.target.value)) }}
          aria-label="Zeitpunkt im Kampf"
          className="flex-1 accent-[rgb(var(--c-accent))]"
        />
        <span className="text-xs text-crystal-muted tabular w-14 text-right">{seconds((i / TICKS_PER_SECOND) * 1000)}</span>
        <select value={speed} onChange={e => setSpeed(Number(e.target.value))} aria-label="Tempo"
          className="crystal-input py-1 text-xs">
          <option value={0.25}>0,25×</option>
          <option value={0.5}>0,5×</option>
          <option value={1}>1×</option>
          <option value={2}>2×</option>
        </select>
      </div>
    </div>
  )
}
