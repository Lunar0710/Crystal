import React, { useEffect, useRef, useState } from 'react'
import * as THREE from 'three'

const api = (window as any).crystal

/**
 * The title-screen panorama of a Minecraft version, drawn the way the game
 * draws it: a camera in the middle of a cube with the six panorama images
 * on its inside, as a still view (main/minecraft/Panorama.ts reads them from
 * the game files). WebGL rather than CSS 3D: Chromium drops a CSS face as
 * soon as part of it is behind the camera. Falls back to the Nexora backdrop
 * for a version that was never started.
 */
export function PanoramaBackdrop({ version, shade = 'from-black/90 via-black/55 to-black/25' }: {
  version: string | null | undefined
  shade?: string
  /** Kept for older callers; the view fills the box. */
  faceSize?: number
}) {
  const [faces, setFaces] = useState<string[] | null>(null)
  const host = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    setFaces(null)
    if (version) api?.getPanorama(version).then((f: string[] | null) => setFaces(f && f.length === 6 ? f : null))
  }, [version])

  useEffect(() => {
    const el = host.current
    if (!faces || !el) return
    const renderer = new THREE.WebGLRenderer({ antialias: false, powerPreference: 'low-power' })
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.5))
    renderer.outputColorSpace = THREE.SRGBColorSpace
    renderer.domElement.style.display = 'block'
    renderer.domElement.style.width = '100%'
    renderer.domElement.style.height = '100%'
    el.appendChild(renderer.domElement)

    const scene = new THREE.Scene()
    const camera = new THREE.PerspectiveCamera(62, 1, 0.05, 10)
    // A still view, turned a little to the side like the title screen's first frame.
    camera.rotation.set(0, -0.35, 0, 'YXZ')

    // Only drawn when something changed: an image finished loading or the box resized.
    let frame = 0
    const draw = () => {
      cancelAnimationFrame(frame)
      frame = requestAnimationFrame(() => renderer.render(scene, camera))
    }

    const loader = new THREE.TextureLoader()
    // Box faces are +x, -x, +y, -y, +z, -z; the camera looks down -z.
    // Minecraft's order is ahead (0), right (1), behind (2), left (3), up (4), down (5).
    const order = [1, 3, 4, 5, 2, 0]
    const materials = order.map(i => {
      const texture = loader.load(faces[i], draw)
      texture.colorSpace = THREE.SRGBColorSpace
      texture.magFilter = THREE.LinearFilter
      // Seen from inside the box, each face is mirrored; flip it back.
      texture.wrapS = THREE.RepeatWrapping
      texture.repeat.x = -1
      return new THREE.MeshBasicMaterial({ map: texture, side: THREE.BackSide })
    })
    const cube = new THREE.Mesh(new THREE.BoxGeometry(2, 2, 2), materials)
    scene.add(cube)

    const resize = () => {
      const w = el.clientWidth || 1, h = el.clientHeight || 1
      renderer.setSize(w, h, false)
      camera.aspect = w / h
      camera.updateProjectionMatrix()
      draw()
    }
    resize()
    const observer = new ResizeObserver(resize)
    observer.observe(el)

    return () => {
      cancelAnimationFrame(frame)
      observer.disconnect()
      materials.forEach(m => { m.map?.dispose(); m.dispose() })
      cube.geometry.dispose()
      renderer.dispose()
      renderer.domElement.remove()
    }
  }, [faces])

  if (!faces) return <div className="nexora-backdrop absolute inset-0" aria-hidden="true" />
  return (
    <div className="absolute inset-0 overflow-hidden" aria-hidden="true">
      <div ref={host} className="absolute inset-0" />
      <div className={`absolute inset-0 bg-gradient-to-r ${shade}`} />
    </div>
  )
}
