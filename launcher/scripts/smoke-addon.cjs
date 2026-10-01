// Lunar Cosmetics test: starts Minecraft 1.21.11 on Fabric Loader 0.19.3 (the
// loader Feather Client runs) with only Fabric API and the addon jar
// (addon/build/libs), no Nexora, in a generated flat world. The addon's smoke
// run (-Dlunarcosmetics.smoke) photographs the worn cosmetics from the front
// and back, the Cosmetics menu and an emote, then quits.
//
// The loadout is taken from the addon's own catalog.json: 3D model hat, wings,
// backpack and pet, an aura, and an animated cape.
//
// LUNAR_SMOKE_MODS adds Modrinth projects by slug (comma-separated, e.g.
// "sodium,iris,entityculling") to check the addon next to them.
// Usage: node scripts/smoke-addon.cjs   (after `npm run build:main` and the addon build)
const path = require('path')
const fs = require('fs')
const os = require('os')
const Module = require('module')

const launcherRoot = path.resolve(__dirname, '..')
const repoRoot = path.resolve(launcherRoot, '..')
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
const LOADER = process.env.LUNAR_SMOKE_LOADER || '0.19.3'
const dataRoot = process.env.CRYSTAL_SMOKE_ROOT || fs.mkdtempSync(path.join(os.tmpdir(), 'lunar-smoke-'))
const { setCrystalRoot } = require(path.join(launcherRoot, 'dist/main/paths.js'))
setCrystalRoot(dataRoot)
const platform = require(path.join(launcherRoot, 'dist/main/minecraft/platform.js'))
const Store = require(path.join(launcherRoot, 'node_modules/electron-store'))
const { MinecraftManager } = require(path.join(launcherRoot, 'dist/main/minecraft/MinecraftManager.js'))
const { requiredJavaMajor } = require(path.join(launcherRoot, 'dist/main/minecraft/versions.js'))
const { prepareOptions, threadDump, findServerJava, generateWorld } = require('./smoke-common.cjs')
const log = (...a) => console.log('[smoke-addon]', ...a)

const ADDON_ASSETS = path.join(repoRoot, 'addon', 'src', 'main', 'resources', 'assets', 'crystal')

/** The newest Fabric build of a Modrinth project for this version, downloaded into modsDir. */
async function modrinth(slug, modsDir) {
  const url = `https://api.modrinth.com/v2/project/${slug}/version` +
    `?game_versions=${encodeURIComponent(JSON.stringify([VERSION]))}&loaders=${encodeURIComponent(JSON.stringify(['fabric']))}`
  const versions = await (await fetch(url, { headers: { 'User-Agent': 'nexora-smoke-test' } })).json()
  if (!Array.isArray(versions) || !versions.length) throw new Error(`${slug}: no Fabric build for ${VERSION}`)
  const file = versions[0].files.find(f => f.primary) || versions[0].files[0]
  fs.writeFileSync(path.join(modsDir, file.filename), Buffer.from(await (await fetch(file.url)).arrayBuffer()))
  log('mod', slug, versions[0].version_number)
}

/** Wears hat, wings, backpack, pet and aura from the addon's catalog, and an animated cape. */
function writeLoadout(gameDir) {
  const root = path.join(gameDir, 'config', 'lunarcosmetics')
  const dir = path.join(root, 'cosmetics')
  fs.rmSync(root, { recursive: true, force: true })
  fs.mkdirSync(dir, { recursive: true })
  // No server connection from CI.
  fs.writeFileSync(path.join(root, 'settings.json'), JSON.stringify({ sync: false }))
  const catalog = JSON.parse(fs.readFileSync(path.join(ADDON_ASSETS, 'cosmetics', 'catalog.json'), 'utf8'))
  const want = { hat: 'md-wizard-hat', wings: 'md-seraph-wings', backpack: 'md-expedition-pack', pet: null, aura: null }
  const loadout = {}
  for (const s of catalog.slots) {
    if (!(s.slot in want)) continue
    // The named item, else the slot's first 3D model item, else its first item.
    const item = s.items.find(i => i.id === want[s.slot]) || s.items.find(i => i.model) || s.items[0]
    loadout[s.slot] = { ...item.variants[0].item, vid: item.variants[0].id }
    log('wearing', s.slot, item.id)
  }
  fs.writeFileSync(path.join(dir, 'loadout.json'), JSON.stringify(loadout))
  const cape = catalog.capes.find(c => c.frames > 1)
  fs.copyFileSync(path.join(ADDON_ASSETS, 'textures', 'cosmetics', 'capes_anim', cape.id + '.png'), path.join(dir, 'equipped_cape.png'))
  fs.writeFileSync(path.join(dir, 'equipped_cape.json'), JSON.stringify({ frames: cape.frames, fps: cape.fps }))
  fs.writeFileSync(path.join(dir, 'equipped.json'), JSON.stringify({ cape: cape.id }))
  log('cape', cape.id, cape.name)
}

;(async () => {
  log(`dataRoot=${dataRoot} version=${VERSION} loader=${LOADER}`)
  const java = findServerJava(platform, requiredJavaMajor(VERSION))
    || await new MinecraftManager(new Store({ cwd: dataRoot, name: 'smoke-store' })).ensureJava(VERSION, () => {})
  if (!java) throw new Error(`no Java ${requiredJavaMajor(VERSION)} found for the world generator`)

  const worldDir = await generateWorld(dataRoot, VERSION, java, log)
  const gameDir = path.join(dataRoot, 'instances', 'smoke-addon')
  const saveDir = path.join(gameDir, 'saves', 'smoke')
  fs.rmSync(saveDir, { recursive: true, force: true })
  fs.mkdirSync(path.dirname(saveDir), { recursive: true })
  fs.cpSync(worldDir, saveDir, { recursive: true })
  prepareOptions(gameDir)
  // No "Move with WASD" tutorial toast over the screenshots.
  const options = path.join(gameDir, 'options.txt')
  if (!/^tutorialStep:/m.test(fs.readFileSync(options, 'utf8'))) fs.appendFileSync(options, 'tutorialStep:none\n')
  writeLoadout(gameDir)

  // The launcher uses a cached loader profile first: pin it to Feather's loader.
  const profileFile = path.join(dataRoot, 'versions', VERSION, 'fabric-loader-profile.json')
  fs.mkdirSync(path.dirname(profileFile), { recursive: true })
  const profile = await (await fetch(`https://meta.fabricmc.net/v2/versions/loader/${VERSION}/${LOADER}/profile/json`)).json()
  fs.writeFileSync(profileFile, JSON.stringify(profile))

  const modsDir = path.join(gameDir, 'mods')
  fs.rmSync(modsDir, { recursive: true, force: true })
  fs.mkdirSync(modsDir, { recursive: true })
  const libs = path.join(repoRoot, 'addon', 'build', 'libs')
  const jar = fs.readdirSync(libs).find(f => /^lunar-cosmetics-.*\.jar$/.test(f) && !f.includes('-sources'))
  if (!jar) throw new Error(`no addon jar in ${libs}`)
  fs.copyFileSync(path.join(libs, jar), path.join(modsDir, jar))
  log('mod', jar)
  await modrinth('fabric-api', modsDir)
  for (const slug of (process.env.LUNAR_SMOKE_MODS || '').split(',').map(s => s.trim()).filter(Boolean)) await modrinth(slug, modsDir)

  const shotName = 'lunar-smoke.png'
  fs.rmSync(path.join(gameDir, 'screenshots'), { recursive: true, force: true })
  const manager = new MinecraftManager(new Store({ cwd: dataRoot, name: 'smoke-store' }))
  const account = { username: 'LunarSmoke', uuid: '00196142-0019-3019-8001-00196142c1a6', accessToken: 'offline', type: 'offline' }
  let pid = null
  log('launching')
  const ok = await manager.launch({
    version: VERSION, instanceId: 'smoke-addon', gameDir, username: account.username, profile: account, maxRam: 3072,
    loader: 'fabric', injectCrystal: false,
    extraJvmArgs: [`-Dlunarcosmetics.smoke=${shotName}`],
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
      if (/LUNAR_SMOKE_DONE/.test(text) || /Crash report saved|Incompatible mods found|Mod resolution failed/.test(text)) { clearInterval(timer); resolve() }
      else if (Date.now() - started > 12 * 60 * 1000) {
        clearInterval(timer)
        const dump = threadDump(gameDir, pid)
        if (pid) { try { process.kill(pid) } catch {} }
        reject(new Error(`timeout waiting for the addon test\n${text.slice(-3000)}\n--- thread dump ---\n${dump}`))
      }
    }, 2000)
  })
  await new Promise(r => setTimeout(r, 4000))
  if (pid) { try { process.kill(pid) } catch {} }

  const text = fs.readFileSync(logPath, 'utf8')
  const problems = text.split('\n').filter(line =>
    /(ERROR|Exception|Failed to load|Couldn't compile|Could not compile|shader)/i.test(line) &&
    !/(401|Realms|user properties|SignedJWT|YggdrasilUserApiService|minecraftservices|Unable to fetch)/.test(line))
  const shots = ['', '-back', '-menu', '-emote'].map(s => path.join(gameDir, 'screenshots', `lunar-smoke${s}.png`))
  const done = /LUNAR_SMOKE_DONE/.test(text)
  const ready = /\[Lunar Cosmetics\] ready/.test(text)
  const worn = /worn: hat=true wings=true aura=true pet=true/.test(text)
  const emote = /emote playing: true/.test(text)
  const loader = (text.match(/Loading Minecraft \S+ with Fabric Loader ([\d.]+)/) || [])[1] || '?'
  const missing = shots.filter(f => !fs.existsSync(f))
  const passed = done && ready && worn && emote && !missing.length
  console.log(`${passed ? 'PASS' : 'FAIL'}: addon test loader=${loader} finished=${done} ready=${ready} worn=${worn} emote=${emote} screenshots=${missing.length ? 'missing ' + missing.map(f => path.basename(f)).join(', ') : 'all'}`)
  console.log(problems.length ? `log problems:\n${problems.slice(0, 25).join('\n')}` : 'log problems: none')
  process.exit(passed ? 0 : 1)
})().catch(err => { console.log('FAIL:', err.message); process.exit(1) })
