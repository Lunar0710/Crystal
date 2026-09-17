/**
 * How a version is shown to players. Crystal has no in-between releases: a fix
 * for 1.2 is "1.2 Hotfix", even though the updater needs it numbered 1.2.1
 * internally to offer it. "1.2.0" → "1.2", "1.2.1" → "1.2 Hotfix",
 * "1.2.2" → "1.2 Hotfix 2".
 */
export function displayVersion(version: string | null | undefined): string {
  if (!version) return ''
  const match = version.match(/^(\d+)\.(\d+)\.(\d+)$/)
  if (!match) return version
  const [, major, minor, patch] = match
  const base = `${major}.${minor}`
  const fix = Number(patch)
  if (fix === 0) return base
  return fix === 1 ? `${base} Hotfix` : `${base} Hotfix ${fix}`
}
