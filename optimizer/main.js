// Lunar Optimizer, main process: reads the hardware, streams live usage,
// measures latency and runs the PowerShell engine (elevated only when a change
// needs it, so the app itself starts without a UAC prompt).
const { app, BrowserWindow, ipcMain, shell } = require('electron')
const path = require('path')
const fs = require('fs')
const os = require('os')
const net = require('net')
const { execFile } = require('child_process')
const si = require('systeminformation')

const isWin = process.platform === 'win32'
const selftestArg = process.argv.find(a => a.startsWith('--selftest'))
const enginePath = app.isPackaged
  ? path.join(process.resourcesPath, 'engine', 'engine.ps1')
  : path.join(__dirname, 'engine', 'engine.ps1')

let win = null

function createWindow(show = true) {
  win = new BrowserWindow({
    width: 1320,
    height: 840,
    minWidth: 1060,
    minHeight: 700,
    frame: false,
    show: false,
    backgroundColor: '#050505',
    title: 'Lunar Optimizer',
    icon: path.join(__dirname, 'build', 'icon.png'),
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false, sandbox: true },
  })
  win.removeMenu()
  win.loadFile(path.join(__dirname, 'renderer', 'index.html'))
  if (show) win.once('ready-to-show', () => win.show())
  win.webContents.setWindowOpenHandler(({ url }) => { shell.openExternal(url); return { action: 'deny' } })
  win.on('maximize', () => win.webContents.send('win:state', { maximized: true }))
  win.on('unmaximize', () => win.webContents.send('win:state', { maximized: false }))
}

// ---------------------------------------------------------------- PowerShell
function runPowerShell(args, timeout = 120000) {
  return new Promise((resolve, reject) => {
    execFile('powershell.exe', ['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', ...args],
      { windowsHide: true, timeout, maxBuffer: 16 * 1024 * 1024 },
      (err, stdout, stderr) => err ? reject(Object.assign(err, { stderr })) : resolve(stdout))
  })
}

let adminCache = null
async function isAdmin() {
  if (!isWin) return false
  if (adminCache !== null) return adminCache
  try {
    const out = await runPowerShell(['-Command', '([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)'])
    adminCache = out.trim() === 'True'
  } catch { adminCache = false }
  return adminCache
}

const psQuote = s => `'${String(s).replace(/'/g, "''")}'`

// Runs one engine action. Answers come through a UTF-8 file: stdout of an
// elevated process can't be read, and the console code page mangles umlauts.
async function engine(action, { ids = [], arg = null, elevate = false } = {}) {
  if (!isWin) return { ok: false, error: 'Nur unter Windows verfügbar.' }
  // Not in %TEMP%: "Aufräumen" empties that folder, answer file included.
  const runDir = path.join(app.getPath('userData'), 'run')
  fs.mkdirSync(runDir, { recursive: true })
  const tmp = fs.mkdtempSync(path.join(runDir, 'x-'))
  const out = path.join(tmp, 'out.json')
  const params = ['-File', enginePath, '-Action', action, '-Out', out]
  if (ids.length) params.push('-Ids', ids.join(','))
  if (arg !== null) {
    const argFile = path.join(tmp, 'arg.json')
    fs.writeFileSync(argFile, JSON.stringify(arg), 'utf8')
    params.push('-Arg', '@' + argFile)
  }
  try {
    // LUNAR_FORCE_ELEVATE: CI runs as admin; this still sends every change through the elevated path.
    if (elevate && (process.env.LUNAR_FORCE_ELEVATE || !(await isAdmin()))) {
      // One UAC prompt for this action; a declined prompt makes Start-Process throw.
      const argList = params.map(p => (/[\s,@]/.test(p) || p.includes('\\') ? `"${p}"` : p)).join(' ')
      await runPowerShell(['-Command',
        `Start-Process -FilePath powershell.exe -Verb RunAs -Wait -WindowStyle Hidden -ArgumentList ${psQuote('-NoProfile -ExecutionPolicy Bypass ' + argList)}`])
    } else {
      await runPowerShell(params)
    }
    if (!fs.existsSync(out)) return { ok: false, error: 'Abgebrochen (keine Administrator-Rechte erteilt).' }
    return JSON.parse(fs.readFileSync(out, 'utf8').replace(/^﻿/, ''))
  } catch (err) {
    const msg = String(err.stderr || err.message || err)
    return { ok: false, error: /cancel|abgebrochen|operation was canceled|vom Benutzer/i.test(msg) ? 'Abgebrochen (keine Administrator-Rechte erteilt).' : msg.slice(0, 400) }
  } finally {
    fs.rmSync(tmp, { recursive: true, force: true })
  }
}

// ---------------------------------------------------------------- hardware
async function driverInfo() {
  if (!isWin) return []
  try {
    const out = await runPowerShell(['-Command',
      "Get-CimInstance Win32_VideoController | ForEach-Object { [pscustomobject]@{ name = $_.Name; version = $_.DriverVersion; date = $(try { [Management.ManagementDateTimeConverter]::ToDateTime($_.DriverDate).ToString('yyyy-MM-dd') } catch { $null }); refresh = $_.CurrentRefreshRate; maxRefresh = $_.MaxRefreshRate } } | ConvertTo-Json -Compress"])
    const parsed = JSON.parse(out || '[]')
    return Array.isArray(parsed) ? parsed : [parsed]
  } catch { return [] }
}

async function hardware() {
  const [cpu, mem, layout, graphics, osInfo, disks, fsSize, system, board, battery, drivers] = await Promise.all([
    si.cpu(), si.mem(), si.memLayout(), si.graphics(), si.osInfo(), si.diskLayout(), si.fsSize(), si.system(), si.baseboard(), si.battery(), driverInfo(),
  ].map(p => p.catch(() => null)))
  return { cpu, mem, layout, graphics, osInfo, disks, fsSize, system, board, battery, drivers, platform: process.platform }
}

// GPU numbers need nvidia-smi on Windows: asked every 2 s, not every second.
let gpuCache = { at: 0, data: null }
async function live() {
  const now = Date.now()
  const gpuPromise = now - gpuCache.at > 1900
    ? si.graphics().then(g => { gpuCache = { at: now, data: g.controllers }; return g.controllers }).catch(() => gpuCache.data)
    : Promise.resolve(gpuCache.data)
  const [load, mem, temp, netStats, gpus] = await Promise.all([
    si.currentLoad().catch(() => null), si.mem().catch(() => null), si.cpuTemperature().catch(() => null),
    si.networkStats().catch(() => null), gpuPromise,
  ])
  const gpu = (gpus || []).find(g => g.utilizationGpu != null) || (gpus || [])[0] || null
  return {
    t: now,
    cpu: load ? load.currentLoad : null,
    cores: load ? load.cpus.map(c => c.load) : [],
    memUsed: mem ? mem.active : null,
    memTotal: mem ? mem.total : null,
    cpuTemp: temp && temp.main ? temp.main : null,
    gpu: gpu && gpu.utilizationGpu != null ? gpu.utilizationGpu : null,
    gpuTemp: gpu && gpu.temperatureGpu != null ? gpu.temperatureGpu : null,
    vramUsed: gpu && gpu.memoryUsed != null ? gpu.memoryUsed : null,
    vramTotal: gpu && gpu.memoryTotal != null ? gpu.memoryTotal : null,
    rx: netStats && netStats[0] ? netStats[0].rx_sec : null,
    tx: netStats && netStats[0] ? netStats[0].tx_sec : null,
  }
}

// Processes worth knowing about: what eats CPU or RAM right now.
const PROTECTED = new Set(['system', 'idle', 'system idle process', 'registry', 'smss.exe', 'csrss.exe', 'wininit.exe', 'winlogon.exe', 'services.exe', 'lsass.exe', 'svchost.exe', 'dwm.exe', 'memory compression', 'fontdrvhost.exe', 'explorer.exe', 'lunar optimizer.exe', 'audiodg.exe', 'ctfmon.exe', 'sihost.exe', 'taskhostw.exe', 'secure system', 'msmpeng.exe'])
async function processes() {
  const p = await si.processes()
  const self = process.pid
  const merged = new Map()
  for (const x of p.list) {
    if (!x.name || x.pid === self || x.pid <= 4) continue
    const key = x.name.toLowerCase()
    const m = merged.get(key) || { name: x.name, pids: [], cpu: 0, mem: 0, protected: PROTECTED.has(key) }
    m.pids.push(x.pid); m.cpu += x.cpu || 0; m.mem += (x.memRss || 0) * 1024 // systeminformation reports KB
    merged.set(key, m)
  }
  return [...merged.values()].sort((a, b) => (b.cpu - a.cpu) || (b.mem - a.mem)).slice(0, 40)
}

// ---------------------------------------------------------------- latency
// TCP connect time: works without admin (unlike ICMP ping) and is what a game
// connection pays for its round trip.
function tcpPing(host, port, timeout = 2000) {
  return new Promise(resolve => {
    const start = process.hrtime.bigint()
    const sock = net.connect({ host, port })
    const done = ms => { sock.destroy(); resolve(ms) }
    sock.setTimeout(timeout, () => done(null))
    sock.once('connect', () => done(Number(process.hrtime.bigint() - start) / 1e6))
    sock.once('error', () => done(null))
  })
}

// ---------------------------------------------------------------- IPC
ipcMain.handle('hw:static', () => hardware())
ipcMain.handle('hw:live', () => live())
ipcMain.handle('proc:list', () => processes())
ipcMain.handle('proc:kill', (_e, pids) => {
  let killed = 0
  for (const pid of pids) { try { process.kill(pid); killed++ } catch {} }
  return { killed }
})
ipcMain.handle('net:ping', (_e, { host, port }) => tcpPing(host, port))
ipcMain.handle('engine', (_e, { action, ids, arg, elevate }) => engine(action, { ids, arg, elevate }))
ipcMain.handle('app:info', async () => ({ version: app.getVersion(), admin: await isAdmin(), platform: process.platform }))
ipcMain.handle('shell:open', (_e, url) => { if (/^https:\/\//.test(url)) shell.openExternal(url) })
ipcMain.handle('shell:taskmgr', () => { if (isWin) execFile('taskmgr.exe') })
ipcMain.handle('win:minimize', () => win && win.minimize())
ipcMain.handle('win:maximize', () => win && (win.isMaximized() ? win.unmaximize() : win.maximize()))
ipcMain.handle('win:close', () => win && win.close())
ipcMain.handle('app:reboot', () => { if (isWin) execFile('shutdown.exe', ['/r', '/t', '5', '/c', 'Lunar Optimizer: Neustart, damit alle Optimierungen wirken.']) })

// ---------------------------------------------------------------- self test
// CI on a real Windows machine: every read, every tweak applied and reverted,
// the page loaded without errors. Writes a JSON report and quits.
async function selftest(file) {
  const report = { started: new Date().toISOString(), steps: [] }
  const step = async (name, fn) => {
    const t0 = Date.now()
    try { const r = await fn(); report.steps.push({ name, ok: true, ms: Date.now() - t0, result: r }) }
    catch (e) { report.steps.push({ name, ok: false, ms: Date.now() - t0, error: String(e && e.stack || e) }) }
  }
  const pageErrors = []
  createWindow(false)
  win.webContents.on('console-message', (_e, level, msg) => { if (level >= 3) pageErrors.push(msg) })
  await step('page', () => new Promise((resolve, reject) => {
    win.webContents.once('did-finish-load', () => setTimeout(resolve, 4000))
    win.webContents.once('did-fail-load', (_e, code, desc) => reject(new Error(desc)))
  }).then(() => ({ errors: pageErrors, title: win.getTitle() })))
  await step('hardware', async () => { const h = await hardware(); return { cpu: h.cpu && h.cpu.brand, gpus: h.graphics && h.graphics.controllers.map(c => c.model), ram: h.mem && h.mem.total, drivers: h.drivers } })
  await step('live', () => live())
  await step('processes', async () => (await processes()).slice(0, 5))
  await step('ping', async () => ({ cloudflare: await tcpPing('1.1.1.1', 443) }))
  await step('admin', () => isAdmin())
  let before = null
  await step('state', async () => { before = await engine('state'); if (!before.ok) throw new Error(before.error); return before })
  await step('startup-list', async () => { const r = await engine('startup-list'); if (!r.ok) throw new Error(r.error); return r })
  await step('clean-scan', async () => { const r = await engine('clean-scan'); if (!r.ok) throw new Error(r.error); return r })
  // Only the elevated path (Start-Process -Verb RunAs, its quoting and the answer file).
  if (selftestArg.includes('elevated')) {
    await step('elevated-clean-scan', async () => { const r = await engine('clean-scan', { elevate: true }); if (!r.ok) throw new Error(r.error); return r.targets.length })
    await step('elevated-startup-set', async () => {
      const list = (await engine('startup-list')).items || []
      const item = list[0]
      if (!item) return 'no startup items'
      const r = await engine('startup-set', { arg: { source: item.source, name: item.name, enabled: item.enabled }, elevate: true })
      if (!r.ok) throw new Error(r.error)
      return item.name
    })
  }
  if (selftestArg.includes('apply') && before && before.tweaks) {
    const ids = before.tweaks.map(t => t.id)
    await step('apply-all', async () => { const r = await engine('apply', { ids, elevate: true }); if (!r.ok || r.results.some(x => !x.ok)) throw new Error(JSON.stringify(r)); return r })
    await step('state-after-apply', async () => {
      const r = await engine('state')
      const missing = r.tweaks.filter(t => !t.applied).map(t => t.id)
      if (missing.length) throw new Error('not applied: ' + missing.join(','))
      return r.powerPlan
    })
    await step('revert-one', async () => { const r = await engine('revert', { ids: ['mouse'], elevate: true }); const s = await engine('state'); if (s.tweaks.find(t => t.id === 'mouse').applied) throw new Error('mouse still applied'); return r })
    await step('undo', async () => { const r = await engine('undo', { elevate: true }); if (!r.ok) throw new Error(r.error); return r })
    await step('state-after-undo', async () => {
      const r = await engine('state')
      const changed = r.tweaks.filter(t => t.applied !== before.tweaks.find(b => b.id === t.id).applied).map(t => t.id)
      if (changed.length) throw new Error('differs from before: ' + changed.join(','))
      return r.powerPlan
    })
    await step('startup-toggle', async () => {
      const list = (await engine('startup-list')).items || []
      const item = list.find(i => !i.admin) || list[0]
      if (!item) return 'no startup items on this machine'
      const set1 = await engine('startup-set', { arg: { source: item.source, name: item.name, enabled: !item.enabled }, elevate: item.admin })
      const flipped = ((await engine('startup-list')).items || []).find(i => i.source === item.source && i.name === item.name)
      const set2 = await engine('startup-set', { arg: { source: item.source, name: item.name, enabled: item.enabled }, elevate: item.admin })
      const back = ((await engine('startup-list')).items || []).find(i => i.source === item.source && i.name === item.name)
      if (flipped.enabled === item.enabled || back.enabled !== item.enabled) throw new Error('toggle did not stick: ' + JSON.stringify({ item, set1, flipped, set2, back }))
      return { item: item.name, was: item.enabled }
    })
    await step('clean-run', async () => { const r = await engine('clean-run', { ids: ['usertemp', 'thumbs'], elevate: true }); if (!r.ok) throw new Error(r.error); return r })
  }
  report.ok = report.steps.every(s => s.ok) && pageErrors.length === 0
  fs.writeFileSync(file, JSON.stringify(report, null, 2))
  app.exit(report.ok ? 0 : 1)
}

app.whenReady().then(() => {
  if (selftestArg) {
    const file = selftestArg.includes('=') ? selftestArg.split('=')[1] : path.join(process.cwd(), 'selftest.json')
    if (selftestArg.includes('elevated')) process.env.LUNAR_FORCE_ELEVATE = '1'
    return selftest(file.replace(/^(apply|elevated):/, ''))
  }
  createWindow()
})
app.on('window-all-closed', () => { if (!selftestArg) app.quit() })
