import React, { useState, useEffect } from 'react'
import { Upload, Trash2, AlertTriangle, ChevronDown } from 'lucide-react'
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
  const [open, setOpen] = useState(false)

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
    return <div className="h-[58px] mb-5 rounded-[10px] bg-crystal-card animate-pulse" />
  }

  if (error || !target) {
    return (
      <div className="crystal-card mb-5 flex items-start gap-3 px-4 py-3">
        <AlertTriangle size={15} strokeWidth={1.75} className="text-crystal-danger shrink-0 mt-0.5" />
        <div className="min-w-0">
          <p className="text-[13px] text-crystal-text">Diese Instanz lässt sich gerade nicht lesen</p>
          <p className="text-xs text-crystal-muted whitespace-pre-wrap mt-0.5">{error}</p>
        </div>
      </div>
    )
  }

  return (
    <div className="crystal-card mb-5">
      <div className="flex items-center gap-3 px-4 py-3">
        <div className="flex-1 min-w-0">
          <p className="text-[13px] text-crystal-text">Eigenen Client installieren</p>
          <p className="text-xs text-crystal-muted truncate">
            {target.blocker
              ? 'Gerade nicht möglich, Details aufklappen.'
              : `Eine Client-.jar für Minecraft ${target.minecraftVersion} mit ${target.loader === 'fabric' ? 'Fabric' : target.loader} in diese Instanz legen.`}
          </p>
        </div>
        <button
          onClick={() => setOpen(o => !o)}
          aria-expanded={open}
          className="inline-flex items-center gap-1 text-xs text-crystal-muted hover:text-crystal-text px-2 py-1 rounded-md hover:bg-crystal-border/50"
        >
          Details <ChevronDown size={12} className={`transition-transform ${open ? 'rotate-180' : ''}`} />
        </button>
        {!target.blocker && (
          <button onClick={installJar} disabled={busy} className="crystal-btn-ghost border border-crystal-border text-crystal-text text-xs py-1.5 disabled:opacity-50">
            <Upload size={12} strokeWidth={1.75} /> {busy ? 'Installiere…' : '.jar auswählen'}
          </button>
        )}
      </div>

      {open && (
        <div className="border-t border-crystal-border px-4 py-3 space-y-3">
          {target.blocker && (
            <div className="flex items-start gap-2 p-2.5 rounded-md bg-crystal-danger/[0.07] border border-crystal-danger/25 text-xs">
              <AlertTriangle size={13} className="text-crystal-danger shrink-0 mt-px" />
              <span className="text-crystal-text whitespace-pre-wrap">{target.blocker}</span>
            </div>
          )}

          <dl className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-1.5 text-xs">
            <dt className="text-crystal-muted">Minecraft</dt><dd className="text-crystal-text tabular">{target.minecraftVersion}</dd>
            <dt className="text-crystal-muted">Fabric Loader</dt>
            <dd className={target.fabricAvailable ? 'text-crystal-text font-mono' : 'text-crystal-danger'}>{target.fabricLoaderVersion ?? 'nicht verfügbar'}</dd>
            <dt className="text-crystal-muted">Zielordner</dt>
            <dd className="text-crystal-text font-mono truncate select-text" title={target.modsDir}>{target.modsDir}</dd>
          </dl>

          {target.installed.length > 0 && (
            <div>
              <p className="crystal-label mb-1.5">Mit Mod-Informationen erkannt</p>
              <ul className="rounded-md border border-crystal-border divide-y divide-crystal-border">
                {target.installed.filter(m => m.name || m.version).map(mod => (
                  <li key={mod.fileName} className="flex items-center gap-2 px-2.5 py-1.5">
                    <span className="flex-1 min-w-0 text-xs text-crystal-text truncate">{mod.name || mod.fileName}</span>
                    {mod.version && <span className="text-[11px] text-crystal-muted font-mono shrink-0">{mod.version}</span>}
                    <button
                      onClick={() => uninstall(mod.fileName)}
                      disabled={busy}
                      aria-label={`${mod.name || mod.fileName} entfernen`}
                      title="Entfernen, eine Sicherungskopie bleibt erhalten"
                      className="p-1 rounded text-crystal-muted hover:text-crystal-danger disabled:opacity-50"
                    >
                      <Trash2 size={11} />
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
