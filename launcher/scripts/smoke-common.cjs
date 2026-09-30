// Shared helpers for the smoke test scripts.
const fs = require('fs')
const path = require('path')
const { spawnSync } = require('child_process')

/**
 * A fresh options.txt opens Minecraft's accessibility onboarding screen first,
 * and Quick Play only starts after that is dismissed, so an unattended test
 * would sit in the menu forever. Also skips the multiplayer warning and mutes sound.
 */
function prepareOptions(gameDir) {
  fs.mkdirSync(gameDir, { recursive: true })
  const file = path.join(gameDir, 'options.txt')
  if (fs.existsSync(file)) {
    // Older test folders: the game must not pause when another window takes
    // the focus mid-test, or later screenshots show the pause menu.
    const text = fs.readFileSync(file, 'utf8')
    if (!/^pauseOnLostFocus:false$/m.test(text)) {
      fs.writeFileSync(file, text.replace(/^pauseOnLostFocus:.*\r?\n?/m, '') + 'pauseOnLostFocus:false\n')
    }
    return
  }
  fs.writeFileSync(file, [
    'pauseOnLostFocus:false',
    'onboardAccessibility:false',
    'skipMultiplayerWarning:true',
    'joinedFirstServer:true',
    'soundCategory_master:0.0',
    'renderDistance:6',
  ].join('\n') + '\n')
}

/** Where the game is stuck: a thread dump of the running JVM, taken with the jstack next to the java it runs on. */
function threadDump(gameDir, pid) {
  if (!pid) return 'no pid'
  const logPath = path.join(gameDir, 'crystal-launch.log')
  const header = fs.existsSync(logPath) ? fs.readFileSync(logPath, 'utf8') : ''
  const java = (header.match(/^# java:\s+(.+)$/m) || [])[1]
  if (!java) return 'java path unknown'
  const jstack = path.join(path.dirname(java.trim()), process.platform === 'win32' ? 'jstack.exe' : 'jstack')
  const out = spawnSync(jstack, [String(pid)], { encoding: 'utf8', timeout: 30000 })
  const dump = (out.stdout || '') + (out.stderr || '')
  // The render thread (called "main" before Minecraft renames it) is what matters for a hang.
  const threads = dump.split('\n\n').filter(t => /"(Render thread|main)"/.test(t))
  return threads.length ? threads.join('\n\n') : dump.slice(0, 6000)
}

/** A Java new enough to run this version's server, from the launcher's candidate list, or null. */
function findServerJava(platform, minMajor) {
  const { spawnSync: run } = require('child_process')
  for (const candidate of platform.javaCandidates()) {
    if (candidate !== 'java' && !fs.existsSync(candidate)) continue
    const out = run(candidate, ['-version'], { encoding: 'utf8' })
    const m = ((out.stderr || '') + (out.stdout || '')).match(/version "(\d+)/)
    if (m && parseInt(m[1], 10) >= minMajor) return candidate
  }
  return null
}

/**
 * A flat test world made by the official server of this version, generated
 * once under dataRoot/smoke-server and reused afterwards. Returns its folder.
 */
async function generateWorld(dataRoot, version, java, log) {
  const { spawn } = require('child_process')
  const serverDir = path.join(dataRoot, 'smoke-server')
  const worldDir = path.join(serverDir, 'world')
  if (fs.existsSync(path.join(worldDir, 'level.dat'))) return worldDir

  fs.mkdirSync(serverDir, { recursive: true })
  const manifest = await (await fetch('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json')).json()
  const versionJson = await (await fetch(manifest.versions.find(v => v.id === version).url)).json()
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

module.exports = { prepareOptions, threadDump, findServerJava, generateWorld }
