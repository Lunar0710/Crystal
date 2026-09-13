import React, { useState, useEffect, useMemo } from 'react'
import {
  Plus, Trash2, ChevronLeft, Search, Download, FolderInput, Boxes,
  Upload, FolderOpen, Check, X, ChevronDown,
} from 'lucide-react'
import { notify } from '../../store/notificationStore'
import { ClientInstallPanel } from '../ui/ClientInstallPanel'
import { Page, PageHeader, EmptyState, Switch } from '../ui/Page'

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

const CONTENT_TABS: { id: ContentType; label: string; upload: string }[] = [
  { id: 'mod',          label: 'Mods',           upload: '.jar hinzufügen' },
  { id: 'resourcepack', label: 'Resource Packs', upload: '.zip hinzufügen' },
  { id: 'shader',       label: 'Shader',         upload: '.zip hinzufügen' },
]

function fmtSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toLocaleString('de-DE', { maximumFractionDigits: 0 })} KB`
  return `${(bytes / 1024 / 1024).toLocaleString('de-DE', { maximumFractionDigits: 1 })} MB`
}

function fmtDownloads(n: number) {
  if (n >= 1_000_000) return `${(n / 1_000_000).toLocaleString('de-DE', { maximumFractionDigits: 1 })} Mio.`
  if (n >= 1_000) return `${(n / 1_000).toLocaleString('de-DE', { maximumFractionDigits: 0 })} Tsd.`
  return `${n}`
}

/** Clean display name for a mod jar: drops ".disabled" and the ".jar"/".zip" suffix. */
function displayName(fileName: string) {
  return fileName.replace(/\.disabled$/, '').replace(/\.(jar|zip)$/i, '')
}

/* ------------------------------------------------------------------------- */

export function Instances() {
  const [instances, setInstances] = useState<Instance[] | null>(null)
  const [openInstance, setOpenInstance] = useState<Instance | null>(null)
  const [creating, setCreating] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState<string | null>(null)

  function refresh() {
    api?.getInstances().then((list: Instance[]) => setInstances(list || []))
  }

  useEffect(refresh, [])

  async function importInstance() {
    const imported = await api?.importInstance(GAME_VERSION)
    if (imported) {
      refresh()
      notify({ type: 'success', message: `${imported.name} importiert` })
    }
  }

  async function setMode(inst: Instance, useCrystalClient: boolean) {
    await api?.updateInstance(inst.id, { useCrystalClient })
    refresh()
  }

  async function removeInstance(inst: Instance) {
    const result: { movedTo: string | null } | undefined = await api?.deleteInstance(inst.id)
    setConfirmDelete(null)
    refresh()
    notify({
      type: 'info',
      title: `${inst.name} entfernt`,
      message: inst.imported
        ? 'Der Ordner liegt unverändert an seinem ursprünglichen Ort.'
        : result?.movedTo
          ? 'Der Ordner mit deinen Welten liegt im Papierkorb unter Einstellungen > Speicherort.'
          : 'Die Instanz wurde aus der Liste entfernt.',
    })
  }

  if (openInstance) {
    return <InstanceDetail instance={openInstance} onBack={() => { setOpenInstance(null); refresh() }} />
  }

  return (
    <Page>
      <PageHeader
        title="Instanzen"
        description="Jede Instanz hat eigene Mods, Welten und Einstellungen."
        actions={
          <>
            <button onClick={importInstance} className="crystal-btn-ghost border border-crystal-border text-crystal-text text-[13px]">
              <FolderInput size={14} strokeWidth={1.75} /> Ordner importieren
            </button>
            <button onClick={() => setCreating(true)} disabled={creating} className="crystal-btn-primary text-[13px] disabled:opacity-60">
              <Plus size={14} /> Neue Instanz
            </button>
          </>
        }
      />

      {creating && (
        <CreateInstance
          onDone={created => { setCreating(false); refresh(); if (created) setOpenInstance(created) }}
          onCancel={() => setCreating(false)}
        />
      )}

      {instances === null && (
        <div className="crystal-card divide-y divide-crystal-border">
          {[0, 1].map(i => (
            <div key={i} className="px-4 py-4 animate-pulse flex gap-3">
              <div className="w-9 h-9 rounded-md bg-crystal-border" />
              <div className="flex-1">
                <div className="h-3.5 w-36 rounded bg-crystal-border mb-2" />
                <div className="h-3 w-24 rounded bg-crystal-border/60" />
              </div>
            </div>
          ))}
        </div>
      )}

      {instances?.length === 0 && !creating && (
        <EmptyState
          icon={<Boxes size={22} strokeWidth={1.75} />}
          title="Noch keine Instanz"
          action={<button onClick={() => setCreating(true)} className="crystal-btn-primary text-[13px]"><Plus size={14} /> Neue Instanz</button>}
        >
          Leg eine leere Instanz an, installiere ein Modpack von Modrinth oder importiere einen vorhandenen Minecraft-Ordner.
        </EmptyState>
      )}

      {instances && instances.length > 0 && (
        <div className="crystal-card divide-y divide-crystal-border overflow-hidden">
          {instances.map(inst => (
            confirmDelete === inst.id ? (
              <div key={inst.id} className="px-4 py-3.5 bg-crystal-danger/[0.06]">
                <p className="text-[13px] text-crystal-text">{inst.name} entfernen?</p>
                <p className="text-xs text-crystal-muted mt-0.5 max-w-[62ch]">
                  {inst.imported
                    ? 'Crystal vergisst nur den Eintrag. Der Ordner bleibt, wo er ist.'
                    : 'Der Ordner kommt in den Papierkorb. Deine Welten werden nicht gelöscht und lassen sich von dort zurückholen.'}
                </p>
                <div className="flex gap-2 mt-2.5">
                  <button onClick={() => removeInstance(inst)} className="crystal-btn text-xs bg-crystal-danger text-white hover:brightness-110">
                    Entfernen
                  </button>
                  <button onClick={() => setConfirmDelete(null)} className="crystal-btn-ghost text-xs">Abbrechen</button>
                </div>
              </div>
            ) : (
              <div key={inst.id} className="group flex items-center gap-3.5 px-4 py-3 hover:bg-crystal-panel/50 transition-colors">
                <button onClick={() => setOpenInstance(inst)} className="flex items-center gap-3.5 flex-1 min-w-0 text-left">
                  <span className="w-9 h-9 rounded-md bg-crystal-panel border border-crystal-border flex items-center justify-center text-[13px] font-semibold text-crystal-text shrink-0">
                    {inst.name.charAt(0).toUpperCase()}
                  </span>
                  <span className="min-w-0">
                    <span className="flex items-center gap-2">
                      <span className="text-[13px] font-medium text-crystal-text truncate">{inst.name}</span>
                      {inst.imported && <span className="text-[11px] px-1.5 rounded bg-crystal-border text-crystal-muted">importiert</span>}
                    </span>
                    <span className="block text-xs text-crystal-muted tabular">Minecraft {inst.version} mit {inst.loader === 'fabric' ? 'Fabric' : inst.loader}</span>
                  </span>
                </button>

                <ModeToggle value={inst.useCrystalClient} onChange={v => setMode(inst, v)} />

                <button
                  onClick={() => setConfirmDelete(inst.id)}
                  aria-label={`${inst.name} entfernen`}
                  className="p-1.5 rounded-md text-crystal-muted opacity-0 group-hover:opacity-100 focus:opacity-100 hover:text-crystal-danger hover:bg-crystal-border/50 transition"
                >
                  <Trash2 size={14} strokeWidth={1.75} />
                </button>
              </div>
            )
          ))}
        </div>
      )}
    </Page>
  )
}

/** Two-option segmented control: which way this instance launches. */
function ModeToggle({ value, onChange }: { value: boolean; onChange: (useCrystal: boolean) => void }) {
  return (
    <div className="flex p-0.5 rounded-md bg-crystal-bg/60 border border-crystal-border text-[11px] shrink-0" role="radiogroup" aria-label="Startmodus">
      {[{ v: true, label: 'Crystal' }, { v: false, label: 'Vanilla' }].map(opt => (
        <button
          key={opt.label}
          role="radio"
          aria-checked={value === opt.v}
          onClick={() => value !== opt.v && onChange(opt.v)}
          className={`px-2 py-1 rounded transition-colors ${
            value === opt.v ? 'bg-crystal-card text-crystal-text shadow-sm' : 'text-crystal-muted hover:text-crystal-text'
          }`}
        >
          {opt.label}
        </button>
      ))}
    </div>
  )
}

/* ------------------------------------------------------------------------- */

function CreateInstance({ onDone, onCancel }: { onDone: (created: Instance | null) => void; onCancel: () => void }) {
  const [mode, setMode] = useState<'empty' | 'modpack'>('empty')
  const [name, setName] = useState('')
  const [useCrystal, setUseCrystal] = useState(true)
  const [query, setQuery] = useState('')
  const [packs, setPacks] = useState<ModrinthHit[]>([])
  const [loading, setLoading] = useState(false)
  const [selected, setSelected] = useState<ModrinthHit | null>(null)
  const [working, setWorking] = useState(false)

  useEffect(() => { if (mode === 'modpack' && packs.length === 0) searchPacks('') }, [mode])

  async function searchPacks(q: string) {
    setLoading(true)
    const hits = await api?.searchModpacks(q, GAME_VERSION)
    setPacks(hits || [])
    setLoading(false)
  }

  async function createBase(): Promise<Instance | null> {
    const created = await api?.createInstance({ name: name.trim(), version: GAME_VERSION, loader: LOADER, gameDir: '', useCrystalClient: useCrystal })
    if (!created) notify({ type: 'error', message: 'Instanz konnte nicht angelegt werden.' })
    return created ?? null
  }

  async function create() {
    if (!name.trim()) return
    setWorking(true)
    const created = await createBase()
    if (created && mode === 'modpack' && selected) {
      const result = await api?.installModpack(created.id, selected.project_id)
      if (result?.success) notify({ type: 'success', title: result.name, message: `${result.filesInstalled} Dateien installiert` })
      else notify({ type: 'error', title: selected.title, message: result?.error || 'Modpack-Installation fehlgeschlagen' })
    } else if (created) {
      notify({ type: 'success', message: `${created.name} angelegt` })
    }
    setWorking(false)
    onDone(created)
  }

  async function createFromFile() {
    if (!name.trim()) return
    setWorking(true)
    const created = await createBase()
    if (created) {
      const result = await api?.pickAndInstallModpackFile(created.id)
      if (!result) notify({ type: 'info', message: `${created.name} angelegt, kein Modpack ausgewählt` })
      else if (result.success) notify({ type: 'success', title: result.name, message: `${result.filesInstalled} Dateien installiert` })
      else notify({ type: 'error', message: result.error || 'Import fehlgeschlagen' })
    }
    setWorking(false)
    onDone(created)
  }

  const canCreate = !!name.trim() && !working && (mode === 'empty' || !!selected)

  return (
    <div className="crystal-card mb-6">
      <div className="flex items-center justify-between px-4 pt-3.5">
        <h2 className="text-[13px] font-semibold text-crystal-text">Neue Instanz</h2>
        <button onClick={onCancel} disabled={working} aria-label="Abbrechen" className="p-1 rounded text-crystal-muted hover:text-crystal-text disabled:opacity-50">
          <X size={14} />
        </button>
      </div>

      <div className="px-4 pt-3 pb-4 space-y-4">
        <div className="flex gap-1 p-0.5 rounded-md bg-crystal-bg/60 border border-crystal-border w-fit text-xs" role="tablist">
          {([['empty', 'Leer'], ['modpack', 'Modpack']] as const).map(([id, label]) => (
            <button
              key={id}
              role="tab"
              aria-selected={mode === id}
              onClick={() => setMode(id)}
              className={`px-3 py-1.5 rounded transition-colors ${mode === id ? 'bg-crystal-card text-crystal-text shadow-sm' : 'text-crystal-muted hover:text-crystal-text'}`}
            >
              {label}
            </button>
          ))}
        </div>

        <div className="space-y-1.5">
          <label htmlFor="instance-name" className="crystal-label">Name</label>
          <input
            id="instance-name"
            autoFocus
            value={name}
            onChange={e => setName(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && canCreate && create()}
            className="crystal-input w-full text-[13px]"
          />
        </div>

        <div className="space-y-1.5">
          <span className="crystal-label">Starten als</span>
          <div className="grid grid-cols-2 gap-2">
            {[
              { v: true, title: 'Crystal Client', hint: 'Mit HUD, Modulen und Cosmetics.' },
              { v: false, title: 'Vanilla mit Mods', hint: 'Nur die Mods, die du selbst installierst.' },
            ].map(opt => (
              <button
                key={opt.title}
                onClick={() => setUseCrystal(opt.v)}
                aria-pressed={useCrystal === opt.v}
                className={`text-left px-3 py-2.5 rounded-lg border transition-colors ${
                  useCrystal === opt.v ? 'border-crystal-accent bg-crystal-accent/[0.06]' : 'border-crystal-border hover:border-crystal-muted/50'
                }`}
              >
                <span className="block text-[13px] text-crystal-text">{opt.title}</span>
                <span className="block text-xs text-crystal-muted mt-0.5">{opt.hint}</span>
              </button>
            ))}
          </div>
        </div>

        {mode === 'modpack' && (
          <div className="space-y-2">
            <div className="flex gap-2">
              <div className="relative flex-1">
                <Search size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-crystal-muted" />
                <input
                  value={query}
                  onChange={e => setQuery(e.target.value)}
                  onKeyDown={e => e.key === 'Enter' && searchPacks(query)}
                  className="crystal-input w-full pl-8 text-[13px]"
                  placeholder="Modpacks auf Modrinth suchen"
                  aria-label="Modpacks suchen"
                />
              </div>
              <button
                onClick={createFromFile}
                disabled={working || !name.trim()}
                title={name.trim() ? '.mrpack-Datei von der Festplatte importieren' : 'Gib zuerst einen Namen ein'}
                className="crystal-btn-ghost border border-crystal-border text-crystal-text text-xs disabled:opacity-50"
              >
                <Upload size={13} strokeWidth={1.75} /> .mrpack-Datei
              </button>
            </div>

            <div className="max-h-64 overflow-y-auto rounded-lg border border-crystal-border divide-y divide-crystal-border">
              {loading && <p className="px-3 py-6 text-xs text-crystal-muted text-center">Suche…</p>}
              {!loading && packs.length === 0 && <p className="px-3 py-6 text-xs text-crystal-muted text-center">Keine Modpacks für {GAME_VERSION} gefunden.</p>}
              {!loading && packs.map(hit => (
                <button
                  key={hit.project_id}
                  onClick={() => { setSelected(hit); if (!name.trim()) setName(hit.title) }}
                  aria-pressed={selected?.project_id === hit.project_id}
                  className={`w-full flex items-center gap-3 px-3 py-2 text-left transition-colors ${
                    selected?.project_id === hit.project_id ? 'bg-crystal-accent/[0.08]' : 'hover:bg-crystal-panel/60'
                  }`}
                >
                  <ProjectIcon url={hit.icon_url} title={hit.title} />
                  <span className="flex-1 min-w-0">
                    <span className="block text-[13px] text-crystal-text truncate">{hit.title}</span>
                    <span className="block text-xs text-crystal-muted tabular">{fmtDownloads(hit.downloads)} Downloads</span>
                  </span>
                  {selected?.project_id === hit.project_id && <Check size={14} className="text-crystal-accent shrink-0" />}
                </button>
              ))}
            </div>
          </div>
        )}
      </div>

      <div className="flex justify-end gap-2 px-4 py-3 border-t border-crystal-border">
        <button onClick={onCancel} disabled={working} className="crystal-btn-ghost text-[13px] disabled:opacity-50">Abbrechen</button>
        <button onClick={create} disabled={!canCreate} className="crystal-btn-primary text-[13px] disabled:opacity-50">
          {working ? 'Wird angelegt…' : mode === 'modpack' ? 'Anlegen und installieren' : 'Anlegen'}
        </button>
      </div>
    </div>
  )
}

function ProjectIcon({ url, title }: { url: string | null; title: string }) {
  return url
    ? <img src={url} alt="" className="w-8 h-8 rounded-md object-cover shrink-0 bg-crystal-panel" />
    : (
      <span className="w-8 h-8 rounded-md bg-crystal-panel border border-crystal-border flex items-center justify-center text-xs font-semibold text-crystal-muted shrink-0">
        {title.charAt(0).toUpperCase()}
      </span>
    )
}

/* ------------------------------------------------------------------------- */

function InstanceDetail({ instance, onBack }: { instance: Instance; onBack: () => void }) {
  const [tab, setTab] = useState<ContentType>('mod')
  const [view, setView] = useState<'installed' | 'browse'>('installed')
  const [files, setFiles] = useState<ContentFile[] | null>(null)
  const [identified, setIdentified] = useState<Record<string, { projectId: string; versionId: string }>>({})
  const [filter, setFilter] = useState('')
  const [confirmRemove, setConfirmRemove] = useState<string | null>(null)

  const [query, setQuery] = useState('')
  const [results, setResults] = useState<ModrinthHit[]>([])
  const [totalHits, setTotalHits] = useState(0)
  const [searching, setSearching] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [installingId, setInstallingId] = useState<string | null>(null)

  const [versionsFor, setVersionsFor] = useState<string | null>(null)
  const [versions, setVersions] = useState<Record<string, ModrinthVersion[]>>({})
  const [pickedVersion, setPickedVersion] = useState<Record<string, string>>({})
  const [switching, setSwitching] = useState<string | null>(null)

  const tabDef = CONTENT_TABS.find(t => t.id === tab)!

  function refreshFiles() {
    api?.listContent(instance.id, tab).then((list: ContentFile[]) => setFiles(list || []))
    // One bulk request for the whole folder, used for both the version switcher
    // and the "already installed" state in search results.
    api?.identifyModFolder(instance.id, tab).then((map: typeof identified) => setIdentified(map || {}))
  }

  useEffect(() => {
    setFiles(null)
    setFilter('')
    setVersionsFor(null)
    refreshFiles()
  }, [tab])

  useEffect(() => { if (view === 'browse' && results.length === 0) search(query) }, [view, tab])

  const installedProjects = useMemo(() => new Set(Object.values(identified).map(i => i.projectId)), [identified])

  const visibleFiles = useMemo(() => {
    const q = filter.trim().toLowerCase()
    const list = files ?? []
    return q ? list.filter(f => f.fileName.toLowerCase().includes(q)) : list
  }, [files, filter])

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

  async function loadVersions(key: string, projectId: string, current?: string) {
    if (versionsFor === key) return setVersionsFor(null)
    setVersionsFor(key)
    if (!versions[projectId]) {
      const list = await api?.getModVersions(projectId, GAME_VERSION, LOADER, tab)
      setVersions(prev => ({ ...prev, [projectId]: list || [] }))
    }
    if (current) setPickedVersion(prev => ({ ...prev, [key]: prev[key] ?? current }))
  }

  async function install(hit: ModrinthHit) {
    setInstallingId(hit.project_id)
    const result = await api?.installFromModrinth(instance.id, hit.project_id, GAME_VERSION, LOADER, tab, pickedVersion[hit.project_id])
    setInstallingId(null)
    if (result?.success) {
      notify({ type: 'success', title: hit.title, message: 'Installiert' })
      setVersionsFor(null)
      refreshFiles()
    } else {
      notify({ type: 'error', title: hit.title, message: result?.error || 'Installation fehlgeschlagen' })
    }
  }

  async function switchVersion(fileName: string) {
    const versionId = pickedVersion[fileName]
    if (!versionId) return
    setSwitching(fileName)
    const result = await api?.switchModVersion(instance.id, tab, fileName, versionId)
    setSwitching(null)
    if (result?.success) {
      notify({ type: 'success', message: `Gewechselt auf ${displayName(result.fileName)}` })
      setVersionsFor(null)
      refreshFiles()
    } else {
      notify({ type: 'error', message: result?.error || 'Wechsel fehlgeschlagen' })
    }
  }

  async function uploadFile() {
    const added = await api?.installContentFile(instance.id, tab)
    if (added) {
      notify({ type: 'success', message: `${displayName(added.fileName)} hinzugefügt` })
      refreshFiles()
    }
  }

  async function toggleFile(fileName: string) {
    await api?.toggleContent(instance.id, tab, fileName)
    refreshFiles()
  }

  async function removeFile(fileName: string) {
    await api?.removeContent(instance.id, tab, fileName)
    setConfirmRemove(null)
    refreshFiles()
  }

  return (
    <Page wide>
      <button onClick={onBack} className="inline-flex items-center gap-1 -ml-1 mb-3 text-xs text-crystal-muted hover:text-crystal-text">
        <ChevronLeft size={14} /> Instanzen
      </button>

      <PageHeader
        title={instance.name}
        description={`Minecraft ${instance.version} mit ${instance.loader === 'fabric' ? 'Fabric' : instance.loader}, ${instance.useCrystalClient ? 'startet mit Crystal Client' : 'startet als Vanilla mit Mods'}.`}
        actions={
          <>
            <button onClick={() => api?.openContentFolder(instance.id, tab)} className="crystal-btn-ghost border border-crystal-border text-crystal-text text-[13px]">
              <FolderOpen size={14} strokeWidth={1.75} /> Ordner öffnen
            </button>
            <button onClick={uploadFile} className="crystal-btn-primary text-[13px]">
              <Upload size={14} /> {tabDef.upload}
            </button>
          </>
        }
      />

      <ClientInstallPanel instanceId={instance.id} />

      <div className="flex items-end justify-between gap-4 border-b border-crystal-border mb-4 mt-2">
        <div className="flex gap-5" role="tablist">
          {CONTENT_TABS.map(t => (
            <button
              key={t.id}
              role="tab"
              aria-selected={tab === t.id}
              onClick={() => { setTab(t.id); setResults([]) }}
              className={`pb-2.5 -mb-px text-[13px] border-b-2 transition-colors ${
                tab === t.id ? 'border-crystal-accent text-crystal-text font-medium' : 'border-transparent text-crystal-muted hover:text-crystal-text'
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>
        <div className="flex gap-1 p-0.5 mb-2 rounded-md bg-crystal-bg/60 border border-crystal-border text-xs">
          {([['installed', `Installiert${files ? ` (${files.length})` : ''}`], ['browse', 'Modrinth']] as const).map(([id, label]) => (
            <button
              key={id}
              onClick={() => setView(id)}
              aria-pressed={view === id}
              className={`px-2.5 py-1 rounded tabular transition-colors ${view === id ? 'bg-crystal-card text-crystal-text shadow-sm' : 'text-crystal-muted hover:text-crystal-text'}`}
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      {view === 'installed' ? (
        <div className="space-y-3">
          {files && files.length > 0 && (
            <div className="relative">
              <Search size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-crystal-muted" />
              <input
                value={filter}
                onChange={e => setFilter(e.target.value)}
                className="crystal-input w-full pl-8 text-[13px]"
                placeholder={`${tabDef.label} filtern`}
                aria-label={`${tabDef.label} filtern`}
              />
            </div>
          )}

          {files === null && <div className="h-40 rounded-[10px] bg-crystal-card animate-pulse" />}

          {files?.length === 0 && (
            <EmptyState
              title={`Keine ${tabDef.label} installiert`}
              action={
                <div className="flex gap-2">
                  <button onClick={() => setView('browse')} className="crystal-btn-primary text-[13px]"><Search size={13} /> Auf Modrinth suchen</button>
                  <button onClick={uploadFile} className="crystal-btn-ghost border border-crystal-border text-crystal-text text-[13px]">{tabDef.upload}</button>
                </div>
              }
            />
          )}

          {files && files.length > 0 && visibleFiles.length === 0 && (
            <p className="text-center text-xs text-crystal-muted py-6">Nichts passt zu „{filter}“.</p>
          )}

          {visibleFiles.length > 0 && (
            <div className="crystal-card divide-y divide-crystal-border overflow-hidden">
              {visibleFiles.map(file => {
                const known = identified[file.fileName]
                const open = versionsFor === file.fileName
                if (confirmRemove === file.fileName) {
                  return (
                    <div key={file.fileName} className="flex items-center gap-3 px-4 py-2.5 bg-crystal-danger/[0.06]">
                      <span className="flex-1 text-[13px] text-crystal-text truncate">{displayName(file.fileName)} löschen?</span>
                      <button onClick={() => removeFile(file.fileName)} className="crystal-btn text-xs py-1.5 bg-crystal-danger text-white hover:brightness-110">Löschen</button>
                      <button onClick={() => setConfirmRemove(null)} className="crystal-btn-ghost text-xs py-1.5">Abbrechen</button>
                    </div>
                  )
                }
                return (
                  <div key={file.fileName} className="group">
                    <div className="flex items-center gap-3 px-4 py-2.5">
                      <div className="flex-1 min-w-0">
                        <p className={`text-[13px] truncate ${file.enabled ? 'text-crystal-text' : 'text-crystal-muted'}`}>{displayName(file.fileName)}</p>
                        <p className="text-xs text-crystal-muted tabular">
                          {fmtSize(file.sizeBytes)}{!file.enabled && ', deaktiviert'}
                        </p>
                      </div>
                      {known && (
                        <button
                          onClick={() => loadVersions(file.fileName, known.projectId, known.versionId)}
                          aria-expanded={open}
                          className="inline-flex items-center gap-1 text-xs text-crystal-muted hover:text-crystal-text px-2 py-1 rounded-md hover:bg-crystal-border/50"
                        >
                          Version <ChevronDown size={12} className={`transition-transform ${open ? 'rotate-180' : ''}`} />
                        </button>
                      )}
                      <Switch checked={file.enabled} onChange={() => toggleFile(file.fileName)} label={`${displayName(file.fileName)} aktiv`} />
                      <button
                        onClick={() => setConfirmRemove(file.fileName)}
                        aria-label={`${displayName(file.fileName)} löschen`}
                        className="p-1.5 rounded-md text-crystal-muted opacity-0 group-hover:opacity-100 focus:opacity-100 hover:text-crystal-danger hover:bg-crystal-border/50 transition"
                      >
                        <Trash2 size={13} strokeWidth={1.75} />
                      </button>
                    </div>
                    {open && known && (
                      <VersionPicker
                        versions={versions[known.projectId]}
                        value={pickedVersion[file.fileName]}
                        onChange={v => setPickedVersion(prev => ({ ...prev, [file.fileName]: v }))}
                        action={
                          <button
                            onClick={() => switchVersion(file.fileName)}
                            disabled={switching === file.fileName || pickedVersion[file.fileName] === known.versionId}
                            className="crystal-btn-primary text-xs py-1.5 disabled:opacity-50"
                          >
                            {switching === file.fileName ? 'Wechsle…' : 'Wechseln'}
                          </button>
                        }
                      />
                    )}
                  </div>
                )
              })}
            </div>
          )}
        </div>
      ) : (
        <div className="space-y-3">
          <div className="relative">
            <Search size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-crystal-muted" />
            <input
              value={query}
              onChange={e => setQuery(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && search(query)}
              className="crystal-input w-full pl-8 text-[13px]"
              placeholder={`${tabDef.label} auf Modrinth suchen`}
              aria-label={`${tabDef.label} auf Modrinth suchen`}
            />
          </div>

          {searching && <div className="h-40 rounded-[10px] bg-crystal-card animate-pulse" />}
          {!searching && results.length === 0 && (
            <p className="text-center text-xs text-crystal-muted py-8">Keine Treffer für {GAME_VERSION}.</p>
          )}

          {!searching && results.length > 0 && (
            <div className="crystal-card divide-y divide-crystal-border overflow-hidden">
              {results.map(hit => {
                const installed = installedProjects.has(hit.project_id)
                const open = versionsFor === hit.project_id
                return (
                  <div key={hit.project_id}>
                    <div className="flex items-center gap-3 px-4 py-3">
                      <ProjectIcon url={hit.icon_url} title={hit.title} />
                      <div className="flex-1 min-w-0">
                        <p className="text-[13px] text-crystal-text truncate">{hit.title}</p>
                        <p className="text-xs text-crystal-muted truncate">{hit.description}</p>
                      </div>
                      <span className="text-xs text-crystal-muted tabular shrink-0 hidden md:block">{fmtDownloads(hit.downloads)}</span>
                      {installed ? (
                        <span className="inline-flex items-center gap-1 text-xs text-crystal-muted px-2 py-1.5 shrink-0">
                          <Check size={13} className="text-crystal-success" /> Installiert
                        </span>
                      ) : (
                        <>
                          <button
                            onClick={() => loadVersions(hit.project_id, hit.project_id)}
                            aria-expanded={open}
                            className="inline-flex items-center gap-1 text-xs text-crystal-muted hover:text-crystal-text px-2 py-1 rounded-md hover:bg-crystal-border/50 shrink-0"
                          >
                            Version <ChevronDown size={12} className={`transition-transform ${open ? 'rotate-180' : ''}`} />
                          </button>
                          <button
                            onClick={() => install(hit)}
                            disabled={installingId === hit.project_id}
                            className="crystal-btn-primary text-xs py-1.5 shrink-0 disabled:opacity-50"
                          >
                            <Download size={12} /> {installingId === hit.project_id ? 'Lädt…' : 'Installieren'}
                          </button>
                        </>
                      )}
                    </div>
                    {open && !installed && (
                      <VersionPicker
                        versions={versions[hit.project_id]}
                        value={pickedVersion[hit.project_id] ?? versions[hit.project_id]?.[0]?.id}
                        onChange={v => setPickedVersion(prev => ({ ...prev, [hit.project_id]: v }))}
                        hint="Wird beim Installieren benutzt. Standard ist die neueste Version."
                      />
                    )}
                  </div>
                )
              })}
            </div>
          )}

          {!searching && results.length > 0 && results.length < totalHits && (
            <button onClick={loadMore} disabled={loadingMore} className="crystal-btn-ghost w-full border border-crystal-border text-crystal-text text-[13px] disabled:opacity-50">
              {loadingMore ? 'Lädt…' : `Weitere laden (${results.length.toLocaleString('de-DE')} von ${totalHits.toLocaleString('de-DE')})`}
            </button>
          )}
        </div>
      )}
    </Page>
  )
}

function VersionPicker({ versions, value, onChange, action, hint }: {
  versions: ModrinthVersion[] | undefined
  value: string | undefined
  onChange: (id: string) => void
  action?: React.ReactNode
  hint?: string
}) {
  return (
    <div className="flex items-center gap-2 px-4 pb-3 -mt-0.5">
      {!versions ? (
        <span className="text-xs text-crystal-muted">Lade Versionen…</span>
      ) : versions.length === 0 ? (
        <span className="text-xs text-crystal-muted">Keine Version für {GAME_VERSION} gefunden.</span>
      ) : (
        <>
          <div className="relative">
            <select
              value={value ?? ''}
              onChange={e => onChange(e.target.value)}
              className="crystal-input appearance-none pr-8 py-1.5 text-xs font-mono"
              aria-label="Version"
            >
              {versions.map((v, i) => (
                <option key={v.id} value={v.id}>{v.version_number}{i === 0 ? '  (neueste)' : ''}</option>
              ))}
            </select>
            <ChevronDown size={12} className="absolute right-2.5 top-1/2 -translate-y-1/2 text-crystal-muted pointer-events-none" />
          </div>
          {action}
          {hint && <span className="text-xs text-crystal-muted">{hint}</span>}
        </>
      )}
    </div>
  )
}
