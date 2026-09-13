import React, { useState, useEffect } from 'react'
import {
  Boxes, Upload, Trash2, FolderOpen, RefreshCw, AlertTriangle, CheckCircle2, Loader2,
} from 'lucide-react'
import { notify } from '../../store/notificationStore'

interface InstalledClient {
  fileName: string
  modId?: string
  name?: string
  version?: string
  installedAt: number
  sizeBytes: number
}

interface InstallTarget {
  instanceId: string
  instanceName: string
  minecraftVersion: string
  loader: string
  fabricLoaderVersion: string | null
  fabricAvailable: boolean
  modsDir: string
  modsDirWritable: boolean
  installed: InstalledClient[]
  blocker?: string
}

const api = (window as any).crystal

function fmtSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

/**
 * Installs a client .jar into one specific instance.
 *
 * Everything the install writes to is spelled out up front — instance, game
 * version, loader, target folder — so it is never ambiguous which instance a
 * click is about to modify.
 */
export function ClientInstallPanel({ instanceId }: { instanceId: string }) {
  const [target, setTarget] = useState<InstallTarget | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function refresh() {
    setLoading(true)
    const result = await api?.getInstallTarget(instanceId)

    // A target without an instanceId is the "can't even look" case.
    if (!result || (result.blocker && !result.instanceId)) {
      setTarget(null)
      setError(result?.blocker || 'Die Instanz konnte nicht gelesen werden.')
    } else {
      setTarget(result)
      setError(null)
    }
    setLoading(false)
  }

  useEffect(() => { refresh() }, [instanceId])

  async function installJar() {
    setBusy(true)
    const result = await api?.pickAndInstallClient(instanceId)
    setBusy(false)

    // null means the user closed the file picker — not an error.
    if (!result) return

    notify({
      type: result.success ? 'success' : 'error',
      title: result.success ? 'Client installiert' : 'Installation fehlgeschlagen',
      message: result.message,
    })
    if (result.success) refresh()
  }

  async function uninstall(fileName: string) {
    setBusy(true)
    const result = await api?.uninstallClientJar(instanceId, fileName)
    setBusy(false)

    notify({
      type: result?.success ? 'info' : 'error',
      message: result?.message || 'Entfernen fehlgeschlagen',
    })
    if (result?.success) refresh()
  }

  if (loading) {
    return (
      <div className="crystal-card p-4 flex items-center gap-2 text-crystal-muted text-sm">
        <Loader2 size={14} className="animate-spin" /> Instanz wird geprüft...
      </div>
    )
  }

  if (error || !target) {
    return (
      <div className="crystal-card p-4 flex items-start gap-2 text-sm">
        <AlertTriangle size={15} className="text-crystal-danger shrink-0 mt-0.5" />
        <div>
          <p className="text-crystal-text font-medium">Client-Installation nicht möglich</p>
          <p className="text-crystal-muted text-xs whitespace-pre-wrap mt-1">{error}</p>
        </div>
      </div>
    )
  }

  return (
    <div className="crystal-card p-4 space-y-3">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Boxes size={15} className="text-crystal-accent" />
          <span className="text-crystal-text text-sm font-medium">Client installieren</span>
        </div>
        <button
          onClick={refresh}
          title="Status neu einlesen"
          className="crystal-btn-ghost p-1.5 border border-crystal-border rounded-lg"
        >
          <RefreshCw size={12} />
        </button>
      </div>

      {/* Exactly what an install would touch */}
      <div className="grid grid-cols-2 gap-x-4 gap-y-1.5 text-xs">
        <Detail label="Instanz" value={target.instanceName} />
        <Detail label="Minecraft" value={target.minecraftVersion} />
        <Detail label="Loader" value={target.loader} />
        <Detail
          label="Fabric Loader"
          value={target.fabricLoaderVersion ?? 'nicht verfügbar'}
          warn={!target.fabricAvailable}
        />
        <div className="col-span-2">
          <Detail label="Zielordner" value={target.modsDir} mono />
        </div>
      </div>

      {target.blocker ? (
        <div className="flex items-start gap-2 p-3 rounded-lg bg-crystal-danger/10 border border-crystal-danger/30 text-xs">
          <AlertTriangle size={14} className="text-crystal-danger shrink-0 mt-0.5" />
          <span className="text-crystal-text whitespace-pre-wrap">{target.blocker}</span>
        </div>
      ) : (
        <div className="flex gap-2">
          <button
            onClick={installJar}
            disabled={busy}
            className="crystal-btn-primary flex items-center gap-1.5 text-xs px-3 py-2 disabled:opacity-60"
          >
            {busy ? <Loader2 size={12} className="animate-spin" /> : <Upload size={12} />}
            Client-.jar auswählen
          </button>
          <button
            onClick={() => api?.openContentFolder(instanceId, 'mod')}
            className="crystal-btn-ghost flex items-center gap-1.5 text-xs px-3 py-2 border border-crystal-border rounded-lg"
          >
            <FolderOpen size={12} /> Ordner öffnen
          </button>
        </div>
      )}

      {/* Installed status */}
      <div className="space-y-1.5">
        <p className="text-crystal-muted text-[11px] font-medium uppercase tracking-wide">
          Installiert ({target.installed.length})
        </p>

        {target.installed.length === 0 ? (
          <p className="text-crystal-muted text-xs">
            Noch kein Mod in diesem Ordner.
          </p>
        ) : (
          target.installed.map(mod => (
            <div
              key={mod.fileName}
              className="flex items-center justify-between gap-2 px-2.5 py-2 rounded-lg bg-crystal-panel"
            >
              <div className="min-w-0">
                <div className="flex items-center gap-1.5">
                  <CheckCircle2 size={11} className="text-crystal-success shrink-0" />
                  <span className="text-crystal-text text-xs font-medium truncate">
                    {mod.name || mod.fileName}
                  </span>
                  {mod.version && (
                    <span className="text-crystal-muted text-[10px] shrink-0">{mod.version}</span>
                  )}
                </div>
                <p className="text-crystal-muted text-[10px] truncate">
                  {mod.fileName} · {fmtSize(mod.sizeBytes)} ·{' '}
                  {new Date(mod.installedAt).toLocaleString('de-DE')}
                </p>
              </div>

              <button
                onClick={() => uninstall(mod.fileName)}
                disabled={busy}
                title="Entfernen (eine Sicherungskopie bleibt erhalten)"
                className="p-1.5 rounded-lg text-crystal-muted hover:text-crystal-danger hover:bg-crystal-border transition-colors shrink-0 disabled:opacity-60"
              >
                <Trash2 size={12} />
              </button>
            </div>
          ))
        )}
      </div>
    </div>
  )
}

function Detail({ label, value, mono, warn }: {
  label: string
  value: string
  mono?: boolean
  warn?: boolean
}) {
  return (
    <div className="flex gap-1.5 min-w-0">
      <span className="text-crystal-muted shrink-0">{label}:</span>
      <span
        title={value}
        className={`truncate ${warn ? 'text-crystal-danger' : 'text-crystal-text'} ${mono ? 'font-mono text-[10px]' : ''}`}
      >
        {value}
      </span>
    </div>
  )
}
