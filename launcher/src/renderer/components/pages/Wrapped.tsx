import React, { useEffect, useMemo, useRef, useState } from 'react'
import { ImageDown, Sparkles } from 'lucide-react'
import { notify } from '../../store/notificationStore'
import { Page, PageHeader, EmptyState } from '../ui/Page'

const api = (window as any).crystal

interface Session { start: number; end: number; version: string; instanceName: string; servers: Record<string, number> }
interface FightSummary { start: number; opponent: string; won: boolean; hits: number; swings: number; longestCombo: number }

const PERIODS = { week: { label: 'Woche', days: 7 }, month: { label: 'Monat', days: 30 } } as const
type Period = keyof typeof PERIODS

function hours(ms: number): string {
  const h = ms / 3600000
  return h >= 10 ? `${Math.round(h)} h` : h >= 1 ? `${h.toFixed(1).replace('.', ',')} h` : `${Math.round(ms / 60000)} min`
}

function top(map: Map<string, number>): [string, number] | null {
  let best: [string, number] | null = null
  for (const e of map) if (!best || e[1] > best[1]) best = e
  return best
}

/**
 * The week or month in Nexora at a glance: playtime, favourite server and
 * version, fights with win rate, accuracy, best combo and the rival. One card,
 * saved as a picture to share.
 */
export function Wrapped() {
  const [period, setPeriod] = useState<Period>('week')
  const [sessions, setSessions] = useState<Session[] | null>(null)
  const [fights, setFights] = useState<FightSummary[]>([])
  const card = useRef<HTMLDivElement>(null)
  const [profile, setProfile] = useState<string>('')

  useEffect(() => {
    api?.getProfile?.().then((p: { username: string } | null) => setProfile(p?.username ?? ''))
    api?.listFights?.().then((l: FightSummary[]) => setFights(l || []))
  }, [])
  useEffect(() => {
    setSessions(null)
    api?.statsSessionsSince?.(Date.now() - PERIODS[period].days * 86400000).then((s: Session[]) => setSessions(s || []))
  }, [period])

  const since = Date.now() - PERIODS[period].days * 86400000
  const data = useMemo(() => {
    const list = sessions ?? []
    const playMs = list.reduce((n, s) => n + Math.max(0, s.end - s.start), 0)
    const days = new Set(list.map(s => new Date(s.start).toDateString())).size
    const servers = new Map<string, number>(), versions = new Map<string, number>()
    for (const s of list) {
      for (const [name, ms] of Object.entries(s.servers || {})) servers.set(name, (servers.get(name) ?? 0) + ms)
      versions.set(s.version, (versions.get(s.version) ?? 0) + (s.end - s.start))
    }
    const f = fights.filter(x => x.start >= since)
    const won = f.filter(x => x.won).length
    const hits = f.reduce((n, x) => n + x.hits, 0), swings = f.reduce((n, x) => n + x.swings, 0)
    const combo = f.reduce((n, x) => Math.max(n, x.longestCombo || 0), 0)
    const rivals = new Map<string, { n: number; won: number; name: string }>()
    for (const x of f) {
      const k = x.opponent.toLowerCase()
      const r = rivals.get(k) ?? { n: 0, won: 0, name: x.opponent }
      r.n++; if (x.won) r.won++
      rivals.set(k, r)
    }
    const rival = [...rivals.values()].sort((a, b) => b.n - a.n)[0] ?? null
    return {
      playMs, sessions: list.length, days, server: top(servers), version: top(versions),
      fights: f.length, won, rate: f.length ? Math.round((100 * won) / f.length) : 0,
      accuracy: swings ? Math.round((100 * hits) / swings) : 0, combo, rival,
    }
  }, [sessions, fights, since])

  async function saveImage() {
    const box = card.current?.getBoundingClientRect()
    if (!box) return
    const r = await api?.saveFightImage({ x: box.left, y: box.top, width: box.width, height: box.height }, `Nexora Rückblick ${PERIODS[period].label}`)
    if (r?.message) notify({ type: r.ok ? 'success' : 'error', title: 'Rückblick', message: r.message })
  }

  const empty = sessions !== null && data.sessions === 0 && data.fights === 0
  const range = `${new Date(since).toLocaleDateString('de-DE', { day: 'numeric', month: 'short' })} bis heute`

  return (
    <Page>
      <PageHeader
        title="Rückblick"
        description="Deine Woche oder dein Monat mit Nexora, auf einer Karte zum Teilen."
        actions={
          <div className="flex items-center gap-2">
            <div className="flex rounded-full p-0.5 bg-white/[0.04] ring-1 ring-inset ring-white/[0.08]">
              {(Object.keys(PERIODS) as Period[]).map(p => (
                <button key={p} onClick={() => setPeriod(p)}
                  className={`px-3.5 py-1.5 rounded-full text-[12.5px] nexora-ease ${period === p ? 'bg-white/[0.1] text-crystal-text' : 'text-crystal-muted hover:text-crystal-text'}`}>
                  {PERIODS[p].label}
                </button>
              ))}
            </div>
            {!empty && sessions && <button onClick={saveImage} className="crystal-btn-ghost text-[13px]"><ImageDown size={14} /> Als Bild speichern</button>}
          </div>
        }
      />

      {sessions === null && <div className="h-96 rounded-[18px] bg-crystal-card animate-pulse" />}
      {empty && (
        <EmptyState icon={<Sparkles size={22} strokeWidth={1.75} />} title="Noch nichts in diesem Zeitraum">
          Spiel ein paar Runden mit Nexora, dann steht hier dein Rückblick.
        </EmptyState>
      )}

      {sessions !== null && !empty && (
        <div ref={card} className="relative overflow-hidden rounded-[22px] p-7 bg-[#0b0b0c] ring-1 ring-white/[0.08]"
          style={{ backgroundImage: 'radial-gradient(80% 60% at 0% 0%, rgb(var(--c-accent) / 0.06), transparent 60%)' }}>
          <div className="flex items-baseline justify-between gap-4">
            <p className="nexora-display text-[13px] tracking-wide text-crystal-muted">Nexora Rückblick</p>
            <p className="text-[12px] text-crystal-muted">{profile && <><span className="text-crystal-text">{profile}</span>, </>}{range}</p>
          </div>

          <p className="nexora-display text-[64px] leading-none text-crystal-text mt-6 tabular">{hours(data.playMs)}</p>
          <p className="text-[14px] text-crystal-muted mt-2">
            gespielt in {data.sessions} {data.sessions === 1 ? 'Sitzung' : 'Sitzungen'}, an {data.days} {data.days === 1 ? 'Tag' : 'Tagen'}
          </p>

          <div className="grid grid-cols-2 gap-3 mt-7">
            <Stat label="Lieblings-Server" value={data.server ? data.server[0] : '—'} sub={data.server ? hours(data.server[1]) : undefined} />
            <Stat label="Lieblings-Version" value={data.version ? data.version[0] : '—'} sub={data.version ? hours(data.version[1]) : undefined} />
          </div>

          {data.fights > 0 && (
            <>
              <p className="nexora-display text-[15px] text-crystal-text mt-8 mb-3">Kämpfe</p>
              <div className="grid grid-cols-4 gap-3">
                <Stat label="Kämpfe" value={String(data.fights)} />
                <Stat label="Siege" value={`${data.rate} %`} sub={`${data.won} gewonnen`} />
                <Stat label="Trefferquote" value={`${data.accuracy} %`} />
                <Stat label="Beste Combo" value={String(data.combo)} />
              </div>
              {data.rival && (
                <div className="mt-3 rounded-[14px] px-4 py-3 bg-white/[0.03] ring-1 ring-inset ring-white/[0.06] flex items-center justify-between">
                  <p className="text-[13px] text-crystal-muted">Dein Rivale <span className="text-crystal-text font-medium">{data.rival.name}</span></p>
                  <p className="nexora-display text-[20px] text-crystal-text tabular">{data.rival.won}:{data.rival.n - data.rival.won}</p>
                </div>
              )}
            </>
          )}
          <p className="text-[11px] text-crystal-muted/70 mt-7">nexora · lunar0710.github.io/Crystal</p>
        </div>
      )}
    </Page>
  )
}

function Stat({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <div className="rounded-[14px] px-4 py-3 bg-white/[0.03] ring-1 ring-inset ring-white/[0.06] min-w-0">
      <p className="text-[11px] text-crystal-muted">{label}</p>
      <p className="nexora-display text-[20px] text-crystal-text mt-0.5 truncate tabular">{value}</p>
      {sub && <p className="text-[11px] text-crystal-muted">{sub}</p>}
    </div>
  )
}
