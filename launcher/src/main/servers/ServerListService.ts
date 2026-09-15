import net from 'net'
import dns from 'dns'
import { randomUUID } from 'crypto'
import Store from 'electron-store'

export interface FavoriteServer {
  id: string
  name: string
  address: string
}

export interface ServerStatus {
  online: boolean
  motd?: string
  playersOnline?: number
  playersMax?: number
  version?: string
  favicon?: string
  pingMs?: number
  error?: string
}

const DEFAULTS: FavoriteServer[] = [
  { id: 'hypixel', name: 'Hypixel', address: 'mc.hypixel.net' },
  { id: 'gomme', name: 'GommeHD', address: 'gommehd.net' },
  { id: 'cubecraft', name: 'CubeCraft', address: 'play.cubecraft.net' },
]

/** host or host:port, letters/digits/dots/dashes only; nothing that could smuggle extra game arguments. */
const ADDRESS = /^[A-Za-z0-9.-]{1,253}(:\d{1,5})?$/

export function isValidServerAddress(address: unknown): address is string {
  if (typeof address !== 'string' || !ADDRESS.test(address)) return false
  const port = address.includes(':') ? Number(address.split(':')[1]) : 25565
  return port > 0 && port < 65536
}

function varInt(value: number): Buffer {
  const bytes: number[] = []
  let v = value >>> 0
  do {
    let b = v & 0x7f
    v >>>= 7
    if (v !== 0) b |= 0x80
    bytes.push(b)
  } while (v !== 0)
  return Buffer.from(bytes)
}

function packet(id: number, payload: Buffer): Buffer {
  const body = Buffer.concat([varInt(id), payload])
  return Buffer.concat([varInt(body.length), body])
}

/** Reads a VarInt at offset; returns [value, bytesRead] or null if the buffer ends first. */
function readVarInt(buf: Buffer, offset: number): [number, number] | null {
  let value = 0
  for (let i = 0; i < 5; i++) {
    if (offset + i >= buf.length) return null
    const b = buf[offset + i]
    value |= (b & 0x7f) << (7 * i)
    if ((b & 0x80) === 0) return [value, i + 1]
  }
  throw new Error('VarInt too long')
}

/** Flattens a chat component (string, {text, extra}) to plain text without § colour codes. */
function plainText(component: unknown): string {
  if (typeof component === 'string') return component.replace(/§./g, '')
  if (!component || typeof component !== 'object') return ''
  const c = component as { text?: unknown; extra?: unknown[]; translate?: unknown }
  let out = typeof c.text === 'string' ? c.text : typeof c.translate === 'string' ? c.translate : ''
  if (Array.isArray(c.extra)) out += c.extra.map(plainText).join('')
  return out.replace(/§./g, '')
}

/**
 * Favourite servers and the Minecraft "server list ping": the same status
 * request the multiplayer menu sends, so it shows the real MOTD, player count,
 * version and logo without joining.
 */
export class ServerListService {
  constructor(private store: Store) {}

  list(): FavoriteServer[] {
    const saved = this.store.get('servers.favorites') as FavoriteServer[] | undefined
    return Array.isArray(saved) ? saved : DEFAULTS
  }

  add(name: string, address: string): FavoriteServer | null {
    const cleanAddress = String(address ?? '').trim().toLowerCase()
    if (!isValidServerAddress(cleanAddress)) return null
    const cleanName = String(name ?? '').trim().slice(0, 40) || cleanAddress
    const server = { id: randomUUID(), name: cleanName, address: cleanAddress }
    this.store.set('servers.favorites', [...this.list(), server])
    return server
  }

  remove(id: string) {
    this.store.set('servers.favorites', this.list().filter(s => s.id !== id))
  }

  async ping(address: string): Promise<ServerStatus> {
    if (!isValidServerAddress(address)) return { online: false, error: 'Ungültige Adresse' }
    let [host, portText] = address.split(':')
    let port = portText ? Number(portText) : 25565

    // Most big servers publish their real host/port as an SRV record.
    if (!portText) {
      try {
        const records = await dns.promises.resolveSrv(`_minecraft._tcp.${host}`)
        if (records.length > 0) { host = records[0].name; port = records[0].port }
      } catch { /* no SRV record: use the name as given */ }
    }

    return new Promise<ServerStatus>(resolve => {
      const socket = net.createConnection({ host, port })
      let buffer = Buffer.alloc(0)
      let startedAt = 0
      let done = false
      const finish = (status: ServerStatus) => {
        if (done) return
        done = true
        socket.destroy()
        resolve(status)
      }
      socket.setTimeout(5000, () => finish({ online: false, error: 'Zeitüberschreitung' }))
      socket.on('error', err => finish({ online: false, error: (err as NodeJS.ErrnoException).code === 'ENOTFOUND' ? 'Server nicht gefunden' : 'Nicht erreichbar' }))

      socket.on('connect', () => {
        const hostBytes = Buffer.from(host, 'utf8')
        const portBytes = Buffer.alloc(2)
        portBytes.writeUInt16BE(port)
        const handshake = packet(0x00, Buffer.concat([varInt(774), varInt(hostBytes.length), hostBytes, portBytes, varInt(1)]))
        startedAt = Date.now()
        socket.write(Buffer.concat([handshake, packet(0x00, Buffer.alloc(0))]))
      })

      socket.on('data', chunk => {
        buffer = Buffer.concat([buffer, chunk])
        if (buffer.length > 256 * 1024) return finish({ online: false, error: 'Antwort zu groß' })
        try {
          const len = readVarInt(buffer, 0)
          if (!len || buffer.length < len[1] + len[0]) return // wait for the full packet
          let offset = len[1]
          const id = readVarInt(buffer, offset)
          if (!id || id[0] !== 0x00) return finish({ online: false, error: 'Unerwartete Antwort' })
          offset += id[1]
          const strLen = readVarInt(buffer, offset)
          if (!strLen) return
          offset += strLen[1]
          const json = JSON.parse(buffer.subarray(offset, offset + strLen[0]).toString('utf8'))
          const favicon = typeof json.favicon === 'string' && json.favicon.startsWith('data:image/png;base64,') ? json.favicon : undefined
          finish({
            online: true,
            motd: plainText(json.description).trim(),
            playersOnline: Number(json.players?.online) || 0,
            playersMax: Number(json.players?.max) || 0,
            version: typeof json.version?.name === 'string' ? json.version.name.replace(/§./g, '') : undefined,
            favicon,
            pingMs: Date.now() - startedAt,
          })
        } catch {
          finish({ online: false, error: 'Unerwartete Antwort' })
        }
      })
    })
  }
}
