import React, { useState, useEffect } from 'react'
import {
  Layers, Plus, Trash2, ArrowLeft, Search, Download, FolderInput, Blocks,
  Package, Image, Sparkles, Upload, FolderOpen, ToggleLeft, ToggleRight, Check
} from 'lucide-react'
import { notify } from '../../store/notificationStore'
import { ClientInstallPanel } from '../ui/ClientInstallPanel'

type ContentType = 'mod' | 'resourcepack' | 'shader'

interface Instance {
  id: string
  name: string
  version: string
  loader: string
  gameDir: string
  useCrystalClient: boolean
  imported?: boolean
  createdAt: number
}

interface ContentFile {
  fileName: string
  sizeBytes: number
  enabled: boolean
  installedAt: number
}

interface ModrinthHit {
  project_id: string
  title: string
  description: string
  icon_url: string | null
  downloads: number
}

interface ModrinthVersion {
  id: string
  version_number: string
  game_versions: string[]
}

const api = (window as any).crystal
const GAME_VERSION = '1.21.11'
const LOADER = 'fabric'

const CONTENT_TABS: { id: ContentType; label: string; icon: typeof Package }[] = [
  { id: 'mod', label: 'Mods', icon: Package },
  { id: 'resourcepack', label: 'Resource Packs', icon: Image },
  { id: 'shader', label: 'Shader', icon: Sparkles },
]

function fmtSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function fmtDownloads(n: number) {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`
  return `${n}`
}

export function Instances() {
  const [instances, setInstances] = useState<Instance[]>([])
  const [openInstance, setOpenInstance] = useState<Instance | null>(null)
  const [creating, setCreating] = useState(false)
  const [createMode, setCreateMode] = useState<'empty' | 'preset'>('empty')
  const [newName, setNewName] = useState('')
  const [newUseCrystal, setNewUseCrystal] = useState(true)

  // Preset (Modrinth modpack) browsing
  const [presetQuery, setPresetQuery] = useState('')
  const [presets, setPresets] = useState<ModrinthHit[]>([])
  const [presetsLoading, setPresetsLoading] = useState(false)
  const [selectedPreset, setSelectedPreset] = useState<ModrinthHit | null>(null)
  const [installingPreset, setInstallingPreset] = useState(false)

  function refresh() {
    api?.getInstances().then((list: Instance[]) => setInstances(list || []))
  }

  useEffect(refresh, [])
  useEffect(() => { if (creating && createMode === 'preset') searchPresets(presetQuery) }, [creating, createMode])

  async function searchPresets(q: string) {
    setPresetsLoading(true)
    const hits = await api?.searchModpacks(q, GAME_VERSION)
    setPresets(hits || [])
    setPresetsLoading(false)
  }

  async function createInstance() {
    if (!newName.trim()) return

    if (createMode === 'preset' && selectedPreset) {
      setInstallingPreset(true)
      const created = await api?.createInstance({
        name: newName.trim(),
        version: GAME_VERSION,
        loader: LOADER,
        gameDir: '',
        useCrystalClient: newUseCrystal,
      })
      if (!created) {
        setInstallingPreset(false)
        notify({ type: 'error', message: 'Instanz konnte nicht angelegt werden' })
        return
      }
      const result = await api?.installModpack(created.id, selectedPreset.project_id)
      setInstallingPreset(false)
      if (result?.success) {
        notify({ type: 'success', title: result.name, message: `${result.filesInstalled} Dateien installiert` })
      } else {
        notify({ type: 'error', title: selectedPreset.title, message: result?.error || 'Modpack-Installation fehlgeschlagen' })
      }
      resetCreateForm()
      refresh()
      return
    }

    await api?.createInstance({
      name: newName.trim(),
      version: GAME_VERSION,
      loader: LOADER,
      gameDir: '',
      useCrystalClient: newUseCrystal,
    })
    notify({ type: 'success', message: `Instanz "${newName.trim()}" erstellt (leer)` })
    resetCreateForm()
    refresh()
  }

  async function createInstanceFromMrpackFile() {
    if (!newName.trim()) return

    setInstallingPreset(true)
    const created = await api?.createInstance({
      name: newName.trim(),
      version: GAME_VERSION,
      loader: LOADER,
      gameDir: '',
      useCrystalClient: newUseCrystal,
    })
    if (!created) {
      setInstallingPreset(false)
      notify({ type: 'error', message: 'Instanz konnte nicht angelegt werden' })
      return
    }

    const result = await api?.pickAndInstallModpackFile(created.id)
    setInstallingPreset(false)

    // null means the user closed the file picker — the empty instance we just
    // made stays around rather than silently vanishing, same as any other create.
    if (!result) {
      notify({ type: 'info', message: `Instanz "${newName.trim()}" angelegt (leer) — kein Modpack ausgewählt` })
    } else if (result.success) {
      notify({ type: 'success', title: result.name, message: `${result.filesInstalled} Dateien installiert` })
    } else {
      notify({ type: 'error', message: result.error || 'Modpack-Import fehlgeschlagen' })
    }
    resetCreateForm()
    refresh()
  }

  function resetCreateForm() {
    setNewName('')
    setCreating(false)
    setCreateMode('empty')
    setSelectedPreset(null)
    setPresetQuery('')
    setPresets([])
  }

  async function importInstance() {
    const imported = await api?.importInstance(GAME_VERSION)
    if (imported) {
      refresh()
      notify({ type: 'success', message: `"${imported.name}" importiert` })
    }
  }

  async function toggleMode(inst: Instance, e: React.MouseEvent) {
    e.stopPropagation()
    await api?.updateInstance(inst.id, { useCrystalClient: !inst.useCrystalClient })
    refresh()
  }

  async function deleteInstance(id: string, e: React.MouseEvent) {
    e.stopPropagation()
    await api?.deleteInstance(id)
    refresh()
    notify({ type: 'info', message: 'Instanz gelöscht' })
  }

  if (openInstance) {
    return <InstanceDetail instance={openInstance} onBack={() => { setOpenInstance(null); refresh() }} />
  }

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Layers size={20} className="text-crystal-accent" />
          <h1 className="text-xl font-bold text-crystal-text">Instanzen</h1>
          <span className="text-xs text-crystal-muted bg-crystal-border px-2 py-0.5 rounded-full">
            {GAME_VERSION} · Fabric
          </span>
        </div>
        <div className="flex gap-2">
          <button onClick={importInstance} className="crystal-btn-ghost flex items-center gap-1.5 text-sm border border-crystal-border rounded-lg">
            <FolderInput size={14} /> Importieren
          </button>
          <button onClick={() => setCreating(true)} className="crystal-btn-primary flex items-center gap-1.5 text-sm">
            <Plus size={14} /> Neue Instanz
          </button>
        </div>
      </div>

      {creating && (
        <div className="crystal-card p-4 space-y-3 border-crystal-accent/50">
          <h3 className="text-crystal-text font-medium text-sm">Neue Instanz</h3>

          <div className="flex gap-1 p-1 bg-crystal-panel rounded-lg w-fit">
            <button
              onClick={() => setCreateMode('empty')}
              className={`px-3 py-1.5 rounded-md text-sm font-medium transition-all ${
                createMode === 'empty' ? 'bg-crystal-gradient text-white shadow-glow' : 'text-crystal-muted hover:text-crystal-text'
              }`}
            >
              Leer
            </button>
            <button
              onClick={() => setCreateMode('preset')}
              className={`px-3 py-1.5 rounded-md text-sm font-medium transition-all ${
                createMode === 'preset' ? 'bg-crystal-gradient text-white shadow-glow' : 'text-crystal-muted hover:text-crystal-text'
              }`}
            >
              Preset (Modpack)
            </button>
          </div>

          <input
            autoFocus
            type="text"
            value={newName}
            onChange={e => setNewName(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && !installingPreset && createInstance()}
            className="crystal-input w-full"
            placeholder="Name der Instanz"
          />

          {createMode === 'empty' ? (
            <>
              <div>
                <span className="text-crystal-muted text-xs block mb-1.5">Starten als</span>
                <div className="grid grid-cols-2 gap-2">
                  <button
                    onClick={() => setNewUseCrystal(true)}
                    className={`py-2 rounded-lg text-sm font-medium border transition-all ${
                      newUseCrystal
                        ? 'bg-crystal-gradient text-white border-transparent shadow-glow'
                        : 'bg-crystal-panel text-crystal-muted border-crystal-border hover:text-crystal-text'
                    }`}
                  >
                    Crystal Client
                  </button>
                  <button
                    onClick={() => setNewUseCrystal(false)}
                    className={`py-2 rounded-lg text-sm font-medium border transition-all ${
                      !newUseCrystal
                        ? 'bg-crystal-gradient text-white border-transparent shadow-glow'
                        : 'bg-crystal-panel text-crystal-muted border-crystal-border hover:text-crystal-text'
                    }`}
                  >
                    Vanilla + Mods
                  </button>
                </div>
              </div>
              <p className="text-crystal-muted text-xs">
                Wird leer erstellt — Minecraft {GAME_VERSION} mit Fabric, keine Mods vorinstalliert.
              </p>
            </>
          ) : (
            <div className="space-y-2">
              <div className="flex gap-2">
                <input
                  type="text"
                  value={presetQuery}
                  onChange={e => setPresetQuery(e.target.value)}
                  onKeyDown={e => e.key === 'Enter' && searchPresets(presetQuery)}
                  className="crystal-input flex-1 text-sm"
                  placeholder="Modpacks durchsuchen..."
                />
                <button
                  onClick={createInstanceFromMrpackFile}
                  disabled={installingPreset || !newName.trim()}
                  title={!newName.trim() ? 'Erst einen Namen eingeben' : '.mrpack-Datei von der Festplatte importieren'}
                  className="crystal-btn-ghost flex items-center gap-1.5 text-sm border border-crystal-border rounded-lg px-3 whitespace-nowrap disabled:opacity-60"
                >
                  <Upload size={14} /> .mrpack
                </button>
              </div>
              <div className="max-h-64 overflow-y-auto space-y-1.5 pr-1">
                {presetsLoading && <p className="text-crystal-muted text-xs text-center py-4">Lädt...</p>}
                {!presetsLoading && presets.length === 0 && (
                  <p className="text-crystal-muted text-xs text-center py-4">Keine Modpacks gefunden.</p>
                )}
                {presets.map(hit => (
                  <button
                    key={hit.project_id}
                    onClick={() => {
                      setSelectedPreset(hit)
                      if (!newName.trim()) setNewName(hit.title)
                    }}
                    className={`w-full flex items-center gap-2.5 p-2 rounded-lg border text-left transition-all ${
                      selectedPreset?.project_id === hit.project_id
                        ? 'border-crystal-accent bg-crystal-accent/10'
                        : 'border-crystal-border bg-crystal-panel hover:border-crystal-accent/40'
                    }`}
                  >
                    {hit.icon_url ? (
                      <img src={hit.icon_url} alt="" className="w-8 h-8 rounded-md shrink-0" />
                    ) : (
                      <div className="w-8 h-8 rounded-md bg-crystal-gradient shrink-0" />
                    )}
                    <div className="min-w-0">
                      <p className="text-crystal-text text-sm font-medium truncate">{hit.title}</p>
                      <p className="text-crystal-muted text-xs truncate">{fmtDownloads(hit.downloads)} Downloads</p>
                    </div>
                  </button>
                ))}
              </div>
              {selectedPreset && (
                <p className="text-crystal-muted text-xs">
                  "{selectedPreset.title}" wird in die neue Instanz installiert (Version + Loader werden vom Modpack übernommen).
                </p>
              )}
            </div>
          )}

          <div className="flex gap-2">
            <button
              onClick={createInstance}
              disabled={installingPreset || !newName.trim() || (createMode === 'preset' && !selectedPreset)}
              className="crystal-btn-primary text-sm disabled:opacity-60"
            >
              {installingPreset ? 'Installiere...' : 'Erstellen'}
            </button>
            <button onClick={resetCreateForm} disabled={installingPreset} className="crystal-btn-ghost text-sm disabled:opacity-60">
              Abbrechen
            </button>
          </div>
        </div>
      )}

      <div className="space-y-2">
        {instances.map(inst => (
          <div
            key={inst.id}
            onClick={() => setOpenInstance(inst)}
            className="crystal-card p-4 flex items-center justify-between hover:border-crystal-accent/40 transition-colors cursor-pointer"
          >
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-lg bg-crystal-gradient flex items-center justify-center text-white font-bold text-sm shadow-crystal">
                {inst.name.charAt(0).toUpperCase()}
              </div>
              <div>
                <p className="text-crystal-text font-medium">
                  {inst.name}
                  {inst.imported && (
                    <span className="ml-2 text-[10px] text-crystal-muted bg-crystal-border px-1.5 py-0.5 rounded-full">
                      importiert
                    </span>
                  )}
                </p>
                <p className="text-crystal-muted text-xs">Minecraft {inst.version} · {inst.loader}</p>
              </div>
            </div>

            <div className="flex items-center gap-2">
              <button
                onClick={e => toggleMode(inst, e)}
                title="Zwischen Crystal Client und Vanilla + Mods wechseln"
                className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-medium border transition-all ${
                  inst.useCrystalClient
                    ? 'bg-crystal-gradient text-white border-transparent'
                    : 'bg-crystal-panel text-crystal-muted border-crystal-border hover:text-crystal-text'
                }`}
              >
                <Blocks size={11} />
                {inst.useCrystalClient ? 'Crystal Client' : 'Vanilla + Mods'}
              </button>
              <button
                onClick={e => deleteInstance(inst.id, e)}
                title={inst.imported ? 'Nur aus Crystal entfernen — Dateien bleiben' : 'Instanz und Dateien löschen'}
                className="p-2 rounded-lg hover:bg-crystal-border text-crystal-muted hover:text-crystal-danger transition-colors"
              >
                <Trash2 size={14} />
              </button>
            </div>
          </div>
        ))}

        {instances.length === 0 && !creating && (
          <div className="crystal-card p-8 text-center text-crystal-muted">
            <Layers size={32} className="mx-auto mb-2 opacity-30" />
            <p className="text-sm">Noch keine Instanzen. Erstelle eine, um loszulegen.</p>
          </div>
        )}
      </div>
    </div>
  )
}

function InstanceDetail({ instance, onBack }: { instance: Instance; onBack: () => void }) {
  const [tab, setTab] = useState<ContentType>('mod')
  const [view, setView] = useState<'installed' | 'browse'>('installed')
  const [files, setFiles] = useState<ContentFile[]>([])
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<ModrinthHit[]>([])
  const [totalHits, setTotalHits] = useState(0)
  const [searching, setSearching] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [installingId, setInstallingId] = useState<string | null>(null)
  const [versionsByProject, setVersionsByProject] = useState<Record<string, ModrinthVersion[]>>({})
  const [pickedVersion, setPickedVersion] = useState<Record<string, string>>({})
  const [expandedProject, setExpandedProject] = useState<string | null>(null)
  const [installedPicker, setInstalledPicker] = useState<string | null>(null)
  const [installedVersions, setInstalledVersions] = useState<Record<string, ModrinthVersion[]>>({})
  const [installedPicked, setInstalledPicked] = useState<Record<string, string>>({})
  const [switching, setSwitching] = useState<string | null>(null)
  const [installedProjectIds, setInstalledProjectIds] = useState<Set<string>>(new Set())
  const [installedQuery, setInstalledQuery] = useState('')

  const visibleFiles = installedQuery.trim()
    ? files.filter(f => f.fileName.toLowerCase().includes(installedQuery.trim().toLowerCase()))
    : files

  function refreshFiles() {
    api?.listContent(instance.id, tab).then((list: ContentFile[]) => setFiles(list || []))
  }

  useEffect(() => {
    refreshFiles()
    if (view === 'browse') search('')
  }, [tab, view])

  // Which Modrinth projects are already on disk, so search results can say
  // "Installiert" instead of offering an install that just downloads the same
  // file again. Resolved from the files themselves (by hash), since the folder
  // only holds jars and knows nothing about project ids.
  useEffect(() => {
    let cancelled = false
    if (files.length === 0) {
      setInstalledProjectIds(new Set())
      return
    }

    Promise.all(files.map(f => api?.identifyModFile(instance.id, tab, f.fileName)))
      .then((identified: ({ projectId: string } | null)[]) => {
        if (cancelled) return
        setInstalledProjectIds(new Set(identified.filter(Boolean).map(i => i!.projectId)))
      })

    return () => { cancelled = true }
  }, [files, tab])

  async function search(q: string) {
    setSearching(true)
    const res = await api?.searchModrinth(q, GAME_VERSION, LOADER, tab, 0)
    setResults(res?.hits || [])
    setTotalHits(res?.totalHits || 0)
    setSearching(false)
  }

  async function loadMore() {
    setLoadingMore(true)
    const res = await api?.searchModrinth(query, GAME_VERSION, LOADER, tab, results.length)
    setResults(prev => [...prev, ...(res?.hits || [])])
    setTotalHits(res?.totalHits || totalHits)
    setLoadingMore(false)
  }

  async function toggleVersionPicker(hit: ModrinthHit) {
    if (expandedProject === hit.project_id) {
      setExpandedProject(null)
      return
    }
    setExpandedProject(hit.project_id)
    if (!versionsByProject[hit.project_id]) {
      const versions = await api?.getModVersions(hit.project_id, GAME_VERSION, LOADER, tab)
      setVersionsByProject(prev => ({ ...prev, [hit.project_id]: versions || [] }))
    }
  }

  async function install(hit: ModrinthHit) {
    setInstallingId(hit.project_id)
    const versionId = pickedVersion[hit.project_id]
    const result = await api?.installFromModrinth(instance.id, hit.project_id, GAME_VERSION, LOADER, tab, versionId)
    setInstallingId(null)
    if (result?.success) {
      notify({ type: 'success', title: hit.title, message: `${result.fileName} installiert` })
      refreshFiles()
    } else {
      notify({ type: 'error', title: hit.title, message: result?.error || 'Installation fehlgeschlagen' })
    }
  }

  async function uploadFile() {
    const added = await api?.installContentFile(instance.id, tab)
    if (added) {
      notify({ type: 'success', message: `${added.fileName} hinzugefügt` })
      refreshFiles()
    }
  }

  async function toggleFile(fileName: string) {
    await api?.toggleContent(instance.id, tab, fileName)
    refreshFiles()
  }

  async function removeFile(fileName: string) {
    await api?.removeContent(instance.id, tab, fileName)
    refreshFiles()
    notify({ type: 'info', message: `${fileName} entfernt` })
  }

  // Installed files are just jars on disk — Modrinth is asked which project a
  // file belongs to (by its SHA-1) before any version list can be shown.
  async function openInstalledVersions(fileName: string) {
    if (installedPicker === fileName) {
      setInstalledPicker(null)
      return
    }
    setInstalledPicker(fileName)
    if (installedVersions[fileName]) return

    const identified = await api?.identifyModFile(instance.id, tab, fileName)
    if (!identified) {
      setInstalledVersions(prev => ({ ...prev, [fileName]: [] }))
      return
    }
    const versions = await api?.getModVersions(identified.projectId, GAME_VERSION, LOADER, tab)
    setInstalledVersions(prev => ({ ...prev, [fileName]: versions || [] }))
    setInstalledPicked(prev => ({ ...prev, [fileName]: identified.versionId }))
  }

  async function applyInstalledVersion(fileName: string) {
    const versionId = installedPicked[fileName]
    if (!versionId) return
    setSwitching(fileName)
    const result = await api?.switchModVersion(instance.id, tab, fileName, versionId)
    setSwitching(null)
    if (result?.success) {
      notify({ type: 'success', message: `Auf ${result.fileName} gewechselt` })
      setInstalledPicker(null)
      refreshFiles()
    } else {
      notify({ type: 'error', message: result?.error || 'Wechsel fehlgeschlagen' })
    }
  }

  const uploadLabel = tab === 'mod' ? '.jar hochladen' : '.zip hochladen'

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <button onClick={onBack} className="p-1.5 rounded-lg hover:bg-crystal-border text-crystal-muted hover:text-crystal-text transition-colors">
            <ArrowLeft size={16} />
          </button>
          <h1 className="text-xl font-bold text-crystal-text">{instance.name}</h1>
          <span className="text-xs text-crystal-muted bg-crystal-border px-2 py-0.5 rounded-full">
            {instance.version} · {instance.loader}
          </span>
        </div>
        <div className="flex gap-2">
          <button onClick={() => api?.openContentFolder(instance.id, tab)} className="crystal-btn-ghost flex items-center gap-1.5 text-sm border border-crystal-border rounded-lg">
            <FolderOpen size={14} /> Ordner
          </button>
          <button onClick={uploadFile} className="crystal-btn-primary flex items-center gap-1.5 text-sm">
            <Upload size={14} /> {uploadLabel}
          </button>
        </div>
      </div>

      <ClientInstallPanel instanceId={instance.id} />

      {/* Content type tabs */}
      <div className="flex gap-1 p-1 bg-crystal-panel rounded-lg w-fit">
        {CONTENT_TABS.map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            onClick={() => setTab(id)}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm font-medium transition-all ${
              tab === id ? 'bg-crystal-gradient text-white shadow-glow' : 'text-crystal-muted hover:text-crystal-text'
            }`}
          >
            <Icon size={14} /> {label}
          </button>
        ))}
      </div>

      {/* Installed vs Browse */}
      <div className="flex gap-4 border-b border-crystal-border">
        {(['installed', 'browse'] as const).map(v => (
          <button
            key={v}
            onClick={() => setView(v)}
            className={`pb-2 text-sm font-medium transition-colors border-b-2 -mb-px ${
              view === v
                ? 'text-crystal-accent border-crystal-accent'
                : 'text-crystal-muted border-transparent hover:text-crystal-text'
            }`}
          >
            {v === 'installed' ? `Installiert (${files.length})` : 'Modrinth durchsuchen'}
          </button>
        ))}
      </div>

      {view === 'installed' ? (
        <div className="space-y-2">
          <div className="relative">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-crystal-muted" />
            <input
              type="text"
              value={installedQuery}
              onChange={e => setInstalledQuery(e.target.value)}
              className="crystal-input w-full pl-8"
              placeholder="Installierte durchsuchen..."
            />
          </div>
          {visibleFiles.map(file => (
            <div key={file.fileName} className="crystal-card p-3 space-y-2">
              <div className="flex items-center gap-3">
                <div className="w-9 h-9 rounded-lg bg-crystal-panel border border-crystal-border flex items-center justify-center">
                  <Package size={16} className={file.enabled ? 'text-crystal-accent' : 'text-crystal-muted'} />
                </div>
                <div className="flex-1 min-w-0">
                  <p className={`font-medium text-sm truncate ${file.enabled ? 'text-crystal-text' : 'text-crystal-muted line-through'}`}>
                    {file.fileName.replace(/\.disabled$/, '')}
                  </p>
                  <p className="text-crystal-muted text-xs">{fmtSize(file.sizeBytes)}</p>
                </div>
                <button
                  onClick={() => openInstalledVersions(file.fileName)}
                  className="crystal-btn-ghost text-xs px-2 py-1.5 border border-crystal-border rounded-lg shrink-0"
                >
                  Version
                </button>
                <button onClick={() => toggleFile(file.fileName)} className="text-crystal-muted hover:text-crystal-accent transition-colors">
                  {file.enabled ? <ToggleRight size={22} className="text-crystal-accent" /> : <ToggleLeft size={22} />}
                </button>
                <button onClick={() => removeFile(file.fileName)} className="p-1.5 rounded hover:bg-crystal-border text-crystal-muted hover:text-crystal-danger transition-colors">
                  <Trash2 size={14} />
                </button>
              </div>

              {installedPicker === file.fileName && (
                <div className="pl-[48px] flex items-center gap-2">
                  {!installedVersions[file.fileName] ? (
                    <p className="text-crystal-muted text-xs">Suche Projekt auf Modrinth...</p>
                  ) : installedVersions[file.fileName].length === 0 ? (
                    <p className="text-crystal-muted text-xs">
                      Nicht auf Modrinth gefunden — Version kann nicht gewechselt werden.
                    </p>
                  ) : (
                    <>
                      <select
                        value={installedPicked[file.fileName] || ''}
                        onChange={e => setInstalledPicked(prev => ({ ...prev, [file.fileName]: e.target.value }))}
                        className="crystal-input text-xs py-1"
                      >
                        {installedVersions[file.fileName].map(v => (
                          <option key={v.id} value={v.id}>{v.version_number}</option>
                        ))}
                      </select>
                      <button
                        onClick={() => applyInstalledVersion(file.fileName)}
                        disabled={switching === file.fileName}
                        className="crystal-btn-primary text-xs px-3 py-1.5 disabled:opacity-50"
                      >
                        {switching === file.fileName ? '...' : 'Wechseln'}
                      </button>
                    </>
                  )}
                </div>
              )}
            </div>
          ))}
          {files.length === 0 && (
            <div className="crystal-card p-8 text-center text-crystal-muted">
              <Package size={28} className="mx-auto mb-2 opacity-30" />
              <p className="text-sm">Nichts installiert. Durchsuche Modrinth oder lade eine Datei hoch.</p>
            </div>
          )}
          {files.length > 0 && visibleFiles.length === 0 && (
            <p className="text-crystal-muted text-sm text-center py-4">
              Nichts gefunden für "{installedQuery}".
            </p>
          )}
        </div>
      ) : (
        <div className="space-y-3">
          <div className="relative">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-crystal-muted" />
            <input
              type="text"
              value={query}
              onChange={e => setQuery(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && search(query)}
              className="crystal-input w-full pl-8"
              placeholder={`${CONTENT_TABS.find(t => t.id === tab)?.label} auf Modrinth suchen...`}
            />
          </div>

          <div className="space-y-2">
            {searching && <p className="text-crystal-muted text-sm text-center py-4">Suche läuft...</p>}
            {!searching && results.length === 0 && (
              <p className="text-crystal-muted text-sm text-center py-4">Keine Ergebnisse.</p>
            )}
            {results.map(hit => (
              <div
                key={hit.project_id}
                className={`crystal-card p-3 space-y-2 ${installedProjectIds.has(hit.project_id) ? 'opacity-60' : ''}`}
              >
                <div className="flex items-center gap-3">
                  {hit.icon_url ? (
                    <img src={hit.icon_url} className="w-10 h-10 rounded-lg object-cover" alt="" />
                  ) : (
                    <div className="w-10 h-10 rounded-lg bg-crystal-panel border border-crystal-border flex items-center justify-center">
                      <Package size={18} className="text-crystal-accent" />
                    </div>
                  )}
                  <div className="flex-1 min-w-0">
                    <p className="text-crystal-text font-medium text-sm">{hit.title}</p>
                    <p className="text-crystal-muted text-xs truncate">{hit.description}</p>
                    <p className="text-crystal-muted text-xs mt-0.5">{fmtDownloads(hit.downloads)} Downloads</p>
                  </div>
                  <button
                    onClick={() => toggleVersionPicker(hit)}
                    className="crystal-btn-ghost text-xs px-2 py-2 border border-crystal-border rounded-lg shrink-0"
                  >
                    {pickedVersion[hit.project_id] ? 'Version ✓' : 'Version wählen'}
                  </button>
                  {installedProjectIds.has(hit.project_id) ? (
                    <span className="text-xs px-3 py-2 rounded-lg border border-crystal-border text-crystal-muted shrink-0 flex items-center gap-1.5">
                      <Check size={12} /> Installiert
                    </span>
                  ) : (
                    <button
                      onClick={() => install(hit)}
                      disabled={installingId === hit.project_id}
                      className="crystal-btn-primary text-xs px-3 py-2 flex items-center gap-1.5 disabled:opacity-50 shrink-0"
                    >
                      <Download size={12} />
                      {installingId === hit.project_id ? '...' : 'Installieren'}
                    </button>
                  )}
                </div>

                {expandedProject === hit.project_id && (
                  <div className="pl-[52px]">
                    {!versionsByProject[hit.project_id] ? (
                      <p className="text-crystal-muted text-xs">Lade Versionen...</p>
                    ) : versionsByProject[hit.project_id].length === 0 ? (
                      <p className="text-crystal-muted text-xs">Keine Versionen für {GAME_VERSION} gefunden.</p>
                    ) : (
                      <select
                        value={pickedVersion[hit.project_id] || versionsByProject[hit.project_id][0]?.id || ''}
                        onChange={e => setPickedVersion(prev => ({ ...prev, [hit.project_id]: e.target.value }))}
                        className="crystal-input text-xs py-1"
                      >
                        {versionsByProject[hit.project_id].map(v => (
                          <option key={v.id} value={v.id}>{v.version_number}</option>
                        ))}
                      </select>
                    )}
                  </div>
                )}
              </div>
            ))}
            {!searching && results.length > 0 && results.length < totalHits && (
              <button
                onClick={loadMore}
                disabled={loadingMore}
                className="crystal-btn-ghost w-full text-sm py-2 border border-crystal-border rounded-lg disabled:opacity-50"
              >
                {loadingMore ? 'Lädt...' : `Mehr laden (${results.length}/${totalHits})`}
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
