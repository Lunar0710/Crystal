declare function fetch(url: string, init?: { headers?: Record<string, string> }): Promise<{
  ok: boolean
  status: number
  json(): Promise<any>
  arrayBuffer(): Promise<ArrayBuffer>
}>

export interface SkinResult {
  success: boolean
  dataUrl?: string
  slim?: boolean
  username?: string
  error?: string
}

const MOJANG_PROFILE = 'https://api.mojang.com/users/profiles/minecraft/'
const SESSION_PROFILE = 'https://sessionserver.mojang.com/session/minecraft/profile/'

/**
 * Resolves a Minecraft username to its skin PNG, returned as a data URI so the
 * renderer can draw it to a canvas (Mojang's texture host isn't reachable from
 * the renderer's CSP, and fetching in main avoids CORS entirely).
 */
export class SkinService {
  private cache = new Map<string, SkinResult>()

  async fetchSkin(username: string): Promise<SkinResult> {
    const key = username.toLowerCase().trim()
    if (!key) return { success: false, error: 'Kein Username angegeben' }

    const cached = this.cache.get(key)
    if (cached) return cached

    try {
      const profileRes = await fetch(MOJANG_PROFILE + encodeURIComponent(key))
      if (!profileRes.ok) {
        return { success: false, error: `Spieler "${username}" nicht gefunden` }
      }
      const { id, name } = await profileRes.json()

      const sessionRes = await fetch(SESSION_PROFILE + id)
      if (!sessionRes.ok) return { success: false, error: 'Profil konnte nicht geladen werden' }

      const session = await sessionRes.json()
      const encoded = session.properties?.[0]?.value
      if (!encoded) return { success: false, error: 'Keine Texturen im Profil' }

      const textures = JSON.parse(Buffer.from(encoded, 'base64').toString()).textures
      const skin = textures?.SKIN
      if (!skin?.url) return { success: false, error: 'Kein Skin hinterlegt' }

      const imageRes = await fetch(skin.url)
      if (!imageRes.ok) return { success: false, error: 'Skin-Download fehlgeschlagen' }

      const base64 = Buffer.from(await imageRes.arrayBuffer()).toString('base64')
      const result: SkinResult = {
        success: true,
        dataUrl: `data:image/png;base64,${base64}`,
        slim: skin.metadata?.model === 'slim',
        username: name,
      }

      this.cache.set(key, result)
      return result
    } catch (err) {
      return { success: false, error: err instanceof Error ? err.message : 'Netzwerkfehler' }
    }
  }
}
