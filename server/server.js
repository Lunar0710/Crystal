'use strict'
/**
 * Crystal server: lets Crystal players see each other's emotes and cosmetics.
 *
 * Every client proves who it is the way Minecraft servers do it: it asks
 * Mojang to "join" a random server id, and we ask Mojang whether that player
 * really did. After that the client says which Minecraft server it is on
 * (as a hash, the address itself never leaves the PC) and only players on the
 * same one hear about each other.
 *
 * Nothing is stored: when the process restarts, clients reconnect and send
 * their state again.
 *
 * Environment:
 *   PORT          port to listen on (default 8787)
 *   OWNERS        comma separated Minecraft names with the owner rank
 *   RANKS_URL     published ranks.json (default: the Crystal repo)
 *   FAKE_AUTH=1   tests only: skip the Mojang check
 */
const http = require('http')
const crypto = require('crypto')
const { WebSocketServer } = require('ws')
const { cleanLoadout, cleanCape, EMOTES } = require('./sanitize')
const { RankBook } = require('./ranks')

const PORT = Number(process.env.PORT) || 8787
const FAKE_AUTH = process.env.FAKE_AUTH === '1'
const PROTOCOL = 1

/** Largest message a client may send; a loadout is a few KB. */
const MAX_MESSAGE_BYTES = 16 * 1024
/** Emotes per client, per 10 seconds. */
const EMOTE_LIMIT = 12
/** Loadout updates per client, per minute. */
const LOADOUT_LIMIT = 20
/** A client has this long to prove who it is. */
const AUTH_TIMEOUT_MS = 15000

const ranks = new RankBook({
  url: process.env.RANKS_URL,
  owners: (process.env.OWNERS || '').split(',').map(s => s.trim()).filter(Boolean),
})

/** uuid -> number of authenticated connections, for the friends list's "playing now" */
const online = new Map()

/** room hash -> Set of authenticated clients in it */
const rooms = new Map()

/** Asks Mojang whether `name` joined `serverId`; resolves to { id, name } or null. */
function hasJoined(name, serverId) {
  if (FAKE_AUTH) {
    const id = crypto.createHash('md5').update('fake:' + name).digest('hex')
    return Promise.resolve({ id, name })
  }
  const url = 'https://sessionserver.mojang.com/session/minecraft/hasJoined'
    + `?username=${encodeURIComponent(name)}&serverId=${encodeURIComponent(serverId)}`
  return fetch(url, { signal: AbortSignal.timeout(8000) })
    .then(res => (res.status === 200 ? res.json() : null))
    .then(body => (body && typeof body.id === 'string' ? { id: body.id, name: body.name } : null))
    .catch(() => null)
}

/** Mojang's undashed id to the dashed form Java's UUID.toString() gives. */
function dashed(id) {
  return `${id.slice(0, 8)}-${id.slice(8, 12)}-${id.slice(12, 16)}-${id.slice(16, 20)}-${id.slice(20)}`
}

function send(ws, message) {
  if (ws.readyState === ws.OPEN) ws.send(JSON.stringify(message))
}

function publicState(client) {
  return { uuid: client.uuid, name: client.name, cape: client.cape, items: client.items, emote: client.emote }
}

function broadcast(room, except, message) {
  const members = rooms.get(room)
  if (!members) return
  const text = JSON.stringify(message)
  for (const other of members) {
    if (other !== except && other.ws.readyState === other.ws.OPEN) other.ws.send(text)
  }
}

function leaveRoom(client) {
  if (!client.room) return
  const members = rooms.get(client.room)
  if (members) {
    members.delete(client)
    if (members.size === 0) rooms.delete(client.room)
  }
  broadcast(client.room, client, { t: 'gone', uuid: client.uuid })
  client.room = null
}

function enterRoom(client, room) {
  if (client.room === room) return
  leaveRoom(client)
  if (!room) return
  client.room = room
  if (!rooms.has(room)) rooms.set(room, new Set())
  const members = rooms.get(room)
  send(client.ws, { t: 'peers', peers: [...members].map(publicState) })
  members.add(client)
  broadcast(room, client, { t: 'peer', ...publicState(client) })
}

/** Sliding window limiter: true while under `limit` events per `windowMs`. */
function allow(bucket, limit, windowMs) {
  const now = Date.now()
  while (bucket.length && now - bucket[0] > windowMs) bucket.shift()
  if (bucket.length >= limit) return false
  bucket.push(now)
  return true
}

async function handle(client, message) {
  if (!client.uuid) {
    if (message.t !== 'hello' || typeof message.name !== 'string' || !/^[A-Za-z0-9_]{1,16}$/.test(message.name)) {
      return client.ws.close(4001, 'bad hello')
    }
    if (client.checking) return
    client.checking = true
    const profile = await hasJoined(message.name, client.serverId)
    if (!profile) return client.ws.close(4003, 'not verified')
    client.uuid = dashed(profile.id)
    client.name = profile.name
    client.rank = ranks.rankOf(profile.name)
    clearTimeout(client.authTimer)
    online.set(client.uuid, (online.get(client.uuid) || 0) + 1)
    send(client.ws, { t: 'welcome', uuid: client.uuid, rank: client.rank })
    return
  }

  switch (message.t) {
    case 'where': {
      // A hash of the Minecraft server address, or null in singleplayer/menus.
      const room = typeof message.room === 'string' && /^[0-9a-f]{16,64}$/.test(message.room) ? message.room : null
      enterRoom(client, room)
      break
    }
    case 'loadout': {
      if (!allow(client.loadoutTimes, LOADOUT_LIMIT, 60000)) return
      client.rank = ranks.rankOf(client.name)
      client.cape = cleanCape(message.cape, client.rank)
      client.items = cleanLoadout(message.items, client.rank)
      if (client.room) broadcast(client.room, client, { t: 'peer', ...publicState(client) })
      break
    }
    case 'emote': {
      if (!allow(client.emoteTimes, EMOTE_LIMIT, 10000)) return
      // Emotes are a Crystal+ perk; without it they are simply not passed on.
      const id = EMOTES.includes(message.id) && ranks.hasPerks(client.rank) ? message.id : null
      client.emote = id
      if (client.room) broadcast(client.room, client, { t: 'emote', uuid: client.uuid, id })
      break
    }
    case 'ping':
      send(client.ws, { t: 'pong' })
      break
    default:
      break
  }
}

function start(port = PORT) {
  const server = http.createServer((req, res) => {
    // Which of up to 100 players (dashed uuids) are playing with Nexora right
    // now. Only that yes or no, never where: the room stays private.
    const url = new URL(req.url || '/', 'http://localhost')
    if (url.pathname === '/presence') {
      const asked = (url.searchParams.get('u') || '').split(',').filter(u => /^[0-9a-f-]{32,36}$/i.test(u)).slice(0, 100)
      res.writeHead(200, { 'content-type': 'application/json', 'access-control-allow-origin': '*' })
      res.end(JSON.stringify({ online: asked.filter(u => online.has(u.toLowerCase())) }))
      return
    }
    // Plain HTTP answers a health check; everything else is the WebSocket.
    res.writeHead(200, { 'content-type': 'application/json' })
    let players = 0
    for (const members of rooms.values()) players += members.size
    res.end(JSON.stringify({ ok: true, protocol: PROTOCOL, rooms: rooms.size, players }))
  })
  const wss = new WebSocketServer({ server, maxPayload: MAX_MESSAGE_BYTES })

  wss.on('connection', ws => {
    const client = {
      ws, uuid: null, name: null, rank: 'member', room: null,
      cape: null, items: {}, emote: null,
      serverId: crypto.randomBytes(10).toString('hex'),
      emoteTimes: [], loadoutTimes: [], checking: false,
    }
    ws.isAlive = true
    client.authTimer = setTimeout(() => { if (!client.uuid) ws.close(4002, 'auth timeout') }, AUTH_TIMEOUT_MS)
    send(ws, { t: 'challenge', serverId: client.serverId, protocol: PROTOCOL })

    ws.on('pong', () => { ws.isAlive = true })
    // Oversized or broken frames arrive here. Without a listener Node treats
    // the error as unhandled and one bad client would take the server down.
    ws.on('error', err => {
      // ws closes the connection itself afterwards (1009 for oversized frames).
      console.warn('connection dropped:', err.code || err.message)
    })
    ws.on('message', data => {
      let message
      try {
        message = JSON.parse(data.toString())
      } catch {
        return ws.close(4000, 'not json')
      }
      if (!message || typeof message !== 'object') return
      handle(client, message).catch(err => console.error('message failed:', err))
    })
    ws.on('close', () => {
      clearTimeout(client.authTimer)
      leaveRoom(client)
      if (client.uuid) {
        const left = (online.get(client.uuid) || 1) - 1
        if (left > 0) online.set(client.uuid, left)
        else online.delete(client.uuid)
      }
    })
  })

  // Drops connections that stopped answering (sleeping PC, lost Wi-Fi).
  const heartbeat = setInterval(() => {
    for (const ws of wss.clients) {
      if (!ws.isAlive) { ws.terminate(); continue }
      ws.isAlive = false
      ws.ping()
    }
  }, 30000)

  ranks.start()
  return new Promise(resolve => server.listen(port, () => {
    console.log(`Crystal server on port ${server.address().port}${FAKE_AUTH ? ' (FAKE_AUTH, tests only)' : ''}`)
    resolve({ server, wss, close: () => { ranks.stop(); clearInterval(heartbeat); wss.close(); server.close() } })
  }))
}

if (require.main === module) start()

module.exports = { start }
