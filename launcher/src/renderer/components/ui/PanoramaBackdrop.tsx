import React, { useEffect, useState } from 'react'

const api = (window as any).crystal

/**
 * The title-screen panorama of a Minecraft version, turning slowly
 * (main/minecraft/Panorama.ts reads it from the game files). Faces 0 to 3 are
 * laid out twice so sliding by half is one full turn with no seam. Falls back
 * to the Nexora backdrop for a version that was never started.
 */
export function PanoramaBackdrop({ version, shade = 'from-black/90 via-black/55 to-black/25', faceSize = 340 }: {
  version: string | null | undefined
  shade?: string
  faceSize?: number
}) {
  const [faces, setFaces] = useState<string[] | null>(null)
  useEffect(() => {
    setFaces(null)
    if (version) api?.getPanorama(version).then((f: string[] | null) => setFaces(f))
  }, [version])

  if (!faces) return <div className="nexora-backdrop absolute inset-0" aria-hidden="true" />
  return (
    <div className="absolute inset-0 overflow-hidden" aria-hidden="true">
      <div className="nexora-pano absolute left-0 top-1/2 flex" style={{ height: faceSize }}>
        {[...faces, ...faces].map((src, i) => (
          <img key={i} src={src} alt="" draggable={false} className="h-full w-auto max-w-none select-none" />
        ))}
      </div>
      <div className={`absolute inset-0 bg-gradient-to-r ${shade}`} />
    </div>
  )
}
