import Store from 'electron-store'
import { BrowserWindow, Notification } from 'electron'
import WebSocket from 'ws'
import crypto from 'crypto'
import { AuthManager } from '../auth/AuthManager'
import { crystalServerAddress } from '../cosmetics/CrystalServer'
import { FriendManager } from './FriendManager'
import { logger } from '../logs/Logger'

/**
 * The account's chat-signing key pair from Mojang, kept until shortly before
 * it expires (a day or so). Only on this PC; the server sees the public half.
 */
let certificate: { uuid: string; privateKey: crypto.KeyObject; publicKey: string; keySignature: string; expiresAt: number } | null = null

function pemToDer(pem: string): Buffer {
  return Buffer.from(pem.replace(/-----[^-]+-----/g, '').replace(/\s+/g, ''), 'base64')
}

/**
 * What the Nexora server needs to know it's really this account, without
 * asking Mojang (see server/worker/src/proof.js): the Mojang-signed public
 * key, the challenge signed with its private key, and the Mojang-signed
 * profile that carries the name.
 */
async function loginProof(accessToken: string, uuid: string, serverId: string) {
  const plainUuid = uuid.replace(/-/g, '').toLowerCase()
  if (!certificate || certificate.uuid !== plainUuid || certificate.expiresAt - Date.now() < 10 * 60 * 1000) {
    const res = await fetch('https://api.minecraftservices.com/player/certificates', {
      method: 'POST',
      headers: { Authorization: `Bearer ${accessToken}`, 'content-type': 'application/json' },
      signal: AbortSignal.timeout(10000),
    } as any)
    if (!res.ok) throw new Error(`Zertifikat: ${res.status}`)
    const cert = await res.json() as any
    certificate = {
      uuid: plainUuid,
      // Mojang labels them "RSA" keys, but the contents are PKCS#8 and X.509.
      privateKey: crypto.createPrivateKey({ key: pemToDer(cert.keyPair.privateKey), format: 'der', type: 'pkcs8' }),
      publicKey: pemToDer(cert.keyPair.publicKey).toString('base64'),
      keySignature: String(cert.publicKeySignatureV2),
      expiresAt: Date.parse(cert.expiresAt),
    }
  }
  const profile = await fetch(`https://sessionserver.mojang.com/session/minecraft/profile/${plainUuid}?unsigned=false`, { signal: AbortSignal.timeout(10000) } as any)
  if (!profile.ok) throw new Error(`Profil: ${profile.status}`)
  const textures = ((await profile.json()) as any).properties?.find((p: any) => p.name === 'textures')
  if (!textures?.signature) throw new Error('Profil ohne Signatur')
  return {
    uuid: plainUuid,
    publicKey: certificate.publicKey,
    keySignature: certificate.keySignature,
    expiresAt: certificate.expiresAt,
    signature: crypto.sign('sha256', Buffer.from(`nexora-login:${serverId}`), certificate.privateKey).toString('base64'),
    textures: { value: textures.value, signature: textures.signature },
  }
}

/** How the launcher's own connection to the Nexora server is doing. */
export type NetState = 'no-server' | 'offline-account' | 'no-account' | 'connecting' | 'online' | 'error'

export interface SocialFriend { uuid: string; name: string | null; status: 'offline' | 'launcher' | 'game' }
export interface SocialPerson { uuid: string; name: string | null }
export interface ChatMessage { id: string; from: string; to: string; name: string; text: string; at: number }

export interface SocialSnapshot {
  state: NetState
  me: { uuid: string; name: string } | null
  friends: SocialFriend[]
  incoming: SocialPerson[]
  outgoing: SocialPerson[]
  unread: Record<string, number>
}

/**
 * The launcher's own connection to the Nexora server, for friends and chat.
 * It proves who you are the way the game does: Mojang is told this account
 * "joins" the server's challenge, and the server asks Mojang whether it did.
 * Offline accounts can't do that, so they get no friends or chat.
 *
 * Everything the server says is kept here (friends, requests, unread counts)
 * and passed to the window as "social:event"; the Friends page asks for the
 * snapshot when it opens.
 */
export class NexoraNet {
  private ws: WebSocket | null = null
  private state: NetState = 'connecting'
  private me: SocialSnapshot['me'] = null
  private friends: SocialFriend[] = []
  private incoming: SocialPerson[] = []
  private outgoing: SocialPerson[] = []
  private unread: Record<string, number> = {}
  private retry = 0
  private timer: NodeJS.Timeout | null = null
  private pingTimer: NodeJS.Timeout | null = null
  private stopped = false

  constructor(private store: Store, private auth: AuthManager, private localFriends: FriendManager) {}

  start(): void {
    this.stopped = false
    this.connect()
  }

  /** Reconnect from scratch: after a login, an account switch or a new server address. */
  restart(): void {
    this.close()
    this.retry = 0
    this.start()
  }

  close(): void {
    this.stopped = true
    if (this.timer) clearTimeout(this.timer)
    if (this.pingTimer) clearInterval(this.pingTimer)
    this.timer = this.pingTimer = null
    try { this.ws?.close() } catch { }
    this.ws = null
  }

  snapshot(): SocialSnapshot {
    return { state: this.state, me: this.me, friends: this.friends, incoming: this.incoming, outgoing: this.outgoing, unread: this.unread }
  }

  /** Passes a request from the Friends page on (only the kinds the page may send). */
  send(message: Record<string, unknown>): boolean {
    const allowed = ['friends', 'friend-add', 'friend-accept', 'friend-decline', 'friend-remove', 'chat-send', 'chat-history', 'chat-read', 'invite']
    if (!message || !allowed.includes(String(message.t))) return false
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN || this.state !== 'online') return false
    this.ws.send(JSON.stringify(message))
    return true
  }

  private setState(state: NetState): void {
    if (this.state === state) return
    this.state = state
    this.emit({ t: 'state', ...this.snapshot() })
  }

  private emit(event: unknown): void {
    for (const w of BrowserWindow.getAllWindows()) if (!w.isDestroyed()) w.webContents.send('social:event', event)
  }

  private later(): void {
    if (this.stopped) return
    // 2 s, 4 s, 8 s ... up to a minute between tries.
    const delay = Math.min(60000, 2000 * 2 ** Math.min(5, this.retry++))
    this.timer = setTimeout(() => this.connect(), delay)
  }

  private connect(): void {
    if (this.stopped) return
    const address = crystalServerAddress(this.store)
    if (!address) return this.setState('no-server')
    const profile = this.auth.getStoredProfile()
    if (!profile) return this.setState('no-account')
    if (profile.type !== 'microsoft') return this.setState('offline-account')
    this.setState('connecting')
    let ws: WebSocket
    try {
      ws = new WebSocket(address, { handshakeTimeout: 10000, maxPayload: 1024 * 1024 })
    } catch (err) {
      logger.warn('launcher', 'Nexora-Server nicht erreichbar', String(err))
      this.setState('error')
      return this.later()
    }
    this.ws = ws
    ws.on('message', data => this.onMessage(ws, data.toString()).catch(err => logger.warn('launcher', 'Nexora-Server: Nachricht nicht verarbeitet', String(err))))
    ws.on('error', err => logger.debug?.('launcher', 'Nexora-Server Verbindung: ' + String(err)))
    ws.on('close', (code, reason) => {
      // The server says why it closed (not verified, bad hello ...): worth a line in the log.
      if (code >= 4000) logger.warn('launcher', `Nexora-Server hat die Verbindung beendet: ${code} ${reason.toString()}`)
      if (this.ws !== ws) return
      if (this.pingTimer) clearInterval(this.pingTimer)
      this.ws = null
      if (this.state === 'online' || this.state === 'connecting') this.setState('error')
      this.later()
    })
  }

  private async onMessage(ws: WebSocket, raw: string): Promise<void> {
    let m: any
    try { m = JSON.parse(raw) } catch { return }
    switch (m.t) {
      case 'challenge': {
        const { profile } = await this.auth.ensureFreshProfile()
        if (!profile || profile.type !== 'microsoft') { this.setState('offline-account'); ws.close(); return }
        // Proof by Mojang's signatures (see server/worker/src/proof.js): the
        // server can't ask Mojang from Cloudflare. The token stays on this PC.
        const proof = await loginProof(profile.accessToken, profile.uuid, m.serverId).catch(err => {
          logger.warn('launcher', 'Nachweis für den Nexora-Server nicht erstellt', String(err))
          return null
        })
        // The classic check as well, for a server that can reach Mojang.
        await fetch('https://sessionserver.mojang.com/session/minecraft/join', {
          method: 'POST',
          headers: { 'content-type': 'application/json' },
          body: JSON.stringify({ accessToken: profile.accessToken, selectedProfile: profile.uuid.replace(/-/g, ''), serverId: m.serverId }),
          signal: AbortSignal.timeout(10000),
        } as any).catch(() => null)
        ws.send(JSON.stringify({ t: 'hello', name: profile.username, client: 'launcher', proof }))
        return
      }
      case 'welcome': {
        this.retry = 0
        this.me = { uuid: m.uuid, name: this.auth.getStoredProfile()?.username ?? '' }
        this.setState('online')
        ws.send(JSON.stringify({ t: 'friends' }))
        this.pingTimer = setInterval(() => { try { ws.send('{"t":"ping"}') } catch { } }, 30000)
        this.migrateLocalFriends(ws)
        return
      }
      case 'friends':
        this.friends = m.friends ?? []
        this.incoming = m.incoming ?? []
        this.outgoing = m.outgoing ?? []
        this.unread = m.unread ?? {}
        break
      case 'presence':
        this.friends = this.friends.map(f => (f.uuid === m.uuid ? { ...f, status: m.status } : f))
        break
      case 'unread':
        this.unread = m.unread ?? {}
        break
      case 'friend-request':
        this.notify('Freundschaftsanfrage', `${m.from?.name ?? 'Jemand'} möchte dein Freund sein.`)
        break
      case 'friend-accepted':
        this.notify('Neuer Freund', `${m.by?.name ?? 'Jemand'} hat deine Anfrage angenommen.`)
        break
      case 'chat':
        if (m.message && m.message.from !== this.me?.uuid) this.notify(m.message.name, m.message.text)
        break
      case 'invite':
        this.notify('Einladung', `${m.from?.name ?? 'Jemand'} lädt dich auf ${m.server} ein.`)
        break
    }
    this.emit(m)
  }

  /** Shown only while no launcher window has focus, so an open chat doesn't pop up notifications. */
  private notify(title: string, body: string): void {
    if (BrowserWindow.getAllWindows().some(w => !w.isDestroyed() && w.isFocused())) return
    try { if (Notification.isSupported()) new Notification({ title, body: body.slice(0, 200) }).show() } catch { }
  }

  /**
   * Friends added before 1.7 lived only in this launcher. On the first
   * connection each gets a real friend request, once.
   */
  private migrateLocalFriends(ws: WebSocket): void {
    if (this.store.get('friendsMigrated')) return
    const names = this.localFriends.list().map(f => f.username).filter(n => /^[A-Za-z0-9_]{1,16}$/.test(n))
    names.forEach((name, i) => setTimeout(() => { try { ws.send(JSON.stringify({ t: 'friend-add', name })) } catch { } }, i * 400))
    this.store.set('friendsMigrated', true)
  }
}
