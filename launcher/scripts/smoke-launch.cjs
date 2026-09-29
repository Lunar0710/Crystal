// CI smoke test: runs the launcher's real launch code (Java lookup/download,
// libraries, natives, JVM flags) and starts Minecraft 1.21.11 with Crystal in a
// throwaway data folder. Passes once the game has finished loading its
// textures, fails on a mixin/startup crash or after a timeout.
//
// Usage: node scripts/smoke-launch.cjs   (after `npm run build:main` and the client jar build)
// Other versions: CRYSTAL_SMOKE_VERSION=1.8.9 CRYSTAL_SMOKE_LOADER=vanilla|fabric
// (Crystal is injected with Fabric whenever client/build/libs has a jar for that version).
//
// Start time: every PASS prints a STARTUP line (launcher steps before Java,
// game time to the main menu, vanilla's data fixer time). With
// CRYSTAL_SMOKE_UNTIL_MENU=1 the run waits for Nexora's "Startup: menu after"
// line instead of the texture atlas. CRYSTAL_SMOKE_EAGER_DFU=1 starts the game
// with vanilla's data fixer optimisation (-Dnexora.eagerDfu=true), for a
// before/after comparison. CRYSTAL_SMOKE_LAUNCHER=<dir> runs another launcher
// checkout's build (its dist/main and ../client/build/libs), e.g. the base branch.
// CRYSTAL_SMOKE_TIMINGS_FILE=<file> appends the numbers as one JSON line.
const path = require('path')
const fs = require('fs')
const os = require('os')
const Module = require('module')

const launcherRoot = path.resolve(process.env.CRYSTAL_SMOKE_LAUNCHER || path.join(__dirname, '..'))
const originalLoad = Module._load
Module._load = function (request, ...rest) {
  // Only the few electron APIs the launch path touches.
  if (request === 'electron') {
    return {
      app: { isPackaged: false, getAppPath: () => launcherRoot, getVersion: () => 'smoke', getPath: () => os.tmpdir() },
      dialog: {},
      BrowserWindow: { getFocusedWindow: () => null },
      shell: {},
      nativeImage: {},
    }
  }
  return originalLoad.call(this, request, ...rest)
}

// CRYSTAL_SMOKE_ROOT reuses an existing data folder (already downloaded assets) for local runs.
const dataRoot = process.env.CRYSTAL_SMOKE_ROOT || fs.mkdtempSync(path.join(os.tmpdir(), 'crystal-smoke-'))
const { setCrystalRoot } = require(path.join(launcherRoot, 'dist/main/paths.js'))
setCrystalRoot(dataRoot)

const Store = require(path.join(launcherRoot, 'node_modules/electron-store'))
const { MinecraftManager } = require(path.join(launcherRoot, 'dist/main/minecraft/MinecraftManager.js'))

const VERSION = process.env.CRYSTAL_SMOKE_VERSION || '1.21.11'
const LOADER = process.env.CRYSTAL_SMOKE_LOADER || 'fabric'
// The block texture atlas is built once the game has really loaded. 1.13+ logs
// "Created: 1024x512x4 minecraft:textures/atlas/blocks.png-atlas", 1.8.9 to
// 1.12 "Created: 512x512 textures-atlas".
const ATLAS = /Created: \d+x\d+(x\d+)? (minecraft:textures\/atlas\/blocks|textures-atlas)/
const MENU = /\[Nexora\] Startup: menu after (\d+) ms/
const UNTIL_MENU = process.env.CRYSTAL_SMOKE_UNTIL_MENU === '1'
const SUCCESS = [UNTIL_MENU ? MENU : ATLAS]
const FAILURE = [/MixinApplyError/, /InvalidInjectionException/, /Mixin transformation of .* failed/, /Exception in thread "main"/, /Crash report saved/, /Incompatible mods found/]
// Counted from the game process start; asset downloads before that can take minutes on CI.
const GAME_TIMEOUT_MS = 8 * 60 * 1000
const TOTAL_TIMEOUT_MS = 30 * 60 * 1000
const { prepareOptions, threadDump } = require('./smoke-common.cjs')

const gameDir = path.join(dataRoot, 'instances', 'smoke')
fs.mkdirSync(path.join(gameDir, 'mods'), { recursive: true })
prepareOptions(gameDir)

const store = new Store({ cwd: dataRoot, name: 'smoke-store' })
const manager = new MinecraftManager(store)
const WITH_CRYSTAL = LOADER === 'fabric' && manager.hasCrystalFor(VERSION)
const profile = { username: 'CrystalSmoke', uuid: '00196142-0019-3019-8001-00196142c1a5', accessToken: 'offline', type: 'offline' }

let pid = null
let gameStartedAt = null
// When crystal-launch.log appeared: the launcher writes it right before it starts Java.
let spawnedAt = null
let atlasAt = null
const launchCalledAt = Date.now()
let lastProgress = ''
const finish = (code, message) => {
  console.log(message)
  if (pid) { try { process.kill(pid, 'SIGKILL') } catch {} }
  setTimeout(() => process.exit(code), 1000)
}

console.log(`platform=${process.platform} arch=${process.arch} dataRoot=${dataRoot} version=${VERSION} loader=${LOADER} crystal=${WITH_CRYSTAL}`)
const logPath = path.join(gameDir, 'crystal-launch.log')
fs.rmSync(logPath, { force: true })
const extraJvmArgs = process.env.CRYSTAL_SMOKE_EAGER_DFU === '1' ? ['-Dnexora.eagerDfu=true'] : []
manager.launch(
  { version: VERSION, instanceId: 'smoke', gameDir, username: profile.username, profile, maxRam: 2048, loader: LOADER, injectCrystal: WITH_CRYSTAL, extraJvmArgs },
  (event, data) => {
    if (event === 'launch:started') { pid = data.pid; gameStartedAt = Date.now(); console.log('[started] pid', pid) }
    else if (event === 'launch:progress' && data.step !== lastProgress) { lastProgress = data.step; console.log('[progress]', data.step) }
    else if (event === 'launch:error') console.log('[launch:error]', String(data).slice(0, 3000))
  },
).then(ok => { console.log('launch() returned', ok); if (!ok) finish(1, 'FAIL: launch() returned false') })

/** Launcher steps before Java, the game's own start and the data fixers, from the launch log. */
function startupReport(text) {
  const dfu = text.match(/(\d+) Datafixer optimizations took (\d+) milliseconds/)
  const menu = text.match(MENU)
  if (atlasAt === null && ATLAS.test(text)) atlasAt = Date.now()
  const steps = (text.match(/^# timings:\s+(.+)$/m) || [])[1] || ''
  const report = {
    label: process.env.CRYSTAL_SMOKE_LABEL || '',
    version: VERSION,
    eagerDfu: process.env.CRYSTAL_SMOKE_EAGER_DFU === '1',
    launcherMs: spawnedAt ? spawnedAt - launchCalledAt : null,
    dfuMs: dfu ? Number(dfu[2]) : 0,
    dfuOptimizations: dfu ? Number(dfu[1]) : 0,
    atlasAfterSpawnMs: atlasAt && spawnedAt ? atlasAt - spawnedAt : null,
    menuJvmUptimeMs: menu ? Number(menu[1]) : null,
    launcherSteps: steps,
  }
  report.clickToMenuMs = report.launcherMs !== null && report.menuJvmUptimeMs !== null ? report.launcherMs + report.menuJvmUptimeMs : null
  if (process.env.CRYSTAL_SMOKE_TIMINGS_FILE) fs.appendFileSync(process.env.CRYSTAL_SMOKE_TIMINGS_FILE, JSON.stringify(report) + '\n')
  const line = `STARTUP ${VERSION}${report.eagerDfu ? ' (vanilla DFU)' : ''}: launcher ${report.launcherMs} ms, ` +
    `data fixers ${report.dfuMs} ms (${report.dfuOptimizations}), menu at ${report.menuJvmUptimeMs ?? '?'} ms JVM uptime, ` +
    `click to menu ${report.clickToMenuMs ?? '?'} ms | launcher steps: ${steps || 'n/a'}`
  if (process.env.GITHUB_ACTIONS) console.log(`::notice title=Startup ${VERSION}${report.eagerDfu ? ' vanilla DFU' : ''}::${line}`)
  return line
}

// Finer than the 2 s check below, only for the two moments timed from outside.
const stamps = setInterval(() => {
  if (spawnedAt === null && fs.existsSync(logPath)) spawnedAt = Date.now()
  if (spawnedAt !== null && atlasAt === null && ATLAS.test(fs.readFileSync(logPath, 'utf8'))) { atlasAt = Date.now(); clearInterval(stamps) }
}, 250)

const started = Date.now()
const timer = setInterval(() => {
  const text = fs.existsSync(logPath) ? fs.readFileSync(logPath, 'utf8') : ''
  const fail = FAILURE.find(r => r.test(text))
  if (fail) {
    clearInterval(timer)
    const i = text.search(fail)
    return finish(1, `FAIL: ${fail}\n${text.slice(Math.max(0, i - 3000), i + 2000)}`)
  }
  if (SUCCESS.every(r => r.test(text))) {
    clearInterval(timer)
    const header = text.split('\n').filter(l => l.startsWith('#')).join('\n')
    // Both spellings: builds before the rename to Nexora still log "Crystal".
    const crystalOk = /(Crystal|Nexora) Client loaded successfully/.test(text)
    if (WITH_CRYSTAL && !crystalOk) return finish(1, `FAIL: Minecraft ${VERSION} loaded but Crystal did not\n${header}`)
    return finish(0, `PASS: Minecraft ${VERSION} (${LOADER}${WITH_CRYSTAL ? ' + Crystal' : ''}) loaded on ${process.platform}/${process.arch}\n${header}\n${startupReport(text)}`)
  }
  // GitHub's macOS machines are VMs without a GPU: Minecraft gets through
  // Java, libraries, natives, Fabric and Crystal, then GLFW can't create an
  // OpenGL window and the game sits in an error dialog. With
  // CRYSTAL_SMOKE_NO_GPU=1 that exact state counts as a (partial) pass, so
  // everything up to the window is still checked on every run.
  if (process.env.CRYSTAL_SMOKE_NO_GPU === '1' && gameStartedAt && Date.now() - gameStartedAt > 90 * 1000
      && /(Crystal|Nexora) Client loaded successfully/.test(text) && /Backend library/.test(text)) {
    const dump = threadDump(gameDir, pid)
    if (/glfwCreateWindow/.test(dump)) {
      clearInterval(timer)
      const header = text.split('\n').filter(l => l.startsWith('#')).join('\n')
      return finish(0, `PARTIAL PASS (no GPU on this machine): Crystal loaded on ${process.platform}/${process.arch}, stopped at OpenGL window creation\n${header}\n${startupReport(text)}`)
    }
  }
  if ((gameStartedAt && Date.now() - gameStartedAt > GAME_TIMEOUT_MS) || Date.now() - started > TOTAL_TIMEOUT_MS) {
    clearInterval(timer)
    finish(1, `FAIL: timeout\n${text.slice(-4000)}\n--- thread dump ---\n${threadDump(gameDir, pid)}`)
  }
}, 2000)
