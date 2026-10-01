#!/usr/bin/env node
'use strict'
/**
 * Writes what the Lunar Cosmetics addon (addon/) ships instead of a launcher:
 *
 *   addon/src/main/resources/assets/crystal/cosmetics/catalog.json            in-game menu catalog
 *   addon/src/main/resources/assets/crystal/textures/cosmetics/capes_full/    every built-in cape, 64x32 up to 4x
 *   addon/src/main/resources/assets/crystal/textures/cosmetics/capes_anim/    animated capes as frame strips
 *
 * Run from the launcher folder after `npm run gen:cosmetics`:  npm run gen:addon
 * It bundles addon-entry.ts with esbuild and opens it in a hidden Electron
 * window, like gen:thumbs, so the capes are drawn by the launcher's own code.
 */
const { execFileSync, spawnSync } = require('child_process')
const fs = require('fs')
const path = require('path')

const HERE = __dirname
const LAUNCHER = path.resolve(HERE, '..', '..', '..')
const REPO = path.resolve(LAUNCHER, '..')
const ASSETS = path.join(REPO, 'addon', 'src', 'main', 'resources', 'assets', 'crystal')
const OUT = {
  cape: path.join(ASSETS, 'textures', 'cosmetics', 'capes_full'),
  anim: path.join(ASSETS, 'textures', 'cosmetics', 'capes_anim'),
}
const CATALOG = path.join(ASSETS, 'cosmetics', 'catalog.json')
const BUILD = path.join(require('os').tmpdir(), 'nexora-addon-assets')

if (process.argv[2] === '--electron') {
  const { app, BrowserWindow } = require('electron')
  app.whenReady().then(async () => {
    const win = new BrowserWindow({ show: false, width: 800, height: 600 })
    await win.loadFile(path.join(BUILD, 'index.html'))
    for (let i = 0; i < 1500; i++) {
      if (await win.webContents.executeJavaScript('window.done === true')) break
      await new Promise(r => setTimeout(r, 200))
    }
    const error = await win.webContents.executeJavaScript('window.error || null')
    if (error) { console.error(error); app.exit(1); return }
    const shots = await win.webContents.executeJavaScript('window.shots')
    const catalog = await win.webContents.executeJavaScript('window.catalog')
    // Replaced as a whole, so capes that were removed go away.
    for (const dir of Object.values(OUT)) fs.rmSync(dir, { recursive: true, force: true })
    let n = 0
    for (const [name, url] of Object.entries(shots)) {
      const [kind, id] = name.split('/')
      const file = path.join(OUT[kind], id + '.png')
      fs.mkdirSync(path.dirname(file), { recursive: true })
      fs.writeFileSync(file, Buffer.from(url.split(',')[1], 'base64'))
      n++
    }
    fs.mkdirSync(path.dirname(CATALOG), { recursive: true })
    fs.writeFileSync(CATALOG, JSON.stringify(JSON.parse(catalog), null, 1) + '\n')
    console.log(`${n} cape textures and catalog.json written`)
    app.exit(0)
  })
  return
}

fs.mkdirSync(BUILD, { recursive: true })
execFileSync(process.execPath, [require.resolve('esbuild/bin/esbuild', { paths: [LAUNCHER] }),
  path.join(HERE, 'addon-entry.ts'), '--bundle', '--format=iife', `--outfile=${path.join(BUILD, 'bundle.js')}`, '--log-level=error'], { stdio: 'inherit', cwd: LAUNCHER })
fs.writeFileSync(path.join(BUILD, 'index.html'), '<!doctype html><html><body><script src="bundle.js"></script></body></html>')
const electron = require(require.resolve('electron', { paths: [LAUNCHER] }))
const env = { ...process.env }
delete env.ELECTRON_RUN_AS_NODE
const r = spawnSync(electron, [__filename, '--electron'], { stdio: 'inherit', env })
process.exit(r.status ?? 1)
