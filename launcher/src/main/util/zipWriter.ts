import fs from 'fs'
import zlib from 'zlib'

/**
 * Writes a plain ZIP file (deflate, no ZIP64), enough for a .mrpack. The
 * whole archive is built in memory, so it is meant for configs and a handful
 * of jars, not for worlds.
 */

const CRC_TABLE = (() => {
  const table = new Uint32Array(256)
  for (let n = 0; n < 256; n++) {
    let c = n
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
    table[n] = c >>> 0
  }
  return table
})()

function crc32(data: Buffer): number {
  let c = 0xffffffff
  for (let i = 0; i < data.length; i++) c = CRC_TABLE[(c ^ data[i]) & 0xff] ^ (c >>> 8)
  return (c ^ 0xffffffff) >>> 0
}

export class ZipWriter {
  private parts: Buffer[] = []
  private central: Buffer[] = []
  private offset = 0
  private count = 0

  /** Adds one file; the name uses forward slashes, e.g. "overrides/config/x.json". */
  add(name: string, data: Buffer): void {
    const nameBytes = Buffer.from(name, 'utf8')
    const packed = zlib.deflateRawSync(data)
    // Already-compressed files (jars, pngs) can grow when deflated again; those are stored.
    const stored = packed.length >= data.length
    const body = stored ? data : packed
    const crc = crc32(data)
    if (this.offset + body.length > 0xffffffff) throw new Error('Das Paket wird zu groß (über 4 GB).')

    const local = Buffer.alloc(30)
    local.writeUInt32LE(0x04034b50, 0)
    local.writeUInt16LE(20, 4)
    local.writeUInt16LE(0x0800, 6) // UTF-8 names
    local.writeUInt16LE(stored ? 0 : 8, 8)
    local.writeUInt32LE(crc, 14)
    local.writeUInt32LE(body.length, 18)
    local.writeUInt32LE(data.length, 22)
    local.writeUInt16LE(nameBytes.length, 26)

    const entry = Buffer.alloc(46)
    entry.writeUInt32LE(0x02014b50, 0)
    entry.writeUInt16LE(20, 4)
    entry.writeUInt16LE(20, 6)
    entry.writeUInt16LE(0x0800, 8)
    entry.writeUInt16LE(stored ? 0 : 8, 10)
    entry.writeUInt32LE(crc, 16)
    entry.writeUInt32LE(body.length, 20)
    entry.writeUInt32LE(data.length, 24)
    entry.writeUInt16LE(nameBytes.length, 28)
    entry.writeUInt32LE(this.offset, 42)

    this.parts.push(local, nameBytes, body)
    this.central.push(entry, nameBytes)
    this.offset += 30 + nameBytes.length + body.length
    this.count++
  }

  writeTo(filePath: string): void {
    if (this.count > 0xffff) throw new Error('Zu viele Dateien für ein Paket.')
    const dir = Buffer.concat(this.central)
    const end = Buffer.alloc(22)
    end.writeUInt32LE(0x06054b50, 0)
    end.writeUInt16LE(this.count, 8)
    end.writeUInt16LE(this.count, 10)
    end.writeUInt32LE(dir.length, 12)
    end.writeUInt32LE(this.offset, 16)
    fs.writeFileSync(filePath, Buffer.concat([...this.parts, dir, end]))
  }
}
