import React, { useEffect, useRef, useState } from 'react'

const api = (window as any).crystal

/**
 * The title-screen panorama of a Minecraft version, the way the game shows
 * it: the four side faces as a cube around the camera, turning slowly
 * (main/minecraft/Panorama.ts reads them from the game files). Laid flat
 * side by side the horizon kinked at every face edge; seen from inside a
 * cube with perspective, it is one continuous view. Falls back to the
 * Nexora backdrop for a version that was never started.
 */
export function PanoramaBackdrop({ version, shade = 'from-black/90 via-black/55 to-black/25' }: {
  version: string | null | undefined
  shade?: string
  /** Kept for callers from before the cube; the cube sizes itself to the box. */
  faceSize?: number
}) {
  const [faces, setFaces] = useState<string[] | null>(null)
  const box = useRef<HTMLDivElement | null>(null)
  const [height, setHeight] = useState(240)

  useEffect(() => {
    setFaces(null)
    if (version) api?.getPanorama(version).then((f: string[] | null) => setFaces(f))
  }, [version])

  useEffect(() => {
    const el = box.current
    if (!el) return
    const observer = new ResizeObserver(() => setHeight(el.clientHeight || 240))
    observer.observe(el)
    return () => observer.disconnect()
  }, [faces])

  if (!faces) return <div className="nexora-backdrop absolute inset-0" aria-hidden="true" />

  // Face size twice the box height, the camera at the cube's centre: about
  // 53° up and down, like Minecraft's own title screen, never past a face's
  // top or bottom edge (the top and bottom faces aren't needed then).
  const size = Math.max(200, height * 2)
  const half = size / 2
  return (
    <div ref={box} className="absolute inset-0 overflow-hidden" aria-hidden="true" style={{ perspective: `${half}px` }}>
      {/* Moved toward the viewer by the perspective distance: the camera sits at the cube's centre. */}
      <div className="absolute left-1/2 top-1/2" style={{ transformStyle: 'preserve-3d', transform: `translateZ(${half}px)` }}>
        <div className="nexora-pano-cube" style={{ transformStyle: 'preserve-3d' }}>
          {faces.map((src, i) => (
            <img
              key={i}
              src={src}
              alt=""
              draggable={false}
              className="absolute select-none max-w-none"
              style={{
                width: size,
                height: size,
                left: -half,
                top: -half,
                // Face 0 ahead, 1 to the right, 2 behind, 3 to the left, each facing in.
                transform: `rotateY(${-i * 90}deg) translateZ(${-half}px)`,
                backfaceVisibility: 'hidden',
              }}
            />
          ))}
        </div>
      </div>
      <div className={`absolute inset-0 bg-gradient-to-r ${shade}`} />
    </div>
  )
}
