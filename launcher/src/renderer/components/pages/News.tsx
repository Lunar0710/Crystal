import React from 'react'
import { ArrowUpRight, WifiOff } from 'lucide-react'
import { Page, PageHeader, EmptyState } from '../ui/Page'
import { useReleases, relativeDate, plainNotes } from '../../hooks/useReleases'

const api = (window as any).crystal

export function News() {
  const { loading, ok, items } = useReleases()

  return (
    <Page>
      <PageHeader title="Neuigkeiten" description="Was sich in jeder Crystal-Version geändert hat." />

      {loading && (
        <div className="space-y-6 ml-1.5 pl-6 border-l border-crystal-border">
          {[0, 1, 2].map(i => (
            <div key={i} className="animate-pulse">
              <div className="h-3 w-20 rounded bg-crystal-border mb-2.5" />
              <div className="h-4 w-56 rounded bg-crystal-border mb-2" />
              <div className="h-3 w-80 max-w-full rounded bg-crystal-border/60" />
            </div>
          ))}
        </div>
      )}

      {!loading && !ok && (
        <EmptyState icon={<WifiOff size={22} strokeWidth={1.75} />} title="Neuigkeiten konnten nicht geladen werden">
          GitHub ist gerade nicht erreichbar. Prüf deine Internetverbindung und öffne die Seite später erneut.
        </EmptyState>
      )}

      {!loading && ok && items.length === 0 && (
        <EmptyState title="Noch keine Versionen veröffentlicht" />
      )}

      {!loading && items.length > 0 && (
        <ol className="relative border-l border-crystal-border ml-1.5">
          {items.map((release, index) => (
            <li key={release.id} className="pl-6 pb-8 last:pb-0 relative">
              <span
                className={`absolute -left-[5px] top-[7px] w-[9px] h-[9px] rounded-full ring-4 ring-crystal-bg ${
                  index === 0 ? 'bg-crystal-accent' : 'bg-crystal-border'
                }`}
              />
              <div className="flex items-baseline gap-2.5 mb-1">
                <span className="font-mono text-xs text-crystal-text">{release.tag}</span>
                <span className="text-xs text-crystal-muted">{relativeDate(release.publishedAt)}</span>
                {index === 0 && (
                  <span className="text-[11px] px-1.5 py-px rounded bg-crystal-accent/15 text-crystal-accent">aktuell</span>
                )}
              </div>
              <h2 className="text-[15px] font-semibold text-crystal-text">{release.title}</h2>
              {release.body.trim() && (
                <div className="mt-1.5 space-y-0.5 text-[13px] text-crystal-muted leading-relaxed max-w-[68ch]">
                  {plainNotes(release.body).map((line, i) => <p key={i}>{line}</p>)}
                </div>
              )}
              <button
                onClick={() => api?.openExternal(release.url)}
                className="mt-2.5 inline-flex items-center gap-1 text-xs text-crystal-muted hover:text-crystal-text transition-colors"
              >
                Auf GitHub ansehen <ArrowUpRight size={12} />
              </button>
            </li>
          ))}
        </ol>
      )}
    </Page>
  )
}
