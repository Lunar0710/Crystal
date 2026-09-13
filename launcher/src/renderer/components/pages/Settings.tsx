import React, { useState, useEffect } from 'react'
import { Settings as SettingsIcon, Save, Check, Lock } from 'lucide-react'
import { notify } from '../../store/notificationStore'
import { useThemeStore } from '../../store/themeStore'
import { themes } from '../../theme/themes'
import { RANKS, RankId, RANK_ORDER, meetsRank, lockLabel, canUseTheme } from '../../data/ranks'
import { RankBadge } from '../ui/RankBadge'
import { LogoMark, LogoVariantId } from '../../theme/logoVariants'

const api = (window as any).crystal
const STAFF_RANKS: RankId[] = ['owner', 'co_owner', 'admin', 'staff', 'developer']

export function Settings() {
  const [language, setLanguage] = useState('de')
  const { theme, setTheme } = useThemeStore()
  const [animations, setAnimations] = useState(true)
  const [notifications, setNotifications] = useState(true)
  const [closeBehavior, setCloseBehavior] = useState('close')
  const [rank, setRankState] = useState<RankId>('member')
  const [icons, setIcons] = useState<{ id: LogoVariantId; name: string }[]>([])
  const [currentIcon, setCurrentIcon] = useState<LogoVariantId>('facet-hex')
  const [apiKey, setApiKey] = useState('')
  const [apiKeySaved, setApiKeySaved] = useState(false)
  const [maxRam, setMaxRam] = useState(4096)
  const [discordEnabled, setDiscordEnabled] = useState(true)
  const [discordConnected, setDiscordConnected] = useState(false)
  const [versions, setVersions] = useState<{ launcher?: string; client?: string | null }>({})
  const [dataRoot, setDataRoot] = useState<{ current?: string; default?: string }>({})

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

  useEffect(() => {
    api?.getDataRoot().then((r: { current: string; default: string }) => r && setDataRoot(r))
    api?.getVersionInfo().then((v: { launcher: string; client: string | null }) => v && setVersions(v))
    api?.isDiscordEnabled().then((v: boolean) => setDiscordEnabled(!!v))
    api?.isDiscordConnected().then((v: boolean) => setDiscordConnected(!!v))
    api?.getRank().then(setRankState)
    api?.listIcons().then(setIcons)
    api?.getCurrentIcon().then((id: LogoVariantId) => id && setCurrentIcon(id))
    api?.getClaudeApiKey().then((k: string) => { setApiKey(k || ''); setApiKeySaved(!!k) })
    api?.getSetting('maxRam').then((v: number | undefined) => v && setMaxRam(v))
  }, [])

  async function saveApiKey() {
    await api?.setClaudeApiKey(apiKey.trim())
    setApiKeySaved(!!apiKey.trim())
    notify({ type: 'success', message: apiKey.trim() ? 'API-Key gespeichert' : 'API-Key entfernt' })
  }

  const canEditIcon = STAFF_RANKS.includes(rank)

  async function changeIcon(id: LogoVariantId) {
    const ok = await api?.setCurrentIcon(id)
    if (ok) {
      setCurrentIcon(id)
      notify({ type: 'success', message: 'App-Icon aktualisiert' })
    } else {
      notify({ type: 'error', message: 'Dafür brauchst du Owner/Co-Owner/Admin/Staff/Developer' })
    }
  }

  function save() {
    notify({ type: 'success', message: 'Settings saved' })
  }

  return (
    <div className="p-6 max-w-xl space-y-4">
      <div className="flex items-center gap-2 mb-2">
        <SettingsIcon size={20} className="text-crystal-accent" />
        <h1 className="text-xl font-bold text-crystal-text">Settings</h1>
      </div>

      {/* Launcher */}
      <Section title="Launcher">
        <Row label="Language">
          <select value={language} onChange={e => setLanguage(e.target.value)} className="crystal-input">
            <option value="de">Deutsch</option>
            <option value="en">English</option>
          </select>
        </Row>
        <div>
          <span className="text-crystal-muted text-sm block mb-2">Theme</span>
          <div className="grid grid-cols-3 gap-2">
            {themes.map(t => {
              const locked = !canUseTheme(rank, t.requiredRank)
              return (
              <button
                key={t.id}
                onClick={() => {
                  if (locked) {
                    notify({ type: 'info', message: `"${t.name}" ist ${lockLabel(t.requiredRank!)} vorbehalten` })
                    return
                  }
                  setTheme(t.id)
                }}
                className={`relative rounded-lg overflow-hidden border-2 transition-all ${
                  theme === t.id ? 'border-crystal-accent shadow-glow' : 'border-crystal-border hover:border-crystal-accent/40'
                } ${locked ? 'opacity-55 cursor-not-allowed' : ''}`}
              >
                <div className="h-10" style={{ background: `linear-gradient(135deg, ${t.preview[0]}, ${t.preview[1]})` }} />
                <div className="px-2 py-1.5 bg-crystal-panel">
                  <p className="text-crystal-text text-xs font-medium text-left truncate">{t.name}</p>
                  {locked && (
                    <p className="text-crystal-muted text-[10px] flex items-center gap-0.5">
                      <Lock size={8} /> {lockLabel(t.requiredRank!)}
                    </p>
                  )}
                </div>
                {theme === t.id && !locked && (
                  <div className="absolute top-1 right-1 w-4 h-4 rounded-full bg-crystal-accent flex items-center justify-center">
                    <Check size={10} className="text-white" />
                  </div>
                )}
              </button>
              )})}
          </div>
        </div>
        {/* RAM is set once here and used by every instance launch. */}
        <div>
          <label className="block text-crystal-muted text-sm mb-1.5">
            Max RAM für Minecraft — <span className="text-crystal-accent">{maxRam} MB</span>
          </label>
          <input
            type="range"
            min={1024} max={16384} step={512}
            value={maxRam}
            onChange={e => setMaxRam(Number(e.target.value))}
            onMouseUp={e => api?.setSetting('maxRam', Number((e.target as HTMLInputElement).value))}
            className="w-full accent-crystal-accent cursor-pointer"
          />
          <div className="flex justify-between text-xs text-crystal-muted mt-1">
            <span>1 GB</span><span>16 GB</span>
          </div>
        </div>
        <div>
          <span className="block text-crystal-muted text-sm mb-1.5">Speicherort für Spieldateien</span>
          <div className="flex items-center gap-2">
            <code className="flex-1 min-w-0 truncate text-xs px-2 py-1.5 rounded bg-crystal-bg border border-crystal-border text-crystal-text" title={dataRoot.current}>
              {dataRoot.current || '…'}
            </code>
            <button onClick={pickDataRoot} className="crystal-btn-ghost text-xs px-3 py-1.5 border border-crystal-border rounded-lg shrink-0">
              Ändern
            </button>
            {dataRoot.current && dataRoot.current !== dataRoot.default && (
              <button onClick={resetDataRoot} className="text-xs text-crystal-muted hover:text-crystal-text shrink-0">
                Standard
              </button>
            )}
          </div>
          <p className="text-crystal-muted text-xs mt-1">
            Neue Instanzen, Java und Downloads landen dort. Bestehende Instanzen bleiben, wo sie sind — es wird nichts verschoben oder gelöscht.
          </p>
        </div>
        <Row label="Animations">
          <Toggle value={animations} onChange={setAnimations} />
        </Row>
        <Row label="Notifications">
          <Toggle value={notifications} onChange={setNotifications} />
        </Row>
        <Row label="Close button behavior">
          <select value={closeBehavior} onChange={e => setCloseBehavior(e.target.value)} className="crystal-input">
            <option value="close">Close launcher</option>
            <option value="minimize">Minimize to tray</option>
          </select>
        </Row>
        <Row label={`Discord Status${discordConnected ? '' : ' (Discord nicht erkannt)'}`}>
          <Toggle
            value={discordEnabled}
            onChange={async v => {
              const next = await api?.setDiscordEnabled(v)
              setDiscordEnabled(!!next)
              api?.isDiscordConnected().then((c: boolean) => setDiscordConnected(!!c))
            }}
          />
        </Row>
      </Section>

      {/* Account / Rank — read-only: ranks are granted, not self-assigned */}
      <Section title="Account">
        <Row label="Rang">
          {rank === 'member'
            ? <span className="text-crystal-text text-sm">Member</span>
            : <RankBadge rank={rank} size="md" />}
        </Row>
        <p className="text-crystal-muted text-xs">
          Jeder startet als Member. Ränge wie Crystal+, Staff oder Owner werden vom Crystal-Team
          vergeben und lassen sich nicht selbst setzen.
        </p>
      </Section>

      {/* Rank management — owner only, server-side re-checked on every call */}
      {rank === 'owner' && <RankManagementSection />}

      {/* Branding — staff-tier ranks only */}
      {canEditIcon && (
        <Section title="Branding">
          <div className="flex items-center gap-2 p-2.5 rounded-lg bg-crystal-accent/10 border border-crystal-accent/25">
            <RankBadge rank={rank} size="md" />
            <p className="text-crystal-muted text-xs">
              Als {RANKS[rank].label} kannst du das App-Icon für alle Launcher-Fenster ändern.
            </p>
          </div>
          <div>
            <span className="text-crystal-muted text-sm block mb-2">App-Icon</span>
            <div className="grid grid-cols-2 gap-2">
              {icons.map(icon => (
                <button
                  key={icon.id}
                  onClick={() => changeIcon(icon.id)}
                  className={`flex items-center gap-2 px-3 py-2 rounded-lg border transition-all ${
                    currentIcon === icon.id ? 'border-crystal-accent bg-crystal-panel shadow-glow' : 'border-crystal-border hover:border-crystal-accent/40'
                  }`}
                >
                  <div className="w-6 h-6 flex items-center justify-center shrink-0">
                    <LogoMark variant={icon.id} size={20} />
                  </div>
                  <span className="text-crystal-text text-xs text-left flex-1">{icon.name}</span>
                  {currentIcon === icon.id && <Check size={12} className="text-crystal-accent shrink-0" />}
                </button>
              ))}
            </div>
          </div>
        </Section>
      )}
      {!canEditIcon && rank !== 'member' && (
        <Section title="Branding">
          <div className="flex items-center gap-2 p-2.5 rounded-lg bg-crystal-border/40">
            <Lock size={14} className="text-crystal-muted shrink-0" />
            <p className="text-crystal-muted text-xs">App-Icon ändern ist Owner, Co-Owner, Admin, Staff und Developer vorbehalten.</p>
          </div>
        </Section>
      )}

      {/* Crash analysis */}
      <Section title="Crash-Analyse">
        <p className="text-crystal-muted text-xs">
          Dein eigener <a href="https://console.anthropic.com/settings/keys" className="text-crystal-accent underline">Anthropic API-Key</a> — wird nur lokal gespeichert, nur für die Crash-Analyse in den Logs verwendet.
        </p>
        <div className="flex gap-2">
          <input
            type="password"
            value={apiKey}
            onChange={e => setApiKey(e.target.value)}
            className="crystal-input flex-1 font-mono text-xs"
            placeholder="sk-ant-..."
          />
          <button onClick={saveApiKey} className="crystal-btn-primary text-sm px-3">Speichern</button>
        </div>
        {apiKeySaved && <p className="text-crystal-success text-xs flex items-center gap-1"><Check size={11} /> Key hinterlegt</p>}
      </Section>

      {/* About */}
      <Section title="About">
        <div className="text-crystal-muted text-xs space-y-1">
          <p>Crystal Launcher <span className="text-crystal-accent font-mono">v{versions.launcher || '—'}</span></p>
          <p>Crystal Client <span className="text-crystal-accent font-mono">v{versions.client || '—'}</span></p>
          <p>Minecraft <span className="text-crystal-accent font-mono">1.21.11</span> · Fabric</p>
        </div>
      </Section>

      <button onClick={save} className="crystal-btn-primary flex items-center gap-2 text-sm">
        <Save size={14} /> Save Settings
      </button>
    </div>
  )
}

interface RankGrant {
  username: string
  rank: RankId
  grantedAt: number
  expiresAt?: number
}

const GRANT_DURATIONS: { label: string; ms: number | undefined }[] = [
  { label: 'Permanent', ms: undefined },
  { label: '1 Tag', ms: 24 * 60 * 60 * 1000 },
  { label: '1 Woche', ms: 7 * 24 * 60 * 60 * 1000 },
  { label: '1 Monat', ms: 30 * 24 * 60 * 60 * 1000 },
]

/**
 * Owner-only. Every action here re-checks the caller's own rank server-side
 * (see ipc.ts) — this panel being hidden from non-owners is a convenience,
 * not the actual security boundary.
 *
 * Important limitation, shown in the panel itself: a grant only applies when
 * that username logs in on THIS device. There's no Crystal account server, so
 * it can't push a rank to a friend's own separate install.
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
    alert(result?.ok ? 'Ränge veröffentlicht.' : `Fehlgeschlagen: ${result?.error || 'Unbekannter Fehler'}`)
  }

  async function grant() {
    if (!username.trim()) return
    setSaving(true)
    const ok = await api?.grantRank(username.trim(), pickedRank, pickedDuration)
    setSaving(false)
    if (ok) {
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
    <Section title="Ränge verwalten">
      {hasToken ? (
        <p className="text-crystal-muted text-xs">
          Ränge werden nach GitHub veröffentlicht und gelten dadurch auf allen Geräten — der
          Launcher des Spielers liest sie beim Start. Bis zu ~5 Minuten Verzögerung.
        </p>
      ) : (
        <div className="space-y-2">
          <p className="text-crystal-muted text-xs">
            Ohne GitHub-Token gilt ein Rang nur auf diesem Gerät. Trag deinen Token ein, damit
            vergebene Ränge auch bei anderen Spielern ankommen. Der Token bleibt nur lokal auf
            diesem PC und ist in keinem Installer enthalten.
          </p>
          <div className="flex gap-2">
            <input
              type="password"
              value={tokenInput}
              onChange={e => setTokenInput(e.target.value)}
              placeholder="GitHub Token (repo-Rechte)"
              className="crystal-input flex-1 text-sm"
            />
            <button onClick={saveToken} disabled={!tokenInput.trim()} className="crystal-btn-primary text-sm px-3 disabled:opacity-60">
              Speichern
            </button>
          </div>
        </div>
      )}

      <div className="flex gap-2">
        <input
          value={username}
          onChange={e => setUsername(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && grant()}
          placeholder="Minecraft-Username"
          className="crystal-input flex-1 text-sm"
        />
        <select
          value={pickedRank}
          onChange={e => setPickedRank(e.target.value as RankId)}
          className="crystal-input text-sm"
        >
          {grantableRanks.map(r => (
            <option key={r} value={r}>{RANKS[r].label}</option>
          ))}
        </select>
        <select
          value={pickedDuration ?? ''}
          onChange={e => setPickedDuration(e.target.value ? Number(e.target.value) : undefined)}
          className="crystal-input text-sm"
        >
          {GRANT_DURATIONS.map(d => (
            <option key={d.label} value={d.ms ?? ''}>{d.label}</option>
          ))}
        </select>
        <button
          onClick={grant}
          disabled={saving || !username.trim()}
          className="crystal-btn-primary text-sm px-3 disabled:opacity-60"
        >
          Vergeben
        </button>
      </div>

      {hasToken && (
        <button onClick={publishNow} className="crystal-btn-ghost text-xs border border-crystal-border rounded-lg px-3 py-1.5 w-fit">
          Jetzt neu veröffentlichen
        </button>
      )}

      {grants.length === 0 ? (
        <p className="text-crystal-muted text-xs">Noch keine Ränge vergeben.</p>
      ) : (
        <div className="space-y-1.5">
          {grants.map(g => (
            <div key={g.username} className="flex items-center justify-between gap-2 p-2 rounded-lg bg-crystal-panel">
              <div className="flex items-center gap-2 min-w-0">
                <RankBadge rank={g.rank} size="sm" />
                <span className="text-crystal-text text-sm truncate">{g.username}</span>
                {g.expiresAt && (
                  <span className="text-crystal-muted text-[11px] shrink-0">
                    bis {new Date(g.expiresAt).toLocaleDateString('de-DE')}
                  </span>
                )}
              </div>
              <button
                onClick={() => revoke(g.username)}
                className="text-crystal-muted hover:text-crystal-danger text-xs shrink-0"
              >
                Entfernen
              </button>
            </div>
          ))}
        </div>
      )}
    </Section>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="crystal-card p-4 space-y-3">
      <h2 className="text-crystal-text font-semibold text-sm border-b border-crystal-border pb-2">{title}</h2>
      {children}
    </div>
  )
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-4">
      <span className="text-crystal-muted text-sm">{label}</span>
      {children}
    </div>
  )
}

function Toggle({ value, onChange }: { value: boolean; onChange: (v: boolean) => void }) {
  return (
    <button
      onClick={() => onChange(!value)}
      className={`w-10 h-5 rounded-full transition-colors relative ${value ? 'bg-crystal-accent' : 'bg-crystal-border'}`}
    >
      <div className={`absolute top-0.5 w-4 h-4 rounded-full bg-white shadow transition-transform ${value ? 'translate-x-5' : 'translate-x-0.5'}`} />
    </button>
  )
}
