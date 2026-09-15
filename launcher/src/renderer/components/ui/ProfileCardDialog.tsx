import React, { useEffect, useState } from 'react'
import { Copy, Download, X } from 'lucide-react'
import { notify } from '../../store/notificationStore'
import { renderProfileCard, type ProfileCardData } from '../../data/profileCard'

const api = (window as any).crystal

/** Shows the rendered profile card with copy and save buttons. */
export function ProfileCardDialog({ data, onClose }: { data: Omit<ProfileCardData, 'playtimeMs' | 'sessions'>; onClose: () => void }) {
  const [image, setImage] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      const stats = (await api?.getStats()) || { playtimeMs: 0, sessions: 0 }
      const url = await renderProfileCard({ ...data, playtimeMs: stats.playtimeMs, sessions: stats.sessions })
      if (!cancelled) setImage(url)
    })()
    return () => { cancelled = true }
  }, [])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  async function copy() {
    if (!image) return
    const ok = await api?.copyProfileCard(image)
    notify(ok ? { type: 'success', message: 'Profil-Karte kopiert. Einfach in Discord einfügen.' } : { type: 'error', message: 'Kopieren hat nicht geklappt' })
  }

  async function save() {
    if (!image) return
    const ok = await api?.saveProfileCard(image)
    if (ok) notify({ type: 'success', message: 'Profil-Karte gespeichert' })
  }

  return (
    <div className="fixed inset-0 z-50 bg-black/75 flex items-center justify-center p-6" onClick={onClose}>
      <div className="crystal-card w-full max-w-[760px] overflow-hidden" onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between px-4 py-3 border-b border-crystal-border">
          <p className="text-[13px] font-semibold text-crystal-text">Profil-Karte</p>
          <button onClick={onClose} className="text-crystal-muted hover:text-crystal-text" aria-label="Schließen"><X size={16} /></button>
        </div>
        <div className="p-4">
          <div className="aspect-[1200/630] rounded-md bg-crystal-panel overflow-hidden flex items-center justify-center">
            {image
              ? <img src={image} alt="Profil-Karte" className="w-full h-full" />
              : <p className="text-[12px] text-crystal-muted">Wird erstellt...</p>}
          </div>
          <div className="flex justify-end gap-2 mt-4">
            <button onClick={save} disabled={!image} className="crystal-btn-ghost text-[13px] inline-flex items-center gap-1.5">
              <Download size={14} /> Speichern
            </button>
            <button onClick={copy} disabled={!image} className="crystal-btn-primary text-[13px] inline-flex items-center gap-1.5">
              <Copy size={14} /> Bild kopieren
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
