import React, { useState, useEffect } from 'react'
import { Sparkles, RefreshCw, FolderOpen, Copy, Trash2, ChevronDown } from 'lucide-react'
import { Page, PageHeader, EmptyState } from '../ui/Page'
import { notify } from '../../store/notificationStore'
import { RunningGamesPanel, useRunningGames } from '../ui/RunningGamesPanel'

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
const SOURCES: { id: Source; label: string; perInstance: boolean }[] = [
  { id: 'launcher', label: 'Launcher',     perInstance: false },
  { id: 'client',   label: 'Spielstart',   perInstance: false },
  { id: 'updater',  label: 'Updates',      perInstance: false },
  { id: 'gameLogs', label: 'Spiel-Logs',   perInstance: true },
  { id: 'crashes',  label: 'Abstürze',     perInstance: true },
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

  // While a game runs (or loads), its log and resources update by themselves.
  const games = useRunningGames()
  const loadRef = React.useRef(load)
  loadRef.current = load
  useEffect(() => {
    if (!games.length) return
    const timer = setInterval(() => loadRef.current(), 3000)
    return () => clearInterval(timer)
  }, [games.length])

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
      notify({ type: 'info', message: 'Spiel-Logs und Crash-Reports schreibt Minecraft selbst. Löschen geht nur im Ordner.' })
      return
    }
    await api?.clearLauncherLog(source)
    load()
    notify({ type: 'info', message: `${current.label}-Log geleert` })
  }

  async function analyze() {
    if (!selected) return
    if (!hasKey) {
      notify({ type: 'error', message: 'Hinterlege zuerst einen Anthropic-API-Key in den Einstellungen.' })
      return
    }
    setAnalyzing(true)
    const result: CrashAnalysis = await api?.analyzeCrash(instanceId, selected)
    setAnalyzing(false)
    setAnalysis(result)
    if (!result?.success) notify({ type: 'error', message: result?.error || 'Analyse fehlgeschlagen' })
  }

  return (
    <Page wide>
      <PageHeader
        title="Logs"
        description="Protokolle des Launchers und deiner Instanzen. Hilfreich, wenn etwas nicht startet."
        actions={
          <>
            <IconButton label="Neu laden" onClick={load}><RefreshCw size={14} strokeWidth={1.75} /></IconButton>
            <IconButton label="Ordner öffnen" onClick={openFolder}><FolderOpen size={14} strokeWidth={1.75} /></IconButton>
            <IconButton label="In Zwischenablage kopieren" onClick={copyLog} disabled={!content}><Copy size={14} strokeWidth={1.75} /></IconButton>
            {!current.perInstance && (
              <IconButton label="Log leeren" onClick={clearLog} danger><Trash2 size={14} strokeWidth={1.75} /></IconButton>
            )}
          </>
        }
      />

      {games.length > 0 && <div className="mb-4"><RunningGamesPanel games={games} /></div>}

      <div className="flex flex-wrap items-end justify-between gap-3 border-b border-crystal-border mb-4">
        <div className="flex gap-5" role="tablist">
          {SOURCES.map(({ id, label }) => (
            <button
              key={id}
              role="tab"
              aria-selected={source === id}
              onClick={() => setSource(id)}
              className={`pb-2.5 -mb-px text-[13px] border-b-2 whitespace-nowrap transition-colors ${
                source === id ? 'border-crystal-accent text-crystal-text font-medium' : 'border-transparent text-crystal-muted hover:text-crystal-text'
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        {current.perInstance && instances.length > 0 && (
          <div className="relative mb-2">
            <select
              value={instanceId}
              onChange={e => setInstanceId(e.target.value)}
              aria-label="Instanz"
              className="crystal-input appearance-none pr-8 py-1.5 text-xs"
            >
              {instances.map(i => <option key={i.id} value={i.id}>{i.name}</option>)}
            </select>
            <ChevronDown size={12} className="absolute right-2.5 top-1/2 -translate-y-1/2 text-crystal-muted pointer-events-none" />
          </div>
        )}
      </div>

      {current.perInstance ? (
        instances.length === 0 ? (
          <EmptyState title="Noch keine Instanz">Logs gibt es, sobald eine Instanz einmal gestartet wurde.</EmptyState>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-[260px_minmax(0,1fr)] gap-4 items-start">
            <div className="crystal-card overflow-hidden max-h-[520px] overflow-y-auto">
              {files.length === 0 ? (
                <p className="px-3 py-6 text-center text-xs text-crystal-muted">
                  {source === 'crashes' ? 'Keine Abstürze. Gut so.' : 'Keine Log-Dateien.'}
                </p>
              ) : (
                <ul className="divide-y divide-crystal-border">
                  {files.map(f => (
                    <li key={f.name}>
                      <button
                        onClick={() => openFile(f.name)}
                        aria-current={selected === f.name}
                        className={`relative w-full text-left px-3 py-2.5 transition-colors ${
                          selected === f.name ? 'bg-crystal-panel' : 'hover:bg-crystal-panel/60'
                        }`}
                      >
                        {selected === f.name && <span className="absolute left-0 top-2 bottom-2 w-[2px] rounded-full bg-crystal-accent" />}
                        <span className={`block text-xs truncate font-mono ${selected === f.name ? 'text-crystal-text' : 'text-crystal-muted'}`}>{f.name}</span>
                        <span className="block text-[11px] text-crystal-muted/80 mt-0.5 tabular">
                          {new Date(f.modifiedAt).toLocaleString('de-DE', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })}, {fmtSize(f.sizeBytes)}
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            <div className="space-y-3 min-w-0">
              {source === 'crashes' && selected && (
                <div className="crystal-card">
                  <div className="flex items-center gap-3 px-4 py-3">
                    <div className="flex-1 min-w-0">
                      <p className="text-[13px] text-crystal-text">Absturz erklären lassen</p>
                      <p className="text-xs text-crystal-muted">
                        {hasKey ? 'Claude liest den Crash-Report und schlägt eine Lösung vor.' : 'Dafür brauchst du einen Anthropic-API-Key in den Einstellungen.'}
                      </p>
                    </div>
                    <button onClick={analyze} disabled={analyzing || !hasKey} className="crystal-btn-primary text-xs py-1.5 disabled:opacity-50">
                      <Sparkles size={12} /> {analyzing ? 'Analysiere…' : 'Analysieren'}
                    </button>
                  </div>
                  {analysis?.success && (
                    <dl className="border-t border-crystal-border px-4 py-3 space-y-2.5 text-[13px]">
                      <div><dt className="crystal-label">Was passiert ist</dt><dd className="text-crystal-text mt-0.5 select-text">{analysis.summary}</dd></div>
                      <div><dt className="crystal-label">Wahrscheinliche Ursache</dt><dd className="text-crystal-text mt-0.5 select-text">{analysis.likelyCause}</dd></div>
                      <div><dt className="crystal-label">So behebst du es</dt><dd className="text-crystal-text mt-0.5 whitespace-pre-wrap select-text">{analysis.suggestedFix}</dd></div>
                    </dl>
                  )}
                </div>
              )}

              <LogView content={content} placeholder="Wähle links eine Datei aus." />
            </div>
          </div>
        )
      ) : (
        <LogView content={content} placeholder="Noch keine Einträge." />
      )}
    </Page>
  )
}

function IconButton({ label, onClick, disabled, danger, children }: {
  label: string
  onClick: () => void
  disabled?: boolean
  danger?: boolean
  children: React.ReactNode
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      title={label}
      className={`p-2 rounded-lg border border-crystal-border text-crystal-muted transition-colors disabled:opacity-40 ${
        danger ? 'hover:text-crystal-danger hover:border-crystal-danger/40' : 'hover:text-crystal-text hover:bg-crystal-border/40'
      }`}
    >
      {children}
    </button>
  )
}

/**
 * Log text is selectable (the app otherwise disables selection) and WARN/ERROR
 * lines are tinted, so the one relevant line is findable in a long log.
 */
function LogView({ content, placeholder }: { content: string; placeholder: string }) {
  const lines = content ? content.split(/\r?\n/) : []
  return (
    <div className="crystal-card overflow-hidden">
      {content ? (
        <pre className="font-mono text-[11.5px] leading-[1.6] max-h-[520px] overflow-auto py-3 select-text">
          {lines.map((line, i) => {
            const tone = /\b(ERROR|FATAL|Exception|Caused by)\b/.test(line)
              ? 'text-crystal-danger bg-crystal-danger/[0.06]'
              : /\bWARN\b/.test(line)
                ? 'text-crystal-warning'
                : 'text-crystal-muted'
            return <div key={i} className={`px-4 whitespace-pre-wrap break-all ${tone}`}>{line || ' '}</div>
          })}
        </pre>
      ) : (
        <p className="text-center text-xs text-crystal-muted py-10">{placeholder}</p>
      )}
    </div>
  )
}
