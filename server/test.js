'use strict'
// End-to-end test of the Crystal server with real WebSocket clients.
// Mojang is skipped (FAKE_AUTH); ranks come from a local ranks.json.
process.env.FAKE_AUTH = '1'
const http = require('http')
const assert = require('assert')
const WebSocket = require('ws')

const ranksServer = http.createServer((req, res) => {
  res.writeHead(200, { 'content-type': 'application/json' })
  res.end(JSON.stringify({ grants: {
    plusplayer: { username: 'PlusPlayer', rank: 'crystal_plus', expiresAt: Date.now() + 86400000 },
    expired: { username: 'Expired', rank: 'crystal_plus', expiresAt: Date.now() - 1000 },
  } }))
})

const ROOM = 'a1b2c3d4e5f60718'
const OTHER_ROOM = 'ffffffffffffffff'
const PLUS_CAPE = 'plus-0'
const FREE_CAPE = Object.entries(require('./cape-ranks.json')).find(([, c]) => !c.rank)[0]
const hat = { color: '#ff0000', plusOnly: true, anchor: 'head', boxes: [{ x: 0, y: 8, z: 0, w: 999, h: 2, d: 8, color: '#00ff00' }] }

/** A connected, verified test client that records everything it receives. */
function connect(port, name) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(`ws://127.0.0.1:${port}`)
    const client = { ws, name, inbox: [], uuid: null }
    ws.on('message', data => {
      const m = JSON.parse(data.toString())
      client.inbox.push(m)
      if (m.t === 'challenge') ws.send(JSON.stringify({ t: 'hello', name }))
      if (m.t === 'welcome') { client.uuid = m.uuid; client.rank = m.rank; resolve(client) }
    })
    ws.on('error', reject)
    ws.on('close', code => { client.closed = code })
  })
}
const send = (c, m) => c.ws.send(JSON.stringify(m))
const wait = ms => new Promise(r => setTimeout(r, ms))
const got = (c, t) => c.inbox.filter(m => m.t === t)

let failures = 0
async function check(name, fn) {
  try { await fn(); console.log('PASS', name) } catch (e) { failures++; console.log('FAIL', name, '-', e.message) }
}

;(async () => {
  await new Promise(r => ranksServer.listen(0, r))
  process.env.RANKS_URL = `http://127.0.0.1:${ranksServer.address().port}/ranks.json`
  const { start } = require('./server')
  const srv = await start(0)
  const port = srv.server.address().port
  await wait(300) // ranks.json fetched

  const plus = await connect(port, 'PlusPlayer')
  const member = await connect(port, 'Member')
  const elsewhere = await connect(port, 'Elsewhere')

  await check('Ränge aus ranks.json', () => {
    assert.strictEqual(plus.rank, 'crystal_plus')
    assert.strictEqual(member.rank, 'member')
  })
  await check('UUID im Java-Format (mit Bindestrichen)', () => assert.match(plus.uuid, /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/))

  send(plus, { t: 'loadout', cape: PLUS_CAPE, items: { hat } })
  send(member, { t: 'loadout', cape: PLUS_CAPE, items: { hat } })
  await wait(100)
  send(plus, { t: 'where', room: ROOM })
  await wait(100)
  send(member, { t: 'where', room: ROOM })
  send(elsewhere, { t: 'where', room: OTHER_ROOM })
  await wait(150)

  await check('Neuer Spieler bekommt die Liste der anderen', () => {
    const peers = got(member, 'peers').at(-1).peers
    assert.strictEqual(peers.length, 1)
    assert.strictEqual(peers[0].uuid, plus.uuid)
  })
  await check('Crystal+ Cape und Plus-Hut bleiben beim Crystal+ Spieler', () => {
    const p = got(member, 'peers').at(-1).peers[0]
    assert.strictEqual(p.cape, PLUS_CAPE)
    assert.ok(p.items.hat, 'Hut fehlt')
    assert.strictEqual(p.items.hat.boxes[0].w, 32, 'übergroße Box nicht begrenzt')
  })
  await check('Ohne Rang: Plus-Cape und Plus-Hut werden entfernt', () => {
    const p = got(plus, 'peer').find(m => m.uuid === member.uuid)
    assert.ok(p, 'kein peer-Eintrag')
    assert.strictEqual(p.cape, null)
    assert.strictEqual(p.items.hat, undefined)
  })

  send(member, { t: 'loadout', cape: FREE_CAPE, items: {} })
  send(member, { t: 'loadout', cape: 'custom:hochgeladen', items: {} })
  await wait(100)
  await check('Freies Cape geht durch, hochgeladenes nie', () => {
    const updates = got(plus, 'peer').filter(m => m.uuid === member.uuid)
    assert.strictEqual(updates.at(-2).cape, FREE_CAPE)
    assert.strictEqual(updates.at(-1).cape, null)
  })

  send(plus, { t: 'emote', id: 'DANCE' })
  send(member, { t: 'emote', id: 'WAVE' })
  send(plus, { t: 'emote', id: 'HACK' })
  await wait(100)
  await check('Emote kommt beim anderen an', () => {
    assert.deepStrictEqual(got(member, 'emote')[0], { t: 'emote', uuid: plus.uuid, id: 'DANCE' })
  })
  await check('Emote ohne Crystal+ und unbekanntes Emote werden nicht gezeigt', () => {
    assert.strictEqual(got(plus, 'emote')[0].id, null)
    assert.strictEqual(got(member, 'emote')[1].id, null)
  })
  await check('Spieler auf anderem Minecraft-Server hört nichts', () => {
    assert.strictEqual(got(elsewhere, 'emote').length, 0)
    assert.strictEqual(got(elsewhere, 'peer').length, 0)
  })

  const before = got(member, 'emote').length
  for (let i = 0; i < 30; i++) send(plus, { t: 'emote', id: 'WAVE' })
  await wait(200)
  await check('Emote-Spam wird begrenzt', () => {
    const delivered = got(member, 'emote').length - before
    assert.ok(delivered <= 12, `${delivered} durchgelassen`)
  })

  member.ws.close()
  await wait(150)
  await check('Verlassen wird gemeldet', () => assert.ok(got(plus, 'gone').some(m => m.uuid === member.uuid)))

  const bad = new WebSocket(`ws://127.0.0.1:${port}`)
  const badClose = await new Promise(r => {
    bad.on('message', () => bad.send(JSON.stringify({ t: 'hello', name: '../../x' })))
    bad.on('close', code => r(code))
  })
  await check('Ungültiger Name wird abgewiesen', () => assert.strictEqual(badClose, 4001))

  const big = new WebSocket(`ws://127.0.0.1:${port}`)
  const bigClose = await new Promise(r => {
    big.on('message', () => big.send('x'.repeat(64 * 1024)))
    big.on('close', code => r(code))
  })
  await check('Zu große Nachricht trennt die Verbindung', () => assert.strictEqual(bigClose, 1009))

  const health = await (await fetch(`http://127.0.0.1:${port}/`)).json()
  await check('Status-Seite zählt Spieler', () => assert.strictEqual(health.players, 2))

  plus.ws.close(); elsewhere.ws.close()
  srv.close(); ranksServer.close()
  console.log(failures ? `${failures} FEHLER` : 'alle Tests bestanden')
  process.exit(failures ? 1 : 0)
})()
