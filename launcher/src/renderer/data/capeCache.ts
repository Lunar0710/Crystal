import { BUILTIN_CAPES, renderCapeTexture } from './capes'

/**
 * Draws every built-in cape once into cosmetics/cape-cache/<id>.png. Other
 * Crystal players' capes arrive in game as an id only (see PeerCapes.java),
 * and this is where the game finds the picture for it. Only Crystal's own
 * designs are in here: an uploaded cape never reaches anyone else.
 *
 * Redrawn when the launcher version or the number of capes changes, a few
 * capes at a time so the launcher stays responsive.
 */
const api = (window as any).crystal

/** Other players are small on screen; 4x (256x128) is plenty. */
const MAX_SCALE = 4
const BATCH = 6

export async function fillCapeCache(): Promise<void> {
  if (!api?.capeCacheStatus) return
  const status: { version: string; current: string } = await api.capeCacheStatus(BUILTIN_CAPES.length)
  if (status.version === status.current) return

  for (let i = 0; i < BUILTIN_CAPES.length; i += BATCH) {
    for (const cape of BUILTIN_CAPES.slice(i, i + BATCH)) {
      try {
        await api.cacheCape(cape.id, renderCapeTexture(cape, MAX_SCALE))
      } catch {
        // One broken design must not stop the rest.
      }
    }
    await new Promise(resolve => setTimeout(resolve, 30))
  }
  await api.capeCacheDone(status.current)
}
