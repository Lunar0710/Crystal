import React, { useRef, useEffect, useState } from 'react'
import * as skinview3d from 'skinview3d'
import * as THREE from 'three'
import { RotateCw, Pause, Play } from 'lucide-react'
import { CosmeticDef } from '../../data/cosmetics'

const COSMETIC_GROUP = 'crystal-cosmetics'

// Cosmetics hang off skinview3d's SkinObject (playerObject.skin), whose space
// is (skinview3d 3.4 model.js): head 0..8, body -12..0, legs down to -24,
// negative z is behind the player. Head pieces are authored around the head's
// centre and placed in a group raised to y=4; see HEAD_CENTER_Y below.
// Attaching to playerObject instead (as before) put everything one head too
// low, because the skin itself sits at y=8 inside the player object.
const HEAD_CENTER_Y = 4
const HEAD_TOP = 4
const BODY_BACK_Z = -2
const FACE_Z = 4

export function SkinPreview3D({
  skinDataUrl,
  slim = false,
  capeUrl,
  hat,
  bandana,
  mask,
  wings,
  backpack,
  aura,
  width = 240,
  height = 320,
}: {
  skinDataUrl: string | null
  slim?: boolean
  capeUrl?: string | null
  hat?: CosmeticDef | null
  bandana?: CosmeticDef | null
  mask?: CosmeticDef | null
  wings?: CosmeticDef | null
  backpack?: CosmeticDef | null
  aura?: CosmeticDef | null
  width?: number
  height?: number
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const viewerRef = useRef<skinview3d.SkinViewer | null>(null)
  const wingRefs = useRef<{ left: THREE.Object3D; right: THREE.Object3D } | null>(null)
  const auraRef = useRef<THREE.Points | null>(null)
  const frameRef = useRef<number>(0)

  const [autoRotate, setAutoRotate] = useState(true)
  const [walking, setWalking] = useState(true)

  useEffect(() => {
    if (!canvasRef.current) return

    const viewer = new skinview3d.SkinViewer({ canvas: canvasRef.current, width, height })
    viewer.controls.enableZoom = true
    viewer.controls.enablePan = false
    viewer.zoom = 0.82
    viewer.animation = new skinview3d.WalkingAnimation()
    viewerRef.current = viewer

    // Wings flap and aura particles drift on their own clock, independent of
    // skinview3d's walk cycle.
    const clock = new THREE.Clock()
    const tick = () => {
      const t = clock.getElapsedTime()

      if (wingRefs.current) {
        const flap = Math.sin(t * 2.4) * 0.36
        wingRefs.current.right.rotation.y = -0.45 - flap
        wingRefs.current.left.rotation.y = 0.45 + flap
        wingRefs.current.right.rotation.z = flap * 0.25
        wingRefs.current.left.rotation.z = -flap * 0.25
      }

      if (auraRef.current) {
        const points = auraRef.current
        const variant = points.userData.variant as string
        const positions = points.geometry.getAttribute('position') as THREE.BufferAttribute
        const phases = (points.userData.phases as number[]) || []

        points.rotation.y = t * (variant === 'ring' ? 1.4 : variant === 'storm' ? 0.9 : 0.5)

        for (let i = 0; i < positions.count; i++) {
          const base = phases[i]
          let y: number

          switch (variant) {
            case 'rising':
            case 'storm':
              // Stream upward and wrap around at the top.
              y = ((base + t * 7 + i) % 26) - 18
              break
            case 'snow':
            case 'petals':
              // Drift downward, gently, and loop.
              y = 8 - (((8 - base) + t * 3 + i) % 26)
              break
            case 'sphere':
              y = base
              break
            default:
              y = base + Math.sin(t * 1.6 + i) * 1.4
          }

          positions.setY(i, y)
        }
        positions.needsUpdate = true

        if (variant === 'sphere') {
          const pulse = 1 + Math.sin(t * 1.5) * 0.07
          points.scale.setScalar(pulse)
        }
      }

      frameRef.current = requestAnimationFrame(tick)
    }
    frameRef.current = requestAnimationFrame(tick)

    return () => {
      cancelAnimationFrame(frameRef.current)
      viewer.dispose()
      viewerRef.current = null
      wingRefs.current = null
      auraRef.current = null
    }
  }, [width, height])

  useEffect(() => {
    const viewer = viewerRef.current
    if (!viewer) return
    viewer.autoRotate = autoRotate
    viewer.autoRotateSpeed = 0.6
  }, [autoRotate])

  useEffect(() => {
    const viewer = viewerRef.current
    if (!viewer?.animation) return
    viewer.animation.paused = !walking
  }, [walking])

  useEffect(() => {
    const viewer = viewerRef.current
    if (!viewer) return
    if (skinDataUrl) viewer.loadSkin(skinDataUrl, { model: slim ? 'slim' : 'default' }).catch(() => {})
    else viewer.resetSkin()
  }, [skinDataUrl, slim])

  useEffect(() => {
    const viewer = viewerRef.current
    if (!viewer) return
    if (capeUrl) viewer.loadCape(capeUrl).catch(() => {})
    else viewer.resetCape()
  }, [capeUrl])

  // Rebuild the cosmetic meshes whenever the loadout changes.
  useEffect(() => {
    const viewer = viewerRef.current
    if (!viewer) return

    const player = viewer.playerObject.skin as unknown as THREE.Object3D
    const previous = player.getObjectByName(COSMETIC_GROUP)
    if (previous) {
      previous.removeFromParent()
      previous.traverse(obj => {
        const mesh = obj as THREE.Mesh
        mesh.geometry?.dispose()
        const material = mesh.material as THREE.Material | THREE.Material[] | undefined
        if (Array.isArray(material)) material.forEach(m => m.dispose())
        else material?.dispose()
      })
    }
    wingRefs.current = null
    auraRef.current = null

    const group = new THREE.Group()
    group.name = COSMETIC_GROUP
    // Hat, bandana and mask coordinates are relative to the head's centre.
    const head = new THREE.Group()
    head.position.y = HEAD_CENTER_Y
    group.add(head)

    const box = (w: number, h: number, d: number, color: string, opacity = 1) =>
      new THREE.Mesh(
        new THREE.BoxGeometry(w, h, d),
        new THREE.MeshLambertMaterial({
          color: new THREE.Color(color),
          transparent: opacity < 1,
          opacity,
        })
      )

    if (bandana) {
      const band = box(8.7, 1.8, 8.7, bandana.color)
      band.position.set(0, -1.4, 0)
      head.add(band)

      const knot = box(1.4, 1.4, 2.2, bandana.secondary ?? bandana.color)
      knot.position.set(0, -1.4, BODY_BACK_Z - 3)
      head.add(knot)
    }

    if (hat) {
      const accent = hat.secondary ?? hat.color
      switch (hat.variant) {
        case 'crown': {
          const base = box(8.8, 1.6, 8.8, hat.color)
          base.position.set(0, HEAD_TOP + 0.8, 0)
          head.add(base)
          for (let i = 0; i < 6; i++) {
            const angle = (i / 6) * Math.PI * 2
            const spike = new THREE.Mesh(
              new THREE.ConeGeometry(0.9, 2.6, 6),
              new THREE.MeshLambertMaterial({ color: new THREE.Color(hat.color) })
            )
            spike.position.set(Math.cos(angle) * 3.4, HEAD_TOP + 2.8, Math.sin(angle) * 3.4)
            head.add(spike)
          }
          const gem = box(1.2, 1.2, 1.2, accent)
          gem.position.set(0, HEAD_TOP + 1, FACE_Z)
          head.add(gem)
          break
        }
        case 'tophat': {
          const brim = box(11.5, 0.8, 11.5, hat.color)
          brim.position.set(0, HEAD_TOP + 0.4, 0)
          const top = box(7.4, 6, 7.4, hat.color)
          top.position.set(0, HEAD_TOP + 3.8, 0)
          const ribbon = box(7.6, 1.2, 7.6, accent)
          ribbon.position.set(0, HEAD_TOP + 1.4, 0)
          head.add(brim, top, ribbon)
          break
        }
        case 'straw': {
          const brim = box(13, 0.7, 13, hat.color)
          brim.position.set(0, HEAD_TOP + 0.4, 0)
          const dome = box(8, 2.8, 8, accent)
          dome.position.set(0, HEAD_TOP + 2.1, 0)
          head.add(brim, dome)
          break
        }
        case 'cap': {
          const dome = box(8.7, 3.2, 8.7, hat.color)
          dome.position.set(0, HEAD_TOP + 1.6, 0)
          const visor = box(8, 0.6, 4.5, accent)
          visor.position.set(0, HEAD_TOP + 0.3, FACE_Z + 1.4)
          head.add(dome, visor)
          break
        }
        case 'halo': {
          const ring = new THREE.Mesh(
            new THREE.TorusGeometry(3.6, 0.5, 10, 28),
            new THREE.MeshBasicMaterial({ color: new THREE.Color(hat.color) })
          )
          ring.rotation.x = Math.PI / 2
          ring.position.set(0, HEAD_TOP + 4, 0)
          head.add(ring)
          break
        }
        case 'horns': {
          for (const side of [-1, 1]) {
            const horn = new THREE.Mesh(
              new THREE.ConeGeometry(1.3, 5, 8),
              new THREE.MeshLambertMaterial({ color: new THREE.Color(hat.color) })
            )
            horn.position.set(side * 3, HEAD_TOP + 2, 0)
            horn.rotation.z = side * -0.4
            head.add(horn)
          }
          break
        }
        case 'antenna': {
          const stalk = box(0.5, 4.5, 0.5, accent)
          stalk.position.set(0, HEAD_TOP + 2.2, 0)
          const bulb = new THREE.Mesh(
            new THREE.SphereGeometry(1.1, 14, 14),
            new THREE.MeshBasicMaterial({ color: new THREE.Color(hat.color) })
          )
          bulb.position.set(0, HEAD_TOP + 5, 0)
          head.add(stalk, bulb)
          break
        }
        default: {
          const beanie = box(8.8, 4, 8.8, hat.color)
          beanie.position.set(0, HEAD_TOP + 1.6, 0)
          const cuff = box(9, 1.4, 9, accent)
          cuff.position.set(0, HEAD_TOP - 0.6, 0)
          head.add(beanie, cuff)
        }
      }
    }

    if (mask) {
      const plate = box(8.2, 3.2, 0.6, mask.color, 0.96)
      plate.position.set(0, -0.6, FACE_Z + 0.2)
      head.add(plate)

      const accent = box(8.2, 0.6, 0.3, mask.secondary ?? mask.color)
      plate.add(accent)
      accent.position.set(0, -1.2, 0.3)
    }

    if (backpack) {
      const body = box(7, 8.5, 3, backpack.color)
      body.position.set(0, -5.5, BODY_BACK_Z - 1.6)
      group.add(body)

      const pocket = box(5, 3.2, 1, backpack.secondary ?? backpack.color)
      pocket.position.set(0, -7, BODY_BACK_Z - 3.3)
      group.add(pocket)

      for (const side of [-1, 1]) {
        const strap = box(1, 7, 0.6, backpack.secondary ?? backpack.color)
        strap.position.set(side * 2.6, -4.5, BODY_BACK_Z + 0.2)
        group.add(strap)
      }
    }

    if (wings) {
      const primary = new THREE.Color(wings.color)
      const secondary = new THREE.Color(wings.secondary ?? wings.color)

      // Each variant has its own silhouette, built from a shared pivot so the
      // flap animation only ever has to rotate one node per wing.
      const buildWing = (direction: 1 | -1) => {
        const pivot = new THREE.Group()
        pivot.position.set(direction * 1.5, -2, BODY_BACK_Z - 0.4)

        const panel = (
          len: number, thick: number, drop: number, color: THREE.Color,
          tilt: number, opacity: number, depth = 0.5
        ) => {
          const mesh = new THREE.Mesh(
            new THREE.BoxGeometry(len, thick, depth),
            new THREE.MeshLambertMaterial({ color, transparent: opacity < 1, opacity })
          )
          mesh.position.set(direction * (len / 2), drop, 0)
          mesh.rotation.z = direction * tilt
          pivot.add(mesh)
          return mesh
        }

        switch (wings.variant) {
          case 'feather':
            // Long, soft, many overlapping quills.
            for (let i = 0; i < 5; i++) {
              panel(12 - i * 1.6, 2.4 - i * 0.2, -i * 2, i % 2 ? secondary : primary, 0.14 - i * 0.1, 0.95)
            }
            break

          case 'bat': {
            // Membrane with visible finger bones.
            panel(11, 7, -2.5, primary, -0.12, 0.82)
            for (let i = 0; i < 3; i++) {
              panel(10 - i, 0.5, -0.5 - i * 2.4, secondary, -0.1 - i * 0.14, 1)
            }
            break
          }

          case 'insect': {
            // Two translucent, rounded wings.
            for (const [len, thick, drop, op] of [[10, 5, 1, 0.45], [7.5, 4, -3.5, 0.38]] as const) {
              const mesh = new THREE.Mesh(
                new THREE.SphereGeometry(1, 16, 10),
                new THREE.MeshLambertMaterial({
                  color: primary, transparent: true, opacity: op,
                  emissive: secondary, emissiveIntensity: 0.35,
                })
              )
              mesh.scale.set(len / 2, thick / 2, 0.3)
              mesh.position.set(direction * (len / 2), drop, 0)
              pivot.add(mesh)
            }
            break
          }

          case 'mecha': {
            // Hard angular plates with a glowing thruster line.
            panel(11, 3.4, 0, primary, 0.05, 1, 1.2)
            panel(8, 2.6, -3.6, primary, -0.18, 1, 1)
            const thruster = panel(9, 0.7, -1.6, secondary, 0.05, 1, 0.6)
            ;(thruster.material as THREE.MeshLambertMaterial).emissive = secondary
            ;(thruster.material as THREE.MeshLambertMaterial).emissiveIntensity = 0.8
            break
          }

          case 'butterfly': {
            // Big upper wing, smaller lower, with a contrasting border.
            const upper = panel(11, 8, 1.5, primary, 0.1, 0.92)
            const lower = panel(8, 6, -5, primary, -0.16, 0.92)
            for (const mesh of [upper, lower]) {
              const edge = new THREE.Mesh(
                new THREE.BoxGeometry(mesh.geometry.parameters.width, 1, 0.55),
                new THREE.MeshLambertMaterial({ color: secondary })
              )
              edge.position.y = -mesh.geometry.parameters.height / 2
              mesh.add(edge)
            }
            break
          }

          case 'flame': {
            // Tapered plumes that fade out, lit from within.
            for (let i = 0; i < 4; i++) {
              const mesh = panel(11 - i * 2, 3 - i * 0.5, -i * 2.2, i % 2 ? secondary : primary, 0.2 - i * 0.16, 0.8 - i * 0.12)
              const material = mesh.material as THREE.MeshLambertMaterial
              material.emissive = secondary
              material.emissiveIntensity = 0.5
            }
            break
          }

          default: {
            // 'shard' — faceted crystal blades.
            for (let i = 0; i < 3; i++) {
              const shard = new THREE.Mesh(
                new THREE.ConeGeometry(1.6 - i * 0.3, 11 - i * 2.4, 4),
                new THREE.MeshLambertMaterial({
                  color: i % 2 ? secondary : primary,
                  transparent: true,
                  opacity: 0.9,
                  emissive: primary,
                  emissiveIntensity: 0.25,
                })
              )
              shard.rotation.z = direction * (Math.PI / 2 - 0.25 + i * 0.3)
              shard.position.set(direction * (5 - i * 0.6), -i * 2.4, 0)
              pivot.add(shard)
            }
          }
        }

        return pivot
      }

      const right = buildWing(1)
      const left = buildWing(-1)
      group.add(right, left)
      wingRefs.current = { left, right }
    }

    if (aura) {
      // Each variant lays its particles out differently — a tight ring reads
      // nothing like drifting snow, even in the same colour.
      const shapes: Record<string, { count: number; size: number; place: (i: number, n: number) => [number, number, number] }> = {
        ring: {
          count: 80, size: 1.1,
          place: (i, n) => {
            const a = (i / n) * Math.PI * 2
            const r = 8 + (i % 2) * 0.8
            return [Math.cos(a) * r, -6 + (i % 3) * 0.6, Math.sin(a) * r]
          },
        },
        rising: {
          count: 110, size: 1.0,
          place: () => {
            const a = Math.random() * Math.PI * 2
            const r = 2 + Math.random() * 4
            return [Math.cos(a) * r, -18 + Math.random() * 22, Math.sin(a) * r]
          },
        },
        snow: {
          count: 130, size: 0.8,
          place: () => [
            (Math.random() - 0.5) * 22,
            -18 + Math.random() * 26,
            (Math.random() - 0.5) * 22,
          ],
        },
        sphere: {
          count: 120, size: 1.0,
          place: () => {
            const theta = Math.random() * Math.PI * 2
            const phi = Math.acos(2 * Math.random() - 1)
            const r = 9
            return [
              Math.sin(phi) * Math.cos(theta) * r,
              -5 + Math.cos(phi) * r,
              Math.sin(phi) * Math.sin(theta) * r,
            ]
          },
        },
        petals: {
          count: 70, size: 1.5,
          place: () => {
            const a = Math.random() * Math.PI * 2
            const r = 4 + Math.random() * 6
            return [Math.cos(a) * r, -16 + Math.random() * 22, Math.sin(a) * r]
          },
        },
        storm: {
          count: 160, size: 1.2,
          place: (i, n) => {
            // Double helix spiralling up the body.
            const t = i / n
            const a = t * Math.PI * 8 + (i % 2) * Math.PI
            const r = 5 + Math.sin(t * Math.PI) * 4
            return [Math.cos(a) * r, -18 + t * 26, Math.sin(a) * r]
          },
        },
        orbit: {
          count: 90, size: 0.9,
          place: () => {
            const a = Math.random() * Math.PI * 2
            const r = 6 + Math.random() * 4
            return [Math.cos(a) * r, -16 + Math.random() * 20, Math.sin(a) * r]
          },
        },
      }

      const shape = shapes[aura.variant ?? 'orbit'] ?? shapes.orbit
      const positions = new Float32Array(shape.count * 3)
      const phases: number[] = []

      for (let i = 0; i < shape.count; i++) {
        const [x, y, z] = shape.place(i, shape.count)
        positions[i * 3] = x
        positions[i * 3 + 1] = y
        positions[i * 3 + 2] = z
        phases.push(y)
      }

      const geometry = new THREE.BufferGeometry()
      geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))

      const points = new THREE.Points(
        geometry,
        new THREE.PointsMaterial({
          color: new THREE.Color(aura.color),
          size: shape.size,
          transparent: true,
          opacity: 0.85,
          blending: THREE.AdditiveBlending,
          depthWrite: false,
        })
      )
      points.userData.phases = phases
      points.userData.variant = aura.variant ?? 'orbit'
      // Particle layouts were tuned for a 22-unit-tall figure; centre them on the real one.
      points.position.y = -4
      group.add(points)
      auraRef.current = points
    }

    player.add(group)
  }, [hat, bandana, mask, wings, backpack, aura])

  return (
    <div className="relative flex flex-col items-center gap-2">
      <div className="relative" style={{ width, height }}>
        <canvas ref={canvasRef} className="relative cursor-grab active:cursor-grabbing" />
        {!skinDataUrl && (
          <div className="absolute inset-0 flex items-center justify-center text-crystal-muted text-xs pointer-events-none">
            Kein Skin geladen
          </div>
        )}
      </div>

      <div className="flex items-center gap-1.5">
        <button
          onClick={() => setAutoRotate(v => !v)}
          title={autoRotate ? 'Auto-Drehung stoppen' : 'Auto-Drehung starten'}
          className={`p-1.5 rounded-lg border transition-colors ${
            autoRotate ? 'border-crystal-accent text-crystal-accent' : 'border-crystal-border text-crystal-muted hover:text-crystal-text'
          }`}
        >
          <RotateCw size={13} />
        </button>
        <button
          onClick={() => setWalking(v => !v)}
          title={walking ? 'Animation pausieren' : 'Animation abspielen'}
          className={`p-1.5 rounded-lg border transition-colors ${
            walking ? 'border-crystal-accent text-crystal-accent' : 'border-crystal-border text-crystal-muted hover:text-crystal-text'
          }`}
        >
          {walking ? <Pause size={13} /> : <Play size={13} />}
        </button>
        <span className="text-crystal-muted text-[10px] ml-1">Ziehen zum Drehen · Scrollen zum Zoomen</span>
      </div>
    </div>
  )
}
