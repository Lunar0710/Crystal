// Renders the Windows installer artwork (NSIS wants 24-bit BMPs) from HTML so
// it matches the launcher's look. Run with: npx electron scripts/make-installer-art.cjs
// Writes build/installerSidebar.bmp (164x314) and build/installerHeader.bmp (150x57).
const { app, BrowserWindow } = require('electron')
const fs = require('fs')
const path = require('path')

const LOGO = `<svg width="W" height="W" viewBox="0 0 100 100">
  <defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#5b8af5"/><stop offset="1" stop-color="#7c6af5"/></linearGradient></defs>
  <polygon points="50,6 88,28 88,72 50,94 12,72 12,28" fill="none" stroke="url(#g)" stroke-width="4"/>
  <polygon points="50,6 88,28 50,50" fill="url(#g)" opacity=".85"/>
  <polygon points="50,50 88,72 50,94" fill="url(#g)" opacity=".45"/>
  <polygon points="50,50 50,94 12,72" fill="url(#g)" opacity=".65"/>
  <polygon points="12,28 50,6 50,50" fill="url(#g)" opacity=".3"/>
</svg>`

const BASE = `* { margin: 0; padding: 0; box-sizing: border-box; }
  body { width: 100vw; height: 100vh; overflow: hidden; font-family: 'Segoe UI', sans-serif; color: #e4e8f0; }`

const art = [
  {
    file: 'installerSidebar.bmp', width: 164, height: 314,
    html: `<style>${BASE}
      body { background: radial-gradient(120% 70% at 30% 18%, #232a4a 0%, #0d0f14 62%); display: flex; flex-direction: column; align-items: center; padding-top: 58px; }
      h1 { margin-top: 16px; font-size: 17px; font-weight: 600; letter-spacing: .02em; }
      h1 span { color: #a35bf5; }
      p { margin-top: 4px; font-size: 10.5px; color: #7d8599; letter-spacing: .08em; text-transform: uppercase; }
      .facets { position: absolute; bottom: 0; left: 0; right: 0; height: 90px;
        background: linear-gradient(160deg, transparent 40%, rgba(91,138,245,.10) 40% 55%, transparent 55%),
                    linear-gradient(20deg, transparent 50%, rgba(124,106,245,.12) 50% 68%, transparent 68%); }
    </style>${LOGO.replaceAll('"W"', '"64"')}<h1><span>C</span>rystal</h1><p>Launcher</p><div class="facets"></div>`,
  },
  {
    file: 'installerHeader.bmp', width: 150, height: 57,
    html: `<style>${BASE}
      body { background: linear-gradient(90deg, #0d0f14 0%, #171b2c 100%); display: flex; align-items: center; justify-content: flex-end; gap: 8px; padding-right: 12px; }
      b { font-size: 13px; font-weight: 600; } b span { color: #a35bf5; }
    </style><b><span>C</span>rystal</b>${LOGO.replaceAll('"W"', '"30"')}`,
  },
]

/** BGRA pixels (top-down) to a 24-bit bottom-up BMP. */
function toBmp(bgra, width, height) {
  const rowSize = Math.ceil((width * 3) / 4) * 4
  const size = 54 + rowSize * height
  const buf = Buffer.alloc(size)
  buf.write('BM', 0)
  buf.writeUInt32LE(size, 2)
  buf.writeUInt32LE(54, 10)
  buf.writeUInt32LE(40, 14)
  buf.writeInt32LE(width, 18)
  buf.writeInt32LE(height, 22)
  buf.writeUInt16LE(1, 26)
  buf.writeUInt16LE(24, 28)
  buf.writeUInt32LE(rowSize * height, 34)
  for (let y = 0; y < height; y++) {
    const row = 54 + (height - 1 - y) * rowSize
    for (let x = 0; x < width; x++) {
      const src = (y * width + x) * 4
      buf[row + x * 3] = bgra[src]
      buf[row + x * 3 + 1] = bgra[src + 1]
      buf[row + x * 3 + 2] = bgra[src + 2]
    }
  }
  return buf
}

// Closing one render window must not end the app before the next image.
app.on('window-all-closed', () => {})

app.whenReady().then(async () => {
  const outDir = path.join(__dirname, '..', 'build')
  for (const item of art) {
    const win = new BrowserWindow({
      width: item.width, height: item.height, show: false, useContentSize: true, frame: false,
      webPreferences: { offscreen: true, zoomFactor: 1 },
    })
    const tmp = path.join(require('os').tmpdir(), 'crystal-art-' + item.file + '.html')
    fs.writeFileSync(tmp, '<!doctype html><meta charset="utf-8">' + item.html)
    await win.loadFile(tmp)
    await new Promise(r => setTimeout(r, 400))
    const image = (await win.webContents.capturePage()).resize({ width: item.width, height: item.height, quality: 'best' })
    const { width, height } = image.getSize()
    fs.writeFileSync(path.join(outDir, item.file), toBmp(image.toBitmap(), width, height))
    fs.writeFileSync(path.join(outDir, item.file.replace('.bmp', '.preview.png')), image.toPNG())
    console.log('wrote', item.file, width, height)
    win.destroy()
  }
  app.quit()
})
