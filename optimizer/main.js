// Lunar Optimizer, main process: reads the hardware, streams live usage,
// measures latency and runs the PowerShell engine (elevated only when a change
// needs it, so the app itself starts without a UAC prompt).
const { app, BrowserWindow, ipcMain, shell } = require('electron')
const path = require('path')
const fs = require('fs')
const os = require('os')
const net = require('net')
const { execFile } = require('child_process')
const dns = require('dns')
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
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false, sandbox: true, spellcheck: false, backgroundThrottling: true },
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

// One PowerShell that stays open for everything that needs no admin rights:
// starting powershell.exe costs 0.5 to 1.5 s on older PCs, a command in the
// running one a few milliseconds. Commands run one after another; if the
// process dies or hangs, the next call starts a fresh one.
const { spawn } = require('child_process')
const psHost = {
  proc: null, queue: Promise.resolve(), n: 0,
  start() {
    this.proc = spawn('powershell.exe', ['-NoProfile', '-NoLogo', '-NoExit', '-ExecutionPolicy', 'Bypass', '-Command', '-'], { windowsHide: true })
    this.proc.stdout.setEncoding('utf8')
    this.buf = ''
    this.proc.stdout.on('data', d => { this.buf += d; if (this.onData) this.onData() })
    this.proc.on('exit', () => { this.proc = null })
    this.proc.stdin.on('error', () => {})
  },
  run(command, timeout = 120000) {
    const job = this.queue.then(() => new Promise((resolve, reject) => {
      if (!this.proc) this.start()
      const marker = `<<LUNAR-${++this.n}>>`
      const timer = setTimeout(() => { this.onData = null; try { this.proc.kill() } catch {} ; this.proc = null; reject(new Error('Zeitüberschreitung')) }, timeout)
      this.onData = () => {
        const i = this.buf.indexOf(marker)
        if (i < 0) return
        this.buf = this.buf.slice(i + marker.length); this.onData = null; clearTimeout(timer); resolve()
      }
      this.proc.stdin.write(`${command}; [Console]::Out.WriteLine('${marker}')\n`)
    }))
    this.queue = job.catch(() => {})
    return job
  },
}

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
      // In the long-running PowerShell: same script, same answer file.
      const args = params.slice(2).map(p => p.startsWith('-') ? p : psQuote(p)).join(' ')
      try { await psHost.run(`& ${psQuote(enginePath)} ${args}`) }
      catch { await runPowerShell(params) }
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

// Some of these ask WMI or nvidia-smi, which can hang for a long time on
// some PCs. Every part gets its own time limit; what is missing stays empty
// instead of holding up the whole page.
const withTimeout = (promise, ms) => Promise.race([promise.catch(() => null), new Promise(r => setTimeout(() => r(null), ms))])
const HW_PARTS = {
  cpu: () => si.cpu(), mem: () => si.mem(), layout: () => si.memLayout(), graphics: () => si.graphics(), osInfo: () => si.osInfo(),
  disks: () => si.diskLayout(), fsSize: () => si.fsSize(), system: () => si.system(), board: () => si.baseboard(), battery: () => si.battery(), drivers: () => driverInfo(),
}
let hwCache = null, hwPending = null
function hardware(part) {
  if (part) return withTimeout(HW_PARTS[part](), 12000)
  if (hwCache) return Promise.resolve(hwCache)
  // One read at a time, however many ask.
  if (!hwPending) hwPending = readHardware().finally(() => { hwPending = null })
  return hwPending
}
let hwEngineError = null
async function readHardware() {
  // Windows: one PowerShell for everything; much lighter than a query per part.
  if (isWin) {
    const r = await withTimeout(engine('hardware'), 60000)
    if (r && r.ok && r.hardware) { hwCache = r.hardware; return hwCache }
    hwEngineError = r ? r.error : 'Zeitüberschreitung'
  }
  const keys = Object.keys(HW_PARTS)
  const values = await Promise.all(keys.map(k => withTimeout(HW_PARTS[k](), 12000)))
  const out = { platform: process.platform }
  keys.forEach((k, i) => { out[k] = values[i] })
  hwCache = out
  return out
}

// Live usage, kept light: CPU and RAM come straight from the OS (no
// process started), the expensive readings (nvidia-smi, WMI temperatures,
// network counters) only when the page showing them asks, and not often.
let lastCpu = os.cpus()
function cpuLoad() {
  const now = os.cpus()
  const cores = now.map((c, i) => {
    const a = lastCpu[i] ? lastCpu[i].times : { user: 0, nice: 0, sys: 0, idle: 0, irq: 0 }, b = c.times
    const busy = (b.user - a.user) + (b.nice - a.nice) + (b.sys - a.sys) + (b.irq - a.irq)
    const total = busy + (b.idle - a.idle)
    return total > 0 ? busy / total * 100 : 0
  })
  lastCpu = now
  return { total: cores.reduce((x, y) => x + y, 0) / Math.max(1, cores.length), cores }
}
const slow = { gpu: { at: 0, data: null, every: 5000 }, temp: { at: 0, data: null, every: 10000 }, net: { at: 0, data: null, every: 3000 } }
async function cached(key, fn) {
  const c = slow[key], now = Date.now()
  if (now - c.at >= c.every) { c.at = now; c.data = await fn().catch(() => c.data) }
  return c.data
}
async function live(want = {}) {
  const load = cpuLoad()
  const total = os.totalmem(), free = os.freemem()
  const [gpus, temp, netStats] = await Promise.all([
    want.gpu ? cached('gpu', () => si.graphics().then(g => g.controllers)) : Promise.resolve(slow.gpu.data),
    want.temp ? cached('temp', () => si.cpuTemperature()) : Promise.resolve(slow.temp.data),
    want.net ? cached('net', () => si.networkStats()) : Promise.resolve(null),
  ])
  const gpu = (gpus || []).find(g => g.utilizationGpu != null) || (gpus || [])[0] || null
  return {
    t: Date.now(),
    cpu: load.total,
    cores: load.cores,
    memUsed: total - free,
    memTotal: total,
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

// ---------------------------------------------------------------- DNS
// Every name lookup (server address, skins, Discord) waits on the DNS server.
// Measured with a few everyday names; the slowest third is dropped as noise.
const DNS_NAMES = ['minecraft.net', 'hypixel.net', 'discord.com', 'youtube.com', 'twitch.tv', 'steampowered.com', 'github.com', 'epicgames.com']
async function dnsTime(server) {
  const r = new dns.promises.Resolver({ timeout: 1500, tries: 1 })
  r.setServers([server])
  const times = []
  let failed = 0
  for (const name of DNS_NAMES) {
    const t0 = process.hrtime.bigint()
    try { await r.resolve4(name); times.push(Number(process.hrtime.bigint() - t0) / 1e6) } catch { failed++ }
  }
  times.sort((a, b) => a - b)
  const keep = times.slice(0, Math.max(1, Math.ceil(times.length * 2 / 3)))
  return { server, ms: keep.length ? keep.reduce((a, b) => a + b, 0) / keep.length : null, failed }
}
async function dnsBench(current) {
  const list = [
    { name: 'Cloudflare', servers: ['1.1.1.1', '1.0.0.1'] },
    { name: 'Google', servers: ['8.8.8.8', '8.8.4.4'] },
    { name: 'Quad9', servers: ['9.9.9.9', '149.112.112.112'] },
    { name: 'AdGuard', servers: ['94.140.14.14', '94.140.15.15'] },
  ]
  if (current && current.length) list.unshift({ name: 'Aktuell', servers: current, current: true })
  const out = []
  for (const d of list) out.push({ ...d, ...(await dnsTime(d.servers[0])) })
  return out
}

// ---------------------------------------------------------------- auto update
// The installed app (Setup) updates itself: electron-updater reads latest.yml
// next to the website's download, fetches the new setup in the background and
// installs it when the user clicks. The portable exe can't replace itself; it
// only gets the notice below.
const isPortable = !!process.env.PORTABLE_EXECUTABLE_DIR
function startAutoUpdate() {
  if (!app.isPackaged || !isWin || isPortable || selftestArg) return
  let autoUpdater
  try { ({ autoUpdater } = require('electron-updater')) } catch { return }
  autoUpdater.autoDownload = true
  autoUpdater.autoInstallOnAppQuit = true
  autoUpdater.on('update-available', info => win && win.webContents.send('update:state', { state: 'downloading', version: info.version }))
  autoUpdater.on('download-progress', p => win && win.webContents.send('update:state', { state: 'downloading', percent: Math.round(p.percent) }))
  autoUpdater.on('update-downloaded', info => win && win.webContents.send('update:state', { state: 'ready', version: info.version }))
  autoUpdater.on('error', () => {})
  ipcMain.handle('update:install', () => autoUpdater.quitAndInstall(true, true))
  const check = () => autoUpdater.checkForUpdates().catch(() => {})
  setTimeout(check, 8000)
  setInterval(check, 6 * 60 * 60 * 1000)
}

// ---------------------------------------------------------------- update check
const VERSION_URL = 'https://lunar0710.github.io/Crystal/optimizer/version.json'
async function checkUpdate() {
  try {
    const { net } = require('electron')
    const res = await net.fetch(VERSION_URL, { cache: 'no-store' })
    if (!res.ok) return null
    const v = await res.json()
    const newer = (a, b) => { const x = a.split('.').map(Number), y = b.split('.').map(Number); for (let i = 0; i < 3; i++) if ((x[i] || 0) !== (y[i] || 0)) return (x[i] || 0) > (y[i] || 0); return false }
    return newer(String(v.version), app.getVersion()) ? { ...v, auto: app.isPackaged && isWin && !isPortable } : null
  } catch { return null }
}

// ---------------------------------------------------------------- boost
// Apps that run in the background for many people and are safe to close
// before a game. Nothing from Windows itself, no anti-cheat, no game launcher
// a running game might need.
const BOOST_APPS = [
  { exe: 'chrome.exe', name: 'Google Chrome', group: 'Browser' },
  { exe: 'msedge.exe', name: 'Microsoft Edge', group: 'Browser' },
  { exe: 'firefox.exe', name: 'Firefox', group: 'Browser' },
  { exe: 'opera.exe', name: 'Opera / Opera GX', group: 'Browser' },
  { exe: 'brave.exe', name: 'Brave', group: 'Browser' },
  { exe: 'OneDrive.exe', name: 'OneDrive', group: 'Cloud' },
  { exe: 'Dropbox.exe', name: 'Dropbox', group: 'Cloud' },
  { exe: 'GoogleDriveFS.exe', name: 'Google Drive', group: 'Cloud' },
  { exe: 'ms-teams.exe', name: 'Microsoft Teams', group: 'Chat' },
  { exe: 'Teams.exe', name: 'Microsoft Teams (alt)', group: 'Chat' },
  { exe: 'Slack.exe', name: 'Slack', group: 'Chat' },
  { exe: 'Discord.exe', name: 'Discord', group: 'Chat', keep: true },
  { exe: 'Spotify.exe', name: 'Spotify', group: 'Musik', keep: true },
  { exe: 'EpicGamesLauncher.exe', name: 'Epic Games Launcher', group: 'Launcher', keep: true },
  { exe: 'Battle.net.exe', name: 'Battle.net', group: 'Launcher', keep: true },
  { exe: 'EADesktop.exe', name: 'EA App', group: 'Launcher', keep: true },
  { exe: 'upc.exe', name: 'Ubisoft Connect', group: 'Launcher', keep: true },
  { exe: 'Creative Cloud.exe', name: 'Adobe Creative Cloud', group: 'Sonstiges' },
  { exe: 'AdobeCollabSync.exe', name: 'Adobe Sync', group: 'Sonstiges' },
  { exe: 'CCXProcess.exe', name: 'Adobe CCX', group: 'Sonstiges' },
  { exe: 'Widgets.exe', name: 'Windows Widgets', group: 'Sonstiges' },
  { exe: 'PhoneExperienceHost.exe', name: 'Smartphone-Link', group: 'Sonstiges' },
  { exe: 'YourPhone.exe', name: 'Ihr Smartphone', group: 'Sonstiges' },
]
async function boostList() {
  const p = await si.processes()
  return BOOST_APPS.map(a => {
    const procs = p.list.filter(x => x.name && x.name.toLowerCase() === a.exe.toLowerCase())
    return { ...a, running: procs.length > 0, pids: procs.map(x => x.pid), mem: procs.reduce((m, x) => m + (x.memRss || 0) * 1024, 0), cpu: procs.reduce((c, x) => c + (x.cpu || 0), 0) }
  }).filter(a => a.running)
}
async function boostClose(exes) {
  if (!isWin) return { closed: [] }
  const allowed = new Set(BOOST_APPS.map(a => a.exe.toLowerCase()))
  const closed = []
  for (const exe of exes) {
    if (!allowed.has(exe.toLowerCase())) continue
    try { await new Promise(r => execFile('taskkill.exe', ['/IM', exe, '/F', '/T'], { windowsHide: true }, () => r())); closed.push(exe) } catch {}
  }
  return { closed }
}

// ---------------------------------------------------------------- network
async function network() {
  try {
    const nics = await si.networkInterfaces('default')
    const n = Array.isArray(nics) ? nics[0] : nics
    return n ? { name: n.ifaceName || n.iface, type: n.type, speed: n.speed } : null
  } catch { return null }
}

// ---------------------------------------------------------------- IPC
ipcMain.handle('hw:static', (_e, part) => part ? hardware(part) : (hwCache = null, hardware()))
ipcMain.handle('hw:parts', () => isWin ? null : Object.keys(HW_PARTS))
ipcMain.handle('hw:live', (_e, want) => live(want))
ipcMain.handle('proc:list', () => processes())
ipcMain.handle('proc:kill', (_e, pids) => {
  let killed = 0
  for (const pid of pids) { try { process.kill(pid); killed++ } catch {} }
  return { killed }
})
ipcMain.handle('net:ping', (_e, { host, port }) => tcpPing(host, port))
ipcMain.handle('net:dns', (_e, current) => dnsBench(current))
ipcMain.handle('net:info', () => network())
ipcMain.handle('app:update', () => checkUpdate())
ipcMain.handle('boost:list', () => boostList())
ipcMain.handle('boost:close', (_e, exes) => boostClose(exes))
ipcMain.handle('engine', (_e, { action, ids, arg, elevate }) => engine(action, { ids, arg, elevate }))
ipcMain.handle('app:info', async () => ({ version: app.getVersion(), admin: await isAdmin(), platform: process.platform }))
ipcMain.handle('shell:open', (_e, url) => { if (/^(https:\/\/|ms-settings:)/.test(url)) shell.openExternal(url) })
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
  await step('hardware', async () => { const h = await hardware(); if (isWin && !(h.cpu && h.cpu.brand)) throw new Error('no CPU from engine hardware: ' + hwEngineError); return { cpu: h.cpu && h.cpu.brand, gpus: h.graphics && h.graphics.controllers.map(c => c.model), ram: h.mem && h.mem.total, drivers: h.drivers } })
  await step('live', () => live({ gpu: true, temp: true, net: true }))
  await step('processes', async () => (await processes()).slice(0, 5))
  await step('ping', async () => ({ cloudflare: await tcpPing('1.1.1.1', 443) }))
  await step('admin', () => isAdmin())
  let before = null
  await step('state', async () => { before = await engine('state'); if (!before.ok) throw new Error(before.error); return before })
  await step('overview', async () => { const r = await engine('overview'); if (!r.ok || !r.tweaks || !r.dns) throw new Error(JSON.stringify(r).slice(0, 300)); return { tweaks: r.tweaks.length, startup: (r.startup || []).length, dns: r.dns.adapters.length } })
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
      const missing = r.tweaks.filter(t => !t.applied).map(t => `${t.id} (${t.actual || '?'})`)
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
    await step('dns', async () => {
      const g = await engine('dns-get')
      if (!g.ok) throw new Error(g.error)
      const before = JSON.stringify(g.adapters.map(a => a.servers))
      const bench = await dnsBench(g.adapters[0] && g.adapters[0].servers)
      const set = await engine('dns-set', { arg: { servers: ['1.1.1.1', '1.0.0.1'] }, elevate: true })
      if (!set.ok) throw new Error('set: ' + set.error)
      if (!set.adapters.every(a => a.servers[0] === '1.1.1.1')) throw new Error('not set: ' + JSON.stringify(set.adapters))
      const reset = await engine('dns-set', { arg: { reset: true }, elevate: true })
      if (!reset.ok) throw new Error('reset: ' + reset.error)
      const after = JSON.stringify(reset.adapters.map(a => a.servers))
      if (after !== before) throw new Error(`reset differs: ${before} -> ${after}`)
      return { adapters: g.adapters, bench }
    })
    await step('boost-list', () => boostList())
    await step('network', () => network())
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
  startAutoUpdate()
})
app.on('window-all-closed', () => { if (!selftestArg) app.quit() })
app.on('will-quit', () => { try { psHost.proc && psHost.proc.kill() } catch {} })
