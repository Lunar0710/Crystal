// In-world test: generates a flat test world with the official Minecraft server,
// then starts the game with Crystal straight into it. Crystal's SmokeTest hook
// (-Dcrystal.smoke.screenshot) gives the player armor, switches to the front
// camera, saves a screenshot and quits. The script then reports errors from the
// game log and where the screenshot is.
//
// Test settings: ColorSaturation on (saturation 1.7, hue 40), icon Armor HUD,
// and a hat, wings and aura from the cosmetics loadout.
//
// Usage: node scripts/smoke-world.cjs   (after `npm run build:main` and the client jar build)
// CRYSTAL_SMOKE_ROOT reuses a data folder with already downloaded assets.
const path = require('path')
const fs = require('fs')
const os = require('os')
const { spawn, spawnSync } = require('child_process')
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

const VERSION = '1.21.11'
const dataRoot = process.env.CRYSTAL_SMOKE_ROOT || fs.mkdtempSync(path.join(os.tmpdir(), 'crystal-smoke-'))
const { setCrystalRoot } = require(path.join(launcherRoot, 'dist/main/paths.js'))
setCrystalRoot(dataRoot)
const platform = require(path.join(launcherRoot, 'dist/main/minecraft/platform.js'))
const Store = require(path.join(launcherRoot, 'node_modules/electron-store'))
const { MinecraftManager } = require(path.join(launcherRoot, 'dist/main/minecraft/MinecraftManager.js'))

const log = (...a) => console.log('[smoke-world]', ...a)
const { prepareOptions, threadDump } = require('./smoke-common.cjs')

function findJava21() {
  for (const candidate of platform.javaCandidates()) {
    if (candidate !== 'java' && !fs.existsSync(candidate)) continue
    const out = spawnSync(candidate, ['-version'], { encoding: 'utf8' })
    const m = ((out.stderr || '') + (out.stdout || '')).match(/version "(\d+)/)
    if (m && parseInt(m[1], 10) >= 21) return candidate
  }
  return null
}

async function generateWorld(java) {
  const serverDir = path.join(dataRoot, 'smoke-server')
  const worldDir = path.join(serverDir, 'world')
  if (fs.existsSync(path.join(worldDir, 'level.dat'))) return worldDir

  fs.mkdirSync(serverDir, { recursive: true })
  const manifest = await (await fetch('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json')).json()
  const versionJson = await (await fetch(manifest.versions.find(v => v.id === VERSION).url)).json()
  const jar = path.join(serverDir, 'server.jar')
  if (!fs.existsSync(jar)) {
    log('downloading server jar')
    fs.writeFileSync(jar, Buffer.from(await (await fetch(versionJson.downloads.server.url)).arrayBuffer()))
  }
  fs.writeFileSync(path.join(serverDir, 'eula.txt'), 'eula=true\n')
  fs.writeFileSync(path.join(serverDir, 'server.properties'),
    'level-type=minecraft\\:flat\nonline-mode=false\nspawn-protection=0\ngenerate-structures=false\nspawn-monsters=false\nserver-port=25599\n')

  log('generating world')
  await new Promise((resolve, reject) => {
    const proc = spawn(java, ['-Xmx1G', '-jar', 'server.jar', 'nogui'], { cwd: serverDir })
    const timer = setTimeout(() => { proc.kill(); reject(new Error('server timeout')) }, 5 * 60 * 1000)
    proc.stdout.on('data', chunk => {
      if (/Done \(/.test(chunk.toString())) proc.stdin.write('stop\n')
    })
    proc.on('exit', () => { clearTimeout(timer); resolve() })
  })
  if (!fs.existsSync(path.join(worldDir, 'level.dat'))) throw new Error('world was not generated')
  return worldDir
}

function writeTestSettings(gameDir) {
  const configDir = path.join(gameDir, '.crystal', 'config')
  fs.mkdirSync(configDir, { recursive: true })
  fs.writeFileSync(path.join(configDir, 'crystal.json'), JSON.stringify({
    modules: {
      ColorSaturation: { enabled: true, settings: { Saturation: 1.7, Hue: 40, Brightness: 1.0, Contrast: 1.1 } },
      ArmorDisplay: { enabled: true, settings: { Style: 'Icons', Background: true } },
      // Modules added in 1.1.8, switched on so a broken mixin or shader shows up here.
      MotionBlur: { enabled: true, settings: { Strength: 40 } },
      TextStyle: { enabled: true, settings: { Font: 'Smooth' } },
      ShinyPots: { enabled: true },
      HitColor: { enabled: true },
      GlintColorizer: { enabled: true },
      NameTags: { enabled: true },
      ItemPhysics: { enabled: true },
      ParticleChanger: { enabled: true },
      BetterSounds: { enabled: true },
      ServerAddress: { enabled: true },
      '3D Skins': { enabled: true },
      WorldEditCUI: { enabled: true },
    },
  }, null, 2))

  const cosmeticsDir = path.join(dataRoot, 'cosmetics')
  fs.mkdirSync(cosmeticsDir, { recursive: true })
  // The launcher's real cosmetic data and shapes, compiled on the fly, so the
  // test renders exactly what a player would have equipped.
  const esbuild = require(path.join(launcherRoot, 'node_modules/esbuild'))
  const out = esbuild.buildSync({
    stdin: {
      contents: "export { COSMETICS_BY_SLOT } from './cosmetics'; export { shapeFor } from './cosmeticShapes'",
      resolveDir: path.join(launcherRoot, 'src/renderer/data'),
      loader: 'ts',
    },
    bundle: true, format: 'cjs', platform: 'node', write: false,
  })
  const mod = { exports: {} }
  new Function('module', 'exports', 'require', out.outputFiles[0].text)(mod, mod.exports, require)
  const { COSMETICS_BY_SLOT, shapeFor } = mod.exports

  const pick = { hat: 'ht-propeller', mask: 'mk-glasses', wings: 'wg-angel', backpack: 'bp-guitar', aura: 'au-hearts' }
  const loadout = {}
  for (const [slot, id] of Object.entries(pick)) {
    const def = COSMETICS_BY_SLOT[slot].find(d => d.id === id)
    const shape = shapeFor(def)
    loadout[slot] = {
      color: def.color, secondary: def.secondary ?? null, variant: def.variant ?? null, plusOnly: !!def.requiredRank,
      anchor: shape ? shape.anchor : null, boxes: shape ? shape.boxes : [],
    }
  }
  fs.writeFileSync(path.join(cosmeticsDir, 'loadout.json'), JSON.stringify(loadout, null, 2))
}

;(async () => {
  log(`platform=${process.platform} dataRoot=${dataRoot}`)
  const java = findJava21()
  if (!java) throw new Error('no Java 21 found for the world generator')

  const worldDir = await generateWorld(java)
  const gameDir = path.join(dataRoot, 'instances', 'smoke-world')
  const saveDir = path.join(gameDir, 'saves', 'smoke')
  fs.rmSync(saveDir, { recursive: true, force: true })
  fs.mkdirSync(path.dirname(saveDir), { recursive: true })
  fs.cpSync(worldDir, saveDir, { recursive: true })
  writeTestSettings(gameDir)
  prepareOptions(gameDir)

  const shotName = 'crystal-smoke.png'
  fs.rmSync(path.join(gameDir, 'screenshots', shotName), { force: true })

  const manager = new MinecraftManager(new Store({ cwd: dataRoot, name: 'smoke-store' }))
  const profile = { username: 'CrystalSmoke', uuid: '00196142-0019-3019-8001-00196142c1a5', accessToken: 'offline', type: 'offline' }
  let pid = null
  // Players get the performance pack on their first Crystal launch, so the test
  // plays with it too (proves Crystal still renders correctly next to Sodium).
  if (process.env.CRYSTAL_SMOKE_PERFPACK) {
    const { ModrinthService } = require(path.join(launcherRoot, 'dist/main/minecraft/ModrinthService.js'))
    const pack = await new ModrinthService().installPerformancePack('smoke-world', VERSION)
    log('performance pack', JSON.stringify(pack))
    if (pack.failed.length) throw new Error('performance pack install failed')
  }
  log('launching')
  const ok = await manager.launch({
    version: VERSION, instanceId: 'smoke-world', gameDir, username: profile.username, profile, maxRam: 3072,
    loader: 'fabric', injectCrystal: true,
    extraJvmArgs: [`-Dcrystal.smoke.screenshot=${shotName}`],
    extraGameArgs: ['--quickPlaySingleplayer', 'smoke'],
  }, (event, data) => {
    if (event === 'launch:started') pid = data.pid
    else if (event === 'launch:error') log('launch:error', String(data).slice(0, 2000))
  })
  if (!ok) throw new Error('launch failed')

  const logPath = path.join(gameDir, 'crystal-launch.log')
  const started = Date.now()
  await new Promise((resolve, reject) => {
    const timer = setInterval(() => {
      const text = fs.existsSync(logPath) ? fs.readFileSync(logPath, 'utf8') : ''
      if (/CRYSTAL_SMOKE_WORLD_DONE/.test(text) || /Crash report saved/.test(text)) { clearInterval(timer); resolve() }
      else if (Date.now() - started > 8 * 60 * 1000) {
        clearInterval(timer)
        const dump = threadDump(gameDir, pid)
        if (pid) { try { process.kill(pid) } catch {} }
        reject(new Error(`timeout waiting for world test\n${text.slice(-3000)}\n--- thread dump ---\n${dump}`))
      }
    }, 2000)
  })
  await new Promise(r => setTimeout(r, 4000))
  if (pid) { try { process.kill(pid) } catch {} }

  const text = fs.readFileSync(logPath, 'utf8')
  const problems = text.split('\n').filter(line =>
    /(ERROR|Exception|Failed to load|Couldn't compile|Could not compile|shader)/i.test(line) &&
    !/(401|Realms|user properties|SignedJWT|YggdrasilUserApiService|minecraftservices|Unable to fetch)/.test(line))
  const shot = path.join(gameDir, 'screenshots', shotName)
  const done = /CRYSTAL_SMOKE_WORLD_DONE/.test(text)
  console.log(`${done && fs.existsSync(shot) ? 'PASS' : 'FAIL'}: world test finished=${done} screenshot=${fs.existsSync(shot) ? shot : 'missing'}`)
  console.log(problems.length ? `log problems:\n${problems.slice(0, 25).join('\n')}` : 'log problems: none')
  process.exit(done && fs.existsSync(shot) ? 0 : 1)
})().catch(err => { console.log('FAIL:', err.message); process.exit(1) })
