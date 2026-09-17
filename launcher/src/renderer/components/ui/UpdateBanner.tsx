import React, { useState, useEffect } from 'react'
import { ArrowDownToLine, X } from 'lucide-react'
import { displayVersion } from '../../data/displayVersion'

const api = (window as any).crystal

/** A quiet strip at the bottom rather than a modal, so an update never interrupts what you were doing. */
export function UpdateBanner() {
  const [version, setVersion] = useState<string | null>(null)
  const [dismissed, setDismissed] = useState(false)
  const [progress, setProgress] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const unsubs = [
      api?.on('update:available', (info: { version?: string }) => setVersion(info?.version ?? 'neue Version')),
      api?.on('update:progress', (data: { percent?: number }) => {
        if (typeof data?.percent === 'number') setProgress(data.percent)
      }),
      api?.on('update:error', (message: string) => {
        setError(typeof message === 'string' ? message : 'Update fehlgeschlagen')
        setProgress(null)
      }),
    ]
    // Ask directly as well: the startup broadcast can land before this
    // component has subscribed, and then the banner would never show.
    api?.checkUpdate().then((info: { available: boolean; version?: string } | undefined) => {
      if (info?.available && info.version) setVersion(info.version)
    }).catch(() => {})
    return () => unsubs.forEach(u => u?.())
  }, [])

  if (!version || dismissed) return null

  const downloading = progress !== null

  return (
    <div className="fixed bottom-4 left-1/2 -translate-x-1/2 z-40 animate-slide-in" role="status">
      <div className="flex items-center gap-3 pl-3.5 pr-2 py-2 rounded-lg bg-crystal-panel shadow-popover min-w-[320px]">
        <ArrowDownToLine size={15} strokeWidth={1.75} className="text-crystal-accent shrink-0" />

        <div className="flex-1 min-w-0 text-[13px]">
          {error ? (
            <span className="text-crystal-danger">{error}</span>
          ) : downloading ? (
            <span className="text-crystal-text tabular">Update wird geladen, {Math.round(progress!)} %</span>
          ) : (
            <span className="text-crystal-text">Crystal {displayVersion(version)} ist verfügbar</span>
          )}
          {downloading && (
            <div className="h-0.5 mt-1.5 bg-crystal-border rounded-full overflow-hidden">
              <div className="h-full bg-crystal-accent transition-[width] duration-300" style={{ width: `${progress}%` }} />
            </div>
          )}
        </div>

        {!downloading && !error && (
          <button onClick={() => { setProgress(0); api?.downloadAndRestart() }} className="crystal-btn-primary text-xs py-1.5">
            Aktualisieren
          </button>
        )}

        <button onClick={() => setDismissed(true)} aria-label="Später" title="Später" className="p-1.5 rounded text-crystal-muted hover:text-crystal-text">
          <X size={13} />
        </button>
      </div>
    </div>
  )
}
