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
  if (fs.existsSync(file)) return
  fs.writeFileSync(file, [
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

module.exports = { prepareOptions, threadDump }
