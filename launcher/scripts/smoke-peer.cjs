// Crystal server part of the world test: a local Crystal server (server/,
// Mojang check skipped) and a second "player" that wears a Crystal+ cape and
// a hat and dances. The game under test should see all three on the stand-in
// SmokeTest.java spawns for it.
const path = require('path')
const fs = require('fs')
const zlib = require('zlib')
const crypto = require('crypto')

const serverDir = path.resolve(__dirname, '..', '..', 'server')
/** Same room the game computes for -Dcrystal.net.testRoom=smoke (CrystalNet.currentRoom). */
const ROOM = crypto.createHash('sha256').update('crystal:smoke').digest('hex').slice(0, 32)
const PEER = 'PeerBot'
const CAPE = 'plus-0'

/** A 64x32 cape texture in loud stripes, so it is unmistakable in the screenshot. */
function stripedCapePng() {
  const w = 64, h = 32
  const raw = Buffer.alloc((w * 4 + 1) * h)
  for (let y = 0; y < h; y++) {
    raw[y * (w * 4 + 1)] = 0
    for (let x = 0; x < w; x++) {
      const i = y * (w * 4 + 1) + 1 + x * 4
      const stripe = Math.floor(y / 3) % 2 === 0
      raw[i] = stripe ? 255 : 30; raw[i + 1] = stripe ? 40 : 230; raw[i + 2] = stripe ? 200 : 60; raw[i + 3] = 255
    }
  }
  const crcTable = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0 })
  const crc = b => { let c = 0xffffffff; for (const v of b) c = crcTable[(c ^ v) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0 }
  const chunk = (type, data) => {
    const len = Buffer.alloc(4); len.writeUInt32BE(data.length)
    const body = Buffer.concat([Buffer.from(type), data])
    const sum = Buffer.alloc(4); sum.writeUInt32BE(crc(body))
    return Buffer.concat([len, body, sum])
  }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))])
}

/** A top hat in the loadout format the launcher writes (skin pixels, y up, head centre). */
const HAT = {
  color: '#ff2a6d', plusOnly: false, anchor: 'head',
  boxes: [
    { x: 0, y: 4.5, z: 0, w: 12, h: 1, d: 12, rz: 0, color: '#1b1b2f', glow: false },
    { x: 0, y: 8, z: 0, w: 8, h: 6, d: 8, rz: 0, color: '#1b1b2f', glow: false },
    { x: 0, y: 5.5, z: 0, w: 8.2, h: 1.2, d: 8.2, rz: 0, color: '#ff2a6d', glow: false },
  ],
}

async function startPeerTest(dataRoot, log) {
  process.env.FAKE_AUTH = '1'
  process.env.OWNERS = PEER
  // No network in the test: an unreachable ranks.json just keeps the owner list.
  process.env.RANKS_URL = 'http://127.0.0.1:9/ranks.json'
  const { start } = require(path.join(serverDir, 'server.js'))
  const WebSocket = require(path.join(serverDir, 'node_modules', 'ws'))
  const srv = await start(0)
  const url = `ws://127.0.0.1:${srv.server.address().port}`

  const cacheDir = path.join(dataRoot, 'cosmetics', 'cape-cache')
  fs.mkdirSync(cacheDir, { recursive: true })
  fs.writeFileSync(path.join(cacheDir, `${CAPE}.png`), stripedCapePng())

  const bot = new WebSocket(url)
  bot.on('message', data => {
    const m = JSON.parse(data.toString())
    if (m.t === 'challenge') bot.send(JSON.stringify({ t: 'hello', name: PEER }))
    if (m.t === 'welcome') {
      bot.send(JSON.stringify({ t: 'loadout', cape: CAPE, items: { hat: HAT } }))
      bot.send(JSON.stringify({ t: 'where', room: ROOM }))
      bot.send(JSON.stringify({ t: 'emote', id: 'DANCE' }))
      log('peer bot ready in room', ROOM.slice(0, 8))
    }
  })
  bot.on('error', err => log('peer bot error', err.message))

  return {
    jvmArgs: [`-Dcrystal.server=${url}`, '-Dcrystal.net.testRoom=smoke'],
    close: () => { try { bot.close() } catch {} srv.close() },
  }
}

module.exports = { startPeerTest }
