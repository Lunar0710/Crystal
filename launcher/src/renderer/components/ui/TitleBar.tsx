import React from 'react'
import { Minus, Square, X } from 'lucide-react'
import { CrystalWordmark } from '../../theme/CrystalWordmark'

const api = (window as any).crystal
const isMac = api?.platform === 'darwin'

export function TitleBar() {
  return (
    <div
      className={`flex items-stretch justify-between h-9 bg-crystal-panel border-b border-crystal-border select-none ${isMac ? 'pl-[78px]' : 'pl-3.5'}`}
      style={{ WebkitAppRegion: 'drag' } as React.CSSProperties}
      // macOS convention: double-clicking the title bar zooms the window.
      onDoubleClick={isMac ? () => api?.maximize() : undefined}
    >
      <div className="flex items-center">
        <CrystalWordmark size={13} className="text-crystal-text" />
      </div>

      {/* Full-height, square-edged buttons like the native Windows caption controls.
          macOS draws its own traffic lights on the left instead. */}
      {!isMac && <div className="flex" style={{ WebkitAppRegion: 'no-drag' } as React.CSSProperties}>
        <CaptionButton onClick={() => api?.minimize()} label="Minimieren"><Minus size={14} strokeWidth={1.5} /></CaptionButton>
        <CaptionButton onClick={() => api?.maximize()} label="Maximieren"><Square size={11} strokeWidth={1.5} /></CaptionButton>
        <CaptionButton onClick={() => api?.close()} label="Schließen" danger><X size={15} strokeWidth={1.5} /></CaptionButton>
      </div>}
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
