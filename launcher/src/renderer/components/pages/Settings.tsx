import React, { useState, useEffect } from 'react'
import { Check, Lock } from 'lucide-react'
import { notify } from '../../store/notificationStore'
import { useThemeStore } from '../../store/themeStore'
import { themes } from '../../theme/themes'
import { RANKS, RankId, RANK_ORDER, lockLabel, canUseTheme } from '../../data/ranks'
import { RankBadge } from '../ui/RankBadge'
import { LogoMark, LogoVariantId } from '../../theme/logoVariants'
import { Page, PageHeader, Section, Field, Switch } from '../ui/Page'

const api = (window as any).crystal
const STAFF_RANKS: RankId[] = ['owner', 'co_owner', 'admin', 'staff', 'developer']

export function Settings() {
  const { theme, setTheme } = useThemeStore()
  const [rank, setRankState] = useState<RankId>('member')
  const [icons, setIcons] = useState<{ id: LogoVariantId; name: string }[]>([])
  const [currentIcon, setCurrentIcon] = useState<LogoVariantId>('facet-hex')
  const [apiKey, setApiKey] = useState('')
  const [apiKeySaved, setApiKeySaved] = useState(false)
  const [maxRam, setMaxRam] = useState(4096)
  const [discordEnabled, setDiscordEnabled] = useState(true)
  const [discordConnected, setDiscordConnected] = useState(false)
  const [discordConfigured, setDiscordConfigured] = useState(true)
  const [versions, setVersions] = useState<{ launcher?: string; client?: string | null }>({})
  const [dataRoot, setDataRoot] = useState<{ current?: string; default?: string }>({})
  const [systemMb, setSystemMb] = useState<number | null>(null)

  useEffect(() => {
    api?.getDataRoot().then((r: { current: string; default: string }) => r && setDataRoot(r))
    api?.getVersionInfo().then((v: { launcher: string; client: string | null }) => v && setVersions(v))
    api?.isDiscordEnabled().then((v: boolean) => setDiscordEnabled(!!v))
    api?.isDiscordConnected().then((v: boolean) => setDiscordConnected(!!v))
    api?.isDiscordConfigured().then((v: boolean) => setDiscordConfigured(!!v))
    api?.getRank().then(setRankState)
    api?.listIcons().then(setIcons)
    api?.getCurrentIcon().then((id: LogoVariantId) => id && setCurrentIcon(id))
    api?.getClaudeApiKey().then((k: string) => { setApiKey(k || ''); setApiKeySaved(!!k) })
    Promise.all([api?.getSetting('maxRam'), api?.getSystemMemory()]).then(([saved, mem]: [number | undefined, { totalMb: number; suggestedMb: number } | undefined]) => {
      if (mem) setSystemMb(mem.totalMb)
      if (saved) setMaxRam(saved)
      else if (mem) setMaxRam(mem.suggestedMb)
    })
  }, [])

  async function pickDataRoot() {
    const result = await api?.pickDataRoot()
    if (!result) return
    if (!result.ok) {
      notify({ type: 'error', title: 'Ordner nicht nutzbar', message: result.error || 'Kein Schreibzugriff' })
      return
    }
    setDataRoot(prev => ({ ...prev, current: result.path }))
    notify({ type: 'success', message: 'Speicherort geändert' })
  }

  async function resetDataRoot() {
    const current = await api?.resetDataRoot()
    setDataRoot(prev => ({ ...prev, current }))
  }

  async function saveApiKey() {
    await api?.setClaudeApiKey(apiKey.trim())
    setApiKeySaved(!!apiKey.trim())
    notify({ type: 'success', message: apiKey.trim() ? 'API-Key gespeichert' : 'API-Key entfernt' })
  }

  async function toggleDiscord(v: boolean) {
    const next = await api?.setDiscordEnabled(v)
    setDiscordEnabled(!!next)
    api?.isDiscordConnected().then((c: boolean) => setDiscordConnected(!!c))
  }

  async function changeIcon(id: LogoVariantId) {
    const ok = await api?.setCurrentIcon(id)
    if (ok) {
      setCurrentIcon(id)
      notify({ type: 'success', message: 'App-Icon aktualisiert' })
    } else {
      notify({ type: 'error', message: 'Dafür brauchst du einen Team-Rang.' })
    }
  }

  const canEditIcon = STAFF_RANKS.includes(rank)

  return (
    <Page>
      <PageHeader title="Einstellungen" description="Änderungen werden sofort gespeichert." />

      <Section title="Darstellung">
        <div className="p-4">
          <div className="grid grid-cols-3 sm:grid-cols-4 gap-2">
            {themes.map(t => {
              const locked = !canUseTheme(rank, t.requiredRank)
              const active = theme === t.id
              return (
                <button
                  key={t.id}
                  onClick={() => {
                    if (locked) {
                      notify({ type: 'info', message: `${t.name} gibt es ab ${lockLabel(t.requiredRank!)}.` })
                      return
                    }
                    setTheme(t.id)
                  }}
                  aria-pressed={active}
                  className={`group text-left rounded-lg p-1.5 border transition-colors ${
                    active ? 'border-crystal-accent bg-crystal-panel' : 'border-transparent hover:bg-crystal-panel'
                  } ${locked ? 'cursor-not-allowed' : ''}`}
                >
                  <span
                    className={`block h-9 rounded-md ring-1 ring-inset ring-black/20 ${locked ? 'opacity-40' : ''}`}
                    style={{ background: `linear-gradient(135deg, ${t.preview[0]} 0 55%, ${t.preview[1]} 55% 100%)` }}
                  />
                  <span className="flex items-center gap-1 mt-1.5 px-0.5">
                    <span className={`text-xs truncate ${locked ? 'text-crystal-muted' : 'text-crystal-text'}`}>{t.name}</span>
                    {active && <Check size={11} className="text-crystal-accent shrink-0" />}
                    {locked && <Lock size={10} className="text-crystal-muted shrink-0" />}
                  </span>
                </button>
              )
            })}
          </div>
        </div>
      </Section>

      <Section title="Spiel">
        <Field
          label="Arbeitsspeicher für Minecraft"
          hint={systemMb
            ? `Gilt für jede Instanz. Dein PC hat ${(systemMb / 1024).toLocaleString('de-DE', { maximumFractionDigits: 0 })} GB. Mehr als die Hälfte davon bringt meist nichts.`
            : 'Gilt für jede Instanz. Mehr als die Hälfte deines System-RAMs bringt meist nichts.'}
          stacked
        >
          <div className="flex items-center gap-4">
            <input
              type="range"
              min={1024} max={systemMb ? Math.max(2048, Math.floor(systemMb * 0.85 / 512) * 512) : 16384} step={512}
              value={maxRam}
              onChange={e => setMaxRam(Number(e.target.value))}
              onPointerUp={e => api?.setSetting('maxRam', Number((e.target as HTMLInputElement).value))}
              onKeyUp={e => api?.setSetting('maxRam', Number((e.target as HTMLInputElement).value))}
              aria-label="Arbeitsspeicher in MB"
              className="flex-1 accent-crystal-accent cursor-pointer"
            />
            <span className={`w-16 text-right text-[13px] tabular ${systemMb && maxRam > systemMb * 0.6 ? 'text-crystal-warning' : 'text-crystal-text'}`}>
              {(maxRam / 1024).toLocaleString('de-DE', { maximumFractionDigits: 1 })} GB
            </span>
          </div>
        </Field>

        <Field
          label="Speicherort für Spieldateien"
          hint="Neue Instanzen, Java und Downloads landen hier. Bestehende Instanzen bleiben, wo sie sind. Es wird nichts verschoben oder gelöscht."
          stacked
        >
          <div className="flex items-center gap-2">
            <code
              title={dataRoot.current}
              className="flex-1 min-w-0 truncate font-mono text-xs px-2.5 py-2 rounded-md bg-crystal-bg/60 border border-crystal-border text-crystal-text select-text"
            >
              {dataRoot.current || '…'}
            </code>
            <button onClick={pickDataRoot} className="crystal-btn-ghost border border-crystal-border text-crystal-text text-xs">
              Ändern
            </button>
            {dataRoot.current && dataRoot.current !== dataRoot.default && (
              <button onClick={resetDataRoot} className="crystal-btn-ghost text-xs">Zurücksetzen</button>
            )}
          </div>
          <div className="mt-2">
            <button onClick={() => api?.openTrashFolder()} className="text-xs text-crystal-muted hover:text-crystal-text">
              Papierkorb öffnen (entfernte Instanzen)
            </button>
          </div>
        </Field>
      </Section>

      <Section title="Integrationen">
        <Field
          label="Discord-Status"
          hint={!discordConfigured
            ? 'Funktioniert derzeit nicht.'
            : discordConnected
              ? 'Zeigt Freunden auf Discord, dass du Crystal benutzt und welche Instanz läuft.'
              : 'Discord wurde nicht gefunden. Der Status erscheint, sobald Discord läuft.'}
        >
          {discordConfigured && <Switch checked={discordEnabled} onChange={toggleDiscord} label="Discord-Status" />}
        </Field>
      </Section>

      <Section title="Konto">
        <Field label="Rang" hint="Ränge vergibt das Crystal-Team. Selbst setzen lässt sich keiner.">
          {rank === 'member'
            ? <span className="text-[13px] text-crystal-muted">Member</span>
            : <RankBadge rank={rank} size="md" />}
        </Field>
      </Section>

      {rank === 'owner' && <RankManagementSection />}

      {canEditIcon && (
        <Section title="App-Icon" description={`Als ${RANKS[rank].label} kannst du das Icon für alle Launcher-Fenster ändern.`}>
          <div className="p-2 grid grid-cols-2 gap-1">
            {icons.map(icon => (
              <button
                key={icon.id}
                onClick={() => changeIcon(icon.id)}
                aria-pressed={currentIcon === icon.id}
                className={`flex items-center gap-2.5 px-2.5 py-2 rounded-md text-left transition-colors ${
                  currentIcon === icon.id ? 'bg-crystal-panel' : 'hover:bg-crystal-panel/60'
                }`}
              >
                <LogoMark variant={icon.id} size={18} />
                <span className="flex-1 text-xs text-crystal-text">{icon.name}</span>
                {currentIcon === icon.id && <Check size={12} className="text-crystal-accent" />}
              </button>
            ))}
          </div>
        </Section>
      )}

      <Section
        title="Crash-Analyse"
        description="Mit deinem eigenen Anthropic-API-Key erklärt Crystal Abstürze im Logs-Bereich. Der Key bleibt auf diesem PC."
      >
        <div className="p-4 flex gap-2">
          <input
            type="password"
            value={apiKey}
            onChange={e => setApiKey(e.target.value)}
            className="crystal-input flex-1 font-mono text-xs"
            placeholder="sk-ant-…"
            aria-label="Anthropic API-Key"
          />
          <button onClick={saveApiKey} className="crystal-btn-primary text-xs">Speichern</button>
        </div>
        {apiKeySaved && (
          <p className="px-4 py-2.5 text-xs text-crystal-muted flex items-center gap-1.5">
            <Check size={12} className="text-crystal-success" /> Ein Key ist hinterlegt.
          </p>
        )}
      </Section>

      <Section title="Über Crystal">
        <Field label="Launcher"><span className="font-mono text-xs text-crystal-text">{versions.launcher ?? '…'}</span></Field>
        <Field label="Client-Mod"><span className="font-mono text-xs text-crystal-text">{versions.client ?? 'nicht gebündelt'}</span></Field>
        <Field label="Minecraft"><span className="font-mono text-xs text-crystal-text">1.21.11 mit Fabric</span></Field>
      </Section>
    </Page>
  )
}

interface RankGrant {
  username: string
  rank: RankId
  grantedAt: number
  expiresAt?: number
}

const GRANT_DURATIONS: { label: string; ms: number | undefined }[] = [
  { label: 'Dauerhaft', ms: undefined },
  { label: '1 Tag', ms: 24 * 60 * 60 * 1000 },
  { label: '1 Woche', ms: 7 * 24 * 60 * 60 * 1000 },
  { label: '1 Monat', ms: 30 * 24 * 60 * 60 * 1000 },
]

/**
 * Owner-only. Every action here is re-checked in the main process (see
 * ipc.ts); hiding the panel from other ranks is a convenience, not the
 * security boundary.
 */
function RankManagementSection() {
  const [grants, setGrants] = useState<RankGrant[]>([])
  const [username, setUsername] = useState('')
  const [pickedRank, setPickedRank] = useState<RankId>('crystal_plus')
  const [pickedDuration, setPickedDuration] = useState<number | undefined>(undefined)
  const [saving, setSaving] = useState(false)
  const [hasToken, setHasToken] = useState(false)
  const [tokenInput, setTokenInput] = useState('')

  function refresh() {
    api?.listRankGrants().then((list: RankGrant[]) => setGrants(list || []))
    api?.hasRankToken().then((v: boolean) => setHasToken(!!v))
  }

  useEffect(refresh, [])

  async function saveToken() {
    const ok = await api?.setRankToken(tokenInput.trim())
    if (ok) {
      setTokenInput('')
      refresh()
    }
  }

  async function publishNow() {
    const result = await api?.publishRanks()
    notify(result?.ok
      ? { type: 'success', message: 'Ränge veröffentlicht' }
      : { type: 'error', title: 'Veröffentlichen fehlgeschlagen', message: result?.error || 'Unbekannter Fehler' })
  }

  async function grant() {
    if (!username.trim()) return
    setSaving(true)
    const ok = await api?.grantRank(username.trim(), pickedRank, pickedDuration)
    setSaving(false)
    if (ok) {
      notify({ type: 'success', message: `${RANKS[pickedRank].label} an ${username.trim()} vergeben` })
      setUsername('')
      refresh()
    }
  }

  async function revoke(name: string) {
    await api?.revokeRankGrant(name)
    refresh()
  }

  const grantableRanks = RANK_ORDER.filter(r => r !== 'owner')

  return (
    <Section
      title="Ränge verwalten"
      description={hasToken
        ? 'Vergebene Ränge werden veröffentlicht und kommen innerhalb weniger Minuten auf den Geräten der Spieler an.'
        : 'Ohne GitHub-Token gilt ein Rang nur auf diesem PC.'}
      actions={hasToken && (
        <button onClick={publishNow} className="text-xs text-crystal-muted hover:text-crystal-text">Erneut veröffentlichen</button>
      )}
    >
      {!hasToken && (
        <div className="p-4 flex gap-2">
          <input
            type="password"
            value={tokenInput}
            onChange={e => setTokenInput(e.target.value)}
            placeholder="GitHub-Token mit repo-Rechten"
            aria-label="GitHub-Token"
            className="crystal-input flex-1 text-xs font-mono"
          />
          <button onClick={saveToken} disabled={!tokenInput.trim()} className="crystal-btn-primary text-xs disabled:opacity-50">
            Speichern
          </button>
        </div>
      )}

      <div className="p-4 grid grid-cols-[minmax(0,1fr)_auto_auto_auto] gap-2">
        <input
          value={username}
          onChange={e => setUsername(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && grant()}
          placeholder="Spielername"
          aria-label="Spielername"
          className="crystal-input text-[13px]"
        />
        <select value={pickedRank} onChange={e => setPickedRank(e.target.value as RankId)} className="crystal-input text-[13px]" aria-label="Rang">
          {grantableRanks.map(r => <option key={r} value={r}>{RANKS[r].label}</option>)}
        </select>
        <select
          value={pickedDuration ?? ''}
          onChange={e => setPickedDuration(e.target.value ? Number(e.target.value) : undefined)}
          className="crystal-input text-[13px]"
          aria-label="Dauer"
        >
          {GRANT_DURATIONS.map(d => <option key={d.label} value={d.ms ?? ''}>{d.label}</option>)}
        </select>
        <button onClick={grant} disabled={saving || !username.trim()} className="crystal-btn-primary text-[13px] disabled:opacity-50">
          Vergeben
        </button>
      </div>

      {grants.length === 0 ? (
        <p className="px-4 py-3 text-xs text-crystal-muted">Noch keine Ränge vergeben.</p>
      ) : (
        grants.map(g => (
          <div key={g.username} className="flex items-center gap-3 px-4 py-2.5">
            <RankBadge rank={g.rank} size="sm" />
            <span className="flex-1 min-w-0 text-[13px] text-crystal-text truncate">{g.username}</span>
            <span className="text-xs text-crystal-muted tabular">
              {g.expiresAt ? `bis ${new Date(g.expiresAt).toLocaleDateString('de-DE')}` : 'dauerhaft'}
            </span>
            <button onClick={() => revoke(g.username)} className="text-xs text-crystal-muted hover:text-crystal-danger">
              Entziehen
            </button>
          </div>
        ))
      )}
    </Section>
  )
}
