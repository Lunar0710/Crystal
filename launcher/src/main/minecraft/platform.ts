import fs from 'fs'
import os from 'os'
import path from 'path'
import { spawnSync } from 'child_process'

/**
 * Everything about launching Minecraft that differs between Windows, macOS
 * and Linux, in one place. The launch code used to hard-wire Windows
 * ("natives-windows", java.exe, a Windows-only JDK download), so on any other
 * system it silently skipped the native libraries and the game could not start.
 */

export type MojangOs = 'windows' | 'osx' | 'linux'

/** The OS name Mojang's version manifests use in library and argument rules. */
export function mojangOs(): MojangOs {
  if (process.platform === 'win32') return 'windows'
  if (process.platform === 'darwin') return 'osx'
  return 'linux'
}

/**
 * Suffix of LWJGL's per-OS natives artifacts ("org.lwjgl:lwjgl:3.3.3:natives-macos").
 * The arm64/x86 variants share the prefix; they unpack into separate
 * windows/x64, macos/arm64, ... folders, so extracting all of them is safe and
 * LWJGL picks the right one for the running JVM.
 */
export function nativesSuffix(): string {
  if (process.platform === 'win32') return 'natives-windows'
  if (process.platform === 'darwin') return 'natives-macos'
  return 'natives-linux'
}

/** Arch as written in old manifest rules ("x86") for the few that still use it. */
function mojangArch(): string {
  return process.arch === 'ia32' ? 'x86' : process.arch === 'arm64' ? 'arm64' : 'x86_64'
}

export interface OsRule {
  action: 'allow' | 'disallow'
  os?: { name?: string; arch?: string }
  features?: Record<string, boolean>
}

/** Mojang's rule semantics: last matching rule wins, no rules means allowed. */
export function rulesAllow(rules?: OsRule[]): boolean {
  if (!rules || rules.length === 0) return true
  let allowed = false
  for (const rule of rules) {
    // Feature-gated rules (demo mode, custom resolution, quick play) never apply to a normal launch.
    if (rule.features) continue
    const nameMatches = !rule.os?.name || rule.os.name === mojangOs()
    const archMatches = !rule.os?.arch || rule.os.arch === mojangArch()
    if (nameMatches && archMatches) allowed = rule.action === 'allow'
  }
  return allowed
}

/**
 * JVM flags the OS itself requires. macOS only lets GLFW open a window from
 * the process's first thread; without -XstartOnFirstThread Minecraft exits
 * right after start.
 */
export function platformJvmArgs(): string[] {
  return process.platform === 'darwin' ? ['-XstartOnFirstThread'] : []
}

export const JAVA_BINARY = process.platform === 'win32' ? 'java.exe' : 'java'

/**
 * Path of the java binary inside an unpacked JDK. Adoptium's macOS archives
 * nest the JDK in a bundle (jdk-21/Contents/Home/bin/java).
 */
export function javaInJdk(jdkDir: string): string {
  const macBundle = path.join(jdkDir, 'Contents', 'Home', 'bin', JAVA_BINARY)
  if (process.platform === 'darwin' && fs.existsSync(macBundle)) return macBundle
  return path.join(jdkDir, 'bin', JAVA_BINARY)
}

/** Adoptium download for this machine, or null on an OS/arch it doesn't build for. */
export function adoptiumJdk(major: number): { url: string; archive: 'zip' | 'tar.gz' } | null {
  const osName = process.platform === 'win32' ? 'windows' : process.platform === 'darwin' ? 'mac' : process.platform === 'linux' ? 'linux' : null
  const arch = process.arch === 'x64' ? 'x64' : process.arch === 'arm64' ? 'aarch64' : null
  if (!osName || !arch) return null
  return {
    url: `https://api.adoptium.net/v3/binary/latest/${major}/ga/${osName}/${arch}/jdk/hotspot/normal/eclipse?project=jdk`,
    archive: osName === 'windows' ? 'zip' : 'tar.gz',
  }
}

/**
 * Unpacks a .tar.gz with the system tar. It ships with macOS and every Linux
 * distribution (and Windows 10 1803+), and unlike a JS unzip it keeps the
 * executable bit, without which the downloaded java can't run.
 */
export function extractTarGz(archive: string, dest: string): void {
  const result = spawnSync('tar', ['-xzf', archive, '-C', dest], { encoding: 'utf8' })
  if (result.status !== 0) throw new Error(`tar fehlgeschlagen: ${result.stderr || result.error?.message || result.status}`)
}

/** Places Java is commonly installed on this OS, newest-first. Globs aren't supported, so directories are listed. */
export function javaCandidates(): string[] {
  const list: string[] = []
  const home = process.env.JAVA_HOME
  if (home) list.push(javaInJdk(home))

  const scan = (dir: string, toJava: (entry: string) => string) => {
    try {
      for (const entry of fs.readdirSync(dir).sort().reverse()) list.push(toJava(path.join(dir, entry)))
    } catch { /* directory doesn't exist on this machine */ }
  }

  if (process.platform === 'win32') {
    for (const vendor of ['Eclipse Adoptium', 'Java', 'Microsoft', 'Zulu', 'BellSoft']) {
      scan(path.join(process.env.ProgramFiles || 'C:\\Program Files', vendor), jdk => path.join(jdk, 'bin', JAVA_BINARY))
    }
    scan('C:\\Program Files (x86)\\Java', jdk => path.join(jdk, 'bin', JAVA_BINARY))
  } else if (process.platform === 'darwin') {
    // java_home knows about every registered JDK, including Homebrew casks.
    const javaHome = spawnSync('/usr/libexec/java_home', ['-v', '21+'], { encoding: 'utf8' })
    if (javaHome.status === 0 && javaHome.stdout.trim()) list.push(path.join(javaHome.stdout.trim(), 'bin', JAVA_BINARY))
    scan('/Library/Java/JavaVirtualMachines', jdk => path.join(jdk, 'Contents', 'Home', 'bin', JAVA_BINARY))
    scan(path.join(os.homedir(), 'Library', 'Java', 'JavaVirtualMachines'), jdk => path.join(jdk, 'Contents', 'Home', 'bin', JAVA_BINARY))
    list.push('/opt/homebrew/opt/openjdk@21/bin/java', '/usr/local/opt/openjdk@21/bin/java')
  } else {
    scan('/usr/lib/jvm', jdk => path.join(jdk, 'bin', JAVA_BINARY))
    scan(path.join(os.homedir(), '.sdkman', 'candidates', 'java'), jdk => path.join(jdk, 'bin', JAVA_BINARY))
  }

  list.push('java') // resolved via PATH
  return list
}
