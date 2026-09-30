import React, { useRef, useEffect, useState } from 'react'
import * as skinview3d from 'skinview3d'
import * as THREE from 'three'
import { RotateCw, Pause, Play } from 'lucide-react'
import { CosmeticDef, CosmeticVariants, resolveCosmetic, variantOf } from '../../data/cosmetics'
import { shapeFor, ShapeBox, PET_POS } from '../../data/cosmeticShapes'
import { buildModel, animateModel, disposeObject, BuiltModel, auraSpriteUrl } from '../../data/cosmeticModels'
import { spriteTexture } from '../../data/cosmeticThumbs'

const COSMETIC_GROUP = 'crystal-cosmetics'

// Cosmetics hang off skinview3d's SkinObject (playerObject.skin), whose space
// is (skinview3d 3.4 model.js): head 0..8, body -12..0, legs down to -24,
// negative z is behind the player. Head pieces are authored around the head's
// centre and placed in a group raised to y=4; see HEAD_CENTER_Y below.
// Attaching to playerObject instead (as before) put everything one head too
// low, because the skin itself sits at y=8 inside the player object.
const HEAD_CENTER_Y = 4
const BODY_BACK_Z = -2

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
  pet,
  variants,
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
  pet?: CosmeticDef | null
  /** Chosen colour variants, by item id. */
  variants?: CosmeticVariants
  width?: number
  height?: number
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const viewerRef = useRef<skinview3d.SkinViewer | null>(null)
  const wingRefs = useRef<{ left: THREE.Object3D; right: THREE.Object3D } | null>(null)
  const auraRef = useRef<THREE.Points | null>(null)
  const petRef = useRef<THREE.Object3D | null>(null)
  const frameRef = useRef<number>(0)
  const modelsRef = useRef<BuiltModel[]>([])
  const walkingRef = useRef(true)

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

      // Wings flap faster and wider while walking, like in-game (CosmeticModelRenderer.flap).
      const move = walkingRef.current ? 0.6 : 0
      const flap = Math.sin(t * (2 + move * 2.2)) * (0.16 + move * 0.2)
      if (wingRefs.current) {
        // Swept back (-z is behind the player here).
        const sweep = 0.8 + flap
        wingRefs.current.right.rotation.y = sweep
        wingRefs.current.left.rotation.y = -sweep
      }
      for (const built of modelsRef.current) animateModel(built, { time: t, move, flap })

      if (petRef.current) {
        // Bobs and looks around slowly, the same motion as in-game.
        petRef.current.position.y = PET_POS.y + Math.sin(t * 2) * 0.8
        petRef.current.rotation.y = Math.sin(t * 0.6) * 0.35
      }

      if (auraRef.current) {
        const points = auraRef.current
        const variant = points.userData.variant as string
        const positions = points.geometry.getAttribute('position') as THREE.BufferAttribute
        const phases = (points.userData.phases as number[]) || []

        points.rotation.y = t * (variant === 'ring' ? 1.4 : variant === 'storm' || variant === 'bolts' ? 0.9 : variant === 'leaves' ? 0.7 : 0.5)

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
            // Bubbles rise slowly and wobble.
            case 'bubbles':
              y = ((base + t * 3.2 + i) % 24) - 18 + Math.sin(t * 3 + i) * 0.5
              break
            // Bolts flicker in place at two heights.
            case 'bolts':
              y = base + (Math.sin(t * 9 + i * 3) > 0.6 ? 1.6 : 0)
              break
            // Notes float up and sway.
            case 'notes':
              y = ((base + t * 4 + i) % 22) - 14
              break
            // Leaves spiral down.
            case 'leaves':
              y = 8 - (((8 - base) + t * 2.4 + i) % 24)
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
      modelsRef.current = []
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
    walkingRef.current = walking
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

    const skin = viewer.playerObject.skin
    const player = skin as unknown as THREE.Object3D
    // Old cosmetics hang off the skin, its head and its body.
    for (const holder of [player, skin.head as unknown as THREE.Object3D, skin.body as unknown as THREE.Object3D]) {
      let previous = holder.getObjectByName(COSMETIC_GROUP)
      while (previous) {
        previous.removeFromParent()
        disposeObject(previous)
        previous = holder.getObjectByName(COSMETIC_GROUP)
      }
    }
    wingRefs.current = null
    auraRef.current = null
    petRef.current = null
    modelsRef.current = []

    const group = new THREE.Group()
    group.name = COSMETIC_GROUP
    // Hat, bandana and mask coordinates are relative to the head's centre.
    // They ride on the head itself, so they turn and nod with it.
    const head = new THREE.Group()
    head.name = COSMETIC_GROUP
    head.position.y = HEAD_CENTER_Y
    ;(skin.head as unknown as THREE.Object3D).add(head)

    // A model cosmetic, in its chosen variant, hung on a parent at its origin.
    const addModel = (parent: THREE.Object3D, def: CosmeticDef) => {
      const built = buildModel(def.model!, variantOf(def, variants)?.id)
      if (!built) return false
      parent.add(built.root)
      modelsRef.current.push(built)
      return true
    }

    // One shape source for preview and game (data/cosmeticShapes.ts).
    const addBoxes = (parent: THREE.Object3D, boxes: ShapeBox[], mirror = 1) => {
      for (const b of boxes) {
        const material = new THREE.MeshLambertMaterial({ color: new THREE.Color(b.color) })
        if (b.glow) { material.emissive = new THREE.Color(b.color); material.emissiveIntensity = 0.6 }
        const mesh = new THREE.Mesh(new THREE.BoxGeometry(b.w, b.h, b.d), material)
        mesh.position.set(b.x * mirror, b.y, b.z)
        if (b.rz) mesh.rotation.z = b.rz * mirror
        parent.add(mesh)
      }
    }

    for (const raw of [hat, bandana, mask]) {
      if (raw?.model && addModel(head, raw)) continue
      const def = resolveCosmetic(raw ?? null, variants)
      const shape = def ? shapeFor(def) : null
      if (shape) addBoxes(head, shape.boxes)
    }
    if (backpack?.model) addModel(group, backpack)
    else {
      const packShape = backpack ? shapeFor(resolveCosmetic(backpack, variants)!) : null
      if (packShape) addBoxes(group, packShape.boxes)
    }

    if (pet) {
      const holder = new THREE.Group()
      holder.position.set(PET_POS.x, PET_POS.y, PET_POS.z)
      if (!(pet.model && addModel(holder, pet))) {
        const petShape = shapeFor(resolveCosmetic(pet, variants)!)
        if (petShape) addBoxes(holder, petShape.boxes)
      }
      group.add(holder)
      petRef.current = holder
    }

    const wingShape = wings && !wings.model ? shapeFor(resolveCosmetic(wings, variants)!) : null
    if (wingShape || wings?.model) {
      const makeWing = (side: 1 | -1) => {
        // Hinge on the upper back; boxes extend along +x and are mirrored for the left wing.
        const pivot = new THREE.Group()
        pivot.position.set(side * 1.5, -2.5, BODY_BACK_Z - 0.6)
        if (wingShape) addBoxes(pivot, wingShape.boxes, side)
        else {
          // A model wing is one right wing; the left one is its mirror image.
          const holder = new THREE.Group()
          holder.scale.x = side
          addModel(holder, wings!)
          pivot.add(holder)
        }
        return pivot
      }
      const right = makeWing(1)
      const left = makeWing(-1)
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

      // Each aura's own pixel-art particle (a snowflake, a flame, a heart...), as in game.
      const sprite = auraSpriteUrl(aura.model)
      const points = new THREE.Points(
        geometry,
        sprite
          ? new THREE.PointsMaterial({ map: spriteTexture(sprite), size: shape.size * 2.4, alphaTest: 0.5, transparent: false })
          : new THREE.PointsMaterial({
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
  }, [hat, bandana, mask, wings, backpack, aura, pet, variants])

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
