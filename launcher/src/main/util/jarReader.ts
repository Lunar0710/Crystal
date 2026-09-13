import fs from 'fs'
import zlib from 'zlib'

const EOCD_SIGNATURE = 0x06054b50
const CENTRAL_SIGNATURE = 0x02014b50
const LOCAL_SIGNATURE = 0x04034b50

/** Maximum size of the trailing comment a ZIP file may carry. */
const MAX_COMMENT = 0xffff

export class NotAZipError extends Error {}

/**
 * Reads single entries out of a .jar without unpacking it.
 *
 * Mods are ZIPs, and all we ever need from one is a small metadata file, so
 * extracting the whole archive to a temp folder just to read fabric.mod.json
 * would be wasteful — and would leave files behind if validation then fails.
 */
export class JarReader {
  private buffer: Buffer
  /** entry name -> central directory offset */
  private entries = new Map<string, number>()

  constructor(filePath: string) {
    this.buffer = fs.readFileSync(filePath)
    this.readCentralDirectory()
  }

  private readCentralDirectory(): void {
    const eocd = this.findEndOfCentralDirectory()
    if (eocd === -1) {
      throw new NotAZipError('Die Datei ist kein gültiges ZIP/JAR-Archiv.')
    }

    const total = this.buffer.readUInt16LE(eocd + 10)
    let offset = this.buffer.readUInt32LE(eocd + 16)

    for (let i = 0; i < total; i++) {
      if (this.buffer.readUInt32LE(offset) !== CENTRAL_SIGNATURE) {
        throw new NotAZipError('Das Archiv-Verzeichnis ist beschädigt.')
      }

      const nameLength = this.buffer.readUInt16LE(offset + 28)
      const extraLength = this.buffer.readUInt16LE(offset + 30)
      const commentLength = this.buffer.readUInt16LE(offset + 32)
      const name = this.buffer.toString('utf8', offset + 46, offset + 46 + nameLength)

      this.entries.set(name, offset)
      offset += 46 + nameLength + extraLength + commentLength
    }
  }

  private findEndOfCentralDirectory(): number {
    const start = Math.max(0, this.buffer.length - MAX_COMMENT - 22)
    for (let i = this.buffer.length - 22; i >= start; i--) {
      if (this.buffer.readUInt32LE(i) === EOCD_SIGNATURE) return i
    }
    return -1
  }

  has(entryName: string): boolean {
    return this.entries.has(entryName)
  }

  names(): string[] {
    return [...this.entries.keys()]
  }

  /** Returns the decompressed entry, or null if the archive doesn't contain it. */
  read(entryName: string): Buffer | null {
    const central = this.entries.get(entryName)
    if (central === undefined) return null

    const method = this.buffer.readUInt16LE(central + 10)
    const compressedSize = this.buffer.readUInt32LE(central + 20)
    const localOffset = this.buffer.readUInt32LE(central + 42)

    if (this.buffer.readUInt32LE(localOffset) !== LOCAL_SIGNATURE) {
      throw new NotAZipError(`Eintrag "${entryName}" ist beschädigt.`)
    }

    const nameLength = this.buffer.readUInt16LE(localOffset + 26)
    const extraLength = this.buffer.readUInt16LE(localOffset + 28)
    const dataStart = localOffset + 30 + nameLength + extraLength
    const data = this.buffer.subarray(dataStart, dataStart + compressedSize)

    if (method === 0) return Buffer.from(data)
    if (method === 8) return zlib.inflateRawSync(data)

    throw new NotAZipError(`Eintrag "${entryName}" nutzt ein nicht unterstütztes Kompressionsverfahren (${method}).`)
  }

  readText(entryName: string): string | null {
    return this.read(entryName)?.toString('utf8') ?? null
  }

  readJson<T>(entryName: string): T | null {
    const text = this.readText(entryName)
    if (text === null) return null
    // Some mods ship fabric.mod.json with a UTF-8 BOM.
    return JSON.parse(text.replace(/^﻿/, '')) as T
  }
}
