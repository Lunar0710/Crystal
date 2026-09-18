// Writes cape-ranks.json: every built-in cape id with the rank it needs, taken
// from the launcher's own cape list so the server never drifts from it.
// Run after adding capes: node scripts/export-capes.cjs
const path = require('path')
const fs = require('fs')
const launcher = path.join(__dirname, '..', '..', 'launcher')
const esbuild = require(path.join(launcher, 'node_modules/esbuild'))

const out = esbuild.buildSync({
  entryPoints: [path.join(launcher, 'src/renderer/data/capes.ts')],
  bundle: true, write: false, platform: 'node', format: 'cjs', logLevel: 'error',
})
const mod = { exports: {} }
new Function('module', 'exports', 'require', out.outputFiles[0].text)(mod, mod.exports, require)

const capes = {}
for (const c of mod.exports.BUILTIN_CAPES) {
  capes[c.id] = { rank: c.requiredRank || null, exact: !!c.exactRank, animated: !!c.animate }
}
const target = path.join(__dirname, '..', 'cape-ranks.json')
fs.writeFileSync(target, JSON.stringify(capes, null, 1))
const byRank = {}
for (const c of Object.values(capes)) byRank[c.rank || 'alle'] = (byRank[c.rank || 'alle'] || 0) + 1
console.log(Object.keys(capes).length, 'capes ->', target, JSON.stringify(byRank))
