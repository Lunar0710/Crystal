#!/usr/bin/env node
'use strict'
/**
 * Renders a picture of every cosmetic in every colour variant, the way the
 * launcher draws it (cosmeticThumbs.ts drawPicture), and ships them:
 *
 *   launcher/src/renderer/assets/cosmetic-thumbs/<item>/<variant>.png   240×180, Cosmetics page
 *   client/.../textures/cosmetics/thumbs/<item>/<variant>.png          120×90, in-game menu
 *   client/.../textures/cosmetics/capes/<cape>.png                     40×64, cape front for the in-game grid
 *   docs/cosmetics/contact-sheet.png, contact-sheet-variants.png       every tile, for checking
 *
 * Run from the launcher folder after `npm run gen:cosmetics`:  npm run gen:thumbs
 * (--only <text> redraws just the items whose id contains it, no sheets;
 * --capes redraws just the cape pictures).
 * It bundles thumbs-entry.ts with esbuild and opens it in a hidden Electron window.
 */
const { execFileSync, spawnSync } = require('child_process')
const fs = require('fs')
const path = require('path')

const HERE = __dirname
const LAUNCHER = path.resolve(HERE, '..', '..', '..')
const REPO = path.resolve(LAUNCHER, '..')
const OUT = {
  thumb: path.join(LAUNCHER, 'src', 'renderer', 'assets', 'cosmetic-thumbs'),
  small: path.join(REPO, 'client', 'src', 'main', 'resources', 'assets', 'crystal', 'textures', 'cosmetics', 'thumbs'),
  cape: path.join(REPO, 'client', 'src', 'main', 'resources', 'assets', 'crystal', 'textures', 'cosmetics', 'capes'),
  sheet: path.join(REPO, 'docs', 'cosmetics'),
}
const BUILD = path.join(require('os').tmpdir(), 'nexora-thumbs')

if (process.argv[2] === '--electron') {
  const { app, BrowserWindow } = require('electron')
  const only = process.argv[3] || ''
  const capesOnly = process.argv[4] === 'capes'
  app.whenReady().then(async () => {
    const win = new BrowserWindow({ show: false, width: 800, height: 600 })
    await win.loadFile(path.join(BUILD, 'index.html'), { query: { ...(only ? { only } : {}), ...(capesOnly ? { capes: '1' } : {}) } })
    for (let i = 0; i < 3000; i++) {
      if (await win.webContents.executeJavaScript('window.done === true')) break
      await new Promise(r => setTimeout(r, 200))
    }
    const error = await win.webContents.executeJavaScript('window.error || null')
    if (error) { console.error(error); app.exit(1); return }
    const shots = await win.webContents.executeJavaScript('window.shots')
    // A full run replaces everything, so pictures of removed items go away.
    if (!only) fs.rmSync(OUT.cape, { recursive: true, force: true })
    if (!only && !capesOnly) {
      fs.rmSync(OUT.thumb, { recursive: true, force: true })
      fs.rmSync(OUT.small, { recursive: true, force: true })
    }
    let n = 0
    for (const [name, url] of Object.entries(shots)) {
      const [kind, ...rest] = name.split('/')
      const file = path.join(OUT[kind], ...rest) + '.png'
      fs.mkdirSync(path.dirname(file), { recursive: true })
      fs.writeFileSync(file, Buffer.from(url.split(',')[1], 'base64'))
      n++
    }
    console.log(`${n} pictures written`)
    app.exit(0)
  })
  return
}

const onlyIndex = process.argv.indexOf('--only')
const only = onlyIndex > 0 ? process.argv[onlyIndex + 1] : ''
const capesOnly = process.argv.includes('--capes')
fs.mkdirSync(BUILD, { recursive: true })
execFileSync(process.execPath, [require.resolve('esbuild/bin/esbuild', { paths: [LAUNCHER] }),
  path.join(HERE, 'thumbs-entry.ts'), '--bundle', '--format=iife', `--outfile=${path.join(BUILD, 'bundle.js')}`, '--log-level=error'], { stdio: 'inherit', cwd: LAUNCHER })
fs.writeFileSync(path.join(BUILD, 'index.html'), '<!doctype html><html><body style="margin:0;background:#15171d"><script src="bundle.js"></script></body></html>')
const electron = require(require.resolve('electron', { paths: [LAUNCHER] }))
const env = { ...process.env }
delete env.ELECTRON_RUN_AS_NODE
const r = spawnSync(electron, [__filename, '--electron', only, capesOnly ? 'capes' : ''], { stdio: 'inherit', env })
process.exit(r.status ?? 1)
