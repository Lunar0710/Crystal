/**
 * Build-time data for the Lunar Cosmetics addon (addon/), which runs without
 * the launcher and so can't have it draw capes or write catalog.json:
 *
 *   catalog      the in-game menu's catalog (buildGameCatalog), everything
 *                selectable; the Nexora server still applies ranks for what
 *                other players see
 *   cape/<id>    every built-in cape as a still texture (animated ones: frame 0)
 *   anim/<id>    animated capes as a strip of ANIMATION_FRAMES textures
 *
 * Team capes stay out: they mark Nexora staff and creators.
 * Bundled and run by addon.cjs in a hidden Electron window.
 */
import { BUILTIN_CAPES, renderCapeTexture, capeAnimationStrip } from '../../../src/renderer/data/capes'
import { ANIMATION_FRAMES, ANIMATION_FPS } from '../../../src/renderer/data/animatedCapes'
import { buildGameCatalog } from '../../../src/renderer/data/cosmetics'

declare global { interface Window { shots: Record<string, string>; catalog: string; done: boolean; error?: string } }
window.shots = {}

/** Other players are small on screen and capes are 64x32 at heart: 4x (256x128) is plenty. */
const SCALE = 4

function main() {
  const capes = BUILTIN_CAPES.filter(c => c.category !== 'team')
  for (const cape of capes) {
    window.shots[`cape/${cape.id}`] = renderCapeTexture(cape, SCALE)
    if (cape.animate) window.shots[`anim/${cape.id}`] = capeAnimationStrip(cape, SCALE)
  }
  const catalog = buildGameCatalog({
    canUse: () => true,
    capes: capes.map(c => ({
      id: c.id, name: c.name, category: c.category, locked: false,
      ...(c.animate ? { frames: ANIMATION_FRAMES, fps: ANIMATION_FPS } : {}),
    })),
    outfits: [],
  })
  window.catalog = JSON.stringify(catalog)
  window.done = true
}

try {
  main()
} catch (e: any) {
  window.error = String(e?.stack || e)
  window.done = true
}
