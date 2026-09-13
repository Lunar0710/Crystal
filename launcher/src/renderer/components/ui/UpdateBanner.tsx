import React, { useState, useEffect } from 'react'
import { Download, X } from 'lucide-react'

const api = (window as any).crystal

/**
 * Discord-style update prompt: a quiet strip at the bottom rather than a modal,
 * so an available update never interrupts what you were doing.
 */
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
    return () => unsubs.forEach(u => u?.())
  }, [])

  if (!version || dismissed) return null

  const downloading = progress !== null

  return (
    <div className="absolute bottom-4 left-1/2 -translate-x-1/2 z-50 animate-slide-in">
      <div className="crystal-card flex items-center gap-3 pl-4 pr-3 py-2.5 border-crystal-accent/40 shadow-glow">
        <Download size={15} className="text-crystal-accent shrink-0" />

        <div className="text-sm">
          {error ? (
            <span className="text-crystal-danger">{error}</span>
          ) : downloading ? (
            <span className="text-crystal-text">Update wird geladen… {Math.round(progress!)}%</span>
          ) : (
            <span className="text-crystal-text">
              Update auf <strong>{version}</strong> verfügbar
            </span>
          )}
        </div>

        {!downloading && !error && (
          <button
            onClick={() => { setProgress(0); api?.downloadAndRestart() }}
            className="crystal-btn-primary text-xs px-3 py-1.5"
          >
            Jetzt neu starten
          </button>
        )}

        <button
          onClick={() => setDismissed(true)}
          className="p-1 rounded text-crystal-muted hover:text-crystal-text transition-colors"
          title="Später"
        >
          <X size={14} />
        </button>
      </div>

      {downloading && (
        <div className="h-1 mt-1 bg-crystal-border rounded-full overflow-hidden">
          <div
            className="h-full bg-crystal-gradient transition-all duration-300"
            style={{ width: `${progress}%` }}
          />
        </div>
      )}
    </div>
  )
}
