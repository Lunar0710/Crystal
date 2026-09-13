import Store from 'electron-store'
import { BrowserWindow } from 'electron'
import { Auth, Minecraft } from 'msmc'
import { logger } from '../logs/Logger'

export interface AuthProfile {
  username: string
  uuid: string
  accessToken: string
  type: 'microsoft' | 'offline'
}

// Mirrors RankId in renderer/src/data/ranks.ts. Kept as a plain string union
// here since the main process's tsconfig can't reach into src/renderer.
export type RankId = 'owner' | 'co_owner' | 'admin' | 'staff' | 'developer' | 'media' | 'crystal_plus' | 'member'

export interface RankGrant {
  username: string
  rank: RankId
  grantedAt: number
}

export class AuthManager {
  private store: Store
  private auth: Auth

  constructor(store: Store) {
    this.store = store
    this.auth = new Auth('select_account')
  }

  async loginMicrosoft(win: BrowserWindow): Promise<AuthProfile | null> {
    try {
      const xboxToken = await this.auth.launch('electron', {
        title: 'Crystal Client — Microsoft Login',
        width: 520,
        height: 650,
        parent: win,
      })

      const minecraft = await xboxToken.getMinecraft()
      if (!minecraft.profile) throw new Error('No Minecraft profile found on this account')

      const profile: AuthProfile = {
        username: minecraft.profile.name,
        uuid: minecraft.profile.id,
        accessToken: minecraft.mcToken,
        type: 'microsoft',
      }

      this.store.set('auth.profile', profile)
      this.store.set('auth.xboxCache', xboxToken.save())
      return profile
    } catch (err) {
      logger.error('launcher', 'Microsoft-Login fehlgeschlagen', err)
      return null
    }
  }

  async loginOffline(username: string): Promise<AuthProfile> {
    const profile: AuthProfile = {
      username,
      uuid: this.generateOfflineUUID(username),
      accessToken: 'offline',
      type: 'offline',
    }
    this.store.set('auth.profile', profile)
    return profile
  }

  async tryAutoLogin(): Promise<AuthProfile | null> {
    const cached = this.store.get('auth.xboxCache') as string | undefined
    if (!cached) return null

    try {
      const xboxToken = await this.auth.refresh(cached)
      const minecraft = await xboxToken.getMinecraft()
      if (!minecraft.profile) throw new Error('No Minecraft profile found on this account')

      const profile: AuthProfile = {
        username: minecraft.profile.name,
        uuid: minecraft.profile.id,
        accessToken: minecraft.mcToken,
        type: 'microsoft',
      }
      this.store.set('auth.profile', profile)
      this.store.set('auth.xboxCache', xboxToken.save())
      return profile
    } catch (err) {
      logger.warn('launcher', 'Auto-Login nicht moeglich, Sitzung abgelaufen?', String(err))
      return null
    }
  }

  getStoredProfile(): AuthProfile | null {
    return (this.store.get('auth.profile') as AuthProfile) || null
  }

  logout() {
    this.store.delete('auth.profile')
    this.store.delete('auth.xboxCache')
  }

  // NOTE: There is no Crystal account server yet, so this is a purely local,
  // cosmetic preference — not a real permission system. Nothing server-side
  // verifies it, so it must never be trusted as an authorization check.
  //
  // A grant made here only ever applies on THIS install: it's keyed by
  // username and checked against whichever profile is currently logged in on
  // this machine. Granting a rank to a friend's username does nothing on
  // their own separate install of Crystal — there's no shared backend to sync
  // it to. It's real and useful for managing alts/testing on one device, and
  // for owner's own rank; it is not a way to remotely rank other people.
  getRank(): RankId {
    const profile = this.getStoredProfile()
    if (profile) {
      const grant = this.getGrants()[profile.username.toLowerCase()]
      if (grant) return grant.rank
    }
    return (this.store.get('account.rank') as RankId) || 'member'
  }

  setRank(rank: RankId) {
    this.store.set('account.rank', rank)
  }

  getGrants(): Record<string, RankGrant> {
    return (this.store.get('account.rankGrants') as Record<string, RankGrant>) || {}
  }

  /** Only ever call this after confirming the caller's own rank is 'owner' — see ipc.ts. */
  grantRank(username: string, rank: RankId): void {
    const grants = this.getGrants()
    grants[username.toLowerCase()] = { username, rank, grantedAt: Date.now() }
    this.store.set('account.rankGrants', grants)
  }

  revokeGrant(username: string): void {
    const grants = this.getGrants()
    delete grants[username.toLowerCase()]
    this.store.set('account.rankGrants', grants)
  }

  private generateOfflineUUID(username: string): string {
    let hash = 0
    for (let i = 0; i < username.length; i++) {
      hash = (Math.imul(31, hash) + username.charCodeAt(i)) | 0
    }
    const hex = Math.abs(hash).toString(16).padStart(8, '0')
    return `${hex.slice(0, 8)}-${hex.slice(0, 4)}-3${hex.slice(1, 4)}-8${hex.slice(0, 3)}-${hex}0000`
  }
}
