import { app, BrowserWindow, nativeImage } from 'electron'
import Store from 'electron-store'
import path from 'path'
import fs from 'fs'
import { crystalPath } from '../paths'

export interface IconVariant {
  id: string
  name: string
  file: string // filename inside src/renderer/assets/icons
}

// The Nexora mark and three reductions of it (see theme/logoVariants.tsx).
// The ids stay as they are: they are saved in the settings.
export const ICON_VARIANTS: IconVariant[] = [
  { id: 'facet-hex',        name: 'Nexora (Standard)',   file: 'crystal.ico' },
  { id: 'crystal-monogram', name: 'Monogramm N',         file: 'crystal-monogram.ico' },
  { id: 'shard-mark',       name: 'Funke',               file: 'shard-mark.ico' },
  { id: 'twin-shard-duel',  name: 'Ring geschlossen',    file: 'twin-shard-duel.ico' },
]

const DEFAULT_ICON_ID = 'facet-hex'

function iconsDir(): string {
  // In dev this resolves relative to the compiled dist/main output;
  // both dev and packaged builds ship the icons alongside the renderer assets.
  return app.isPackaged
    ? path.join(process.resourcesPath, 'icons')
    : path.join(app.getAppPath(), 'src', 'renderer', 'assets', 'icons')
}

export class BrandingManager {
  private store: Store

  constructor(store: Store) {
    this.store = store
  }

  list(): IconVariant[] {
    return ICON_VARIANTS
  }

  getCurrentId(): string {
    return (this.store.get('branding.iconId') as string) || DEFAULT_ICON_ID
  }

  getIconPath(id?: string): string {
    const variant = ICON_VARIANTS.find(v => v.id === (id || this.getCurrentId())) || ICON_VARIANTS[0]
    // Only Windows reads .ico; macOS and Linux need the PNG twin shipped next to it.
    const file = process.platform === 'win32' ? variant.file : variant.file.replace(/\.ico$/, '.png')
    return path.join(iconsDir(), file)
  }

  apply(win: BrowserWindow | null, id?: string) {
    const custom = this.customLogoImage()
    const image = custom ?? nativeImage.createFromPath(this.getIconPath(id))
    if (!image.isEmpty()) win?.setIcon(image)
  }

  // ---- Own logo: an uploaded picture shown in the title bar and as the window icon.

  private customLogoFile(): string {
    return crystalPath('branding', 'logo.png')
  }

  private customLogoImage(): Electron.NativeImage | null {
    const file = this.customLogoFile()
    if (!fs.existsSync(file)) return null
    const image = nativeImage.createFromPath(file)
    return image.isEmpty() ? null : image
  }

  /** The uploaded logo as a data URL, or null when the default wordmark is used. */
  getCustomLogo(): string | null {
    return this.customLogoImage()?.toDataURL() ?? null
  }

  /** Stores the picture at `source`, scaled down to at most 256px tall. False when it isn't a readable image. */
  setCustomLogo(win: BrowserWindow | null, source: string): boolean {
    let image = nativeImage.createFromPath(source)
    if (image.isEmpty()) return false
    const { height } = image.getSize()
    if (height > 256) image = image.resize({ height: 256, quality: 'best' })
    const file = this.customLogoFile()
    fs.mkdirSync(path.dirname(file), { recursive: true })
    fs.writeFileSync(file, image.toPNG())
    this.apply(win)
    return true
  }

  clearCustomLogo(win: BrowserWindow | null): void {
    fs.rmSync(this.customLogoFile(), { force: true })
    this.apply(win)
  }

  setCurrent(win: BrowserWindow | null, id: string): boolean {
    if (!ICON_VARIANTS.some(v => v.id === id)) return false
    this.store.set('branding.iconId', id)
    this.apply(win, id)
    return true
  }
}
