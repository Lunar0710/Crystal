import React, { useEffect, useState } from 'react'
import { Cpu, MemoryStick, Clock, X } from 'lucide-react'

export interface RunningGame {
  instanceId: string
  instanceName: string
  version: string
  username: string
  accountUuid: string
  pid: number
  startedAt: number
  maxRamMb: number
  usage: { cpuPercent: number; ramMb: number } | null
}

const api = (window as any).crystal

/** The games the launcher has running, kept up to date from the main process. */
export function useRunningGames(): RunningGame[] {
  const [games, setGames] = useState<RunningGame[]>([])
  useEffect(() => {
    api?.listRunningGames?.().then((list: RunningGame[]) => setGames(list || []))
    return api?.on('games:update', (list: RunningGame[]) => setGames(list || []))
  }, [])
  return games
}

function gb(mb: number): string {
  return (mb / 1024).toLocaleString('de-DE', { minimumFractionDigits: 1, maximumFractionDigits: 1 }) + ' GB'
}

function duration(ms: number): string {
  const minutes = Math.floor(ms / 60000)
  if (minutes < 1) return 'gerade gestartet'
  const h = Math.floor(minutes / 60)
  return h > 0 ? `${h} h ${minutes % 60} min` : `${minutes} min`
}

/** A thin bar with the value on the right; with warn, it turns to the warning colour when nearly full. */
function Meter({ icon, label, value, fraction, title, warn = true }: {
  icon: React.ReactNode; label: string; value: string; fraction: number; title: string; warn?: boolean
}) {
  const f = Math.max(0, Math.min(1, fraction))
  return (
    <div className="min-w-0" title={title}>
      <div className="flex items-center justify-between gap-2 text-xs">
        <span className="flex items-center gap-1.5 text-crystal-muted">{icon}{label}</span>
        <span className="text-crystal-text tabular">{value}</span>
      </div>
      <div className="mt-1.5 h-1 rounded-full bg-crystal-border overflow-hidden">
        <div
          className={`h-full rounded-full transition-[width] duration-700 ${warn && f > 0.9 ? 'bg-crystal-warning' : 'bg-crystal-accent'}`}
          style={{ width: `${f * 100}%` }}
        />
      </div>
    </div>
  )
}

/**
 * One row per running game: instance, account, how long it has run, and what
 * it uses right now. Hidden while nothing runs.
 */
export function RunningGamesPanel({ games }: { games: RunningGame[] }) {
  // Re-render once a minute so the running time moves on.
  const [, tick] = useState(0)
  useEffect(() => {
    const t = setInterval(() => tick(n => n + 1), 60000)
    return () => clearInterval(t)
  }, [])

  if (games.length === 0) return null
  const totalCpu = games.reduce((sum, g) => sum + (g.usage?.cpuPercent ?? 0), 0)
  const totalRam = games.reduce((sum, g) => sum + (g.usage?.ramMb ?? 0), 0)

  return (
    <section aria-label="Laufende Spiele">
      <div className="flex items-baseline justify-between mb-2 px-0.5">
        <h2 className="text-[13px] font-semibold text-crystal-text">
          {games.length === 1 ? 'Läuft gerade' : `${games.length} Spiele laufen`}
        </h2>
        {games.length > 1 && (
          <span className="text-xs text-crystal-muted tabular">
            Zusammen {Math.round(totalCpu)} % CPU, {gb(totalRam)} RAM
          </span>
        )}
      </div>
      <div className="crystal-card divide-y divide-crystal-border">
        {games.map(game => (
          <div key={game.instanceId} className="px-4 py-3">
            <div className="flex items-center gap-3">
              <span className="relative flex h-2 w-2 shrink-0" aria-hidden="true">
                <span className="absolute inline-flex h-full w-full rounded-full bg-crystal-success opacity-60 motion-safe:animate-ping" />
                <span className="relative inline-flex h-2 w-2 rounded-full bg-crystal-success" />
              </span>
              <div className="min-w-0 flex-1">
                <p className="text-[13px] text-crystal-text truncate">
                  {game.instanceName}
                  {!game.instanceName.includes(game.version) && <span className="text-crystal-muted tabular"> {game.version}</span>}
                </p>
                <p className="text-xs text-crystal-muted truncate">als {game.username}</p>
              </div>
              <span className="flex items-center gap-1 text-xs text-crystal-muted tabular shrink-0">
                <Clock size={12} strokeWidth={1.75} /> {duration(Date.now() - game.startedAt)}
              </span>
              <button
                onClick={() => api?.closeGame(game.instanceId)}
                title="Schließt Minecraft wie das X am Fenster, die Welt wird vorher gespeichert."
                className="crystal-btn-ghost border border-crystal-border text-xs px-2.5 py-1 text-crystal-text"
              >
                <X size={12} /> Schließen
              </button>
            </div>
            <div className="mt-3 grid grid-cols-2 gap-4 pl-5">
              <Meter
                icon={<Cpu size={12} strokeWidth={1.75} />}
                label="CPU"
                value={game.usage ? `${Math.round(game.usage.cpuPercent)} %` : '…'}
                fraction={(game.usage?.cpuPercent ?? 0) / 100}
                title="Anteil an der ganzen CPU, wie im Task-Manager"
              />
              <Meter
                icon={<MemoryStick size={12} strokeWidth={1.75} />}
                label="RAM"
                value={game.usage ? `${gb(game.usage.ramMb)}${game.maxRamMb ? ` von ${gb(game.maxRamMb)}` : ''}` : '…'}
                fraction={game.usage && game.maxRamMb ? game.usage.ramMb / game.maxRamMb : 0}
                warn={false}
                title="Was der Prozess im Speicher hält, gegen den eingestellten Arbeitsspeicher. Java braucht etwas mehr als den eingestellten Wert, über 100 % ist also normal."
              />
            </div>
          </div>
        ))}
      </div>
    </section>
  )
}
