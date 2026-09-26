/**
 * Nexora server on Cloudflare: one Worker in front of one Durable Object
 * ("Hub") that holds every connection, the same job server.js does as a Node
 * process, plus friends and chat.
 *
 * What it does:
 *  - checks who a client is the way Minecraft servers do (Mojang hasJoined);
 *  - lets game clients on the same Minecraft server (a "room", only ever sent
 *    as a hash) see each other's emotes and cosmetics;
 *  - friends: requests by name, accept, decline, remove; both sides must agree;
 *  - chat between friends, kept (the last 200 messages per pair) so messages
 *    reach friends who are offline, with unread counts;
 *  - presence: whether a friend is offline, in the launcher, or in the game.
 *
 * The Hub uses the WebSocket hibernation API, so an idle server costs nothing:
 * what each connection needs is kept in its attachment, not in memory.
 *
 * Environment (wrangler.toml [vars] or secrets):
 *   OWNERS     comma separated Minecraft names with the owner rank
 *   RANKS_URL  published ranks.json (default: the Crystal repo)
 *   FAKE_AUTH  "1" for tests only: skip the Mojang check
 */
import { cleanCape, cleanLoadout, EMOTES } from '../../sanitize.js'
import { RankBook } from '../../ranks.js'

const PROTOCOL = 2
const MAX_MESSAGE_BYTES = 16 * 1024
const EMOTE_LIMIT = 12          // per 10 s
const LOADOUT_LIMIT = 20        // per minute
const CHAT_LIMIT = 10           // per 10 s
const FRIEND_LIMIT = 20         // friend actions per minute
const MAX_FRIENDS = 200
const MAX_PENDING = 100
const KEEP_MESSAGES = 200
const MAX_CHAT_CHARS = 500
const AUTH_TIMEOUT_MS = 15000
const NAME = /^[A-Za-z0-9_]{1,16}$/
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/
/** A Minecraft server address as the game shows it: host with an optional port. */
const SERVER_ADDRESS = /^[A-Za-z0-9.-]{1,253}(:\d{1,5})?$/
const INVITE_LIMIT = 10 // per minute

export default {
  async fetch(request, env) {
    // A single hub: everyone who plays Nexora meets there.
    const hub = env.HUB.get(env.HUB.idFromName('hub'))
    return hub.fetch(request)
  },
}

/** Mojang's undashed id to the dashed form Java's UUID.toString() gives. */
function dashed(id) {
  return `${id.slice(0, 8)}-${id.slice(8, 12)}-${id.slice(12, 16)}-${id.slice(16, 20)}-${id.slice(20)}`.toLowerCase()
}

async function fakeId(name) {
  const bytes = await crypto.subtle.digest('SHA-256', new TextEncoder().encode('fake:' + name.toLowerCase()))
  return [...new Uint8Array(bytes)].slice(0, 16).map(b => b.toString(16).padStart(2, '0')).join('')
}

function randomHex(bytes) {
  return [...crypto.getRandomValues(new Uint8Array(bytes))].map(b => b.toString(16).padStart(2, '0')).join('')
}

const pairKey = (a, b) => (a < b ? `chat:${a}|${b}` : `chat:${b}|${a}`)

export class Hub {
  constructor(ctx, env) {
    this.ctx = ctx
    this.env = env
    this.fake = env.FAKE_AUTH === '1'
    this.ranks = new RankBook({
      url: env.RANKS_URL,
      owners: String(env.OWNERS || '').split(',').map(s => s.trim()).filter(Boolean),
    })
    this.ranksAt = 0
    /** Rate limit buckets per connection; lost when the hub sleeps, which is fine. */
    this.buckets = new WeakMap()
  }

  // ------------------------------------------------------------ plumbing

  async rankOf(name) {
    if (Date.now() - this.ranksAt > 5 * 60 * 1000) {
      this.ranksAt = Date.now()
      await this.ranks.refresh()
    }
    return this.ranks.rankOf(name)
  }

  info(ws) { return ws.deserializeAttachment() || {} }
  save(ws, info) { ws.serializeAttachment(info) }

  send(ws, message) {
    try { ws.send(JSON.stringify(message)) } catch { /* closed meanwhile */ }
  }

  /** Every verified connection of one player. */
  socketsOf(uuid) {
    return this.ctx.getWebSockets().filter(ws => this.info(ws).uuid === uuid)
  }

  sendTo(uuid, message) {
    for (const ws of this.socketsOf(uuid)) this.send(ws, message)
  }

  /** "game" when any of the player's connections is the game, "launcher" when only the launcher is, else "offline". */
  statusOf(uuid, except) {
    let status = 'offline'
    for (const ws of this.ctx.getWebSockets()) {
      if (ws === except) continue
      const i = this.info(ws)
      if (i.uuid !== uuid) continue
      if (i.client === 'game') return 'game'
      status = 'launcher'
    }
    return status
  }

  allow(ws, key, limit, windowMs) {
    let all = this.buckets.get(ws)
    if (!all) { all = {}; this.buckets.set(ws, all) }
    const bucket = all[key] || (all[key] = [])
    const now = Date.now()
    while (bucket.length && now - bucket[0] > windowMs) bucket.shift()
    if (bucket.length >= limit) return false
    bucket.push(now)
    return true
  }

  async hasJoined(name, serverId) {
    if (this.fake) return { id: await fakeId(name), name }
    const url = 'https://sessionserver.mojang.com/session/minecraft/hasJoined'
      + `?username=${encodeURIComponent(name)}&serverId=${encodeURIComponent(serverId)}`
    try {
      const res = await fetch(url, { signal: AbortSignal.timeout(8000) })
      if (res.status !== 200) return null
      const body = await res.json()
      return body && typeof body.id === 'string' ? { id: body.id, name: body.name } : null
    } catch {
      return null
    }
  }

  /** A Minecraft name to { uuid, name }: players seen here first, then Mojang. */
  async lookup(name) {
    const known = await this.ctx.storage.get(`name:${name.toLowerCase()}`)
    if (known) {
      const user = await this.ctx.storage.get(`user:${known}`)
      return { uuid: known, name: user?.name || name }
    }
    if (this.fake) return { uuid: dashed(await fakeId(name)), name }
    try {
      const res = await fetch(`https://api.mojang.com/users/profiles/minecraft/${encodeURIComponent(name)}`, { signal: AbortSignal.timeout(8000) })
      if (res.status !== 200) return null
      const body = await res.json()
      return body && typeof body.id === 'string' ? { uuid: dashed(body.id), name: body.name } : null
    } catch {
      return null
    }
  }

  async user(uuid) {
    return (await this.ctx.storage.get(`user:${uuid}`)) || { uuid, name: null, friends: [], incoming: [], outgoing: [], unread: {} }
  }

  async putUser(user) {
    await this.ctx.storage.put(`user:${user.uuid}`, user)
  }

  async names(uuids) {
    const out = {}
    const keys = uuids.map(u => `user:${u}`)
    for (let i = 0; i < keys.length; i += 128) {
      const got = await this.ctx.storage.get(keys.slice(i, i + 128))
      for (const [key, user] of got) out[key.slice(5)] = user?.name || null
    }
    return out
  }

  // ------------------------------------------------------------ HTTP

  async fetch(request) {
    const url = new URL(request.url)
    if (request.headers.get('Upgrade') === 'websocket') {
      const pair = new WebSocketPair()
      const [client, server] = Object.values(pair)
      this.ctx.acceptWebSocket(server)
      const serverId = randomHex(10)
      this.save(server, { serverId, opened: Date.now() })
      this.send(server, { t: 'challenge', serverId, protocol: PROTOCOL })
      // Unverified connections are dropped by the next alarm after the auth timeout.
      const alarm = await this.ctx.storage.getAlarm()
      if (!alarm) await this.ctx.storage.setAlarm(Date.now() + AUTH_TIMEOUT_MS)
      return new Response(null, { status: 101, webSocket: client })
    }
    if (url.pathname === '/presence') {
      // Which of up to 100 players (dashed uuids) are playing with Nexora right
      // now. Only that yes or no, never where: the room stays private.
      const asked = (url.searchParams.get('u') || '').split(',').map(u => u.toLowerCase()).filter(u => UUID.test(u)).slice(0, 100)
      const online = asked.filter(u => this.statusOf(u) !== 'offline')
      return Response.json({ online }, { headers: { 'access-control-allow-origin': '*' } })
    }
    const sockets = this.ctx.getWebSockets()
    const rooms = new Set(sockets.map(ws => this.info(ws).room).filter(Boolean))
    return Response.json({ ok: true, protocol: PROTOCOL, rooms: rooms.size, players: sockets.filter(ws => this.info(ws).room).length })
  }

  async alarm() {
    const now = Date.now()
    let pending = false
    for (const ws of this.ctx.getWebSockets()) {
      const i = this.info(ws)
      if (i.uuid) continue
      if (now - (i.opened || 0) >= AUTH_TIMEOUT_MS) { try { ws.close(4002, 'auth timeout') } catch { } } else pending = true
    }
    if (pending) await this.ctx.storage.setAlarm(now + AUTH_TIMEOUT_MS)
  }

  // ------------------------------------------------------------ WebSocket

  async webSocketMessage(ws, data) {
    if (typeof data !== 'string' || data.length > MAX_MESSAGE_BYTES) return ws.close(1009, 'too big')
    let message
    try { message = JSON.parse(data) } catch { return ws.close(4000, 'not json') }
    if (!message || typeof message !== 'object') return
    try {
      await this.handle(ws, message)
    } catch (err) {
      console.error('message failed:', err && err.stack || err)
    }
  }

  async webSocketClose(ws) { await this.left(ws) }
  async webSocketError(ws) { await this.left(ws) }

  async left(ws) {
    const i = this.info(ws)
    if (i.room) this.broadcast(i.room, ws, { t: 'gone', uuid: i.uuid })
    if (i.uuid) await this.announce(i.uuid, ws)
  }

  broadcast(room, except, message) {
    const text = JSON.stringify(message)
    for (const ws of this.ctx.getWebSockets()) {
      if (ws === except || this.info(ws).room !== room) continue
      try { ws.send(text) } catch { }
    }
  }

  publicState(i) {
    return { uuid: i.uuid, name: i.name, cape: i.cape || null, items: i.items || {}, emote: i.emote || null }
  }

  /** Tells the player's friends how they are now (online, in game, gone). */
  async announce(uuid, except) {
    const user = await this.user(uuid)
    const status = this.statusOf(uuid, except)
    for (const friend of user.friends) this.sendTo(friend, { t: 'presence', uuid, status })
  }

  async handle(ws, m) {
    const i = this.info(ws)
    if (!i.uuid) {
      if (m.t !== 'hello' || typeof m.name !== 'string' || !NAME.test(m.name)) return ws.close(4001, 'bad hello')
      if (i.checking) return
      i.checking = true
      this.save(ws, i)
      const profile = await this.hasJoined(m.name, i.serverId)
      if (!profile) return ws.close(4003, 'not verified')
      const uuid = dashed(profile.id)
      const rank = await this.rankOf(profile.name)
      const client = m.client === 'launcher' ? 'launcher' : 'game'
      this.save(ws, { ...i, uuid, name: profile.name, rank, client, checking: false })
      // Remember the name, so friends can add this player by it.
      const user = await this.user(uuid)
      if (user.name !== profile.name) {
        if (user.name) await this.ctx.storage.delete(`name:${user.name.toLowerCase()}`)
        user.name = profile.name
        await this.putUser(user)
      }
      await this.ctx.storage.put(`name:${profile.name.toLowerCase()}`, uuid)
      this.send(ws, { t: 'welcome', uuid, rank, protocol: PROTOCOL })
      await this.announce(uuid)
      return
    }

    switch (m.t) {
      // ---------------------------------------------------- game: rooms, looks, emotes
      case 'where': {
        const room = typeof m.room === 'string' && /^[0-9a-f]{16,64}$/.test(m.room) ? m.room : null
        if (i.room === room) return
        if (i.room) this.broadcast(i.room, ws, { t: 'gone', uuid: i.uuid })
        i.room = room
        this.save(ws, i)
        if (room) {
          const peers = this.ctx.getWebSockets().filter(o => o !== ws && this.info(o).room === room).map(o => this.publicState(this.info(o)))
          this.send(ws, { t: 'peers', peers })
          this.broadcast(room, ws, { t: 'peer', ...this.publicState(i) })
        }
        return
      }
      case 'loadout': {
        if (!this.allow(ws, 'loadout', LOADOUT_LIMIT, 60000)) return
        i.rank = await this.rankOf(i.name)
        i.cape = cleanCape(m.cape, i.rank)
        i.items = cleanLoadout(m.items, i.rank)
        this.save(ws, i)
        if (i.room) this.broadcast(i.room, ws, { t: 'peer', ...this.publicState(i) })
        return
      }
      case 'emote': {
        if (!this.allow(ws, 'emote', EMOTE_LIMIT, 10000)) return
        // Emotes are a Nexora+ perk; without it they are simply not passed on.
        const id = EMOTES.includes(m.id) && this.ranks.hasPerks(i.rank) ? m.id : null
        i.emote = id
        this.save(ws, i)
        if (i.room) this.broadcast(i.room, ws, { t: 'emote', uuid: i.uuid, id })
        return
      }
      case 'ping':
        return this.send(ws, { t: 'pong' })

      // ---------------------------------------------------- playing together
      case 'server': {
        // The game says which server it is on. Kept with the connection, never
        // passed on by itself: only an invite the player sends reveals it.
        i.server = typeof m.address === 'string' && SERVER_ADDRESS.test(m.address) ? m.address.toLowerCase() : null
        this.save(ws, i)
        return
      }
      case 'invite': {
        if (!this.allow(ws, 'invite', INVITE_LIMIT, 60000)) return this.send(ws, { t: 'error', code: 'slow', message: 'Zu viele Einladungen, warte kurz.' })
        const to = String(m.to || '').toLowerCase()
        const mine = await this.user(i.uuid)
        if (!mine.friends.includes(to)) return this.send(ws, { t: 'error', code: 'notfriend', message: 'Einladen kannst du nur Freunde.' })
        // The server of whichever game this player has open.
        const game = this.socketsOf(i.uuid).map(s => this.info(s)).find(x => x.client === 'game' && x.server)
        if (!game) return this.send(ws, { t: 'error', code: 'noserver', message: 'Du bist gerade auf keinem Server. Tritt erst einem bei, dann kannst du einladen.' })
        if (this.statusOf(to) === 'offline') return this.send(ws, { t: 'error', code: 'offline', message: 'Dein Freund ist gerade offline.' })
        this.sendTo(to, { t: 'invite', from: { uuid: i.uuid, name: i.name }, server: game.server, at: Date.now() })
        return this.send(ws, { t: 'invite-sent', to, server: game.server })
      }

      // ---------------------------------------------------- friends
      case 'friends':
        return this.send(ws, await this.friendList(i.uuid))
      case 'friend-add': {
        if (!this.allow(ws, 'friend', FRIEND_LIMIT, 60000)) return this.send(ws, { t: 'error', code: 'slow', message: 'Zu viele Anfragen, warte kurz.' })
        const name = typeof m.name === 'string' ? m.name.trim() : ''
        if (!NAME.test(name)) return this.send(ws, { t: 'error', code: 'name', message: 'Das ist kein gültiger Minecraft-Name.' })
        const target = await this.lookup(name)
        if (!target) return this.send(ws, { t: 'error', code: 'unknown', message: `Einen Spieler "${name}" gibt es nicht.` })
        return this.request(ws, i.uuid, target)
      }
      case 'friend-accept': return this.accept(ws, i.uuid, m.uuid)
      case 'friend-decline': return this.decline(i.uuid, m.uuid)
      case 'friend-remove': return this.remove(i.uuid, m.uuid)

      // ---------------------------------------------------- chat
      case 'chat-send': return this.chatSend(ws, i, m)
      case 'chat-history': {
        const other = String(m.with || '').toLowerCase()
        if (!UUID.test(other)) return
        const messages = (await this.ctx.storage.get(pairKey(i.uuid, other))) || []
        return this.send(ws, { t: 'chat-history', with: other, messages })
      }
      case 'chat-read': {
        const other = String(m.with || '').toLowerCase()
        const user = await this.user(i.uuid)
        if (user.unread && user.unread[other]) {
          delete user.unread[other]
          await this.putUser(user)
        }
        return this.sendTo(i.uuid, { t: 'unread', unread: user.unread || {} })
      }
      default:
        return
    }
  }

  async friendList(uuid) {
    const user = await this.user(uuid)
    const names = await this.names([...user.friends, ...user.incoming, ...user.outgoing])
    const row = u => ({ uuid: u, name: names[u] || null, status: this.statusOf(u) })
    return {
      t: 'friends',
      friends: user.friends.map(row),
      incoming: user.incoming.map(u => ({ uuid: u, name: names[u] || null })),
      outgoing: user.outgoing.map(u => ({ uuid: u, name: names[u] || null })),
      unread: user.unread || {},
    }
  }

  async pushLists(...uuids) {
    for (const u of uuids) if (this.socketsOf(u).length) this.sendTo(u, await this.friendList(u))
  }

  async request(ws, me, target) {
    if (target.uuid === me) return this.send(ws, { t: 'error', code: 'self', message: 'Du kannst dich nicht selbst hinzufügen.' })
    const mine = await this.user(me)
    const theirs = await this.user(target.uuid)
    if (!theirs.name) theirs.name = target.name
    if (mine.friends.includes(target.uuid)) return this.send(ws, { t: 'error', code: 'already', message: `${target.name} ist schon dein Freund.` })
    // They already asked us: that is a yes from both sides.
    if (mine.incoming.includes(target.uuid)) return this.accept(ws, me, target.uuid)
    if (mine.outgoing.includes(target.uuid)) return this.send(ws, { t: 'error', code: 'pending', message: `Die Anfrage an ${target.name} ist schon unterwegs.` })
    if (mine.friends.length >= MAX_FRIENDS || mine.outgoing.length >= MAX_PENDING || theirs.incoming.length >= MAX_PENDING) {
      return this.send(ws, { t: 'error', code: 'full', message: 'Die Freundesliste ist voll.' })
    }
    mine.outgoing.push(target.uuid)
    theirs.incoming.push(me)
    await this.putUser(mine)
    await this.putUser(theirs)
    await this.ctx.storage.put(`name:${target.name.toLowerCase()}`, target.uuid)
    this.sendTo(target.uuid, { t: 'friend-request', from: { uuid: me, name: mine.name } })
    await this.pushLists(me, target.uuid)
  }

  async accept(ws, me, other) {
    other = String(other || '').toLowerCase()
    const mine = await this.user(me)
    if (!mine.incoming.includes(other)) return
    const theirs = await this.user(other)
    mine.incoming = mine.incoming.filter(u => u !== other)
    theirs.outgoing = theirs.outgoing.filter(u => u !== me)
    if (!mine.friends.includes(other)) mine.friends.push(other)
    if (!theirs.friends.includes(me)) theirs.friends.push(me)
    await this.putUser(mine)
    await this.putUser(theirs)
    this.sendTo(other, { t: 'friend-accepted', by: { uuid: me, name: mine.name } })
    await this.pushLists(me, other)
  }

  async decline(me, other) {
    other = String(other || '').toLowerCase()
    const mine = await this.user(me)
    const theirs = await this.user(other)
    // Also withdraws a request of our own.
    mine.incoming = mine.incoming.filter(u => u !== other)
    mine.outgoing = mine.outgoing.filter(u => u !== other)
    theirs.outgoing = theirs.outgoing.filter(u => u !== me)
    theirs.incoming = theirs.incoming.filter(u => u !== me)
    await this.putUser(mine)
    await this.putUser(theirs)
    await this.pushLists(me, other)
  }

  async remove(me, other) {
    other = String(other || '').toLowerCase()
    const mine = await this.user(me)
    const theirs = await this.user(other)
    mine.friends = mine.friends.filter(u => u !== other)
    theirs.friends = theirs.friends.filter(u => u !== me)
    if (mine.unread) delete mine.unread[other]
    if (theirs.unread) delete theirs.unread[me]
    await this.putUser(mine)
    await this.putUser(theirs)
    await this.pushLists(me, other)
  }

  async chatSend(ws, i, m) {
    const to = String(m.to || '').toLowerCase()
    const text = typeof m.text === 'string' ? m.text.replace(/[\u0000-\u001f\u007f]/g, ' ').trim().slice(0, MAX_CHAT_CHARS) : ''
    if (!UUID.test(to) || !text) return
    if (!this.allow(ws, 'chat', CHAT_LIMIT, 10000)) return this.send(ws, { t: 'error', code: 'slow', message: 'Du schreibst zu schnell.' })
    const mine = await this.user(i.uuid)
    if (!mine.friends.includes(to)) return this.send(ws, { t: 'error', code: 'notfriend', message: 'Nachrichten gehen nur an Freunde.' })
    const message = { id: randomHex(8), from: i.uuid, to, name: i.name, text, at: Date.now() }
    const key = pairKey(i.uuid, to)
    const history = (await this.ctx.storage.get(key)) || []
    history.push(message)
    await this.ctx.storage.put(key, history.slice(-KEEP_MESSAGES))
    const theirs = await this.user(to)
    theirs.unread = theirs.unread || {}
    theirs.unread[i.uuid] = (theirs.unread[i.uuid] || 0) + 1
    await this.putUser(theirs)
    this.sendTo(i.uuid, { t: 'chat', message })
    this.sendTo(to, { t: 'chat', message })
    this.sendTo(to, { t: 'unread', unread: theirs.unread })
  }
}
