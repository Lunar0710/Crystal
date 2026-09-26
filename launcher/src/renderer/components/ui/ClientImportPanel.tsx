import React, { useEffect, useState } from 'react'
import { ArrowRightLeft, Check } from 'lucide-react'
import { Section } from './Page'
import { notify } from '../../store/notificationStore'

const api = (window as any).crystal

type Source = 'lunar' | 'feather'
interface Detected { source: Source; name: string; profiles: string[]; active: string }
interface Item { from: string; to: string; enabled: boolean; moved: boolean }
interface Preview {
  items: Item[]
  unmatched: string[]
  keybinds: string[]
  extras: { options: boolean; servers: boolean; packs: number }
}
interface Result extends Preview { optionsCopied: number; serversCopied: boolean; packsCopied: number; notes: string[] }
interface InstanceInfo { id: string; name: string; version: string }

/**
 * "Von Lunar oder Feather umziehen": finds the other client on this PC and
 * takes its mods, HUD layout, keys, options, servers and packs into a Nexora
 * instance in one click (main/import/ClientImport.ts does the work).
 */
export function ClientImportPanel() {
  const [clients, setClients] = useState<Detected[] | null>(null)
  const [source, setSource] = useState<Source>('lunar')
  const [profile, setProfile] = useState('')
  const [instances, setInstances] = useState<InstanceInfo[]>([])
  const [instanceId, setInstanceId] = useState('')
  const [preview, setPreview] = useState<Preview | null>(null)
  const [result, setResult] = useState<Result | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api?.importDetect().then((list: Detected[]) => {
      setClients(list || [])
      if (list?.length) { setSource(list[0].source); setProfile(list[0].active) }
    })
    api?.getInstances().then((list: InstanceInfo[]) => {
      setInstances(list || [])
      if (list?.length) setInstanceId(list[0].id)
    })
  }, [])

  const client = clients?.find(c => c.source === source)

  useEffect(() => {
    setResult(null)
    if (!client || !profile) { setPreview(null); return }
    api?.importPreview(source, profile, instanceId || undefined).then(setPreview)
  }, [source, profile, instanceId, clients])

  const pickSource = (c: Detected) => { setSource(c.source); setProfile(c.active) }

  const run = async () => {
    if (!instanceId) return
    setBusy(true)
    try {
      const r: Result = await api.importApply(source, profile, instanceId)
      setResult(r)
      notify({ type: 'success', title: 'Umzug', message: `Von ${client?.name} übernommen` })
    } catch (e: any) {
      notify({ type: 'error', title: 'Umzug', message: String(e?.message || e).replace(/^Error invoking remote method '[^']+': (Error: )?/, '') })
    } finally {
      setBusy(false)
    }
  }

  if (clients === null) return null

  return (
    <Section
      title="Von Lunar oder Feather umziehen"
      description="Übernimmt deine Mods, wo deine HUD-Anzeigen sitzen, deine Tasten, Minecraft-Einstellungen, Serverliste und Resource Packs. Dein alter Client bleibt, wie er ist."
    >
      {clients.length === 0 ? (
        <p className="px-4 py-3 text-xs text-crystal-muted">Auf diesem PC ist weder Lunar Client noch Feather eingerichtet.</p>
      ) : (
        <div className="p-4 flex flex-col gap-3">
          <div className="flex flex-wrap gap-2">
            {clients.map(c => (
              <button
                key={c.source}
                onClick={() => pickSource(c)}
                aria-pressed={source === c.source}
                className={`px-3 py-1.5 rounded-md text-xs border transition-colors ${
                  source === c.source ? 'border-crystal-text/40 bg-crystal-panel text-crystal-text' : 'border-crystal-border text-crystal-muted hover:text-crystal-text'
                }`}
              >
                {c.name}
              </button>
            ))}
          </div>

          <div className="grid grid-cols-2 gap-2">
            <label className="flex flex-col gap-1 text-[11px] text-crystal-muted">
              Profil in {client?.name}
              <select value={profile} onChange={e => setProfile(e.target.value)} className="crystal-input text-[13px] cursor-pointer">
                {client?.profiles.map(p => <option key={p} value={p}>{p}</option>)}
              </select>
            </label>
            <label className="flex flex-col gap-1 text-[11px] text-crystal-muted">
              In diese Nexora-Instanz
              <select value={instanceId} onChange={e => setInstanceId(e.target.value)} className="crystal-input text-[13px] cursor-pointer">
                {instances.map(i => <option key={i.id} value={i.id}>{i.name} ({i.version})</option>)}
              </select>
            </label>
          </div>

          {preview && !result && (
            <ul className="text-xs text-crystal-text flex flex-col gap-1">
              <li>{preview.items.filter(i => i.enabled).length} Mods an, {preview.items.length} Mods übernommen, {preview.items.filter(i => i.moved).length} Anzeigen an ihrem Platz</li>
              {preview.extras.options && <li>Minecraft-Einstellungen: FOV, Empfindlichkeit, Tastenbelegung, Lautstärken</li>}
              {preview.extras.servers && <li>Deine Serverliste</li>}
              {preview.extras.packs > 0 && <li>{preview.extras.packs} Resource Packs</li>}
              {preview.unmatched.length > 0 && (
                <li className="text-crystal-muted">Gibt es in Nexora nicht: {preview.unmatched.join(', ')}</li>
              )}
            </ul>
          )}

          {result && (
            <div className="text-xs text-crystal-text flex flex-col gap-1">
              <p className="flex items-center gap-1.5"><Check size={12} className="text-crystal-success" />
                {result.items.length} Mods übernommen, {result.items.filter(i => i.enabled).length} davon an.
              </p>
              {result.optionsCopied > 0 && <p>{result.optionsCopied} Minecraft-Einstellungen übernommen.</p>}
              {result.serversCopied && <p>Serverliste übernommen.</p>}
              {result.packsCopied > 0 && <p>{result.packsCopied} Resource Packs kopiert.</p>}
              {result.notes.map(n => <p key={n} className="text-crystal-muted">{n}</p>)}
              <p className="text-crystal-muted">Die Plätze der Anzeigen sind umgerechnet. Feinschliff geht im Spiel mit dem HUD-Editor.</p>
            </div>
          )}

          <div>
            <button onClick={run} disabled={busy || !instanceId || !preview} className="crystal-btn-primary text-xs">
              <ArrowRightLeft size={12} /> {busy ? 'Wird übernommen …' : result ? 'Nochmal übernehmen' : `Von ${client?.name} übernehmen`}
            </button>
          </div>
        </div>
      )}
    </Section>
  )
}
