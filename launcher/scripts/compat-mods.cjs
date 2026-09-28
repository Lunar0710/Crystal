// Downloads the mod set for the compatibility test: popular mods that bring
// native code or load code of their own. Nexora has to start a world with all
// of them before a release. Prints the jars as a ';'-separated list for
// CRYSTAL_SMOKE_EXTRA_MODS. Usage: node compat-mods.cjs <mcVersion> <cacheDir>
const fs = require('fs')
const path = require('path')
const crypto = require('crypto')

const MODS = [
  'simple-voice-chat', // native Opus/RNNoise audio libraries
  'essential', // loads its own code at start
  'iris', // shaders, needs Sodium (the performance pack brings it)
  'modmenu',
]
const HEADERS = { 'User-Agent': 'Nexora-Launcher compat-test' }

async function main() {
  const [mc = '1.21.11', cache = 'D:/crystal-tests/compat-mods'] = process.argv.slice(2)
  fs.mkdirSync(cache, { recursive: true })
  const jars = []
  for (const slug of MODS) {
    const url = `https://api.modrinth.com/v2/project/${slug}/version?game_versions=${encodeURIComponent(JSON.stringify([mc]))}&loaders=${encodeURIComponent(JSON.stringify(['fabric']))}`
    const versions = await (await fetch(url, { headers: HEADERS })).json()
    const file = versions[0] && (versions[0].files.find(f => f.primary) || versions[0].files[0])
    if (!file) { console.error(`skip ${slug}: no ${mc} build`); continue }
    const target = path.join(cache, file.filename)
    if (!fs.existsSync(target)) {
      const data = Buffer.from(await (await fetch(file.url, { headers: HEADERS })).arrayBuffer())
      if (crypto.createHash('sha512').update(data).digest('hex') !== file.hashes.sha512) throw new Error(`${slug}: checksum mismatch`)
      fs.writeFileSync(target, data)
    }
    jars.push(target)
  }
  process.stdout.write(jars.join(';'))
}
main().catch(err => { console.error(err.message); process.exit(1) })
