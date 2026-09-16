// CI smoke test: runs the launcher's real launch code (Java lookup/download,
// libraries, natives, JVM flags) and starts Minecraft 1.21.11 with Crystal in a
// throwaway data folder. Passes once the game has finished loading its
// textures, fails on a mixin/startup crash or after a timeout.
//
// Usage: node scripts/smoke-launch.cjs   (after `npm run build:main` and the client jar build)
// Other versions: CRYSTAL_SMOKE_VERSION=1.8.9 CRYSTAL_SMOKE_LOADER=vanilla|fabric
// (Crystal is injected with Fabric whenever client/build/libs has a jar for that version).
const path = require('path')
const fs = require('fs')
const os = require('os')
const Module = require('module')

const launcherRoot = path.resolve(__dirname, '..')
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
const SUCCESS = [/Created: \d+x\d+(x\d+)? (minecraft:textures\/atlas\/blocks|textures-atlas)/]
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
let lastProgress = ''
const finish = (code, message) => {
  console.log(message)
  if (pid) { try { process.kill(pid, 'SIGKILL') } catch {} }
  setTimeout(() => process.exit(code), 1000)
}

console.log(`platform=${process.platform} arch=${process.arch} dataRoot=${dataRoot} version=${VERSION} loader=${LOADER} crystal=${WITH_CRYSTAL}`)
manager.launch(
  { version: VERSION, instanceId: 'smoke', gameDir, username: profile.username, profile, maxRam: 2048, loader: LOADER, injectCrystal: WITH_CRYSTAL },
  (event, data) => {
    if (event === 'launch:started') { pid = data.pid; gameStartedAt = Date.now(); console.log('[started] pid', pid) }
    else if (event === 'launch:progress' && data.step !== lastProgress) { lastProgress = data.step; console.log('[progress]', data.step) }
    else if (event === 'launch:error') console.log('[launch:error]', String(data).slice(0, 3000))
  },
).then(ok => { console.log('launch() returned', ok); if (!ok) finish(1, 'FAIL: launch() returned false') })

const logPath = path.join(gameDir, 'crystal-launch.log')
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
    const crystalOk = /Crystal Client loaded successfully/.test(text)
    if (WITH_CRYSTAL && !crystalOk) return finish(1, `FAIL: Minecraft ${VERSION} loaded but Crystal did not\n${header}`)
    return finish(0, `PASS: Minecraft ${VERSION} (${LOADER}${WITH_CRYSTAL ? ' + Crystal' : ''}) loaded on ${process.platform}/${process.arch}\n${header}`)
  }
  // GitHub's macOS machines are VMs without a GPU: Minecraft gets through
  // Java, libraries, natives, Fabric and Crystal, then GLFW can't create an
  // OpenGL window and the game sits in an error dialog. With
  // CRYSTAL_SMOKE_NO_GPU=1 that exact state counts as a (partial) pass, so
  // everything up to the window is still checked on every run.
  if (process.env.CRYSTAL_SMOKE_NO_GPU === '1' && gameStartedAt && Date.now() - gameStartedAt > 90 * 1000
      && /Crystal Client loaded successfully/.test(text) && /Backend library/.test(text)) {
    const dump = threadDump(gameDir, pid)
    if (/glfwCreateWindow/.test(dump)) {
      clearInterval(timer)
      const header = text.split('\n').filter(l => l.startsWith('#')).join('\n')
      return finish(0, `PARTIAL PASS (no GPU on this machine): Crystal loaded on ${process.platform}/${process.arch}, stopped at OpenGL window creation\n${header}`)
    }
  }
  if ((gameStartedAt && Date.now() - gameStartedAt > GAME_TIMEOUT_MS) || Date.now() - started > TOTAL_TIMEOUT_MS) {
    clearInterval(timer)
    finish(1, `FAIL: timeout\n${text.slice(-4000)}\n--- thread dump ---\n${threadDump(gameDir, pid)}`)
  }
}, 2000)
