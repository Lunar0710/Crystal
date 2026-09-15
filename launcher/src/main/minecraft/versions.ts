/**
 * What differs between Minecraft versions, from 1.8.9 to the 26.x releases:
 * which Java they need, which Fabric flavour exists for them, and how to
 * compare version ids at all (26.1 comes after 1.21.11).
 */

/** Oldest and newest release Crystal supports. */
export const OLDEST_VERSION = '1.8.9'
export const NEWEST_VERSION = '26.2'

/** Numeric compare of "1.8.9", "1.21.11", "26.1"; negative if a < b. */
export function compareVersions(a: string, b: string): number {
  const pa = a.split('.').map(n => parseInt(n, 10) || 0)
  const pb = b.split('.').map(n => parseInt(n, 10) || 0)
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const diff = (pa[i] || 0) - (pb[i] || 0)
    if (diff !== 0) return diff
  }
  return 0
}

/** Releases inside the supported range (ids from Mojang's manifest, releases only). */
export function isSupportedVersion(id: string): boolean {
  return /^\d+(\.\d+)+$/.test(id) && compareVersions(id, OLDEST_VERSION) >= 0 && compareVersions(id, NEWEST_VERSION) <= 0
}

/**
 * Java major version Minecraft needs. Mojang's version file says so too
 * (javaVersion.majorVersion); this is the same table, usable before that file
 * is downloaded.
 */
export function requiredJavaMajor(version: string): number {
  if (compareVersions(version, '26.1') >= 0) return 25
  if (compareVersions(version, '1.20.5') >= 0) return 21
  if (compareVersions(version, '1.18') >= 0) return 17
  if (compareVersions(version, '1.17') >= 0) return 16
  return 8
}

/**
 * Java version to download for a Minecraft version. Java 16 is end-of-life,
 * 17 runs 1.17 fine. Versions on Java 8 need exactly 8: their old libraries
 * (LWJGL 2, LaunchWrapper) break on newer Java.
 */
export function downloadJavaMajor(version: string): number {
  const required = requiredJavaMajor(version)
  return required === 16 ? 17 : required
}

/** Whether an installed Java of `major` can run this Minecraft version. */
export function javaFits(version: string, major: number): boolean {
  const required = requiredJavaMajor(version)
  return required === 8 ? major === 8 : major >= required
}

/**
 * Apple Silicon: Minecraft before 1.19 ships no arm64 natives, so it has to
 * run on an x64 Java through Rosetta.
 */
export function needsX64JavaOnArmMac(version: string): boolean {
  return process.platform === 'darwin' && process.arch === 'arm64' && compareVersions(version, '1.19') < 0
}

/** Old lines Legacy Fabric supports (only the last patch of each). */
const LEGACY_FABRIC_VERSIONS = new Set(['1.8.9', '1.9.4', '1.10.2', '1.11.2', '1.12.2', '1.13.2'])

/**
 * Fabric metadata server for this version: official Fabric from 1.14,
 * Legacy Fabric for the old lines it covers, none otherwise (e.g. 1.9.1).
 */
export function fabricMetaFor(version: string): string | null {
  if (compareVersions(version, '1.14') >= 0) return 'https://meta.fabricmc.net'
  if (LEGACY_FABRIC_VERSIONS.has(version)) return 'https://meta.legacyfabric.net'
  return null
}
