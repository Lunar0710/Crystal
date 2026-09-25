'use strict'
// End-to-end test of the Nexora Worker, run against `wrangler dev` with
// FAKE_AUTH=1 (the Mojang check skipped). Starts wrangler itself.
const { spawn } = require('child_process')
const assert = require('assert')
const WebSocket = require('ws')

const PORT = 8799
const BASE = `127.0.0.1:${PORT}`
const ROOM = 'a1b2c3d4e5f60718'
const wait = ms => new Promise(r => setTimeout(r, ms))

function connect(name, client = 'game') {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(`ws://${BASE}`)
    const c = { ws, name, inbox: [], uuid: null }
    ws.on('message', data => {
      const m = JSON.parse(data.toString())
      c.inbox.push(m)
      if (m.t === 'challenge') ws.send(JSON.stringify({ t: 'hello', name, client }))
      if (m.t === 'welcome') { c.uuid = m.uuid; c.rank = m.rank; resolve(c) }
    })
    ws.on('error', reject)
    ws.on('close', code => { c.closed = code })
  })
}
const send = (c, m) => c.ws.send(JSON.stringify(m))
const got = (c, t) => c.inbox.filter(m => m.t === t)
const last = (c, t) => got(c, t).at(-1)

let failures = 0
async function check(name, fn) {
  try { await fn(); console.log('PASS', name) } catch (e) { failures++; console.log('FAIL', name, '-', e.message) }
}

;(async () => {
  const dev = spawn('npx', ['wrangler', 'dev', '--port', String(PORT), '--var', 'FAKE_AUTH:1', '--persist-to', '.wrangler/test-state'], { cwd: __dirname, shell: true })
  let log = ''
  dev.stdout.on('data', d => { log += d })
  dev.stderr.on('data', d => { log += d })
  for (let i = 0; i < 60; i++) {
    await wait(1000)
    try { const r = await fetch(`http://${BASE}/`); if (r.ok) break } catch { }
    if (i === 59) { console.log(log); throw new Error('wrangler dev did not start') }
  }

  const stamp = Date.now().toString(36).slice(-5)
  const a = await connect('Alice' + stamp)
  const b = await connect('Bob' + stamp)
  const bLauncher = await connect('Bob' + stamp, 'launcher')

  await check('Anmeldung', () => { assert.ok(a.uuid && b.uuid && a.uuid !== b.uuid) })

  await check('Räume: Peers sehen sich', async () => {
    send(a, { t: 'where', room: ROOM })
    await wait(200)
    send(b, { t: 'where', room: ROOM })
    await wait(400)
    assert.ok(got(a, 'peer').some(p => p.uuid === b.uuid), 'A sieht B nicht')
    assert.ok(last(b, 'peers').peers.some(p => p.uuid === a.uuid), 'B sieht A nicht')
  })

  await check('Freundschaftsanfrage kommt an', async () => {
    send(a, { t: 'friend-add', name: b.name })
    await wait(600)
    const req = last(b, 'friend-request')
    assert.ok(req && req.from.uuid === a.uuid, 'keine Anfrage bei B')
    assert.ok(last(bLauncher, 'friend-request'), 'Launcher von B bekommt die Anfrage nicht')
    assert.deepStrictEqual(last(a, 'friends').outgoing.map(f => f.uuid), [b.uuid])
  })

  await check('Annehmen macht beide zu Freunden, Status stimmt', async () => {
    send(bLauncher, { t: 'friend-accept', uuid: a.uuid })
    await wait(600)
    assert.ok(last(a, 'friend-accepted'), 'A erfährt nichts')
    const la = last(a, 'friends'), lb = last(b, 'friends')
    assert.strictEqual(la.friends[0].uuid, b.uuid)
    assert.strictEqual(la.friends[0].status, 'game')
    assert.strictEqual(lb.friends[0].uuid, a.uuid)
    assert.strictEqual(la.outgoing.length, 0)
  })

  await check('Chat: Nachricht an alle Verbindungen, ungelesen gezählt', async () => {
    send(a, { t: 'chat-send', to: b.uuid, text: 'Hallo Bob' })
    await wait(600)
    assert.strictEqual(last(b, 'chat').message.text, 'Hallo Bob')
    assert.strictEqual(last(bLauncher, 'chat').message.text, 'Hallo Bob')
    assert.strictEqual(last(a, 'chat').message.from, a.uuid)
    assert.strictEqual(last(bLauncher, 'unread').unread[a.uuid], 1)
  })

  await check('Chat-Verlauf und gelesen', async () => {
    send(bLauncher, { t: 'chat-history', with: a.uuid })
    send(bLauncher, { t: 'chat-read', with: a.uuid })
    await wait(600)
    assert.strictEqual(last(bLauncher, 'chat-history').messages.at(-1).text, 'Hallo Bob')
    assert.strictEqual(last(bLauncher, 'unread').unread[a.uuid], undefined)
  })

  await check('Presence: Spiel zu, Launcher bleibt', async () => {
    b.ws.close()
    await wait(800)
    const p = got(a, 'presence').filter(x => x.uuid === b.uuid).at(-1)
    assert.strictEqual(p && p.status, 'launcher')
    const presence = await (await fetch(`http://${BASE}/presence?u=${b.uuid}`)).json()
    assert.deepStrictEqual(presence.online, [b.uuid])
  })

  await check('Nachrichten nur an Freunde', async () => {
    const c = await connect('Carol' + stamp)
    send(c, { t: 'chat-send', to: a.uuid, text: 'Spam' })
    await wait(500)
    assert.strictEqual(last(c, 'error').code, 'notfriend')
    assert.ok(!got(a, 'chat').some(x => x.message.text === 'Spam'))
    c.ws.close()
  })

  await check('Entfernen', async () => {
    send(a, { t: 'friend-remove', uuid: b.uuid })
    await wait(600)
    assert.strictEqual(last(a, 'friends').friends.length, 0)
    assert.strictEqual(last(bLauncher, 'friends').friends.length, 0)
  })

  await check('Unbekannter Name und sich selbst', async () => {
    send(a, { t: 'friend-add', name: 'x!' })
    await wait(300)
    assert.strictEqual(last(a, 'error').code, 'name')
    send(a, { t: 'friend-add', name: a.name })
    await wait(400)
    assert.strictEqual(last(a, 'error').code, 'self')
  })

  a.ws.close(); bLauncher.ws.close()
  dev.kill()
  spawn('taskkill', ['/F', '/T', '/PID', String(dev.pid)], { shell: true })
  console.log(failures ? `${failures} FAILED` : 'ALL PASS')
  process.exit(failures ? 1 : 0)
})().catch(err => { console.error(err); process.exit(1) })
