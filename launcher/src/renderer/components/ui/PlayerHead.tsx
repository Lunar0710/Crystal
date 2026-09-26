import React, { useEffect, useRef, useState } from 'react'

const api = (window as any).crystal
const heads = new Map<string, string | null>()

/**
 * A player's face from their skin (face plus hat layer), drawn pixel-sharp.
 * Skins are fetched once per name; without one the first letter shows.
 */
export function PlayerHead({ name, size = 32, className = '' }: { name: string | null; size?: number; className?: string }) {
  const [src, setSrc] = useState<string | null>(name ? heads.get(name) ?? null : null)
  const canvas = useRef<HTMLCanvasElement | null>(null)

  useEffect(() => {
    if (!name) { setSrc(null); return }
    if (heads.has(name)) { setSrc(heads.get(name)!); return }
    let alive = true
    api?.fetchSkin(name).then((r: { success: boolean; dataUrl?: string } | null) => {
      if (!r?.success || !r.dataUrl) { heads.set(name, null); return }
      const img = new Image()
      img.onload = () => {
        const c = canvas.current ?? (canvas.current = document.createElement('canvas'))
        c.width = c.height = 8
        const ctx = c.getContext('2d')!
        ctx.imageSmoothingEnabled = false
        ctx.drawImage(img, 8, 8, 8, 8, 0, 0, 8, 8)
        ctx.drawImage(img, 40, 8, 8, 8, 0, 0, 8, 8)
        const url = c.toDataURL()
        heads.set(name, url)
        if (alive) setSrc(url)
      }
      img.src = r.dataUrl
    }).catch(() => heads.set(name, null))
    return () => { alive = false }
  }, [name])

  const box = { width: size, height: size }
  if (!src) {
    return (
      <span style={box} className={`flex items-center justify-center rounded-md bg-crystal-card text-[11px] font-semibold text-crystal-text ${className}`}>
        {name ? name.charAt(0).toUpperCase() : '?'}
      </span>
    )
  }
  return <img src={src} alt="" style={{ ...box, imageRendering: 'pixelated' }} className={`rounded-md ${className}`} draggable={false} />
}
