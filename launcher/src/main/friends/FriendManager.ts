import Store from 'electron-store'
import { randomUUID } from 'crypto'

declare function fetch(url: string): Promise<{ ok: boolean; status: number; json(): Promise<any> }>

export interface Friend {
  id: string
  username: string
  uuid: string | null
  addedAt: number
}

export interface AddFriendResult {
  success: boolean
  friend?: Friend
  error?: string
}

/**
 * Local friends list. There's no Nexora account server yet, so this stores
 * who you added and verifies the name against Mojang — it deliberately does
 * not claim to show online status, which would need a real backend.
 */
export class FriendManager {
  private store: Store

  constructor(store: Store) {
    this.store = store
  }

  list(): Friend[] {
    return (this.store.get('friends', []) as Friend[])
  }

  async add(username: string): Promise<AddFriendResult> {
    const name = username.trim()
    if (!name) return { success: false, error: 'Bitte einen Namen eingeben' }

    if (this.list().some(f => f.username.toLowerCase() === name.toLowerCase())) {
      return { success: false, error: `${name} ist bereits in deiner Liste` }
    }

    // Verify the account exists so typos don't end up in the list.
    let uuid: string | null = null
    try {
      const res = await fetch(`https://api.mojang.com/users/profiles/minecraft/${encodeURIComponent(name)}`)
      if (res.ok) {
        const data = await res.json()
        uuid = data.id ?? null
      } else if (res.status === 404) {
        return { success: false, error: `Kein Minecraft-Account namens "${name}" gefunden` }
      }
    } catch {
      // Offline: still allow adding, just without a verified UUID.
    }

    const friend: Friend = { id: randomUUID(), username: name, uuid, addedAt: Date.now() }
    this.store.set('friends', [...this.list(), friend])
    return { success: true, friend }
  }

  remove(id: string): boolean {
    this.store.set('friends', this.list().filter(f => f.id !== id))
    return true
  }
}
