import Store from 'electron-store'
import { BrowserWindow } from 'electron'
import { Auth, Minecraft } from 'msmc'
import { logger } from '../logs/Logger'
import { seal, unseal, isSealed, canSeal } from '../util/secureStore'

export interface AuthProfile {
  username: string
  uuid: string
  accessToken: string
  type: 'microsoft' | 'offline'
}

// Mirrors RankId in renderer/src/data/ranks.ts. Kept as a plain string union
// here since the main process's tsconfig can't reach into src/renderer.
export type RankId = 'owner' | 'co_owner' | 'admin' | 'staff' | 'developer' | 'media' | 'crystal_plus' | 'member'

/** One saved account. xboxCache is the refreshable Microsoft session; offline accounts have none. */
interface StoredAccount {
  profile: AuthProfile
  xboxCache?: string
}

/** Expiry (ms) read from a Minecraft access token, which is a JWT; null when it can't be read. */
function tokenExpiry(token: string): number | null {
  try {
    const payload = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf8'))
    return typeof payload.exp === 'number' ? payload.exp * 1000 : null
  } catch {
    return null
  }
}

export interface RankGrant {
  username: string
  rank: RankId
  grantedAt: number
  /** Absent means permanent. Expired grants are dropped the next time getGrants() reads the store. */
  expiresAt?: number
  /**
   * Tester, on top of the rank: unlocks features still being tried out (the
   * auto builder) in the game. A tester without a rank is a grant with rank 'member'.
   */
  tester?: boolean
}

export class AuthManager {
  private store: Store
  private auth: Auth

  constructor(store: Store) {
    this.store = store
    this.auth = new Auth('select_account')
    this.sealExisting()
  }

  /** Account data is stored sealed (see secureStore); plain values from older versions are sealed once. */
  private get<T>(key: string): T | undefined {
    return unseal<T>(this.store.get(key))
  }

  private put(key: string, value: unknown): void {
    this.store.set(key, seal(value))
  }

  private sealExisting(): void {
    if (!canSeal()) {
      logger.warn('launcher', 'Keine System-Verschlüsselung verfügbar: Anmeldedaten bleiben unverschlüsselt gespeichert')
      return
    }
    let sealed = 0
    for (const key of ['auth.accounts', 'auth.profile', 'auth.xboxCache']) {
      const raw = this.store.get(key)
      if (raw !== undefined && raw !== null && !isSealed(raw)) { this.store.set(key, seal(raw)); sealed++ }
    }
    if (sealed) logger.info('launcher', 'Anmeldedaten jetzt verschlüsselt gespeichert')
  }

  async loginMicrosoft(win: BrowserWindow): Promise<AuthProfile | null> {
    try {
      const xboxToken = await this.auth.launch('electron', {
        title: 'Nexora: Mit Microsoft anmelden',
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

      this.setActive(profile, xboxToken.save())
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
    this.setActive(profile)
    return profile
  }

  // --- multiple accounts ---------------------------------------------------
  //
  // Every account lives in auth.accounts; the active one is also mirrored to
  // auth.profile / auth.xboxCache, which is what launching, ranks and
  // cosmetics already read. Keeping that mirror means adding multi-account
  // support didn't have to touch any of those call sites.

  listAccounts(): AuthProfile[] {
    return this.getAccounts().map(a => a.profile)
  }

  private getAccounts(): StoredAccount[] {
    const accounts = this.get<StoredAccount[]>('auth.accounts')
    if (accounts) return accounts

    // Installs from before multi-account only have the single active login —
    // carry it over so it shows up in the list instead of looking logged out.
    const legacy = this.getStoredProfile()
    if (!legacy) return []
    const migrated: StoredAccount[] = [{ profile: legacy, xboxCache: this.get<string>('auth.xboxCache') }]
    this.put('auth.accounts', migrated)
    return migrated
  }

  /** Stores the profile as the active account, replacing any earlier entry for the same UUID. */
  private setActive(profile: AuthProfile, xboxCache?: string) {
    const accounts = this.getAccounts().filter(a => a.profile.uuid !== profile.uuid)
    accounts.push({ profile, xboxCache })
    this.put('auth.accounts', accounts)

    this.put('auth.profile', profile)
    if (xboxCache) this.put('auth.xboxCache', xboxCache)
    else this.store.delete('auth.xboxCache')
  }

  /**
   * Switches to an already-added account. Microsoft accounts get their token
   * refreshed, since a stored access token is short-lived and launching with
   * a stale one fails at Mojang's session server rather than here.
   */
  async switchAccount(uuid: string): Promise<AuthProfile | null> {
    const account = this.getAccounts().find(a => a.profile.uuid === uuid)
    if (!account) return null

    if (account.profile.type === 'offline' || !account.xboxCache) {
      this.setActive(account.profile, account.xboxCache)
      return account.profile
    }

    try {
      const xboxToken = await this.auth.refresh(account.xboxCache)
      const minecraft = await xboxToken.getMinecraft()
      if (!minecraft.profile) throw new Error('No Minecraft profile found on this account')

      const profile: AuthProfile = {
        username: minecraft.profile.name,
        uuid: minecraft.profile.id,
        accessToken: minecraft.mcToken,
        type: 'microsoft',
      }
      this.setActive(profile, xboxToken.save())
      return profile
    } catch (err) {
      logger.warn('launcher', `Konto ${account.profile.username} konnte nicht aktualisiert werden`, String(err))
      // Still switch to it — the user gets a clear session error on launch
      // rather than a silent no-op here.
      this.setActive(account.profile, account.xboxCache)
      return account.profile
    }
  }

  removeAccount(uuid: string): void {
    const remaining = this.getAccounts().filter(a => a.profile.uuid !== uuid)
    this.put('auth.accounts', remaining)

    if (this.getStoredProfile()?.uuid !== uuid) return

    // The active account was the one removed — fall back to another, or to
    // logged-out, so the launcher never points at an account that's gone.
    const next = remaining[0]
    if (next) this.setActive(next.profile, next.xboxCache)
    else this.logout()
  }

  async tryAutoLogin(): Promise<AuthProfile | null> {
    const cached = this.get<string>('auth.xboxCache')
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
      this.setActive(profile, xboxToken.save())
      return profile
    } catch (err) {
      logger.warn('launcher', 'Auto-Login nicht moeglich, Sitzung abgelaufen?', String(err))
      return null
    }
  }

  /**
   * The active profile with a Minecraft token that is still valid, refreshing
   * it first when needed. Called right before every launch.
   *
   * A Minecraft access token lives about 24 hours. The launcher used to pass
   * the token saved at login straight to the game, so a day later every
   * multiplayer server rejected the join with "Invalid session" (singleplayer
   * never checks it, which hid the problem).
   *
   * Returns an error string instead when a Microsoft account can't be
   * refreshed, so the launch stops with a clear message rather than failing
   * later on the server.
   */
  async ensureFreshProfile(uuid?: string): Promise<{ profile: AuthProfile | null; error?: string }> {
    const active = this.getStoredProfile()
    if (uuid && uuid !== active?.uuid) return this.freshProfileOf(uuid)
    const profile = active
    if (!profile || profile.type !== 'microsoft') return { profile }

    // Keep a token with more than 30 minutes left; anything shorter is renewed
    // so joins early in the session don't hit the expiry.
    const expiresAt = tokenExpiry(profile.accessToken)
    if (expiresAt !== null && expiresAt - Date.now() > 30 * 60 * 1000) return { profile }

    const cached = this.get<string>('auth.xboxCache')
    if (!cached) {
      return { profile: null, error: 'Deine Microsoft-Anmeldung ist abgelaufen. Bitte melde dich neu an.' }
    }

    try {
      const xboxToken = await this.auth.refresh(cached)
      const minecraft = await xboxToken.getMinecraft()
      if (!minecraft.profile) throw new Error('No Minecraft profile found on this account')

      const fresh: AuthProfile = {
        username: minecraft.profile.name,
        uuid: minecraft.profile.id,
        accessToken: minecraft.mcToken,
        type: 'microsoft',
      }
      this.setActive(fresh, xboxToken.save())
      logger.info('launcher', `Microsoft-Sitzung für ${fresh.username} erneuert`)
      return { profile: fresh }
    } catch (err) {
      logger.warn('launcher', 'Microsoft-Sitzung konnte nicht erneuert werden', String(err))
      return {
        profile: null,
        error: 'Deine Microsoft-Anmeldung konnte nicht erneuert werden. Prüfe deine Internetverbindung oder melde dich neu an.',
      }
    }
  }

  /**
   * Like ensureFreshProfile, for an added account that is not the active one
   * (an instance that always plays on its own account). The active account
   * stays as it is.
   */
  private async freshProfileOf(uuid: string): Promise<{ profile: AuthProfile | null; error?: string }> {
    const account = this.getAccounts().find(a => a.profile.uuid === uuid)
    if (!account) {
      return { profile: null, error: 'Das Konto, mit dem diese Instanz startet, ist nicht mehr angemeldet. Wähle bei der Instanz ein anderes Konto.' }
    }
    if (account.profile.type !== 'microsoft') return { profile: account.profile }
    const expiresAt = tokenExpiry(account.profile.accessToken)
    if (expiresAt !== null && expiresAt - Date.now() > 30 * 60 * 1000) return { profile: account.profile }
    if (!account.xboxCache) {
      return { profile: null, error: `Die Anmeldung von ${account.profile.username} ist abgelaufen. Bitte melde das Konto neu an.` }
    }
    try {
      const xboxToken = await this.auth.refresh(account.xboxCache)
      const minecraft = await xboxToken.getMinecraft()
      if (!minecraft.profile) throw new Error('No Minecraft profile found on this account')
      const fresh: AuthProfile = {
        username: minecraft.profile.name,
        uuid: minecraft.profile.id,
        accessToken: minecraft.mcToken,
        type: 'microsoft',
      }
      const accounts = this.getAccounts().map(a => a.profile.uuid === uuid ? { profile: fresh, xboxCache: xboxToken.save() } : a)
      this.put('auth.accounts', accounts)
      logger.info('launcher', `Microsoft-Sitzung für ${fresh.username} erneuert`)
      return { profile: fresh }
    } catch (err) {
      logger.warn('launcher', `Sitzung von ${account.profile.username} konnte nicht erneuert werden`, String(err))
      return { profile: null, error: `Die Anmeldung von ${account.profile.username} konnte nicht erneuert werden. Prüfe deine Internetverbindung oder melde das Konto neu an.` }
    }
  }

  getStoredProfile(): AuthProfile | null {
    return this.get<AuthProfile>('auth.profile') || null
  }

  logout() {
    this.store.delete('auth.profile')
    this.store.delete('auth.xboxCache')
  }

  // NOTE: There is no Nexora account server yet, so this is a purely local,
  // cosmetic preference — not a real permission system. Nothing server-side
  // verifies it, so it must never be trusted as an authorization check.
  //
  // A grant made here only ever applies on THIS install: it's keyed by
  // username and checked against whichever profile is currently logged in on
  // this machine. Granting a rank to a friend's username does nothing on
  // their own separate install of Nexora — there's no shared backend to sync
  // it to. It's real and useful for managing alts/testing on one device, and
  // for owner's own rank; it is not a way to remotely rank other people.
  getRank(): RankId {
    const profile = this.getStoredProfile()
    if (profile) {
      const key = profile.username.toLowerCase()

      // A locally granted rank wins over the published one, so the owner can
      // test a rank on this machine without publishing it to everyone.
      const local = this.getGrants()[key]
      if (local) return local.rank

      const remote = this.getRemoteGrant(key)
      if (remote) return remote.rank
    }
    return (this.store.get('account.rank') as RankId) || 'member'
  }

  /** Whether the logged-in player may try test features: the owner, and anyone marked tester. */
  isTester(): boolean {
    if (this.getRank() === 'owner') return true
    const profile = this.getStoredProfile()
    if (!profile) return false
    const key = profile.username.toLowerCase()
    return !!(this.getGrants()[key]?.tester || this.getRemoteGrant(key)?.tester)
  }

  /** Only ever call this after confirming the caller is the owner — see ipc.ts. */
  setTester(username: string, tester: boolean): void {
    const grants = this.getGrants()
    const key = username.toLowerCase()
    const grant = grants[key]
    if (grant) {
      if (tester) grant.tester = true
      else delete grant.tester
      // A tester-only entry has nothing left once the tester mark goes.
      if (!tester && grant.rank === 'member') delete grants[key]
    } else if (tester) {
      grants[key] = { username, rank: 'member', grantedAt: Date.now(), tester: true }
    }
    this.store.set('account.rankGrants', grants)
  }

  /** Published grants mirrored from the repo by RankSyncService — expired ones are ignored. */
  private getRemoteGrant(usernameKey: string): RankGrant | null {
    const remote = (this.store.get('ranks.remoteCache') as Record<string, RankGrant>) || {}
    const grant = remote[usernameKey]
    if (!grant) return null
    if (grant.expiresAt && grant.expiresAt <= Date.now()) return null
    return grant
  }

  setRank(rank: RankId) {
    this.store.set('account.rank', rank)
  }

  getGrants(): Record<string, RankGrant> {
    const stored = (this.store.get('account.rankGrants') as Record<string, RankGrant>) || {}
    const now = Date.now()
    let changed = false
    for (const key of Object.keys(stored)) {
      if (stored[key].expiresAt && stored[key].expiresAt! <= now) {
        delete stored[key]
        changed = true
      }
    }
    if (changed) this.store.set('account.rankGrants', stored)
    return stored
  }

  /**
   * Only ever call this after confirming the caller's own rank is 'owner' — see ipc.ts.
   * durationMs is optional; omit it for a permanent grant.
   */
  grantRank(username: string, rank: RankId, durationMs?: number): void {
    const grants = this.getGrants()
    const previous = grants[username.toLowerCase()]
    grants[username.toLowerCase()] = {
      username,
      rank,
      grantedAt: Date.now(),
      ...(durationMs ? { expiresAt: Date.now() + durationMs } : {}),
      // A new rank keeps the tester mark.
      ...(previous?.tester ? { tester: true } : {}),
    }
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
