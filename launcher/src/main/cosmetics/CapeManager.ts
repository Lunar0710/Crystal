import { dialog, BrowserWindow } from 'electron'
import Store from 'electron-store'
import fs from 'fs'
import path from 'path'
import os from 'os'
import { randomUUID } from 'crypto'
import { crystalPath } from '../paths'

export interface CustomCape {
  id: string
  name: string
  fileName: string
  addedAt: number
}

function capesDir(): string {
  return crystalPath('cosmetics', 'capes')
}

const MIME: Record<string, string> = { '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg' }

export class CapeManager {
  private store: Store

  constructor(store: Store) {
    this.store = store
  }

  listCustom(): CustomCape[] {
    return (this.store.get('cosmetics.customCapes', []) as CustomCape[])
  }

  async upload(): Promise<CustomCape | null> {
    const win = BrowserWindow.getFocusedWindow()
    const result = await dialog.showOpenDialog(win!, {
      title: 'Bild oder Cape-Textur auswählen',
      properties: ['openFile'],
      filters: [{ name: 'Bilder', extensions: ['png', 'jpg', 'jpeg'] }],
    })
    if (result.canceled || result.filePaths.length === 0) return null

    const src = result.filePaths[0]
    const dir = capesDir()
    fs.mkdirSync(dir, { recursive: true })

    const id = randomUUID()
    const ext = path.extname(src).toLowerCase()
    const fileName = `${id}${ext}`
    fs.copyFileSync(src, path.join(dir, fileName))

    const cape: CustomCape = {
      id,
      name: path.basename(src, path.extname(src)),
      fileName,
      addedAt: Date.now(),
    }

    const list = this.listCustom()
    list.push(cape)
    this.store.set('cosmetics.customCapes', list)
    return cape
  }

  /**
   * Replaces an uploaded cape's file with a converted PNG (a normal picture the
   * renderer turned into an HD cape texture).
   */
  replaceImage(id: string, pngDataUrl: string): boolean {
    const list = this.listCustom()
    const cape = list.find(c => c.id === id)
    const match = /^data:image\/png;base64,(.+)$/.exec(pngDataUrl || '')
    if (!cape || !match) return false
    const dir = capesDir()
    const oldPath = path.join(dir, cape.fileName)
    const fileName = `${id}.png`
    fs.writeFileSync(path.join(dir, fileName), Buffer.from(match[1], 'base64'))
    if (cape.fileName !== fileName && fs.existsSync(oldPath)) fs.unlinkSync(oldPath)
    cape.fileName = fileName
    this.store.set('cosmetics.customCapes', list)
    return true
  }

  remove(id: string) {
    const list = this.listCustom()
    const cape = list.find(c => c.id === id)
    if (cape) {
      const filePath = path.join(capesDir(), cape.fileName)
      if (fs.existsSync(filePath)) fs.unlinkSync(filePath)
    }
    this.store.set('cosmetics.customCapes', list.filter(c => c.id !== id))
  }

  // Returns the cape image as a data: URI so the renderer can display it via
  // <img src> without needing file:// access (blocked by CSP in the app).
  getCapeDataUrl(id: string): string | null {
    const cape = this.listCustom().find(c => c.id === id)
    if (!cape) return null

    const filePath = path.join(capesDir(), cape.fileName)
    if (!fs.existsSync(filePath)) return null

    const ext = path.extname(cape.fileName).toLowerCase()
    const mime = MIME[ext] || 'application/octet-stream'
    const base64 = fs.readFileSync(filePath).toString('base64')
    return `data:${mime};base64,${base64}`
  }

  getSelected(): string {
    return (this.store.get('cosmetics.selectedCape') as string) || 'builtin:solid-0'
  }

  setSelected(id: string) {
    this.store.set('cosmetics.selectedCape', id)
  }
}
