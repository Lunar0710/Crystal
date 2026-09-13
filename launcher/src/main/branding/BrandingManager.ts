import { app, BrowserWindow, nativeImage } from 'electron'
import Store from 'electron-store'
import path from 'path'

export interface IconVariant {
  id: string
  name: string
  file: string // filename inside src/renderer/assets/icons
}

// Kept in sync with the logo concepts (Facet Hex, Crystal Monogram, Shard Mark,
// Twin Shard Duel) — see the "Icon" section in Settings for the rank-gated picker.
export const ICON_VARIANTS: IconVariant[] = [
  { id: 'facet-hex',        name: 'Facet Hex (Default)', file: 'crystal.ico' },
  { id: 'crystal-monogram', name: 'Crystal Monogram',    file: 'crystal-monogram.ico' },
  { id: 'shard-mark',       name: 'Shard Mark',          file: 'shard-mark.ico' },
  { id: 'twin-shard-duel',  name: 'Twin Shard Duel',     file: 'twin-shard-duel.ico' },
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
    return path.join(iconsDir(), variant.file)
  }

  apply(win: BrowserWindow | null, id?: string) {
    const iconPath = this.getIconPath(id)
    const image = nativeImage.createFromPath(iconPath)
    if (!image.isEmpty()) win?.setIcon(image)
  }

  setCurrent(win: BrowserWindow | null, id: string): boolean {
    if (!ICON_VARIANTS.some(v => v.id === id)) return false
    this.store.set('branding.iconId', id)
    this.apply(win, id)
    return true
  }
}
