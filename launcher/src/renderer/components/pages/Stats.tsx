import React, { useEffect, useState } from 'react'
import { Clock, Gamepad2, Flame, Timer, type LucideIcon } from 'lucide-react'
import { Page, PageHeader, EmptyState } from '../ui/Page'

const api = (window as any).crystal

interface Ranked { name: string; ms: number }

interface StatsSummary {
  playtimeMs: number
  sessions: number
  longestSessionMs: number
  streakDays: number
  days: { date: string; ms: number }[]
  byVersion: Ranked[]
  byServer: Ranked[]
  byInstance: Ranked[]
  trackedSince: number | null
}

/** "3 Std. 20 Min.", "45 Min.", "< 1 Min." */
function duration(ms: number): string {
  const minutes = Math.floor(ms / 60_000)
  if (minutes < 1) return ms > 0 ? '< 1 Min.' : '0 Min.'
  const hours = Math.floor(minutes / 60)
  if (hours === 0) return `${minutes} Min.`
  const rest = minutes % 60
  return rest ? `${hours} Std. ${rest} Min.` : `${hours} Std.`
}

function shortDate(iso: string): string {
  const [, m, d] = iso.split('-')
  return `${Number(d)}.${Number(m)}.`
}

/** Playtime over the last 30 days, by version, server and instance. */
export function Stats() {
  const [stats, setStats] = useState<StatsSummary | null>(null)

  useEffect(() => {
    api?.getStatsSummary?.().then((s: StatsSummary) => s && setStats(s))
  }, [])

  const hasHistory = !!stats && stats.trackedSince !== null

  return (
    <Page wide>
      <PageHeader
        title="Statistik"
        description={stats?.trackedSince
          ? `Deine Spielzeit mit Nexora, aufgezeichnet seit ${new Date(stats.trackedSince).toLocaleDateString('de-DE')}.`
          : 'Deine Spielzeit mit Nexora.'}
      />

      {stats === null && <div className="h-40 rounded-[10px] bg-crystal-card animate-pulse" />}

      {stats && (
        <div className="space-y-4">
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
            <Tile icon={Clock} label="Spielzeit gesamt" value={duration(stats.playtimeMs)} />
            <Tile icon={Gamepad2} label="Sitzungen" value={stats.sessions.toLocaleString('de-DE')} />
            <Tile icon={Timer} label="Längste Sitzung" value={hasHistory ? duration(stats.longestSessionMs) : '–'} />
            <Tile icon={Flame} label="Tage am Stück" value={hasHistory ? String(stats.streakDays) : '–'} />
          </div>

          {!hasHistory ? (
            <EmptyState title="Noch keine Sitzungen aufgezeichnet">
              <p className="text-xs text-crystal-muted">Ab deiner nächsten Runde zeigt Nexora hier, wann, mit welcher Version und auf welchen Servern du gespielt hast.</p>
            </EmptyState>
          ) : (
            <>
              <DayChart days={stats.days} />
              <div className="grid lg:grid-cols-3 gap-3">
                <RankList title="Server" items={stats.byServer} empty="Noch auf keinem Server gespielt." />
                <RankList title="Minecraft-Version" items={stats.byVersion} empty="–" />
                <RankList title="Instanz" items={stats.byInstance} empty="–" />
              </div>
            </>
          )}
        </div>
      )}
    </Page>
  )
}

function Tile({ icon: Icon, label, value }: { icon: LucideIcon; label: string; value: string }) {
  return (
    <div className="crystal-card px-4 py-3">
      <p className="flex items-center gap-1.5 text-xs text-crystal-muted"><Icon size={13} /> {label}</p>
      <p className="mt-1 text-xl font-semibold text-crystal-text tabular">{value}</p>
    </div>
  )
}

/** Playtime per day as bars; hovering a day shows its date and time. */
function DayChart({ days }: { days: StatsSummary['days'] }) {
  const [hover, setHover] = useState<number | null>(null)
  const max = Math.max(...days.map(d => d.ms), 1)
  const total = days.reduce((sum, d) => sum + d.ms, 0)
  const active = hover === null ? null : days[hover]

  return (
    <div className="crystal-card p-4">
      <div className="flex items-baseline justify-between mb-3">
        <h2 className="text-[13px] font-semibold text-crystal-text">Letzte 30 Tage</h2>
        <p className="text-xs text-crystal-muted tabular">
          {active ? `${shortDate(active.date)}: ${duration(active.ms)}` : `Zusammen ${duration(total)}`}
        </p>
      </div>
      <div className="relative h-36" role="img" aria-label={`Spielzeit pro Tag, zusammen ${duration(total)}`}>
        {/* Recessive guides at half and full height. */}
        <div className="absolute inset-x-0 top-0 border-t border-crystal-border/60" />
        <div className="absolute inset-x-0 top-1/2 border-t border-dashed border-crystal-border/40" />
        <div className="absolute inset-0 flex items-end gap-[2px]" onMouseLeave={() => setHover(null)}>
          {days.map((d, i) => (
            <div
              key={d.date}
              className="relative flex-1 h-full flex items-end cursor-default"
              onMouseEnter={() => setHover(i)}
            >
              <div
                className={`w-full rounded-t-[4px] transition-colors ${
                  d.ms === 0 ? 'bg-crystal-border/50' : hover === i ? 'bg-crystal-accent' : 'bg-crystal-accent/70'
                }`}
                style={{ height: d.ms === 0 ? 2 : `${Math.max(4, (d.ms / max) * 100)}%` }}
              />
            </div>
          ))}
        </div>
      </div>
      <div className="mt-1.5 flex justify-between text-[11px] text-crystal-muted tabular">
        <span>{shortDate(days[0].date)}</span>
        <span>Max. {duration(max)}</span>
        <span>Heute</span>
      </div>
      <table className="sr-only">
        <caption>Spielzeit pro Tag</caption>
        <tbody>{days.map(d => <tr key={d.date}><td>{d.date}</td><td>{duration(d.ms)}</td></tr>)}</tbody>
      </table>
    </div>
  )
}

function RankList({ title, items, empty }: { title: string; items: Ranked[]; empty: string }) {
  const max = Math.max(...items.map(i => i.ms), 1)
  return (
    <div className="crystal-card p-4">
      <h2 className="text-[13px] font-semibold text-crystal-text mb-3">{title}</h2>
      {items.length === 0 && <p className="text-xs text-crystal-muted">{empty}</p>}
      <ul className="space-y-2.5">
        {items.map(item => (
          <li key={item.name}>
            <div className="flex items-baseline justify-between gap-2 text-xs">
              <span className="truncate text-crystal-text">{item.name}</span>
              <span className="shrink-0 text-crystal-muted tabular">{duration(item.ms)}</span>
            </div>
            <div className="mt-1 h-1.5 rounded-full bg-crystal-border/40">
              <div className="h-full rounded-full bg-crystal-accent/80" style={{ width: `${Math.max(3, (item.ms / max) * 100)}%` }} />
            </div>
          </li>
        ))}
      </ul>
    </div>
  )
}
