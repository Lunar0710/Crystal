#!/usr/bin/env node
'use strict'
/**
 * Renders the model cosmetics the way the launcher preview draws them and
 * saves the pictures to docs/cosmetics/ (a sheet of outfits, one of every
 * colour variant). Run from the launcher folder:
 *   node scripts/cosmetic-models/showcase/showcase.cjs
 * It bundles entry.ts with esbuild and opens it in a hidden Electron window.
 */
const { execFileSync, spawnSync } = require('child_process')
const fs = require('fs')
const path = require('path')

const HERE = __dirname
const LAUNCHER = path.resolve(HERE, '..', '..', '..')
const OUT = path.resolve(LAUNCHER, '..', 'docs', 'cosmetics')
const BUILD = path.join(require('os').tmpdir(), 'nexora-showcase')

if (process.argv[2] === '--electron') {
  // Inside Electron.
  const { app, BrowserWindow } = require('electron')
  app.whenReady().then(async () => {
    const win = new BrowserWindow({ show: false, width: 800, height: 600, webPreferences: { offscreen: false } })
    // SHOWCASE_PREMIUM=1: only the premium sheet, for a quick look while designing.
    await win.loadFile(path.join(BUILD, 'index.html'), { query: process.env.SHOWCASE_PREMIUM ? { premium: '1' } : {} })
    for (let i = 0; i < 600; i++) {
      if (await win.webContents.executeJavaScript('window.done === true')) break
      await new Promise(r => setTimeout(r, 200))
    }
    const error = await win.webContents.executeJavaScript('window.error || null')
    if (error) { console.error(error); app.exit(1); return }
    const shots = await win.webContents.executeJavaScript('window.shots')
    fs.mkdirSync(OUT, { recursive: true })
    const keep = process.env.SHOWCASE_ALL ? Object.keys(shots) : Object.keys(shots).filter(k => k.startsWith('sheet-') || /^outfit-(wizard|crown|dragon|jet|frog)/.test(k))
    for (const name of keep) {
      fs.writeFileSync(path.join(OUT, `${name}.png`), Buffer.from(shots[name].split(',')[1], 'base64'))
    }
    console.log(`${keep.length} pictures in ${path.relative(process.cwd(), OUT)}`)
    app.exit(0)
  })
  return
}

fs.mkdirSync(BUILD, { recursive: true })
execFileSync(process.execPath, [require.resolve('esbuild/bin/esbuild', { paths: [LAUNCHER] }),
  path.join(HERE, 'entry.ts'), '--bundle', '--format=iife', `--outfile=${path.join(BUILD, 'bundle.js')}`, '--log-level=warning'], { stdio: 'inherit', cwd: LAUNCHER })
fs.writeFileSync(path.join(BUILD, 'index.html'), '<!doctype html><html><body style="margin:0;background:#15171d"><script src="bundle.js"></script></body></html>')
const electron = require(require.resolve('electron', { paths: [LAUNCHER] }))
const env = { ...process.env }
delete env.ELECTRON_RUN_AS_NODE
const r = spawnSync(electron, [__filename, '--electron'], { stdio: 'inherit', env })
process.exit(r.status ?? 1)
