// Nexora Lite against full Nexora: frame rate and start time, measured the same way.
//
// Both modes start the same flat test world with the same mods (Nexora, Fabric
// API and the performance pack, as players get it) and the fresh-install
// Nexora settings. The only difference is -Dnexora.lite=true. In the world,
// Nexora's SmokeTest (-Dcrystal.smoke.bench=steady) turns the camera slowly
// for a warm-up, then times every frame for CRYSTAL_BENCH_SECONDS and logs
// "BENCH_RESULT" with the average, the 1% low (slowest 1% of frames) and the
// startup marks. Runs alternate full, lite, full, lite ... so drift on the
// machine (heat, chunk caches) hits both modes alike.
//
// Usage: node scripts/smoke-lite-bench.cjs   (after `npm run build:main` and the client jar build)
// CRYSTAL_SMOKE_VERSION (1.21.11), CRYSTAL_SMOKE_ROOT (reuse downloads), CRYSTAL_BENCH_ROUNDS (2),
// CRYSTAL_BENCH_SECONDS (60), CRYSTAL_BENCH_NO_PACK=1 (without the performance pack),
// CRYSTAL_BENCH_RESULTS=<file> (writes the numbers as JSON), CRYSTAL_SMOKE_RAM (3072).
const path = require('path')
const fs = require('fs')
const os = require('os')
const Module = require('module')

const launcherRoot = path.resolve(__dirname, '..')
const originalLoad = Module._load
Module._load = function (request, ...rest) {
  if (request === 'electron') {
    return {
      app: { isPackaged: false, getAppPath: () => launcherRoot, getVersion: () => 'smoke', getPath: () => os.tmpdir() },
      dialog: {}, BrowserWindow: { getFocusedWindow: () => null }, shell: {}, nativeImage: {},
    }
  }
  return originalLoad.call(this, request, ...rest)
}

const VERSION = process.env.CRYSTAL_SMOKE_VERSION || '1.21.11'
const ROUNDS = Math.max(1, Number(process.env.CRYSTAL_BENCH_ROUNDS || 2))
const SECONDS = Math.max(10, Number(process.env.CRYSTAL_BENCH_SECONDS || 60))
const dataRoot = process.env.CRYSTAL_SMOKE_ROOT || fs.mkdtempSync(path.join(os.tmpdir(), 'crystal-bench-'))
const { setCrystalRoot } = require(path.join(launcherRoot, 'dist/main/paths.js'))
setCrystalRoot(dataRoot)
const platform = require(path.join(launcherRoot, 'dist/main/minecraft/platform.js'))
const Store = require(path.join(launcherRoot, 'node_modules/electron-store'))
const { MinecraftManager } = require(path.join(launcherRoot, 'dist/main/minecraft/MinecraftManager.js'))
const { requiredJavaMajor } = require(path.join(launcherRoot, 'dist/main/minecraft/versions.js'))
const { prepareOptions, threadDump, findServerJava, generateWorld } = require('./smoke-common.cjs')
const log = (...a) => console.log('[lite-bench]', ...a)

const RESULT = /BENCH_RESULT lite=(\w+) avg=([\d.]+) low1=([\d.]+) median=([\d.]+) frames=(\d+) seconds=([\d.]+) clientReadyMs=(-?\d+) loadedMs=(-?\d+) worldMs=(-?\d+) heapMb=(\d+)/
const MENU = /\[Nexora\] Startup: menu after (\d+) ms/

/** One game start in the given mode; resolves with its numbers or rejects. */
async function run(mode, worldDir, round) {
  const instanceId = `bench-${mode}`
  const gameDir = path.join(dataRoot, 'instances', instanceId)
  const saveDir = path.join(gameDir, 'saves', 'bench')
  fs.rmSync(saveDir, { recursive: true, force: true })
  fs.mkdirSync(path.dirname(saveDir), { recursive: true })
  fs.cpSync(worldDir, saveDir, { recursive: true })
  prepareOptions(gameDir)
  // Fresh-install Nexora settings every run, so neither mode carries anything over.
  fs.rmSync(path.join(gameDir, '.crystal', 'config', 'crystal.json'), { force: true })

  const manager = new MinecraftManager(new Store({ cwd: dataRoot, name: 'smoke-store' }))
  if (round === 0 && !process.env.CRYSTAL_BENCH_NO_PACK) {
    const { ModrinthService } = require(path.join(launcherRoot, 'dist/main/minecraft/ModrinthService.js'))
    const pack = await new ModrinthService().installPerformancePack(instanceId, VERSION)
    log(mode, 'performance pack', JSON.stringify(pack))
    if (pack.failed.length) throw new Error('performance pack install failed')
  }
  const mods = fs.existsSync(path.join(gameDir, 'mods')) ? fs.readdirSync(path.join(gameDir, 'mods')).sort() : []

  const profile = { username: 'CrystalBench', uuid: '00196142-0019-3019-8001-00196142c1a6', accessToken: 'offline', type: 'offline' }
  const logPath = path.join(gameDir, 'crystal-launch.log')
  fs.rmSync(logPath, { force: true })
  let pid = null
  const startedAt = Date.now()
  const ok = await manager.launch({
    version: VERSION, instanceId, gameDir, username: profile.username, profile, maxRam: Number(process.env.CRYSTAL_SMOKE_RAM || 3072),
    loader: 'fabric', injectCrystal: true,
    extraJvmArgs: [
      '-Dcrystal.smoke.screenshot=bench.png', '-Dcrystal.smoke.bench=steady', `-Dcrystal.smoke.bench.seconds=${SECONDS}`,
      ...(mode === 'lite' ? ['-Dnexora.lite=true'] : []),
    ],
    extraGameArgs: ['--quickPlaySingleplayer', 'bench'],
  }, (event, data) => {
    if (event === 'launch:started') pid = data.pid
    else if (event === 'launch:error') log('launch:error', String(data).slice(0, 2000))
  })
  if (!ok) throw new Error(`${mode}: launch failed`)

  const text = await new Promise((resolve, reject) => {
    const limit = (8 * 60 + SECONDS) * 1000
    const timer = setInterval(() => {
      const t = fs.existsSync(logPath) ? fs.readFileSync(logPath, 'utf8') : ''
      if (/CRYSTAL_SMOKE_WORLD_DONE/.test(t) || /Crash report saved/.test(t)) { clearInterval(timer); resolve(t) }
      else if (Date.now() - startedAt > limit) {
        clearInterval(timer)
        const dump = threadDump(gameDir, pid)
        if (pid) { try { process.kill(pid) } catch {} }
        reject(new Error(`${mode}: timeout\n${t.slice(-3000)}\n--- thread dump ---\n${dump}`))
      }
    }, 2000)
  })
  await new Promise(r => setTimeout(r, 4000))
  if (pid) { try { process.kill(pid) } catch {} }

  const m = text.match(RESULT)
  if (!m) throw new Error(`${mode}: no BENCH_RESULT in the log\n${text.slice(-3000)}`)
  const menu = text.match(MENU)
  const mixinsSkipped = (text.match(/Nexora Lite: only the FPS-first module set/) ? 'lite log line present' : '')
  const result = {
    mode, round, lite: m[1] === 'true',
    avgFps: Number(m[2]), low1Fps: Number(m[3]), medianFps: Number(m[4]), frames: Number(m[5]), seconds: Number(m[6]),
    clientReadyMs: Number(m[7]), loadedMs: Number(m[8]), worldMs: Number(m[9]), heapMb: Number(m[10]),
    menuMs: menu ? Number(menu[1]) : null,
    wallToDoneMs: Date.now() - startedAt, mods, note: mixinsSkipped,
  }
  if ((mode === 'lite') !== result.lite) throw new Error(`${mode}: the client reported lite=${result.lite}`)
  log(JSON.stringify(result))
  return result
}

const mean = xs => xs.reduce((a, b) => a + b, 0) / xs.length
const fmt = (n, d = 1) => Number.isFinite(n) ? n.toFixed(d) : '-'

;(async () => {
  log(`platform=${process.platform} dataRoot=${dataRoot} version=${VERSION} rounds=${ROUNDS} seconds=${SECONDS}`)
  const java = findServerJava(platform, requiredJavaMajor(VERSION))
    || await new MinecraftManager(new Store({ cwd: dataRoot, name: 'smoke-store' })).ensureJava(VERSION, () => {})
  if (!java) throw new Error(`no Java ${requiredJavaMajor(VERSION)} found for the world generator`)
  const worldDir = await generateWorld(dataRoot, VERSION, java, log)

  const results = []
  for (let round = 0; round < ROUNDS; round++) {
    for (const mode of ['full', 'lite']) results.push(await run(mode, worldDir, round))
  }

  const summary = {}
  for (const mode of ['full', 'lite']) {
    const rs = results.filter(r => r.mode === mode)
    summary[mode] = {
      avgFps: mean(rs.map(r => r.avgFps)), low1Fps: mean(rs.map(r => r.low1Fps)), medianFps: mean(rs.map(r => r.medianFps)),
      clientReadyMs: mean(rs.map(r => r.clientReadyMs)), loadedMs: mean(rs.map(r => r.loadedMs)), worldMs: mean(rs.map(r => r.worldMs)),
      heapMb: mean(rs.map(r => r.heapMb)),
    }
  }
  const pct = (a, b) => b ? `${a >= b ? '+' : ''}${fmt((a / b - 1) * 100)} %` : '-'
  const f = summary.full, l = summary.lite
  const table = [
    `Nexora Lite vs. full Nexora, Minecraft ${VERSION}, ${ROUNDS} round(s) of ${SECONDS} s each, ${process.platform}`,
    `                 full      lite      change`,
    `avg FPS       ${fmt(f.avgFps).padStart(7)}   ${fmt(l.avgFps).padStart(7)}   ${pct(l.avgFps, f.avgFps)}`,
    `1% low FPS    ${fmt(f.low1Fps).padStart(7)}   ${fmt(l.low1Fps).padStart(7)}   ${pct(l.low1Fps, f.low1Fps)}`,
    `median FPS    ${fmt(f.medianFps).padStart(7)}   ${fmt(l.medianFps).padStart(7)}   ${pct(l.medianFps, f.medianFps)}`,
    `client ready  ${fmt(f.clientReadyMs, 0).padStart(7)}   ${fmt(l.clientReadyMs, 0).padStart(7)}   ms after JVM start (end of Nexora init)`,
    `loaded        ${fmt(f.loadedMs, 0).padStart(7)}   ${fmt(l.loadedMs, 0).padStart(7)}   ms after JVM start (loading screen gone)`,
    `in world      ${fmt(f.worldMs, 0).padStart(7)}   ${fmt(l.worldMs, 0).padStart(7)}   ms after JVM start`,
    `heap used     ${fmt(f.heapMb, 0).padStart(7)}   ${fmt(l.heapMb, 0).padStart(7)}   MB at the end`,
  ].join('\n')
  console.log(table)
  if (process.env.CRYSTAL_BENCH_RESULTS) fs.writeFileSync(process.env.CRYSTAL_BENCH_RESULTS, JSON.stringify({ version: VERSION, rounds: ROUNDS, seconds: SECONDS, summary, results, table }, null, 2))
  console.log('PASS: lite bench finished')
  process.exit(0)
})().catch(err => { console.log('FAIL:', err.message); process.exit(1) })
