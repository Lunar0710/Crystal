import fs from 'fs'
import path from 'path'
import os from 'os'

export interface LogFile {
  name: string
  sizeBytes: number
  modifiedAt: number
}

const LAUNCH_LOG = 'crystal-launch.log'

/** Resolves an instance's folder; imported instances live outside ~/.crystal. */
let resolveGameDir: (instanceId: string) => string | null = () => null

export function setInstanceDirResolver(resolver: (instanceId: string) => string | null): void {
  resolveGameDir = resolver
}

function instanceDir(instanceId: string): string {
  return resolveGameDir(instanceId) ?? path.join(os.homedir(), '.crystal', 'instances', instanceId)
}

export { instanceDir }

export class LogManager {
  listLogs(instanceId: string): LogFile[] {
    const root = instanceDir(instanceId)
    const logs = this.listDir(path.join(root, 'logs'), ['.log', '.log.gz'])

    // The launcher writes its own launch log to the instance root — it's the
    // most useful file when the game dies on startup, so surface it first.
    const launchLog = path.join(root, LAUNCH_LOG)
    if (fs.existsSync(launchLog)) {
      const stat = fs.statSync(launchLog)
      logs.unshift({ name: LAUNCH_LOG, sizeBytes: stat.size, modifiedAt: stat.mtimeMs })
    }

    return logs
  }

  listCrashReports(instanceId: string): LogFile[] {
    const dir = path.join(instanceDir(instanceId), 'crash-reports')
    return this.listDir(dir, ['.txt'])
  }

  private listDir(dir: string, extensions: string[]): LogFile[] {
    if (!fs.existsSync(dir)) return []
    return fs.readdirSync(dir)
      .filter(f => extensions.some(ext => f.endsWith(ext)))
      .map(f => {
        const stat = fs.statSync(path.join(dir, f))
        return { name: f, sizeBytes: stat.size, modifiedAt: stat.mtimeMs }
      })
      .sort((a, b) => b.modifiedAt - a.modifiedAt)
  }

  readLog(instanceId: string, fileName: string, maxChars = 50_000): string {
    const root = instanceDir(instanceId)
    const filePath = fileName === LAUNCH_LOG
      ? path.join(root, LAUNCH_LOG)
      : path.join(root, 'logs', fileName)
    return this.readTail(filePath, maxChars)
  }

  readCrashReport(instanceId: string, fileName: string, maxChars = 50_000): string {
    const filePath = path.join(instanceDir(instanceId), 'crash-reports', fileName)
    return this.readTail(filePath, maxChars)
  }

  private readTail(filePath: string, maxChars: number): string {
    if (!fs.existsSync(filePath)) return ''
    const content = fs.readFileSync(filePath, 'utf-8')
    return content.length > maxChars ? content.slice(-maxChars) : content
  }
}
