import Store from 'electron-store'
import { logger } from '../logs/Logger'
import type { RankGrant } from './AuthManager'

// Electron ships Node 18+'s global fetch, but this tsconfig's lib is ES2020
// only (no DOM), so it's declared locally rather than pulling in a DOM lib.
declare function fetch(url: string, init?: {
  method?: string
  headers?: Record<string, string>
  body?: string
}): Promise<{ ok: boolean; status: number; json(): Promise<any>; text(): Promise<string> }>

const OWNER = 'Lunar0710'
const REPO = 'Nexora'
const FILE = 'ranks.json'

const RAW_URL = `https://raw.githubusercontent.com/${OWNER}/${REPO}/main/${FILE}`
const API_URL = `https://api.github.com/repos/${OWNER}/${REPO}/contents/${FILE}`

/** Raw ranks.json shape. Keys are lowercased Minecraft usernames. */
interface RanksFile {
  updatedAt: number
  grants: Record<string, RankGrant>
}

/**
 * Shared rank storage with no server to run: ranks.json lives in the public
 * Nexora repo, every launcher READS it over raw.githubusercontent.com, and
 * only the owner's own machine can WRITE it (via a GitHub token that is
 * entered locally and never ships inside the installer).
 *
 * This is cosmetic-only, exactly like the local grants it replaces — nothing
 * here is an authorization boundary, it just decides which badge someone sees.
 */
export class RankSyncService {
  private store: Store
  private inFlight: Promise<unknown> | null = null

  constructor(store: Store) {
    this.store = store
  }

  /**
   * Resolves once the startup fetch has finished. On a brand new install the
   * cache is empty, so answering getRank() before the first fetch lands would
   * report "member" to someone who actually has a published rank — they'd only
   * see it after restarting the launcher.
   */
  async ready(): Promise<void> {
    if (this.inFlight) await this.inFlight.catch(() => {})
  }

  /**
   * Re-reads the published ranks periodically. Without this a rank stays in
   * place for the whole session after the owner revokes it — the launcher
   * would only notice on its next start.
   */
  startAutoRefresh(intervalMs = 3 * 60 * 1000): void {
    setInterval(() => { this.fetchRemoteGrants() }, intervalMs).unref?.()
  }

  // --- token (owner's machine only) ---------------------------------------

  getToken(): string | null {
    return (this.store.get('ranks.githubToken') as string) || null
  }

  setToken(token: string): void {
    const trimmed = token.trim()
    if (trimmed) this.store.set('ranks.githubToken', trimmed)
    else this.store.delete('ranks.githubToken')
  }

  hasToken(): boolean {
    return !!this.getToken()
  }

  // --- reading (every launcher) -------------------------------------------

  /** Last successfully fetched remote grants — used when offline so ranks don't flicker away. */
  getCachedRemoteGrants(): Record<string, RankGrant> {
    return (this.store.get('ranks.remoteCache') as Record<string, RankGrant>) || {}
  }

  /**
   * Fetches the published ranks. Falls back to the last cached copy on any
   * failure (offline, rate limit, file not created yet) rather than wiping
   * everyone's rank because a request failed.
   */
  fetchRemoteGrants(): Promise<Record<string, RankGrant>> {
    const promise = this.doFetch()
    this.inFlight = promise
    return promise
  }

  private async doFetch(): Promise<Record<string, RankGrant>> {
    try {
      // Cache-busting query: raw.githubusercontent.com caches for ~5 minutes,
      // which would otherwise hide a grant that was just published.
      const res = await fetch(`${RAW_URL}?t=${Date.now()}`)
      if (!res.ok) {
        if (res.status !== 404) logger.warn('launcher', `Rang-Sync: HTTP ${res.status}`)
        return this.getCachedRemoteGrants()
      }

      const data = JSON.parse(await res.text()) as RanksFile
      const grants = data?.grants || {}
      this.store.set('ranks.remoteCache', grants)
      return grants
    } catch (err) {
      logger.warn('launcher', 'Rang-Sync fehlgeschlagen, nutze lokalen Cache', String(err))
      return this.getCachedRemoteGrants()
    }
  }

  // --- writing (owner only) -----------------------------------------------

  /**
   * Publishes the full grant map to the repo. Requires the local token;
   * without one this is a no-op so a normal user's launcher never tries
   * (and never could) write.
   */
  async publish(grants: Record<string, RankGrant>): Promise<{ ok: boolean; error?: string }> {
    const token = this.getToken()
    if (!token) return { ok: false, error: 'Kein GitHub-Token hinterlegt.' }

    const headers = {
      Authorization: `token ${token}`,
      Accept: 'application/vnd.github+json',
      'User-Agent': 'crystal-launcher',
    }

    try {
      // The Contents API needs the current blob sha to replace an existing
      // file; its absence (404) just means we're creating it the first time.
      let sha: string | undefined
      const current = await fetch(API_URL, { headers })
      if (current.ok) sha = (await current.json())?.sha

      const payload: RanksFile = { updatedAt: Date.now(), grants }
      const content = Buffer.from(JSON.stringify(payload, null, 2)).toString('base64')

      const res = await fetch(API_URL, {
        method: 'PUT',
        headers: { ...headers, 'Content-Type': 'application/json' },
        body: JSON.stringify({
          message: `Update ranks (${Object.keys(grants).length} grants)`,
          content,
          ...(sha ? { sha } : {}),
        }),
      })

      if (!res.ok) {
        const detail = await res.text()
        logger.error('launcher', `Rang-Veröffentlichung fehlgeschlagen: HTTP ${res.status} ${detail}`)
        return { ok: false, error: `GitHub antwortete mit HTTP ${res.status}` }
      }

      this.store.set('ranks.remoteCache', grants)
      logger.info('launcher', `Ränge veröffentlicht (${Object.keys(grants).length} Einträge)`)
      return { ok: true }
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Unbekannter Fehler'
      logger.error('launcher', 'Rang-Veröffentlichung fehlgeschlagen', err)
      return { ok: false, error: message }
    }
  }
}
