import React from 'react'
import { Minus, Square, X } from 'lucide-react'
import { CrystalWordmark } from '../../theme/CrystalWordmark'

const api = (window as any).crystal

export function TitleBar() {
  return (
    <div
      className="flex items-center justify-between h-10 px-4 bg-crystal-panel border-b border-crystal-border"
      style={{ WebkitAppRegion: 'drag' } as React.CSSProperties}
    >
      <div className="flex items-center gap-2">
        <CrystalWordmark size={15} className="text-crystal-text" />
        <span className="text-crystal-muted text-sm tracking-wide">Launcher</span>
      </div>

      <div
        className="flex items-center gap-1"
        style={{ WebkitAppRegion: 'no-drag' } as React.CSSProperties}
      >
        <button
          onClick={() => api?.minimize()}
          className="p-1.5 rounded hover:bg-crystal-border text-crystal-muted hover:text-crystal-text transition-colors"
        >
          <Minus size={14} />
        </button>
        <button
          onClick={() => api?.maximize()}
          className="p-1.5 rounded hover:bg-crystal-border text-crystal-muted hover:text-crystal-text transition-colors"
        >
          <Square size={14} />
        </button>
        <button
          onClick={() => api?.close()}
          className="p-1.5 rounded hover:bg-crystal-danger text-crystal-muted hover:text-white transition-colors"
        >
          <X size={14} />
        </button>
      </div>
    </div>
  )
}
