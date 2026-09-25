import React, { useEffect, useState } from 'react'
import { X, Sparkles } from 'lucide-react'
import { useReleases, plainNotes } from '../../hooks/useReleases'

const api = (window as any).crystal

/**
 * After an update, once: what the new version brings, from its release notes.
 * A first install shows nothing (there is no "before" to compare with); the
 * version is only remembered.
 */
export function WhatsNew() {
  const [version, setVersion] = useState<string | null>(null)
  const { loading, items } = useReleases()

  useEffect(() => {
    Promise.all([api?.getVersionInfo?.(), api?.getSetting('lastSeenVersion')]).then(([info, seen]) => {
      const current = info?.launcher as string | undefined
      if (!current) return
      if (seen && seen !== current) setVersion(current)
      else if (!seen) api?.setSetting('lastSeenVersion', current)
    })
  }, [])

  if (!version || loading) return null
  const release = items.find(r => r.tag === `v${version}`)
  const close = () => {
    api?.setSetting('lastSeenVersion', version)
    setVersion(null)
  }
  const lines = release ? plainNotes(release.body).slice(0, 14) : []

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4" role="dialog" aria-modal="true" aria-labelledby="whatsnew-title">
      <div className="crystal-card w-full max-w-lg max-h-[80vh] flex flex-col">
        <div className="flex items-center gap-2 px-5 pt-4">
          <Sparkles size={16} className="text-crystal-accent" />
          <h2 id="whatsnew-title" className="flex-1 text-[15px] font-semibold text-crystal-text">
            {release?.title ?? `Nexora ${version}`}
          </h2>
          <button onClick={close} aria-label="Schließen" className="p-1 rounded text-crystal-muted hover:text-crystal-text"><X size={15} /></button>
        </div>
        <div className="px-5 py-3 overflow-y-auto text-[13px] text-crystal-text space-y-1.5">
          {lines.length > 0
            ? lines.map((line, i) => <p key={i} className={line.startsWith('•') ? 'pl-3 -indent-3' : 'text-crystal-muted'}>{line}</p>)
            : <p className="text-crystal-muted">Nexora wurde auf {version} aktualisiert. Was neu ist, steht unter Neuigkeiten.</p>}
        </div>
        <div className="px-5 pb-4 pt-1 flex justify-end">
          <button onClick={close} className="crystal-btn-primary text-[13px]">Weiter</button>
        </div>
      </div>
    </div>
  )
}
