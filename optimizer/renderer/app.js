(() => {
  const $ = id => document.getElementById(id)
  const api = window.lunar || window.lunarMock
  const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]))
  const GB = 1024 ** 3
  const fmtBytes = b => b == null ? '–' : b >= GB ? (b / GB).toFixed(b >= 100 * GB ? 0 : 1) + ' GB' : b >= 1024 ** 2 ? (b / 1024 ** 2).toFixed(0) + ' MB' : b >= 1024 ? (b / 1024).toFixed(0) + ' KB' : b + ' B'
  const fmtRate = b => b == null ? '–' : b >= 1024 ** 2 ? (b / 1024 ** 2).toFixed(1) + ' MB/s' : (b / 1024).toFixed(0) + ' KB/s'
  const pct = v => v == null ? '–' : Math.round(v) + ' %'
  const accent = () => getComputedStyle(document.documentElement).getPropertyValue('--accent').trim() || '#8ea2ff'

  // ------------------------------------------------------------ chrome
  $('wMin').onclick = () => api.window.minimize()
  $('wMax').onclick = () => api.window.maximize()
  $('wClose').onclick = () => api.window.close()

  let toastTimer = 0
  function toast(text, bad = false) {
    const t = $('toast'); t.textContent = text; t.classList.toggle('bad', bad); t.classList.add('on')
    clearTimeout(toastTimer); toastTimer = setTimeout(() => t.classList.remove('on'), 4200)
  }
  function confirmBox(title, text, yes = 'OK') {
    return new Promise(resolve => {
      $('mTitle').textContent = title; $('mText').textContent = text; $('mYes').querySelector('span').textContent = yes
      $('modal').hidden = false
      const done = v => { $('modal').hidden = true; $('mYes').onclick = $('mNo').onclick = null; resolve(v) }
      $('mYes').onclick = () => done(true); $('mNo').onclick = () => done(false)
    })
  }
  async function busy(btn, fn) {
    btn.classList.add('busy'); btn.disabled = true
    try { return await fn() } finally { btn.classList.remove('busy'); btn.disabled = false }
  }

  // ------------------------------------------------------------ navigation
  let page = 'home'
  function go(p) {
    page = p
    document.querySelectorAll('#nav a').forEach(a => a.classList.toggle('on', a.dataset.page === p))
    document.querySelectorAll('.page').forEach(s => s.classList.toggle('on', s.id === 'p-' + p))
    $('main').scrollTop = 0
    if (p === 'monitor') refreshProcs()
    if (p === 'startup' && !startupLoaded) loadStartup()
    if (p === 'clean' && !cleanLoaded) scanClean()
    requestAnimationFrame(() => charts.forEach(c => { c.resize(); c.draw() }))
  }
  document.querySelectorAll('#nav a').forEach(a => a.onclick = () => go(a.dataset.page))
  document.addEventListener('click', e => { const g = e.target.closest('[data-go]'); if (g) go(g.dataset.go) })

  // ------------------------------------------------------------ state
  const S = { hw: null, tweaks: null, startup: null, clean: null, cpuSamples: [], ping: [], admin: false }
  const charts = []
  const mk = (id, o) => { const c = new LineChart($(id), o); charts.push(c); return c }
  const sparkCpu = mk('sCpu', { grid: false }), sparkGpu = mk('sGpu', { grid: false, color: '#b9a4ff' })
  const sparkRam = mk('sRam', { grid: false, color: '#6fd3c1' }), sparkPing = mk('sPing', { grid: false, max: 'auto', color: '#f0c36b' })
  const gCpu = mk('gCpu', { unit: ' %' }), gGpu = mk('gGpu', { unit: ' %', color: '#b9a4ff' })
  const gRam = mk('gRam', { unit: ' %', color: '#6fd3c1' }), gNet = mk('gNet', { max: 'auto', color: '#8ea2ff', second: '#b9a4ff' })
  gNet.fmt = v => fmtRate(v)
  const gLat = mk('gLat', { max: 'auto', unit: ' ms', color: '#f0c36b' }); gLat.markGaps = true; gLat.length = 90

  // ------------------------------------------------------------ live usage
  async function liveTick() {
    let d = null
    try { d = await api.live() } catch {}
    if (d) {
      const ramPct = d.memUsed && d.memTotal ? d.memUsed / d.memTotal * 100 : null
      $('tCpu').textContent = pct(d.cpu); sparkCpu.push(d.cpu)
      $('tGpu').textContent = d.gpu == null ? 'n. v.' : pct(d.gpu); sparkGpu.push(d.gpu)
      $('tRam').textContent = pct(ramPct); sparkRam.push(ramPct)
      $('cCpu').textContent = pct(d.cpu) + (d.cpuTemp ? ` · ${Math.round(d.cpuTemp)} °C` : ''); gCpu.push(d.cpu)
      $('cGpu').textContent = d.gpu == null ? 'n. v.' : pct(d.gpu) + (d.gpuTemp ? ` · ${Math.round(d.gpuTemp)} °C` : ''); gGpu.push(d.gpu)
      $('cGpuFoot').textContent = d.vramTotal ? `Grafikspeicher ${(d.vramUsed / 1024).toFixed(1)} von ${(d.vramTotal / 1024).toFixed(1)} GB` : (d.gpu == null ? 'Auslastung liefert Windows nur für NVIDIA-Karten direkt.' : '')
      $('cRam').textContent = pct(ramPct); gRam.push(ramPct)
      $('cRamFoot').textContent = d.memTotal ? `${fmtBytes(d.memUsed)} von ${fmtBytes(d.memTotal)} belegt` : ''
      $('cNet').textContent = fmtRate((d.rx || 0) + (d.tx || 0)); gNet.push(d.rx, d.tx)
      $('cNetFoot').textContent = `↓ ${fmtRate(d.rx)}   ↑ ${fmtRate(d.tx)}`
      const cores = $('cores')
      if (cores.children.length !== d.cores.length) cores.innerHTML = d.cores.map(() => '<i></i>').join('')
      d.cores.forEach((v, i) => { cores.children[i].style.height = Math.max(4, v) + '%' })
      const mini = $('miniCpu'); mini.querySelector('b').textContent = pct(d.cpu)
      mini.querySelector('i').className = d.cpu > 85 ? 'bad' : d.cpu > 60 ? 'warn' : 'good'
      if (S.cpuSamples.length < 20) { S.cpuSamples.push(d.cpu); if (S.cpuSamples.length === 20) render() }
    }
    setTimeout(liveTick, 1000)
  }

  async function pingTick() {
    const ms = await api.ping('1.1.1.1', 443).catch(() => null)
    S.ping.push(ms); if (S.ping.length > 30) S.ping.shift()
    $('tPing').textContent = ms == null ? 'Timeout' : Math.round(ms) + ' ms'; sparkPing.push(ms)
    const mini = $('miniPing'); mini.querySelector('b').textContent = ms == null ? '–' : Math.round(ms) + ' ms'
    mini.querySelector('i').className = ms == null ? 'bad' : ms > 80 ? 'warn' : 'good'
    setTimeout(pingTick, 2000)
  }

  // ------------------------------------------------------------ hardware
  const driverUrl = name => /nvidia/i.test(name) ? 'https://www.nvidia.com/de-de/drivers/'
    : /amd|radeon/i.test(name) ? 'https://www.amd.com/de/support/download/drivers.html'
    : /intel/i.test(name) ? 'https://www.intel.de/content/www/de/de/support/detect.html' : null
  const daysSince = iso => iso ? Math.floor((Date.now() - new Date(iso).getTime()) / 864e5) : null

  async function loadHardware() {
    $('hw').innerHTML = '<div class="empty wide">Wird eingelesen …</div>'
    try { S.hw = await api.hardware() } catch (e) { S.hw = null; $('hw').innerHTML = `<div class="empty wide">Fehler: ${esc(e.message)}</div>` ; return }
    renderHardware(); render()
  }

  function gpus() {
    const h = S.hw; if (!h || !h.graphics) return []
    return h.graphics.controllers.filter(c => c.model && !/basic|microsoft|remote|virtual|parsec/i.test(c.model)).map(c => {
      const drv = (h.drivers || []).find(d => d.name && (d.name.includes(c.model) || c.model.includes(d.name))) || {}
      return { model: c.model, vendor: c.vendor, vram: c.vram, driver: drv.version || c.driverVersion, date: drv.date }
    })
  }
  function sysDisk() {
    const h = S.hw; if (!h) return null
    const fsC = (h.fsSize || []).find(f => /^C:/i.test(f.mount)) || (h.fsSize || []).find(f => f.mount === '/') || (h.fsSize || [])[0]
    return fsC || null
  }

  function renderHardware() {
    const h = S.hw; if (!h) return
    const cpu = h.cpu || {}, mem = h.mem || {}, layout = (h.layout || []).filter(m => m.size), os = h.osInfo || {}
    const cards = []
    const check = (sev, text) => `<div class="check"><i class="sev ${sev}"></i><span>${text}</span></div>`
    cards.push(`<div class="shell"><div class="core"><h3>Prozessor</h3><div class="name">${esc((cpu.manufacturer || '') + ' ' + (cpu.brand || ''))}</div>
      <dl class="kv"><dt>Kerne / Threads</dt><dd>${cpu.physicalCores || '–'} / ${cpu.cores || '–'}</dd><dt>Takt</dt><dd>${cpu.speed || '–'} GHz${cpu.speedMax ? `, bis ${cpu.speedMax} GHz` : ''}</dd><dt>Sockel</dt><dd>${esc(cpu.socket || '–')}</dd></dl>
      ${cpu.physicalCores && cpu.physicalCores < 4 ? check('warn', 'Weniger als 4 Kerne: Browser und Discord beim Spielen schließen hilft spürbar.') : ''}</div></div>`)
    const gl = gpus()
    cards.push(`<div class="shell"><div class="core"><h3>Grafikkarte</h3>${gl.map(g => {
      const age = daysSince(g.date)
      const url = driverUrl(g.model)
      return `<div class="name">${esc(g.model)}</div><dl class="kv"><dt>Grafikspeicher</dt><dd>${g.vram ? (g.vram / 1024).toFixed(g.vram >= 1024 ? 0 : 1) + ' GB' : '–'}</dd><dt>Treiber</dt><dd>${esc(g.driver || '–')}${g.date ? ` vom ${new Date(g.date).toLocaleDateString('de-DE')}` : ''}</dd></dl>
        ${age != null && age > 180 && url ? check('warn', `Treiber ist ${Math.round(age / 30)} Monate alt. <a href="#" data-url="${url}">Neuen Treiber holen</a>, oft mehr FPS als jede Einstellung.`) : age != null ? check('good', 'Treiber ist aktuell genug.') : ''}`
    }).join('') || '<div class="empty">Keine Grafikkarte erkannt</div>'}</div></div>`)
    const dual = layout.length >= 2
    cards.push(`<div class="shell"><div class="core"><h3>Arbeitsspeicher</h3><div class="name">${fmtBytes(mem.total)}</div>
      <dl class="kv"><dt>Module</dt><dd>${layout.length ? layout.map(m => fmtBytes(m.size)).join(' + ') : '–'}</dd><dt>Takt</dt><dd>${layout[0] && layout[0].clockSpeed ? layout[0].clockSpeed + ' MHz' : '–'}</dd><dt>Typ</dt><dd>${esc(layout[0] && layout[0].type || '–')}</dd></dl>
      ${mem.total && mem.total < 7.5 * GB ? check('bad', 'Unter 8 GB: Minecraft mit Mods und ein Browser daneben wird eng.') : ''}
      ${layout.length === 1 ? check('warn', 'Nur ein Modul (Single-Channel): Ein zweites gleiches Modul verdoppelt die Speicher-Bandbreite und bringt vor allem mit Onboard-Grafik viele FPS.') : dual ? check('good', 'Zwei oder mehr Module: Dual-Channel möglich.') : ''}</div></div>`)
    const displays = (h.graphics && h.graphics.displays) || []
    const drv = (h.drivers || [])[0] || {}
    cards.push(`<div class="shell"><div class="core"><h3>Bildschirm</h3>${displays.map(d => `<div class="name">${esc(d.model || 'Bildschirm')}${d.main ? ' <span class="tag">Haupt</span>' : ''}</div>
      <dl class="kv"><dt>Auflösung</dt><dd>${d.currentResX || d.resolutionX || '–'} × ${d.currentResY || d.resolutionY || '–'}</dd><dt>Bildrate</dt><dd>${d.currentRefreshRate || drv.refresh || '–'} Hz</dd></dl>`).join('') || '<div class="empty">–</div>'}
      ${refreshIssue() ? check('bad', `Dein Bildschirm läuft mit ${refreshIssue().now} Hz, kann aber ${refreshIssue().max} Hz. <a href="#" data-url="ms-settings:display-advanced">Bildwiederholrate einstellen</a>`) : ''}</div></div>`)
    const disks = (h.fsSize || []).filter(f => f.size > 4 * GB)
    const phys = h.disks || []
    const sys = sysDisk()
    const sysPhys = phys.find(p => p.type) || {}
    cards.push(`<div class="shell wide"><div class="core"><h3>Laufwerke</h3>${disks.map(f => {
      const used = f.used / f.size * 100, free = f.size - f.used
      return `<div class="disk"><div class="dl"><b>${esc(f.mount)} ${esc(f.fs || '')}</b><span>${fmtBytes(free)} frei von ${fmtBytes(f.size)}</span></div><div class="bar"><i class="${free < 15 * GB ? 'bad' : free < 30 * GB ? 'warn' : ''}" style="width:${used.toFixed(1)}%"></i></div></div>`
    }).join('')}
      <dl class="kv" style="margin-top:14px">${phys.map(p => `<dt>${esc(p.type || 'Laufwerk')}</dt><dd>${esc(p.name || p.vendor || '')} · ${fmtBytes(p.size)}${p.interfaceType ? ' · ' + esc(p.interfaceType) : ''}</dd>`).join('')}</dl>
      ${phys.length && phys.every(p => /HD/i.test(p.type || '')) ? check('bad', 'Nur Festplatten (HDD): Eine SSD für Windows und Spiele verkürzt Lade- und Startzeiten um ein Vielfaches.') : ''}
      ${sys && sys.size - sys.used < 15 * GB ? check('bad', 'Weniger als 15 GB frei auf dem Systemlaufwerk. <a href="#" data-go="clean">Aufräumen</a>') : ''}</div></div>`)
    const b = h.board || {}, sysI = h.system || {}
    cards.push(`<div class="shell wide"><div class="core"><h3>System</h3><dl class="kv"><dt>Windows</dt><dd>${esc(os.distro || '–')} ${esc(os.release || '')} (Build ${esc(os.build || '–')})</dd><dt>Mainboard</dt><dd>${esc((b.manufacturer || '') + ' ' + (b.model || ''))}</dd><dt>PC</dt><dd>${esc((sysI.manufacturer || '') + ' ' + (sysI.model || ''))}</dd>${h.battery && h.battery.hasBattery ? `<dt>Akku</dt><dd>${h.battery.percent} %${h.battery.isCharging ? ', lädt' : ''}</dd>` : ''}</dl></div></div>`)
    $('hw').innerHTML = cards.join('')
  }
  document.addEventListener('click', e => {
    const a = e.target.closest('[data-url]'); if (!a) return
    e.preventDefault(); api.open(a.dataset.url)
  })
  $('hwReload').onclick = e => busy(e.currentTarget, loadHardware)

  function refreshIssue() {
    const h = S.hw; if (!h) return null
    const d = (h.drivers || []).find(x => x.maxRefresh && x.refresh)
    if (d && d.maxRefresh > d.refresh && d.refresh <= 75 && d.maxRefresh >= 100) return { now: d.refresh, max: d.maxRefresh }
    return null
  }

  // ------------------------------------------------------------ tweaks
  async function loadTweaks() {
    const r = await api.engine('state')
    if (!r || !r.ok) { $('tweaks').innerHTML = `<div class="empty">${esc(r && r.error || 'Nicht verfügbar')}</div>`; S.tweaks = null; render(); return }
    S.tweaks = r.tweaks; S.admin = r.admin
    renderTweaks(); render()
  }
  function renderTweaks() {
    const cats = [...new Set(S.tweaks.map(t => t.cat))]
    $('tweaks').innerHTML = cats.map(c => `<div class="cat">${esc(c)}</div><div class="tw">${S.tweaks.filter(t => t.cat === c).map(t => `
      <div class="twc shell"><div class="core"><div class="body"><b>${esc(t.name)}</b><p>${esc(t.desc)}</p><div class="tags">
        ${t.applied ? '<span class="tag done">aktiv</span>' : ''}<span class="tag ${t.impact === 'hoch' ? 'hoch' : ''}">Wirkung ${esc(t.impact)}</span>${t.admin ? '<span class="tag">Admin</span>' : ''}${t.reboot ? '<span class="tag">Neustart</span>' : ''}${t.optional ? '<span class="tag">optional</span>' : ''}
      </div></div><div class="switch ${t.applied ? 'on' : ''}" data-tweak="${t.id}" role="switch" aria-checked="${t.applied}" tabindex="0"></div></div></div>`).join('')}</div>`).join('')
  }
  let needsReboot = false
  document.addEventListener('click', async e => {
    const sw = e.target.closest('[data-tweak]'); if (!sw) return
    const t = S.tweaks.find(x => x.id === sw.dataset.tweak)
    sw.classList.add('busy'); sw.classList.toggle('on', !t.applied)
    const r = await api.engine(t.applied ? 'revert' : 'apply', { ids: [t.id], elevate: t.admin })
    const failed = !r || !r.ok || (r.results || []).some(x => !x.ok)
    if (failed) toast('Nicht geändert: ' + (r && (r.error || (r.results || []).map(x => x.error).filter(Boolean).join(', '))), true)
    else { toast(t.applied ? `„${t.name}“ zurückgesetzt.` : `„${t.name}“ ist aktiv.`); if (t.reboot) needsReboot = true }
    await loadTweaks()
    $('rebootBanner').hidden = !needsReboot
  })

  async function applyRecommended(btn) {
    if (!S.tweaks) return
    const todo = S.tweaks.filter(t => !t.optional && !t.applied)
    if (!todo.length) { toast('Alles Empfohlene ist schon aktiv.'); return }
    await busy(btn, async () => {
      const r = await api.engine('apply', { ids: todo.map(t => t.id), elevate: todo.some(t => t.admin) })
      if (!r || !r.ok) { toast('Nicht geändert: ' + (r && r.error), true); return }
      const bad = (r.results || []).filter(x => !x.ok)
      toast(bad.length ? `${todo.length - bad.length} von ${todo.length} aktiv. Fehler: ${bad.map(x => x.error).join(', ')}` : `${todo.length} Optimierungen aktiv.`, bad.length > 0)
      if (todo.some(t => t.reboot)) needsReboot = true
      await loadTweaks()
      $('rebootBanner').hidden = !needsReboot
    })
  }
  $('optimizeAll').onclick = e => applyRecommended(e.currentTarget)
  $('applyAll').onclick = e => applyRecommended(e.currentTarget)
  $('undoAll').onclick = async e => {
    if (!await confirmBox('Alles rückgängig machen?', 'Alle Einstellungen, die der Lunar Optimizer geändert hat, bekommen ihren alten Wert zurück, auch der Energiesparplan.', 'Rückgängig')) return
    await busy(e.currentTarget, async () => {
      const r = await api.engine('undo', { elevate: true })
      toast(r && r.ok ? `${r.restored} Einstellungen zurückgesetzt.` : 'Nicht geändert: ' + (r && r.error), !(r && r.ok))
      await loadTweaks()
    })
  }
  $('rebootNow').onclick = async () => { if (await confirmBox('Jetzt neu starten?', 'Speichere offene Arbeit. Der PC startet in 5 Sekunden neu.', 'Neu starten')) api.reboot() }

  // ------------------------------------------------------------ startup
  let startupLoaded = false
  async function loadStartup() {
    startupLoaded = true
    const r = await api.engine('startup-list')
    if (!r || !r.ok) { $('startup').innerHTML = `<div class="empty">${esc(r && r.error || 'Nicht verfügbar')}</div>`; return }
    S.startup = r.items || []
    renderStartup(); render()
  }
  function renderStartup() {
    const items = S.startup
    if (!items.length) { $('startup').innerHTML = '<div class="empty">Nichts im Autostart. Sehr gut.</div>'; return }
    $('startup').innerHTML = items.map((it, i) => `<div class="li"><div><b>${esc(it.name)}</b><small>${esc(it.command)}</small></div><span class="src">${esc(it.sourceLabel)}</span><div class="switch ${it.enabled ? 'on' : ''}" data-start="${i}" role="switch" tabindex="0"></div></div>`).join('')
  }
  document.addEventListener('click', async e => {
    const sw = e.target.closest('[data-start]'); if (!sw) return
    const it = S.startup[+sw.dataset.start]
    sw.classList.add('busy'); sw.classList.toggle('on', !it.enabled)
    const r = await api.engine('startup-set', { arg: { source: it.source, name: it.name, enabled: !it.enabled }, elevate: it.admin })
    if (!r || !r.ok) toast('Nicht geändert: ' + (r && r.error), true)
    else toast(`${it.name} ${it.enabled ? 'startet nicht mehr mit Windows' : 'startet wieder mit Windows'}.`)
    await loadStartup()
  })

  // ------------------------------------------------------------ cleanup
  let cleanLoaded = false
  const cleanPick = new Set()
  async function scanClean() {
    cleanLoaded = true
    $('clean').innerHTML = '<div class="empty">Wird gescannt …</div>'; $('cleanRun').disabled = true
    const r = await api.engine('clean-scan')
    if (!r || !r.ok) { $('clean').innerHTML = `<div class="empty">${esc(r && r.error || 'Nicht verfügbar')}</div>`; return }
    S.clean = r
    cleanPick.clear(); r.targets.forEach(t => { if (t.id !== 'recycle' && t.bytes > 0) cleanPick.add(t.id) })
    renderClean(); render()
  }
  function renderClean() {
    const r = S.clean
    $('clean').innerHTML = r.targets.map(t => `<div class="li"><span class="box ${cleanPick.has(t.id) ? 'on' : ''}" data-clean="${t.id}"></span><div><b>${esc(t.name)}</b><small>${esc(t.desc)}${t.admin ? ' · braucht Admin' : ''}</small></div><span class="size">${fmtBytes(t.bytes)}</span></div>`).join('')
    const total = r.targets.filter(t => cleanPick.has(t.id)).reduce((a, t) => a + t.bytes, 0)
    $('cleanTotal').innerHTML = `Ausgewählt: <b>${fmtBytes(total)}</b> · frei auf C: <b>${fmtBytes(r.freeBytes)}</b>`
    $('cleanRun').disabled = cleanPick.size === 0
  }
  document.addEventListener('click', e => {
    const b = e.target.closest('[data-clean]'); if (!b) return
    cleanPick.has(b.dataset.clean) ? cleanPick.delete(b.dataset.clean) : cleanPick.add(b.dataset.clean)
    renderClean()
  })
  $('cleanScan').onclick = e => busy(e.currentTarget, scanClean)
  $('cleanRun').onclick = e => busy(e.currentTarget, async () => {
    const ids = [...cleanPick]
    const r = await api.engine('clean-run', { ids, elevate: S.clean.targets.some(t => cleanPick.has(t.id) && t.admin) })
    toast(r && r.ok ? `${fmtBytes(r.freedBytes)} frei geworden.` : 'Nicht aufgeräumt: ' + (r && r.error), !(r && r.ok))
    await scanClean()
  })

  // ------------------------------------------------------------ processes
  let procTimer = 0
  async function refreshProcs() {
    clearTimeout(procTimer)
    if (page !== 'monitor') return
    const list = await api.processes().catch(() => [])
    const total = S.hw && S.hw.mem ? S.hw.mem.total : 0
    $('procCount').textContent = list.length
    $('procs').innerHTML = list.slice(0, 18).map(p => `<tr><td><span class="n">${esc(p.name.replace(/\.exe$/i, ''))}</span>${p.pids.length > 1 ? `<small>${p.pids.length}×</small>` : ''}</td>
      <td class="r"><span class="heat" style="color:${p.cpu > 25 ? 'var(--bad)' : p.cpu > 8 ? 'var(--warn)' : 'inherit'}">${p.cpu.toFixed(1)} %</span></td>
      <td class="r">${fmtBytes(p.mem)}${total ? `<small>${(p.mem / total * 100).toFixed(0)} %</small>` : ''}</td>
      <td class="r"><button class="kill" data-kill="${p.pids.join(',')}" data-name="${esc(p.name)}" ${p.protected ? 'disabled title="Gehört zu Windows"' : ''}>Beenden</button></td></tr>`).join('')
    procTimer = setTimeout(refreshProcs, 3000)
  }
  document.addEventListener('click', async e => {
    const k = e.target.closest('[data-kill]'); if (!k) return
    if (!await confirmBox(`${k.dataset.name} beenden?`, 'Nicht gespeicherte Daten in diesem Programm gehen verloren.', 'Beenden')) return
    const r = await api.kill(k.dataset.kill.split(',').map(Number))
    toast(r.killed ? `${k.dataset.name} beendet.` : `${k.dataset.name} ließ sich nicht beenden (vielleicht braucht es Admin-Rechte).`, !r.killed)
    refreshProcs()
  })
  $('openTaskmgr').onclick = () => api.taskManager()

  // ------------------------------------------------------------ latency
  const targets = [
    { name: 'Cloudflare', host: '1.1.1.1', port: 443 },
    { name: 'Google', host: '8.8.8.8', port: 53 },
    { name: 'Hypixel', host: 'mc.hypixel.net', port: 25565 },
    { name: 'Discord', host: 'discord.com', port: 443 },
    { name: 'Steam', host: 'store.steampowered.com', port: 443 },
  ]
  let target = 0
  const hist = new Map()
  function renderTargets() {
    $('targets').innerHTML = targets.map((t, i) => { const h = hist.get(i) || []; const last = h[h.length - 1]; return `<button class="${i === target ? 'on' : ''}" data-target="${i}">${esc(t.name)} <b>${last == null ? '–' : Math.round(last) + ' ms'}</b></button>` }).join('') +
      '<input id="customTarget" placeholder="Eigener Server, z. B. play.server.de" spellcheck="false">'
    $('customTarget').onkeydown = e => {
      if (e.key !== 'Enter') return
      const [host, port] = e.target.value.trim().split(':')
      if (!host) return
      targets.push({ name: host, host, port: Number(port) || 25565 }); target = targets.length - 1; gLat.data = []; renderTargets(); renderLat()
    }
  }
  document.addEventListener('click', e => { const b = e.target.closest('[data-target]'); if (!b) return; target = +b.dataset.target; gLat.data = (hist.get(target) || []).slice(); gLat.draw(); renderTargets(); renderLat() })
  function renderLat() {
    const h = hist.get(target) || [], ok = h.filter(v => v != null)
    $('latTarget').textContent = `${targets[target].name} (${targets[target].host}:${targets[target].port})`
    const last = h[h.length - 1]
    $('latNow').textContent = last == null ? (h.length ? 'Timeout' : '–') : Math.round(last) + ' ms'
    if (!ok.length) { ['lAvg', 'lJit', 'lMm', 'lLoss'].forEach(id => $(id).textContent = '–'); $('lVerdict').textContent = ''; return }
    const avg = ok.reduce((a, b) => a + b, 0) / ok.length
    let jit = 0; for (let i = 1; i < ok.length; i++) jit += Math.abs(ok[i] - ok[i - 1]); jit = ok.length > 1 ? jit / (ok.length - 1) : 0
    const loss = (h.length - ok.length) / h.length * 100
    $('lAvg').textContent = Math.round(avg) + ' ms'; $('lJit').textContent = jit.toFixed(1) + ' ms'
    $('lMm').textContent = `${Math.round(Math.min(...ok))} / ${Math.round(Math.max(...ok))} ms`; $('lLoss').textContent = loss.toFixed(0) + ' %'
    const word = loss > 3 ? ['schlecht', 'Pakete gehen verloren. WLAN? Ein LAN-Kabel hilft meist sofort.']
      : jit > 15 ? ['wackelig', 'Der Ping schwankt stark. Downloads, Streams oder WLAN-Störungen im Hintergrund?']
      : avg < 30 ? ['super', 'Kaum spürbare Verzögerung.'] : avg < 60 ? ['gut', 'Für die meisten Spiele völlig in Ordnung.']
      : avg < 100 ? ['okay', 'Im PvP merkbar. Näherer Server oder LAN-Kabel helfen.'] : ['hoch', 'Deutlich spürbar. Server weit weg oder Leitung ausgelastet.']
    $('lVerdict').innerHTML = `Verbindung <b>${word[0]}</b>. ${word[1]}`
  }
  async function latTick() {
    const i = target, t = targets[i]
    const ms = await api.ping(t.host, t.port).catch(() => null)
    const h = hist.get(i) || []; h.push(ms); if (h.length > 90) h.shift(); hist.set(i, h)
    if (i === target) { gLat.push(ms); renderLat() }
    if (page === 'latency') renderTargets()
    setTimeout(latTick, page === 'latency' ? 1000 : 5000)
  }
  renderTargets()

  // Reaction test: the whole chain (mouse, PC, screen and you).
  const react = $('react'); let rState = 'idle', rTimer = 0, rStart = 0; const rTimes = []
  react.onmousedown = () => {
    if (rState === 'idle' || rState === 'done') {
      rState = 'wait'; react.className = 'core wait'; react.innerHTML = '<b>Warten …</b><span>Erst klicken, wenn es grün wird.</span>'
      rTimer = setTimeout(() => { rState = 'go'; rStart = performance.now(); react.className = 'core go'; react.innerHTML = '<b>JETZT!</b>' }, 1500 + Math.random() * 2500)
    } else if (rState === 'wait') {
      clearTimeout(rTimer); rState = 'done'; react.className = 'core'; react.innerHTML = '<b>Zu früh!</b><span>Nochmal klicken zum Neustarten.</span>'
    } else if (rState === 'go') {
      const ms = performance.now() - rStart; rTimes.push(ms); if (rTimes.length > 5) rTimes.shift()
      const avg = rTimes.reduce((a, b) => a + b, 0) / rTimes.length
      rState = 'done'; react.className = 'core'
      react.innerHTML = `<b>${Math.round(ms)} ms</b><span>Schnitt der letzten ${rTimes.length}: ${Math.round(avg)} ms · Bestwert: ${Math.round(Math.min(...rTimes))} ms. ${ms < 200 ? 'Sehr schnell!' : ms < 260 ? 'Gut, Durchschnitt liegt bei etwa 250 ms.' : 'Nochmal?'} Klicken zum Wiederholen.</span>`
    }
  }

  // ------------------------------------------------------------ score
  function issues() {
    const list = []
    if (S.tweaks) {
      const miss = S.tweaks.filter(t => !t.optional && !t.applied)
      if (miss.length) list.push({ sev: miss.length > 3 ? 'bad' : 'warn', pts: Math.min(30, miss.length * 4), title: `${miss.length} Windows-Einstellungen bremsen beim Spielen`, detail: miss.map(t => t.name).join(', '), act: ['Optimieren', 'tweaks'] })
    }
    for (const g of gpus()) {
      const age = daysSince(g.date), url = driverUrl(g.model)
      if (age != null && age > 180 && url) list.push({ sev: age > 365 ? 'bad' : 'warn', pts: age > 365 ? 12 : 7, title: `Grafiktreiber ist ${Math.round(age / 30)} Monate alt`, detail: `${g.model}: Neue Treiber bringen oft mehr FPS als jede Einstellung.`, url: [url, 'Treiber holen'] })
    }
    const ri = refreshIssue()
    if (ri) list.push({ sev: 'bad', pts: 12, title: `Bildschirm läuft nur mit ${ri.now} Hz`, detail: `Er kann ${ri.max} Hz. Das ist der größte Unterschied, den du sehen kannst.`, url: ['ms-settings:display-advanced', 'Einstellen'] })
    const sys = sysDisk()
    if (sys) { const free = sys.size - sys.used; if (free < 15 * GB) list.push({ sev: 'bad', pts: 10, title: `Nur ${fmtBytes(free)} frei auf dem Systemlaufwerk`, detail: 'Unter 15 GB wird Windows langsam und Updates scheitern.', act: ['Aufräumen', 'clean'] }); else if (free < 30 * GB) list.push({ sev: 'warn', pts: 4, title: `${fmtBytes(free)} frei auf dem Systemlaufwerk`, detail: 'Etwas Platz schaffen schadet nicht.', act: ['Aufräumen', 'clean'] }) }
    if (S.hw && S.hw.mem) { const t = S.hw.mem.total; if (t < 7.5 * GB) list.push({ sev: 'bad', pts: 10, title: `Nur ${fmtBytes(t)} Arbeitsspeicher`, detail: 'Für Spiele sind 16 GB heute Standard. Programme im Hintergrund schließen hilft kurzfristig.', act: ['Live-Monitor', 'monitor'] }) }
    if (S.hw && (S.hw.layout || []).filter(m => m.size).length === 1) list.push({ sev: 'info', pts: 4, title: 'Arbeitsspeicher im Single-Channel', detail: 'Ein zweites gleiches Modul verdoppelt die Bandbreite.', act: ['Details', 'hardware'] })
    if (S.hw && (S.hw.disks || []).length && S.hw.disks.every(p => /HD/i.test(p.type || ''))) list.push({ sev: 'bad', pts: 10, title: 'Keine SSD gefunden', detail: 'Mit einer SSD starten Windows und Spiele um ein Vielfaches schneller.', act: ['Details', 'hardware'] })
    if (S.startup) { const on = S.startup.filter(i => i.enabled).length; if (on > 8) list.push({ sev: 'warn', pts: 5, title: `${on} Programme starten mit Windows`, detail: 'Jedes davon kostet beim Hochfahren Zeit und läuft danach im Hintergrund.', act: ['Autostart', 'startup'] }) }
    if (S.clean) { const junk = S.clean.targets.filter(t => t.id !== 'recycle').reduce((a, t) => a + t.bytes, 0); if (junk > 2 * GB) list.push({ sev: 'info', pts: 3, title: `${fmtBytes(junk)} Datenmüll`, detail: 'Temporäre Dateien und alte Update-Downloads.', act: ['Aufräumen', 'clean'] }) }
    if (S.cpuSamples.length >= 20) { const avg = S.cpuSamples.reduce((a, b) => a + b, 0) / S.cpuSamples.length; if (avg > 35) list.push({ sev: 'warn', pts: 6, title: `CPU im Leerlauf zu ${Math.round(avg)} % ausgelastet`, detail: 'Irgendetwas läuft im Hintergrund mit. Der Live-Monitor zeigt was.', act: ['Ansehen', 'monitor'] }) }
    const pings = S.ping.filter(v => v != null)
    if (S.ping.length >= 10 && (S.ping.length - pings.length) / S.ping.length > .1) list.push({ sev: 'bad', pts: 8, title: 'Verbindung verliert Pakete', detail: 'Mehr als 10 % der Ping-Anfragen kommen nicht an. WLAN? Ein LAN-Kabel hilft meist sofort.', act: ['Latenz', 'latency'] })
    return list.sort((a, b) => b.pts - a.pts)
  }

  let shownScore = 0
  function render() {
    const ready = S.hw && (S.tweaks || !api.isWindows)
    const list = issues()
    const score = Math.max(5, 100 - list.reduce((a, i) => a + i.pts, 0))
    $('issueCount').textContent = list.length || ''
    $('issues').innerHTML = list.length ? list.map((i, n) => `<div class="issue" style="animation-delay:${n * 60}ms"><i class="sev ${i.sev}"></i><div><b>${esc(i.title)}</b><small>${esc(i.detail)}</small></div><span class="pts">−${i.pts}</span>
      ${i.act ? `<button class="btn ghost sm" data-go="${i.act[1]}"><span>${esc(i.act[0])}</span></button>` : ''}${i.url ? `<button class="btn ghost sm" data-url="${i.url[0]}"><span>${esc(i.url[1])}</span></button>` : ''}</div>`).join('')
      : `<div class="issue"><i class="sev good"></i><div><b>Nichts gefunden</b><small>Dein PC ist fürs Gaming bestens eingestellt.</small></div></div>`
    if (!ready) return
    const arc = $('scoreArc'), col = score >= 85 ? '#3dbe7a' : score >= 65 ? accent() : score >= 45 ? '#f0a13a' : '#e5484d'
    arc.style.strokeDashoffset = 540.35 * (1 - score / 100); arc.style.stroke = col; arc.style.filter = `drop-shadow(0 0 10px ${col})`
    const from = shownScore, t0 = performance.now()
    ;(function count(now) { const p = Math.min(1, (now - t0) / 1400), e = 1 - Math.pow(1 - p, 4); $('scoreNum').textContent = Math.round(from + (score - from) * e); if (p < 1) requestAnimationFrame(count) })(t0)
    shownScore = score
    $('scoreWord').textContent = score >= 85 ? 'stark' : score >= 65 ? 'gut' : score >= 45 ? 'ausbaufähig' : 'schwach'
    $('heroTitle').textContent = score >= 90 ? 'Dein PC ist bestens eingestellt.' : score >= 70 ? 'Gut, aber da geht noch was.' : 'Da bleibt Leistung liegen.'
    $('heroSub').textContent = list.length ? `${list.length} ${list.length === 1 ? 'Punkt' : 'Punkte'} gefunden. „Jetzt optimieren“ stellt Windows mit einem Klick auf Spielen ein, der Rest steht unten.` : 'Alles geprüft: Hardware, Treiber, Windows, Speicher und Verbindung.'
    const cpu = S.hw.cpu || {}, g = gpus()[0]
    $('tCpuName').textContent = (cpu.brand || '').replace(/\(R\)|\(TM\)|CPU|Processor/gi, '').trim()
    $('tGpuName').textContent = g ? g.model : ''
    $('tRamName').textContent = S.hw.mem ? fmtBytes(S.hw.mem.total) + ' gesamt' : ''
  }

  // ------------------------------------------------------------ start
  api.info().then(i => {
    $('ver').textContent = `Version ${i.version}`
    $('adminNote').textContent = i.platform !== 'win32' ? 'Optimierungen nur unter Windows.' : i.admin ? 'Läuft als Administrator.' : 'Änderungen fragen einmal nach Admin-Rechten.'
    api.isWindows = i.platform === 'win32'
  })
  liveTick(); pingTick(); latTick()
  loadHardware(); loadTweaks()
  setTimeout(() => { if (!startupLoaded) loadStartup(); if (!cleanLoaded) scanClean() }, 2500)
  window.__lunarReady = true
})()
