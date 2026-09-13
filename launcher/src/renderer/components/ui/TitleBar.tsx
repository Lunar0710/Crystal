import React from 'react'
import { Minus, Square, X } from 'lucide-react'
import { CrystalWordmark } from '../../theme/CrystalWordmark'

const api = (window as any).crystal

export function TitleBar() {
  return (
    <div
      className="flex items-stretch justify-between h-9 pl-3.5 bg-crystal-panel border-b border-crystal-border select-none"
      style={{ WebkitAppRegion: 'drag' } as React.CSSProperties}
    >
      <div className="flex items-center">
        <CrystalWordmark size={13} className="text-crystal-text" />
      </div>

      {/* Full-height, square-edged buttons like the native Windows caption controls. */}
      <div className="flex" style={{ WebkitAppRegion: 'no-drag' } as React.CSSProperties}>
        <CaptionButton onClick={() => api?.minimize()} label="Minimieren"><Minus size={14} strokeWidth={1.5} /></CaptionButton>
        <CaptionButton onClick={() => api?.maximize()} label="Maximieren"><Square size={11} strokeWidth={1.5} /></CaptionButton>
        <CaptionButton onClick={() => api?.close()} label="Schließen" danger><X size={15} strokeWidth={1.5} /></CaptionButton>
      </div>
    </div>
  )
}

function CaptionButton({ onClick, label, danger, children }: {
  onClick: () => void
  label: string
  danger?: boolean
  children: React.ReactNode
}) {
  return (
    <button
      onClick={onClick}
      aria-label={label}
      className={`w-11 flex items-center justify-center text-crystal-muted transition-colors ${
        danger ? 'hover:bg-[#c42b1c] hover:text-white' : 'hover:bg-crystal-border/70 hover:text-crystal-text'
      }`}
    >
      {children}
    </button>
  )
}
