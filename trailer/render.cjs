// Renders trailer.html to an MP4: every frame is drawn with renderAt(t) and
// screenshotted, then ffmpeg joins the frames with the soundtrack from music.py.
//
//   python3 music.py music.wav
//   node render.cjs [out.mp4] [fps]
//
// Needs Playwright (Chromium) and ffmpeg (FFMPEG env var, or ffmpeg on PATH).
const path = require('path')
const { spawn, execSync } = require('child_process')

let chromium
try { ({ chromium } = require('playwright')) } catch { ({ chromium } = require(path.join(execSync('npm root -g').toString().trim(), 'playwright'))) }

const out = process.argv[2] || 'nexora-trailer.mp4'
const fps = Number(process.argv[3]) || 60
const ffmpeg = process.env.FFMPEG || 'ffmpeg'
const music = path.join(process.cwd(), 'music.wav')

;(async () => {
  const browser = await chromium.launch({ executablePath: process.env.CHROMIUM || undefined, args: ['--no-sandbox', '--force-color-profile=srgb'] })
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 1 })
  page.on('pageerror', e => { console.error('page error:', e.message); process.exitCode = 1 })
  await page.goto('file://' + path.join(__dirname, 'trailer.html'))
  await page.evaluate(() => window.ready)
  const duration = await page.evaluate(() => window.DURATION)
  const frames = Math.round(duration * fps)

  const enc = spawn(ffmpeg, [
    '-y', '-loglevel', 'error',
    '-f', 'image2pipe', '-framerate', String(fps), '-c:v', 'mjpeg', '-i', '-',
    '-i', music,
    '-c:v', 'libx264', '-preset', 'slow', '-crf', '15', '-pix_fmt', 'yuv420p', '-profile:v', 'high',
    '-color_primaries', 'bt709', '-color_trc', 'bt709', '-colorspace', 'bt709',
    '-c:a', 'aac', '-b:a', '256k', '-shortest', '-movflags', '+faststart', out,
  ], { stdio: ['pipe', 'inherit', 'inherit'] })

  const started = Date.now()
  for (let i = 0; i < frames; i++) {
    await page.evaluate(t => window.renderAt(t), i / fps)
    const shot = await page.screenshot({ type: 'jpeg', quality: 97 })
    if (!enc.stdin.write(shot)) await new Promise(r => enc.stdin.once('drain', r))
    if (i % fps === 0) process.stdout.write(`\r${(i / fps).toFixed(0)}/${duration} s  (${((Date.now() - started) / 1000).toFixed(0)} s)`)
  }
  enc.stdin.end()
  await new Promise((resolve, reject) => enc.on('close', code => code === 0 ? resolve() : reject(new Error('ffmpeg exit ' + code))))
  await browser.close()
  console.log(`\nwrote ${out}: ${frames} frames at ${fps} fps`)
})()
