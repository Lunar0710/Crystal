import React, { useState, useEffect, useMemo } from 'react'
import { Check, Upload, Trash2, X, Lock, Search, Sparkles, Smile, GripVertical } from 'lucide-react'
import { Page, PageHeader, EmptyState } from '../ui/Page'
import { OutfitsPanel } from '../ui/OutfitsPanel'
import { notify } from '../../store/notificationStore'
import { BUILTIN_CAPES, CAPE_CATEGORIES, CapeCategory, capeTextureUrl, capePreviewUrl, isCapeTexture, pictureToCapeTexture, canUseCape, capeFrameUrls, capeAnimationStrip } from '../../data/capes'
import { ANIMATION_FRAMES, ANIMATION_FPS } from '../../data/animatedCapes'
import {
  SLOTS, CosmeticSlot, NonCapeSlot, COSMETICS_BY_SLOT, EMPTY_LOADOUT, CosmeticDef, CosmeticVariant, CosmeticVariants,
  EquippedCosmetics, findCosmetic, syncLoadoutToGame, variantOf, EMOTES, MAX_WHEEL_EMOTES,
} from '../../data/cosmetics'
import { thumbnail, cachedThumbnail } from '../../data/cosmeticThumbs'
import { pictureOf } from '../../data/cosmeticPictures'
import { syncCatalogToGame, loadEmoteWheel, loadVariants } from '../../data/gameCatalog'
import { SkinPreview3D } from '../ui/SkinPreview3D'
import { ProfileCardDialog } from '../ui/ProfileCardDialog'
import { RankId, RANKS, meetsRank, lockLabel } from '../../data/ranks'

interface CustomCape {
  id: string
  name: string
  fileName: string
}

type Tab = CosmeticSlot | 'emote'
type Filter = 'all' | 'owned' | 'locked' | 'new'

const FILTERS: { id: Filter; label: string }[] = [
  { id: 'all', label: 'Alle' },
  { id: 'owned', label: 'Verfügbar' },
  { id: 'locked', label: 'Gesperrt' },
  { id: 'new', label: 'Neu' },
]

const NON_CAPE_SLOTS = Object.keys(COSMETICS_BY_SLOT) as NonCapeSlot[]
const SLOT_LABEL = Object.fromEntries(SLOTS.map(s => [s.id, s.label])) as Record<CosmeticSlot, string>

const api = (window as any).crystal

export function Cosmetics() {
  const [tab, setTab] = useState<Tab>('cape')
  const [capeCategory, setCapeCategory] = useState<CapeCategory | 'mine'>('plus')
  const [loadout, setLoadout] = useState<EquippedCosmetics>(EMPTY_LOADOUT)
  const [loadoutLoaded, setLoadoutLoaded] = useState(false)
  const [variants, setVariants] = useState<CosmeticVariants>({})
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState<Filter>('all')
  const [wheel, setWheel] = useState<string[]>([])

  const [customCapes, setCustomCapes] = useState<CustomCape[]>([])
  const [customThumbs, setCustomThumbs] = useState<Record<string, string>>({})
  const [uploading, setUploading] = useState(false)

  const [skinUser, setSkinUser] = useState('')
  const [skinDataUrl, setSkinDataUrl] = useState<string | null>(null)
  const [skinSlim, setSkinSlim] = useState(false)
  const [loadingSkin, setLoadingSkin] = useState(false)
  const [rank, setRank] = useState<RankId>('member')
  const [rankLoaded, setRankLoaded] = useState(false)
  const [showCard, setShowCard] = useState(false)

  useEffect(() => {
    ;(async () => {
      // Things put on in the in-game menu come over first, so the page shows them.
      await api?.adoptGameSelection?.().catch(() => false)
      api?.getRank().then((r: RankId) => { setRank(r); setRankLoaded(true) })
      setVariants(await loadVariants())
      setWheel(await loadEmoteWheel())
      const l: EquippedCosmetics | null = await api?.getLoadout()
      if (l) setLoadout({ ...EMPTY_LOADOUT, ...l })
      setLoadoutLoaded(true)
    })()
    refreshCustom()

    // Start on the logged-in player's own skin so the preview isn't empty.
    api?.getProfile().then((p: { username: string } | null) => {
      if (p?.username) {
        setSkinUser(p.username)
        loadSkin(p.username)
      }
    })
  }, [])

  async function refreshCustom() {
    const list: CustomCape[] = (await api?.listCustomCapes()) || []
    setCustomCapes(list)
    for (const cape of list) {
      const url = await api?.getCapeDataUrl(cape.id)
      if (url) setCustomThumbs(prev => ({ ...prev, [cape.id]: url }))
    }
  }

  async function loadSkin(username: string) {
    if (!username.trim()) return
    setLoadingSkin(true)
    const result = await api?.fetchSkin(username.trim())
    setLoadingSkin(false)

    if (result?.success) {
      setSkinDataUrl(result.dataUrl)
      setSkinSlim(!!result.slim)
    } else {
      setSkinDataUrl(null)
      notify({ type: 'error', message: result?.error || 'Skin konnte nicht geladen werden' })
    }
  }

  async function equip(slotId: CosmeticSlot, id: string | null, required?: RankId) {
    if (id && required && !meetsRank(rank, required)) {
      notify({ type: 'info', message: `Dieses Item ist ${lockLabel(required)} vorbehalten` })
      return
    }
    const next = { ...loadout, [slotId]: loadout[slotId] === id ? null : id }
    setLoadout(next)
    await api?.setLoadout(next)
  }

  async function chooseVariant(def: CosmeticDef, variantId: string) {
    const next = { ...variants, [def.id]: variantId }
    setVariants(next)
    await api?.setSetting('cosmeticVariants', next)
    // Picking a colour of an item you don't wear puts it on.
    if (loadout[def.slot] !== def.id) await equip(def.slot, def.id, def.requiredRank)
  }

  async function takeAllOff() {
    const next = { ...EMPTY_LOADOUT }
    setLoadout(next)
    await api?.setLoadout(next)
  }

  async function uploadCape() {
    setUploading(true)
    const cape = await api?.uploadCape()
    setUploading(false)
    if (cape) {
      // A normal picture (anything not already shaped like a 2:1 cape texture)
      // becomes a detailed HD cape, cropped around its centre.
      const original = await api?.getCapeDataUrl(cape.id)
      if (original) {
        const img = await new Promise<HTMLImageElement | null>(resolve => {
          const el = new Image()
          el.onload = () => resolve(el)
          el.onerror = () => resolve(null)
          el.src = original
        })
        if (img && !isCapeTexture(img.naturalWidth, img.naturalHeight)) {
          await api?.replaceCapeImage(cape.id, pictureToCapeTexture(img))
        }
      }
      notify({ type: 'success', title: cape.name, message: 'Cape hochgeladen' })
      // Awaited: equipping before the thumbnail is loaded leaves equippedCapeUrl
      // null, which the sync effect deliberately skips — the cape would then
      // only reach the game on a later re-render.
      await refreshCustom()
      equip('cape', `custom:${cape.id}`)
    }
  }

  async function removeCustom(id: string, e: React.MouseEvent) {
    e.stopPropagation()
    await api?.removeCape(id)
    if (loadout.cape === `custom:${id}`) equip('cape', null)
    refreshCustom()
  }

  // The picker tile and the 3D model share this exact texture, so what you see
  // in the grid is what lands on the character.
  const equippedCapeUrl = (() => {
    if (!loadout.cape) return null
    if (loadout.cape.startsWith('custom:')) return customThumbs[loadout.cape.slice(7)] ?? null
    const def = BUILTIN_CAPES.find(c => `builtin:${c.id}` === loadout.cape)
    return def ? capeTextureUrl(def) : null
  })()

  // Every built-in cape only ever exists as a canvas data URL inside this
  // renderer process — the in-game client can't reach that, so whenever the
  // equipped cape changes (including on load), push the actual PNG bytes out
  // to a file the Java mod reads. Skipped while a custom cape's thumbnail
  // hasn't loaded yet, so we don't briefly sync "no cape" over a real one.
  const equippedDef = loadout.cape?.startsWith('builtin:') ? BUILTIN_CAPES.find(c => `builtin:${c.id}` === loadout.cape) : undefined
  const animatedDef = equippedDef?.animate ? equippedDef : undefined

  useEffect(() => {
    if (loadout.cape?.startsWith('custom:') && !equippedCapeUrl) return
    // Animated capes go to the game as a strip of frames instead.
    if (animatedDef) {
      api?.syncCapeAnimation(capeAnimationStrip(animatedDef), ANIMATION_FRAMES, ANIMATION_FPS, animatedDef.id)
      return
    }
    // The id lets other Nexora players see a built-in cape; uploaded ones stay private.
    api?.syncEquippedCape(equippedCapeUrl, equippedDef?.id ?? null)
  }, [equippedCapeUrl, animatedDef?.id])

  // The 3D preview plays animated capes at the same speed as the game.
  const [frame, setFrame] = useState(0)
  useEffect(() => {
    if (!animatedDef) return
    const timer = setInterval(() => setFrame(f => (f + 1) % ANIMATION_FRAMES), 1000 / ANIMATION_FPS)
    return () => clearInterval(timer)
  }, [animatedDef?.id])
  const previewCapeUrl = animatedDef ? capeFrameUrls(animatedDef)[frame] ?? equippedCapeUrl : equippedCapeUrl

  // Hats, masks, wings… reach the game the same way: the resolved colours and
  // shapes go to a file the Java client reads. Rank-locked items are flagged so
  // the client hides them again if the rank runs out.
  const nonCapeKey = NON_CAPE_SLOTS.map(s => `${loadout[s]}:${loadout[s] ? variants[loadout[s]!] ?? '' : ''}`).join('|')
  useEffect(() => {
    // Not before the saved loadout has arrived: syncing the empty initial state
    // briefly took everything off in a running game each time the page opened.
    if (loadoutLoaded) syncLoadoutToGame(loadout, variants)
  }, [nonCapeKey, loadoutLoaded])

  // The in-game menu's catalog follows the rank (locks) and the outfits.
  useEffect(() => {
    if (rankLoaded) syncCatalogToGame(rank).catch(() => {})
  }, [rankLoaded, rank])

  // A timed rank can run out while a perk cape is still equipped; take it off
  // then, but only once the real rank has arrived, never on the initial default.
  useEffect(() => {
    if (!rankLoaded || !loadout.cape?.startsWith('builtin:')) return
    const def = BUILTIN_CAPES.find(c => `builtin:${c.id}` === loadout.cape)
    if (def && !canUseCape(rank, def)) equip('cape', null)
  }, [rankLoaded, rank, loadout.cape])

  const canUse = (def: CosmeticDef) => !def.requiredRank || meetsRank(rank, def.requiredRank)
  const passes = (def: CosmeticDef) =>
    filter === 'all' || (filter === 'owned' && canUse(def)) || (filter === 'locked' && !canUse(def)) || (filter === 'new' && !!def.isNew)

  const q = query.trim().toLowerCase()
  // A search looks through every category at once.
  const searchResults = useMemo(() => {
    if (!q) return null
    const items = NON_CAPE_SLOTS.flatMap(s => COSMETICS_BY_SLOT[s]).filter(d => d.name.toLowerCase().includes(q) || SLOT_LABEL[d.slot].toLowerCase().includes(q))
    const capes = BUILTIN_CAPES.filter(c => c.name.toLowerCase().includes(q))
    return { items, capes }
  }, [q])

  const visibleCapes = BUILTIN_CAPES.filter(c => c.category === capeCategory)
    .filter(c => filter === 'all' || (filter === 'owned' ? canUseCape(rank, c) : filter === 'locked' ? !canUseCape(rank, c) : false))

  function equippedLabel(slotId: CosmeticSlot): string | null {
    const value = loadout[slotId]
    if (!value) return null
    if (slotId === 'cape') {
      return value.startsWith('custom:')
        ? customCapes.find(c => `custom:${c.id}` === value)?.name ?? 'Eigenes Cape'
        : BUILTIN_CAPES.find(c => `builtin:${c.id}` === value)?.name ?? null
    }
    const def = findCosmetic(slotId as NonCapeSlot, value)
    if (!def) return null
    const v = variantOf(def, variants)
    return v && def.variants!.length > 1 ? `${def.name} · ${v.name}` : def.name
  }

  // The item in this tab that's worn and has colours to choose from.
  const wornWithVariants = tab !== 'cape' && tab !== 'emote' ? findCosmetic(tab, loadout[tab]) : null

  async function toggleWheel(id: string) {
    let next = wheel.includes(id) ? wheel.filter(e => e !== id) : [...wheel, id]
    if (next.length > MAX_WHEEL_EMOTES) {
      notify({ type: 'info', message: `Höchstens ${MAX_WHEEL_EMOTES} Emotes passen aufs Rad` })
      return
    }
    if (!next.length) next = wheel
    setWheel(next)
    await api?.setSetting('emoteWheel', next)
    await api?.syncEmoteWheel?.(next)
  }

  async function moveWheel(id: string, by: number) {
    const i = wheel.indexOf(id), j = i + by
    if (i < 0 || j < 0 || j >= wheel.length) return
    const next = [...wheel]
    ;[next[i], next[j]] = [next[j], next[i]]
    setWheel(next)
    await api?.setSetting('emoteWheel', next)
    await api?.syncEmoteWheel?.(next)
  }

  const itemTile = (item: CosmeticDef, showSlot = false) => {
    const locked = !canUse(item)
    return (
      <ItemTile
        key={item.id}
        def={item}
        variants={variants}
        selected={loadout[item.slot] === item.id}
        locked={locked ? lockLabel(item.requiredRank!) : undefined}
        slotLabel={showSlot ? SLOT_LABEL[item.slot] : undefined}
        onClick={() => equip(item.slot, item.id, item.requiredRank)}
        onVariant={v => chooseVariant(item, v)}
      />
    )
  }

  const capeTile = (cape: typeof BUILTIN_CAPES[number]) => {
    const id = `builtin:${cape.id}`
    const locked = !canUseCape(rank, cape)
    // A rank cape names its one rank; other locked capes say "Nexora+" or "Team".
    const lockText = cape.exactRank && cape.requiredRank ? RANKS[cape.requiredRank].label : cape.requiredRank ? lockLabel(cape.requiredRank) : ''
    return (
      <Tile
        key={cape.id}
        selected={loadout.cape === id}
        onClick={() => locked
          ? notify({ type: 'info', message: `Dieses Cape ist ${lockText} vorbehalten` })
          : equip('cape', id)}
        label={cape.name}
        background={`url(${capePreviewUrl(cape)}) center/cover`}
        lockedLabel={locked ? lockText : undefined}
        pixelated={!cape.hd}
      />
    )
  }

  return (
    <Page wide>
      <PageHeader
        title="Cosmetics"
        description="Alles, was du hier ausrüstest, siehst du genau so im Spiel, und andere Nexora-Spieler sehen es bei dir. Im Spiel öffnest du dieselbe Auswahl mit der Taste J."
        actions={
          <>
            <button onClick={takeAllOff} className="crystal-btn-ghost text-[13px]">Alles ablegen</button>
            <button onClick={() => setShowCard(true)} className="crystal-btn-ghost text-[13px]">Profil-Karte</button>
          </>
        }
      />

      {showCard && (
        <ProfileCardDialog
          onClose={() => setShowCard(false)}
          data={{
            username: skinUser || 'Spieler',
            rank,
            skinDataUrl,
            slim: skinSlim,
            capeUrl: equippedCapeUrl,
            capeName: equippedLabel('cape'),
          }}
        />
      )}

      <div className="grid grid-cols-1 lg:grid-cols-[300px_minmax(0,1fr)] gap-6 items-start">
        <aside className="lg:sticky lg:top-4 space-y-3">
          <div className="crystal-card overflow-hidden">
            <div className="flex justify-center bg-crystal-panel/60 border-b border-crystal-border pt-2">
              <SkinPreview3D
                skinDataUrl={skinDataUrl}
                slim={skinSlim}
                capeUrl={previewCapeUrl}
                hat={findCosmetic('hat', loadout.hat)}
                bandana={findCosmetic('bandana', loadout.bandana)}
                mask={findCosmetic('mask', loadout.mask)}
                wings={findCosmetic('wings', loadout.wings)}
                backpack={findCosmetic('backpack', loadout.backpack)}
                aura={findCosmetic('aura', loadout.aura)}
                pet={findCosmetic('pet', loadout.pet)}
                variants={variants}
                width={296}
                height={340}
              />
            </div>
            <div className="p-3 space-y-1.5">
              <label htmlFor="skin-user" className="crystal-label">Skin eines Spielers anzeigen</label>
              <div className="flex gap-2">
                <input
                  id="skin-user"
                  value={skinUser}
                  onChange={e => setSkinUser(e.target.value)}
                  onKeyDown={e => e.key === 'Enter' && loadSkin(skinUser)}
                  maxLength={16}
                  className="crystal-input flex-1 min-w-0 text-[13px]"
                />
                <button onClick={() => loadSkin(skinUser)} disabled={loadingSkin} className="crystal-btn-ghost border border-crystal-border text-crystal-text text-xs disabled:opacity-50">
                  {loadingSkin ? 'Lädt…' : 'Anzeigen'}
                </button>
              </div>
            </div>
          </div>

          <OutfitsPanel
            loadout={loadout}
            variants={variants}
            rank={rank}
            onApply={(next, nextVariants) => {
              setLoadout(next)
              api?.setLoadout(next)
              if (nextVariants) {
                const merged = { ...variants, ...nextVariants }
                setVariants(merged)
                api?.setSetting('cosmeticVariants', merged)
              }
            }}
            onChange={() => syncCatalogToGame(rank).catch(() => {})}
          />

          <div className="crystal-card divide-y divide-crystal-border">
            {SLOTS.map(s => {
              const label = equippedLabel(s.id)
              return (
                <div key={s.id} className="flex items-center justify-between gap-2 px-3 py-2 text-xs">
                  <button onClick={() => setTab(s.id)} className="text-crystal-muted hover:text-crystal-text">{s.label}</button>
                  {label ? (
                    <button onClick={() => equip(s.id, null)} title="Ablegen" className="group inline-flex items-center gap-1 text-crystal-text min-w-0">
                      <span className="truncate">{label}</span>
                      <X size={11} className="text-crystal-muted group-hover:text-crystal-danger shrink-0" />
                    </button>
                  ) : (
                    <span className="text-crystal-muted/60">keins</span>
                  )}
                </div>
              )
            })}
          </div>
        </aside>

        <div className="min-w-0">
          <div className="flex gap-5 border-b border-crystal-border mb-4 overflow-x-auto" role="tablist">
            {[...SLOTS, { id: 'emote' as const, label: 'Emotes' }].map(s => (
              <button
                key={s.id}
                role="tab"
                aria-selected={tab === s.id && !q}
                onClick={() => { setTab(s.id); setQuery('') }}
                className={`pb-2.5 -mb-px text-[13px] border-b-2 whitespace-nowrap transition-colors ${
                  tab === s.id && !q ? 'border-crystal-accent text-crystal-text font-medium' : 'border-transparent text-crystal-muted hover:text-crystal-text'
                }`}
              >
                {s.label}
              </button>
            ))}
          </div>

          {tab !== 'emote' && (
            <div className="flex flex-wrap items-center gap-2 mb-3">
              <div className="relative flex-1 min-w-[180px] max-w-[320px]">
                <Search size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-crystal-muted pointer-events-none" />
                <input
                  value={query}
                  onChange={e => setQuery(e.target.value)}
                  placeholder="Alle Cosmetics durchsuchen"
                  aria-label="Cosmetics durchsuchen"
                  className="crystal-input w-full pl-8 text-[13px]"
                />
              </div>
              <div className="flex items-center gap-1" role="group" aria-label="Filter">
                {FILTERS.map(f => (
                  <Chip key={f.id} active={filter === f.id} onClick={() => setFilter(f.id)}>{f.label}</Chip>
                ))}
              </div>
            </div>
          )}

          {searchResults ? (
            searchResults.items.length + searchResults.capes.length === 0 ? (
              <EmptyState icon={<Search size={20} strokeWidth={1.75} />} title="Nichts gefunden">
                Kein Cosmetic heißt „{query}“.
              </EmptyState>
            ) : (
              <>
                {searchResults.items.filter(passes).length > 0 && (
                  <TileGrid>{searchResults.items.filter(passes).map(item => itemTile(item, true))}</TileGrid>
                )}
                {searchResults.capes.length > 0 && (
                  <>
                    <p className="text-xs text-crystal-muted mt-5 mb-2">Capes</p>
                    <TileGrid>{searchResults.capes.slice(0, 48).map(capeTile)}</TileGrid>
                  </>
                )}
              </>
            )
          ) : tab === 'emote' ? (
            <EmotesTab wheel={wheel} onToggle={toggleWheel} onMove={moveWheel} />
          ) : tab === 'cape' ? (
            <>
              <div className="flex flex-wrap items-center gap-1.5 mb-3">
                {CAPE_CATEGORIES.map(c => (
                  <Chip key={c.id} active={capeCategory === c.id} onClick={() => setCapeCategory(c.id)}>{c.label}</Chip>
                ))}
                <Chip active={capeCategory === 'mine'} onClick={() => setCapeCategory('mine')}>
                  Eigene{customCapes.length > 0 && ` (${customCapes.length})`}
                </Chip>
                <button
                  onClick={uploadCape}
                  disabled={uploading}
                  className="ml-auto crystal-btn-ghost border border-crystal-border text-crystal-text text-xs py-1.5 disabled:opacity-50"
                >
                  <Upload size={12} strokeWidth={1.75} /> {uploading ? 'Lädt…' : 'Cape hochladen'}
                </button>
              </div>

              {capeCategory === 'mine' ? (
                customCapes.length === 0 ? (
                  <EmptyState
                    icon={<Upload size={20} strokeWidth={1.75} />}
                    title="Noch keine eigenen Capes"
                    action={<button onClick={uploadCape} className="crystal-btn-primary text-[13px]">Cape hochladen</button>}
                  >
                    PNG oder JPG. Nexora wandelt es in das Minecraft-Format 64 × 32 um.
                  </EmptyState>
                ) : (
                  <TileGrid>
                    {customCapes.map(cape => {
                      const id = `custom:${cape.id}`
                      return (
                        <Tile
                          key={cape.id}
                          selected={loadout.cape === id}
                          onClick={() => equip('cape', id)}
                          label={cape.name}
                          background={customThumbs[cape.id] ? `url(${customThumbs[cape.id]}) center/cover` : undefined}
                          onRemove={e => removeCustom(cape.id, e)}
                        />
                      )
                    })}
                  </TileGrid>
                )
              ) : (
                <TileGrid>{visibleCapes.map(capeTile)}</TileGrid>
              )}
            </>
          ) : (
            <>
              {wornWithVariants?.variants && wornWithVariants.variants.length > 1 && (
                <div className="crystal-card flex flex-wrap items-center gap-2 px-3 py-2 mb-3">
                  <span className="text-xs text-crystal-muted mr-1">Farbe für {wornWithVariants.name}</span>
                  {wornWithVariants.variants.map(v => {
                    const on = variantOf(wornWithVariants, variants)?.id === v.id
                    return (
                      <button
                        key={v.id}
                        onClick={() => chooseVariant(wornWithVariants, v.id)}
                        aria-pressed={on}
                        className={`inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-xs transition-colors ${on ? 'bg-crystal-panel text-crystal-text ring-1 ring-crystal-accent' : 'text-crystal-muted hover:text-crystal-text'}`}
                      >
                        <VariantPicture def={wornWithVariants} variant={v} size="md" />
                        {v.name}
                      </button>
                    )
                  })}
                </div>
              )}
              <p className="text-xs text-crystal-muted mb-3 flex items-center gap-1.5">
                <Sparkles size={12} className="text-crystal-accent" />
                Alle Items sind 3D-Modelle mit eigenen Texturen, die mit „Neu“ zusätzlich animiert.
              </p>
              {(() => {
                const items = COSMETICS_BY_SLOT[tab as NonCapeSlot].filter(passes)
                return items.length ? <TileGrid>{items.map(item => itemTile(item))}</TileGrid> : (
                  <EmptyState icon={<Search size={20} strokeWidth={1.75} />} title="Nichts in diesem Filter" />
                )
              })()}
            </>
          )}

          <p className="text-xs text-crystal-muted mt-5 max-w-[70ch]">
            Nexora liefert nur eigene Designs. Motive von geschützten Marken und Figuren kannst du als eigenes Cape hochladen.
          </p>
        </div>
      </div>
    </Page>
  )
}

function EmotesTab({ wheel, onToggle, onMove }: { wheel: string[]; onToggle: (id: string) => void; onMove: (id: string, by: number) => void }) {
  return (
    <div className="space-y-4">
      <p className="text-xs text-crystal-muted max-w-[70ch]">
        Im Spiel hältst du die Emote-Taste (Standard B) gedrückt, zeigst mit der Maus auf ein Emote und lässt los.
        Andere Nexora-Spieler sehen deine Emotes. Wähle bis zu {MAX_WHEEL_EMOTES} fürs Rad aus.
      </p>
      <div className="grid grid-cols-1 md:grid-cols-[minmax(0,1fr)_240px] gap-4 items-start">
        <div className="grid grid-cols-[repeat(auto-fill,minmax(150px,1fr))] gap-2.5">
          {EMOTES.map(e => {
            const on = wheel.includes(e.id)
            return (
              <button
                key={e.id}
                onClick={() => onToggle(e.id)}
                aria-pressed={on}
                className={`relative text-left rounded-lg border px-3 py-2.5 transition-colors ${on ? 'border-crystal-accent ring-1 ring-crystal-accent bg-crystal-card' : 'border-crystal-border hover:border-crystal-muted/60 bg-crystal-card/60'}`}
              >
                <span className="flex items-center gap-1.5 text-[13px] text-crystal-text">
                  <Smile size={13} className={on ? 'text-crystal-accent' : 'text-crystal-muted'} />
                  {e.name}
                  {e.isNew && <span className="ml-auto text-[9.5px] uppercase tracking-wide text-crystal-accent">Neu</span>}
                </span>
                <span className="block text-[11px] text-crystal-muted mt-0.5">{e.description}</span>
              </button>
            )
          })}
        </div>
        <div className="crystal-card p-3">
          <p className="text-xs text-crystal-muted mb-2">Auf dem Rad, im Uhrzeigersinn ab oben</p>
          <ol className="space-y-1">
            {wheel.map((id, i) => (
              <li key={id} className="flex items-center gap-2 text-[13px] text-crystal-text">
                <GripVertical size={12} className="text-crystal-muted" />
                <span className="w-4 text-crystal-muted text-[11px]">{i + 1}</span>
                <span className="flex-1 truncate">{EMOTES.find(e => e.id === id)?.name ?? id}</span>
                <button onClick={() => onMove(id, -1)} disabled={i === 0} aria-label="Nach oben" className="px-1 text-crystal-muted hover:text-crystal-text disabled:opacity-30">↑</button>
                <button onClick={() => onMove(id, 1)} disabled={i === wheel.length - 1} aria-label="Nach unten" className="px-1 text-crystal-muted hover:text-crystal-text disabled:opacity-30">↓</button>
              </li>
            ))}
          </ol>
        </div>
      </div>
    </div>
  )
}

/** A small picture of an item in one of its colour variants (its colours only if there is none). */
function VariantPicture({ def, variant, size = 'sm' }: { def: CosmeticDef; variant: CosmeticVariant; size?: 'sm' | 'md' }) {
  const url = pictureOf(def.id, variant.id)
  const box = size === 'md' ? 'w-8 h-6' : 'w-7 h-[21px]'
  if (!url) return <Swatch color={variant.color} secondary={variant.secondary} />
  return <img src={url} alt="" className={`${box} object-contain shrink-0`} draggable={false} />
}

function Swatch({ color, secondary }: { color: string; secondary?: string }) {
  return (
    <span
      className="inline-block w-3.5 h-3.5 rounded-full ring-1 ring-black/30 shrink-0"
      style={{ background: `linear-gradient(135deg, ${color} 0 50%, ${secondary ?? color} 50% 100%)` }}
    />
  )
}

function Chip({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      onClick={onClick}
      aria-pressed={active}
      className={`px-2.5 py-1 rounded-md text-xs transition-colors ${
        active ? 'bg-crystal-card text-crystal-text ring-1 ring-inset ring-crystal-border' : 'text-crystal-muted hover:text-crystal-text'
      }`}
    >
      {children}
    </button>
  )
}

function TileGrid({ children }: { children: React.ReactNode }) {
  return <div className="grid grid-cols-[repeat(auto-fill,minmax(124px,1fr))] gap-2.5">{children}</div>
}

/** An item with its 3D picture, colour dots and lock/new badges. */
function ItemTile({ def, variants, selected, locked, slotLabel, onClick, onVariant }: {
  def: CosmeticDef
  variants: CosmeticVariants
  selected: boolean
  locked?: string
  slotLabel?: string
  onClick: () => void
  onVariant: (id: string) => void
}) {
  const [thumb, setThumb] = useState<string | undefined>(() => cachedThumbnail(def, variants))
  const current = variantOf(def, variants)
  useEffect(() => {
    let alive = true
    const hit = cachedThumbnail(def, variants)
    if (hit) setThumb(hit)
    else thumbnail(def, variants).then(url => { if (alive && url) setThumb(url) })
    return () => { alive = false }
  }, [def.id, current?.id])

  // A soft neutral spotlight with a hint of the item's colour: the picture is the
  // item, the tile never paints its colours as a flat logo.
  const backdrop = `linear-gradient(${def.color}14, ${def.color}14), radial-gradient(circle at 50% 42%, #383e4c, #16181e 72%)`
  return (
    <div
      role="button"
      tabIndex={0}
      aria-pressed={selected}
      onClick={onClick}
      onKeyDown={e => (e.key === 'Enter' || e.key === ' ') && onClick()}
      className={`group relative rounded-lg overflow-hidden border cursor-pointer transition-colors ${
        selected ? 'border-crystal-accent ring-1 ring-crystal-accent' : 'border-crystal-border hover:border-crystal-muted/60'
      }`}
    >
      <div
        className={`relative aspect-[4/3] bg-crystal-panel flex items-center justify-center ${locked ? 'opacity-45' : ''}`}
        style={{ background: backdrop }}
      >
        {thumb
          ? <img src={thumb} alt="" className="w-full h-full object-contain transition-transform group-hover:scale-105" draggable={false} />
          : <span className="w-5 h-5 rounded-full border-2 border-crystal-muted/30 border-t-crystal-muted animate-spin" aria-hidden />}
        {def.isNew && <span className="absolute top-1.5 left-1.5 text-[9.5px] uppercase tracking-wide px-1.5 py-0.5 rounded bg-crystal-accent/90 text-black font-medium">Neu</span>}
        {slotLabel && <span className="absolute top-1.5 right-1.5 text-[9.5px] px-1.5 py-0.5 rounded bg-black/50 text-crystal-text">{slotLabel}</span>}
      </div>
      {def.variants && def.variants.length > 1 && (
        <div className="absolute left-1.5 bottom-9 flex gap-1" onClick={e => e.stopPropagation()}>
          {def.variants.map(v => (
            <button
              key={v.id}
              title={v.name}
              aria-label={`${def.name}: ${v.name}`}
              onClick={() => onVariant(v.id)}
              className={`rounded bg-black/55 ${current?.id === v.id ? 'ring-2 ring-crystal-accent' : 'ring-1 ring-white/10 opacity-85 hover:opacity-100'}`}
            >
              <VariantPicture def={def} variant={v} />
            </button>
          ))}
        </div>
      )}
      <div className="flex items-center gap-1 px-2 py-1.5 bg-crystal-card">
        <span className={`flex-1 min-w-0 text-[11.5px] truncate ${locked ? 'text-crystal-muted' : 'text-crystal-text'}`}>{def.name}</span>
        {locked && <span className="inline-flex items-center gap-0.5 text-[10px] text-crystal-muted shrink-0"><Lock size={9} />{locked}</span>}
        {selected && !locked && <Check size={12} className="text-crystal-accent shrink-0" />}
      </div>
    </div>
  )
}

function Tile({
  selected, onClick, label, background, onRemove, lockedLabel, pixelated,
}: {
  selected: boolean
  onClick: () => void
  label: string
  background?: string
  onRemove?: (e: React.MouseEvent) => void
  lockedLabel?: string
  pixelated?: boolean
}) {
  return (
    <div
      role="button"
      tabIndex={0}
      aria-pressed={selected}
      onClick={onClick}
      onKeyDown={e => (e.key === 'Enter' || e.key === ' ') && onClick()}
      className={`group relative rounded-lg overflow-hidden border cursor-pointer transition-colors ${
        selected ? 'border-crystal-accent ring-1 ring-crystal-accent' : 'border-crystal-border hover:border-crystal-muted/60'
      }`}
    >
      <div
        className={`aspect-[4/3] bg-crystal-panel ${lockedLabel ? 'opacity-40' : ''}`}
        style={{ background, imageRendering: pixelated ? 'pixelated' : undefined }}
      />
      <div className="flex items-center gap-1 px-2 py-1.5 bg-crystal-card">
        <span className={`flex-1 min-w-0 text-[11.5px] truncate ${lockedLabel ? 'text-crystal-muted' : 'text-crystal-text'}`}>{label}</span>
        {lockedLabel && <span className="inline-flex items-center gap-0.5 text-[10px] text-crystal-muted shrink-0"><Lock size={9} />{lockedLabel}</span>}
        {selected && !lockedLabel && <Check size={12} className="text-crystal-accent shrink-0" />}
        {onRemove && (
          <button onClick={onRemove} aria-label={`${label} löschen`} className="p-0.5 rounded text-crystal-muted opacity-0 group-hover:opacity-100 hover:text-crystal-danger shrink-0">
            <Trash2 size={11} />
          </button>
        )}
      </div>
    </div>
  )
}
