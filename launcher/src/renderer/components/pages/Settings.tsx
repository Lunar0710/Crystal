import React, { useState, useEffect } from 'react'
import { Check, Lock, Upload, RotateCcw } from 'lucide-react'
import { notify } from '../../store/notificationStore'
import { useThemeStore } from '../../store/themeStore'
import { themes } from '../../theme/themes'
import { RANKS, RankId, RANK_ORDER, lockLabel, canUseTheme, hasPerks } from '../../data/ranks'
import { BUILTIN_CAPES } from '../../data/capes'
import { COSMETICS_BY_SLOT } from '../../data/cosmetics'
import { RankBadge } from '../ui/RankBadge'
import { LogoMark, LogoVariantId } from '../../theme/logoVariants'
import { Page, PageHeader, Section, Field, Switch } from '../ui/Page'
import { LOGO_CHANGED } from '../ui/TitleBar'
import { displayVersion } from '../../data/displayVersion'

const api = (window as any).crystal

export function Settings() {
  const { theme, setTheme } = useThemeStore()
  const [rank, setRankState] = useState<RankId>('member')
  const [icons, setIcons] = useState<{ id: LogoVariantId; name: string }[]>([])
  const [currentIcon, setCurrentIcon] = useState<LogoVariantId>('facet-hex')
  const [apiKey, setApiKey] = useState('')
  const [crystalServer, setCrystalServer] = useState('')
  const [apiKeySaved, setApiKeySaved] = useState(false)
  const [maxRam, setMaxRam] = useState(4096)
  const [discordEnabled, setDiscordEnabled] = useState(true)
  const [discordConnected, setDiscordConnected] = useState(false)
  const [discordConfigured, setDiscordConfigured] = useState(true)
  const [versions, setVersions] = useState<{ launcher?: string; client?: string | null }>({})
  const [crystalVersions, setCrystalVersions] = useState<string[] | null>(null)
  const [dataRoot, setDataRoot] = useState<{ current?: string; default?: string }>({})
  const [systemMb, setSystemMb] = useState<number | null>(null)
  const [minimizeOnLaunch, setMinimizeOnLaunch] = useState(true)
  const [autoPerformancePack, setAutoPerformancePack] = useState(true)

  useEffect(() => {
    api?.getSetting('minimizeOnLaunch').then((v: boolean | undefined) => setMinimizeOnLaunch(v !== false))
    api?.getSetting('autoPerformancePack').then((v: boolean | undefined) => setAutoPerformancePack(v !== false))
    api?.getDataRoot().then((r: { current: string; default: string }) => r && setDataRoot(r))
    api?.getVersionInfo().then((v: { launcher: string; client: string | null }) => v && setVersions(v))
    api?.getVersionOptions?.().then((list: { id: string; crystal: boolean }[]) =>
      setCrystalVersions((list || []).filter(o => o.crystal).map(o => o.id)))
    api?.isDiscordEnabled().then((v: boolean) => setDiscordEnabled(!!v))
    api?.isDiscordConnected().then((v: boolean) => setDiscordConnected(!!v))
    api?.isDiscordConfigured().then((v: boolean) => setDiscordConfigured(!!v))
    api?.getRank().then(setRankState)
    api?.listIcons().then(setIcons)
    api?.getCurrentIcon().then((id: LogoVariantId) => id && setCurrentIcon(id))
    api?.getClaudeApiKey().then((k: string) => { setApiKey(k || ''); setApiKeySaved(!!k) })
    api?.getSetting('crystalServer').then((v: string | undefined) => setCrystalServer(v || ''))
    Promise.all([api?.getSetting('maxRam'), api?.getSystemMemory()]).then(([saved, mem]: [number | undefined, { totalMb: number; suggestedMb: number } | undefined]) => {
      if (mem) setSystemMb(mem.totalMb)
      if (saved) setMaxRam(saved)
      else if (mem) setMaxRam(mem.suggestedMb)
    })
  }, [])

  /** Same rule as the main process (CrystalServer.ts): ws:// or wss://, host, port, path. */
  function saveCrystalServer() {
    const value = crystalServer.trim()
    if (value && !/^wss?:\/\/[A-Za-z0-9.-]+(:\d{1,5})?(\/[A-Za-z0-9._~/-]*)?$/.test(value)) {
      notify({ type: 'error', title: 'Crystal-Server', message: 'Die Adresse muss mit ws:// oder wss:// beginnen, z. B. wss://crystal.example.com' })
      return
    }
    api?.setSetting('crystalServer', value)
    notify({ type: 'success', title: 'Crystal-Server', message: value ? 'Gilt ab dem nächsten Start von Minecraft.' : 'Zurück auf den Standard.' })
  }

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

  const [logo, setLogo] = useState<string | null>(null)
  useEffect(() => { api?.getLogo?.().then((l: string | null) => setLogo(l ?? null)) }, [rank])

  async function pickLogo() {
    const result = await api?.pickLogo()
    if (result?.ok) {
      setLogo(result.logo ?? null)
      window.dispatchEvent(new Event(LOGO_CHANGED))
      notify({ type: 'success', message: 'Logo aktualisiert' })
    } else if (result?.error) {
      notify({ type: 'error', message: result.error })
    }
  }

  async function resetLogo() {
    await api?.resetLogo()
    setLogo(null)
    window.dispatchEvent(new Event(LOGO_CHANGED))
  }

  async function changeIcon(id: LogoVariantId) {
    const ok = await api?.setCurrentIcon(id)
    if (ok) {
      setCurrentIcon(id)
      notify({ type: 'success', message: 'App-Icon aktualisiert' })
    } else {
      notify({ type: 'error', message: 'Dafür brauchst du einen Rang (ab Crystal+).' })
    }
  }

  const canEditIcon = !!rank && rank !== 'member'

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
          label="Launcher beim Spielstart minimieren"
          hint="Der Launcher braucht im Hintergrund kaum Leistung. Das hilft vor allem auf schwächeren PCs. Nach dem Spiel kommt er wieder nach vorne."
        >
          <Switch
            checked={minimizeOnLaunch}
            onChange={v => { setMinimizeOnLaunch(v); api?.setSetting('minimizeOnLaunch', v) }}
            label="Launcher beim Spielstart minimieren"
          />
        </Field>

        <Field
          label="Performance-Mods automatisch installieren"
          hint="Beim ersten Start einer Crystal-Instanz kommen Sodium, Lithium, FerriteCore, EntityCulling und ImmediatelyFast dazu. Das bringt vor allem bei hoher Sichtweite und unter Tage viel mehr FPS. Mods, die du danach entfernst, bleiben entfernt."
        >
          <Switch
            checked={autoPerformancePack}
            onChange={v => { setAutoPerformancePack(v); api?.setSetting('autoPerformancePack', v) }}
            label="Performance-Mods automatisch installieren"
          />
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
        <Field
          label="Crystal-Server"
          hint="Damit andere Crystal-Spieler deine Emotes und Cosmetics sehen und du ihre. Leer lassen für den Standard."
        >
          <div className="flex gap-2">
            <input
              value={crystalServer}
              onChange={e => setCrystalServer(e.target.value)}
              className="crystal-input w-56 font-mono text-xs"
              placeholder="wss://…"
              aria-label="Crystal-Server-Adresse"
            />
            <button onClick={saveCrystalServer} className="crystal-btn-primary text-xs">Speichern</button>
          </div>
        </Field>
      </Section>

      <Section title="Konto">
        <Field label="Rang" hint="Ränge vergibt das Crystal-Team. Selbst setzen lässt sich keiner.">
          {rank === 'member'
            ? <span className="text-[13px] text-crystal-muted">Member</span>
            : <RankBadge rank={rank} size="md" />}
        </Field>
      </Section>

      <CrystalPlusSection unlocked={hasPerks(rank)} />

      {rank === 'owner' && <RankManagementSection />}

      {canEditIcon && (
        <Section title="Logo" description={`Als ${RANKS[rank].label} kannst du das Logo oben links und das App-Icon ändern.`}>
          <div className="p-3 flex items-center gap-3 border-b border-crystal-border">
            <div className="h-10 w-28 rounded-md bg-crystal-panel flex items-center justify-center overflow-hidden">
              {logo
                ? <img src={logo} alt="Eigenes Logo" className="max-h-8 max-w-[100px] object-contain" />
                : <span className="text-[11px] text-crystal-muted">Standard</span>}
            </div>
            <button onClick={pickLogo} className="crystal-btn-primary text-xs py-1.5"><Upload size={12} /> Eigenes Logo</button>
            {logo && (
              <button onClick={resetLogo} className="crystal-btn-ghost border border-crystal-border text-crystal-text text-xs py-1.5">
                <RotateCcw size={12} /> Zurücksetzen
              </button>
            )}
          </div>
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
        <Field label="Launcher"><span className="font-mono text-xs text-crystal-text">{versions.launcher ? displayVersion(versions.launcher) : '…'}</span></Field>
        <Field label="Client-Mod"><span className="font-mono text-xs text-crystal-text">{versions.client ? displayVersion(versions.client) : 'nicht gebündelt'}</span></Field>
        <Field label="Minecraft"><span className="font-mono text-xs text-crystal-text">1.8.9 bis 26.2</span></Field>
        <Field label="Crystal-Module auf">
          <span className="font-mono text-xs text-crystal-text">
            {crystalVersions === null ? '…' : crystalVersions.length ? crystalVersions.join(', ') : 'keiner Version'}
          </span>
        </Field>
      </Section>
    </Page>
  )
}

interface RankGrant {
  username: string
  rank: RankId
  grantedAt: number
  expiresAt?: number
  /** Tester, on top of the rank: may try test features such as the auto builder. */
  tester?: boolean
}

const GRANT_DURATIONS: { label: string; ms: number | undefined }[] = [
  { label: 'Dauerhaft', ms: undefined },
  { label: '1 Tag', ms: 24 * 60 * 60 * 1000 },
  { label: '1 Woche', ms: 7 * 24 * 60 * 60 * 1000 },
  { label: '1 Monat', ms: 30 * 24 * 60 * 60 * 1000 },
]

/** What Crystal+ actually unlocks. Counts come from the real data, so the list can't drift from what ships. */
function CrystalPlusSection({ unlocked }: { unlocked: boolean }) {
  const plusThemes = themes.filter(t => t.requiredRank === 'crystal_plus').length
  const plusCapes = BUILTIN_CAPES.filter(c => c.requiredRank === 'crystal_plus').length
  const plusCosmetics = Object.values(COSMETICS_BY_SLOT).flat().filter(c => c.requiredRank === 'crystal_plus').length

  const perks: { title: string; detail: string }[] = [
    { title: `${plusThemes} Themes`, detail: 'Für Launcher und Client-Menü.' },
    { title: `${plusCapes} Capes`, detail: 'Handgezeichnet, im Spiel sichtbar.' },
    { title: `${plusCosmetics} Cosmetics`, detail: 'Hüte, Masken, Flügel und mehr, auch im Spiel.' },
    { title: 'HUD-Stile', detail: 'Glass, Neon, Pill, Gradient, Split und Rainbow als Hintergrund für jedes HUD-Modul.' },
    { title: 'Emotes', detail: 'Winken, Jubeln, Tanzen und mehr über ein Rad auf einer Taste (vorerst nur für dich sichtbar).' },
    { title: 'Chroma-Text', detail: 'Farbverlauf für HUD-Module, der langsam durchläuft.' },
    { title: 'Crosshair-Formen', detail: 'Gap Cross, Kreis, X und Klammern, auf Wunsch in Chroma.' },
    { title: 'Hauptmenü', detail: 'Crystal+-Abzeichen neben deinem Namen und ein Logo mit wechselnder Farbe.' },
  ]

  return (
    <Section
      title="Crystal+"
      description={unlocked
        ? 'Alles hier ist für dich freigeschaltet. Die Extras im Spiel stellst du in den Modul-Einstellungen ein (Rechts-Shift).'
        : 'Crystal+ ist rein kosmetisch: Capes, Cosmetics, Emotes, Themes und HUD-Extras. Keine Spielhilfen.'}
    >
      <ul className="divide-y divide-crystal-border">
        {perks.map(p => (
          <li key={p.title} className="flex items-start gap-3 px-4 py-2.5">
            {unlocked
              ? <Check size={14} className="mt-0.5 shrink-0 text-crystal-accent" />
              : <Lock size={13} className="mt-0.5 shrink-0 text-crystal-muted" />}
            <span className="min-w-0">
              <span className="block text-[13px] text-crystal-text">{p.title}</span>
              <span className="block text-xs text-crystal-muted">{p.detail}</span>
            </span>
          </li>
        ))}
      </ul>
    </Section>
  )
}

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
    warnIfNotPublished(ok)
    if (ok) {
      notify({ type: 'success', message: `${RANKS[pickedRank].label} an ${username.trim()} vergeben` })
      setUsername('')
      refresh()
    }
  }

  /** Saved on this PC, but GitHub refused it: the others won't see it until the token can write. */
  function warnIfNotPublished(result: unknown) {
    const error = (result as { publishError?: string } | null)?.publishError
    if (error) notify({ type: 'error', title: 'Nur auf diesem PC gespeichert', message: `Veröffentlichen fehlgeschlagen: ${error}. Der GitHub-Token braucht "Contents: Read and write".` })
  }

  async function setTester(name: string, tester: boolean) {
    setSaving(true)
    const ok = await api?.setRankTester(name, tester)
    setSaving(false)
    warnIfNotPublished(ok)
    if (ok) {
      notify({ type: 'success', message: tester ? `${name} ist jetzt Tester` : `${name} ist kein Tester mehr` })
      if (name === username.trim()) setUsername('')
      refresh()
    }
  }

  async function revoke(name: string) {
    warnIfNotPublished(await api?.revokeRankGrant(name))
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

      <div className="p-4 grid grid-cols-[minmax(0,1fr)_auto_auto_auto_auto] gap-2">
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
        <button
          onClick={() => setTester(username.trim(), true)}
          disabled={saving || !username.trim()}
          title="Darf Test-Funktionen wie den Auto-Builder ausprobieren, zusätzlich zu seinem Rang"
          className="crystal-btn-ghost text-[13px] disabled:opacity-50"
        >
          Als Tester
        </button>
      </div>

      {grants.length === 0 ? (
        <p className="px-4 py-3 text-xs text-crystal-muted">Noch keine Ränge vergeben.</p>
      ) : (
        grants.map(g => (
          <div key={g.username} className="flex items-center gap-3 px-4 py-2.5">
            <RankBadge rank={g.rank} size="sm" />
            <span className="flex-1 min-w-0 text-[13px] text-crystal-text truncate">{g.username}</span>
            <button
              onClick={() => setTester(g.username, !g.tester)}
              aria-pressed={!!g.tester}
              title={g.tester ? 'Tester entziehen' : 'Zum Tester machen'}
              className={`text-[11px] px-2 py-0.5 rounded-full border transition-colors ${g.tester
                ? 'border-crystal-accent text-crystal-accent'
                : 'border-crystal-border text-crystal-muted hover:text-crystal-text'}`}
            >
              Tester
            </button>
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
