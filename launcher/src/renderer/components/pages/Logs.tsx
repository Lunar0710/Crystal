import React, { useState, useEffect } from 'react'
import {
  Terminal, AlertTriangle, FileText, Sparkles, RefreshCw,
  FolderOpen, Copy, Trash2, ChevronDown, Rocket,
} from 'lucide-react'
import { notify } from '../../store/notificationStore'

type Source = 'launcher' | 'client' | 'updater' | 'gameLogs' | 'crashes'

interface LogFile {
  name: string
  sizeBytes: number
  modifiedAt: number
}

interface Instance {
  id: string
  name: string
}

interface CrashAnalysis {
  success: boolean
  summary?: string
  likelyCause?: string
  suggestedFix?: string
  error?: string
}

const api = (window as any).crystal

// Launcher-side categories read one file each; the game-side ones list files
// from the selected instance.
const SOURCES: { id: Source; label: string; icon: typeof FileText; perInstance: boolean }[] = [
  { id: 'launcher',  label: 'Launcher',      icon: Terminal,      perInstance: false },
  { id: 'client',    label: 'Client-Start',  icon: Rocket,        perInstance: false },
  { id: 'updater',   label: 'Updater',       icon: RefreshCw,     perInstance: false },
  { id: 'gameLogs',  label: 'Spiel-Logs',    icon: FileText,      perInstance: true },
  { id: 'crashes',   label: 'Crash Reports', icon: AlertTriangle, perInstance: true },
]

function fmtSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

export function Logs() {
  const [source, setSource] = useState<Source>('launcher')
  const [instances, setInstances] = useState<Instance[]>([])
  const [instanceId, setInstanceId] = useState('')
  const [files, setFiles] = useState<LogFile[]>([])
  const [selected, setSelected] = useState<string | null>(null)
  const [content, setContent] = useState('')
  const [analysis, setAnalysis] = useState<CrashAnalysis | null>(null)
  const [analyzing, setAnalyzing] = useState(false)
  const [hasKey, setHasKey] = useState(false)

  const current = SOURCES.find(s => s.id === source)!

  useEffect(() => {
    api?.getInstances().then((list: Instance[]) => {
      const all = list || []
      setInstances(all)
      setInstanceId(id => (all.some(i => i.id === id) ? id : all[0]?.id ?? ''))
    })
    api?.hasClaudeApiKey().then(setHasKey)
  }, [])

  useEffect(() => { load() }, [source, instanceId])

  async function load() {
    setSelected(null)
    setAnalysis(null)

    if (!current.perInstance) {
      setFiles([])
      const text = await api?.readLauncherLog(source)
      setContent(text || '(noch keine Einträge)')
      return
    }

    if (!instanceId) {
      setFiles([])
      setContent('')
      return
    }

    const list: LogFile[] = source === 'crashes'
      ? await api?.listCrashes(instanceId)
      : await api?.listLogs(instanceId)

    setFiles(list || [])
    setContent('')
  }

  async function openFile(name: string) {
    setSelected(name)
    setAnalysis(null)
    const text = source === 'crashes'
      ? await api?.readCrash(instanceId, name)
      : await api?.readLog(instanceId, name)
    setContent(text || '')
  }

  async function copyLog() {
    if (!content) return
    await navigator.clipboard.writeText(content)
    notify({ type: 'success', message: 'Log in die Zwischenablage kopiert' })
  }

  async function openFolder() {
    if (current.perInstance) {
      if (!instanceId) return
      await api?.openInstanceLogFolder(instanceId, source === 'crashes' ? 'crash-reports' : 'logs')
    } else {
      await api?.openLogFolder()
    }
  }

  async function clearLog() {
    if (current.perInstance) {
      notify({ type: 'info', message: 'Spiel- und Crash-Logs schreibt Minecraft selbst — hier nur im Ordner löschbar.' })
      return
    }
    await api?.clearLauncherLog(source)
    load()
    notify({ type: 'info', message: `${current.label}-Log geleert` })
  }

  async function analyze() {
    if (!selected) return
    if (!hasKey) {
      notify({ type: 'error', message: 'Erst einen Anthropic API-Key in den Settings hinterlegen.' })
      return
    }
    setAnalyzing(true)
    const result: CrashAnalysis = await api?.analyzeCrash(instanceId, selected)
    setAnalyzing(false)
    setAnalysis(result)
    if (!result?.success) notify({ type: 'error', message: result?.error || 'Analyse fehlgeschlagen' })
  }

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Terminal size={20} className="text-crystal-accent" />
          <h1 className="text-xl font-bold text-crystal-text">Logs</h1>
        </div>

        <div className="flex items-center gap-2">
          <button onClick={load} title="Neu laden" className="crystal-btn-ghost p-2 border border-crystal-border rounded-lg">
            <RefreshCw size={14} />
          </button>
          <button onClick={openFolder} title="Ordner öffnen" className="crystal-btn-ghost p-2 border border-crystal-border rounded-lg">
            <FolderOpen size={14} />
          </button>
          <button onClick={copyLog} title="Log kopieren" className="crystal-btn-ghost p-2 border border-crystal-border rounded-lg">
            <Copy size={14} />
          </button>
          <button onClick={clearLog} title="Log leeren" className="crystal-btn-ghost p-2 border border-crystal-border rounded-lg hover:!text-crystal-danger">
            <Trash2 size={14} />
          </button>
        </div>
      </div>

      {/* Source tabs */}
      <div className="flex gap-1 p-1 bg-crystal-panel rounded-lg w-fit flex-wrap">
        {SOURCES.map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            onClick={() => setSource(id)}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm font-medium transition-all ${
              source === id ? 'bg-crystal-gradient text-white shadow-glow' : 'text-crystal-muted hover:text-crystal-text'
            }`}
          >
            <Icon size={14} /> {label}
          </button>
        ))}
      </div>

      {/* Instance selector, only where it applies */}
      {current.perInstance && (
        <div className="relative max-w-sm">
          <select
            value={instanceId}
            onChange={e => setInstanceId(e.target.value)}
            className="crystal-input w-full appearance-none pr-8 cursor-pointer text-sm"
          >
            {instances.length === 0 && <option value="">Keine Instanz vorhanden</option>}
            {instances.map(i => <option key={i.id} value={i.id}>{i.name}</option>)}
          </select>
          <ChevronDown size={14} className="absolute right-3 top-1/2 -translate-y-1/2 text-crystal-muted pointer-events-none" />
        </div>
      )}

      {current.perInstance ? (
        <div className="grid grid-cols-3 gap-3">
          <div className="crystal-card p-3 space-y-1 max-h-[460px] overflow-y-auto">
            {files.length === 0 && (
              <p className="text-crystal-muted text-xs text-center py-4">Keine Dateien gefunden.</p>
            )}
            {files.map(f => (
              <button
                key={f.name}
                onClick={() => openFile(f.name)}
                className={`w-full text-left px-2.5 py-2 rounded-lg text-xs transition-colors ${
                  selected === f.name ? 'bg-crystal-gradient text-white' : 'text-crystal-muted hover:bg-crystal-panel hover:text-crystal-text'
                }`}
              >
                <p className="truncate font-medium">{f.name}</p>
                <p className="opacity-70">{fmtSize(f.sizeBytes)} · {new Date(f.modifiedAt).toLocaleString('de-DE')}</p>
              </button>
            ))}
          </div>

          <div className="col-span-2 space-y-3">
            {source === 'crashes' && selected && (
              <div className="crystal-card p-4">
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-1.5 text-crystal-text text-sm font-medium">
                    <Sparkles size={14} className="text-crystal-accent" /> Claude-Analyse
                  </div>
                  <button onClick={analyze} disabled={analyzing} className="crystal-btn-primary text-xs px-3 py-1.5 disabled:opacity-60">
                    {analyzing ? 'Analysiere...' : 'Crash analysieren'}
                  </button>
                </div>
                {!hasKey && <p className="text-crystal-muted text-xs">Kein API-Key hinterlegt — Settings → Crash-Analyse.</p>}
                {analysis?.success && (
                  <div className="space-y-2 text-xs">
                    <div><span className="text-crystal-accent font-medium">Zusammenfassung: </span><span className="text-crystal-text">{analysis.summary}</span></div>
                    <div><span className="text-crystal-accent font-medium">Ursache: </span><span className="text-crystal-text">{analysis.likelyCause}</span></div>
                    <div><span className="text-crystal-accent font-medium">Fix: </span><span className="text-crystal-text whitespace-pre-wrap">{analysis.suggestedFix}</span></div>
                  </div>
                )}
              </div>
            )}

            <LogView content={content} placeholder="Wähle links eine Datei aus." />
          </div>
        </div>
      ) : (
        <LogView content={content} placeholder="Noch keine Einträge." />
      )}
    </div>
  )
}

function LogView({ content, placeholder }: { content: string; placeholder: string }) {
  return (
    <div className="crystal-card p-4">
      {content ? (
        <pre className="text-crystal-muted text-[11px] leading-relaxed whitespace-pre-wrap max-h-[460px] overflow-y-auto font-mono">
          {content}
        </pre>
      ) : (
        <p className="text-crystal-muted text-sm text-center py-8">{placeholder}</p>
      )}
    </div>
  )
}
