import React, { useState, useEffect } from 'react'
import { Check, Upload, Trash2, X, Lock } from 'lucide-react'
import { Page, PageHeader, EmptyState } from '../ui/Page'
import { OutfitsPanel } from '../ui/OutfitsPanel'
import { notify } from '../../store/notificationStore'
import { BUILTIN_CAPES, CAPE_CATEGORIES, CapeCategory, capeTextureUrl, capePreviewUrl, isCapeTexture, pictureToCapeTexture, canUseCape, capeFrameUrls, capeAnimationStrip } from '../../data/capes'
import { ANIMATION_FRAMES, ANIMATION_FPS } from '../../data/animatedCapes'
import {
  SLOTS, CosmeticSlot, NonCapeSlot, COSMETICS_BY_SLOT, EMPTY_LOADOUT,
  EquippedCosmetics, findCosmetic, syncLoadoutToGame,
} from '../../data/cosmetics'
import { SkinPreview3D } from '../ui/SkinPreview3D'
import { ProfileCardDialog } from '../ui/ProfileCardDialog'
import { RankId, RANKS, meetsRank, lockLabel } from '../../data/ranks'

interface CustomCape {
  id: string
  name: string
  fileName: string
}

const api = (window as any).crystal

export function Cosmetics() {
  const [slot, setSlot] = useState<CosmeticSlot>('cape')
  const [capeCategory, setCapeCategory] = useState<CapeCategory | 'mine'>('plus')
  const [loadout, setLoadout] = useState<EquippedCosmetics>(EMPTY_LOADOUT)
  const [loadoutLoaded, setLoadoutLoaded] = useState(false)

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
    api?.getRank().then((r: RankId) => { setRank(r); setRankLoaded(true) })
    api?.getLoadout().then((l: EquippedCosmetics) => {
      if (l) setLoadout({ ...EMPTY_LOADOUT, ...l })
      setLoadoutLoaded(true)
    })
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
  const nonCapeKey = `${loadout.hat}|${loadout.bandana}|${loadout.mask}|${loadout.wings}|${loadout.backpack}|${loadout.aura}|${loadout.pet}`
  useEffect(() => {
    // Not before the saved loadout has arrived: syncing the empty initial state
    // briefly took everything off in a running game each time the page opened.
    if (loadoutLoaded) syncLoadoutToGame(loadout)
  }, [nonCapeKey, loadoutLoaded])

  // A timed rank can run out while a perk cape is still equipped; take it off
  // then, but only once the real rank has arrived, never on the initial default.
  useEffect(() => {
    if (!rankLoaded || !loadout.cape?.startsWith('builtin:')) return
    const def = BUILTIN_CAPES.find(c => `builtin:${c.id}` === loadout.cape)
    if (def && !canUseCape(rank, def)) equip('cape', null)
  }, [rankLoaded, rank, loadout.cape])

  const visibleCapes = BUILTIN_CAPES.filter(c => c.category === capeCategory)

  function equippedLabel(slotId: CosmeticSlot): string | null {
    const value = loadout[slotId]
    if (!value) return null
    if (slotId === 'cape') {
      return value.startsWith('custom:')
        ? customCapes.find(c => `custom:${c.id}` === value)?.name ?? 'Eigenes Cape'
        : BUILTIN_CAPES.find(c => `builtin:${c.id}` === value)?.name ?? null
    }
    return findCosmetic(slotId as NonCapeSlot, value)?.name ?? null
  }

  return (
    <Page wide>
      <PageHeader
        title="Cosmetics"
        description="Alles, was du hier ausrüstest, siehst du genau so auch im Spiel, und andere Nexora-Spieler sehen es bei dir."
        actions={
          <button onClick={() => setShowCard(true)} className="crystal-btn-ghost text-[13px]">
            Profil-Karte
          </button>
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

      <div className="grid grid-cols-1 lg:grid-cols-[272px_minmax(0,1fr)] gap-6 items-start">
        <aside className="lg:sticky lg:top-4 space-y-3">
          <div className="crystal-card overflow-hidden">
            <div className="flex justify-center bg-crystal-panel/60 border-b border-crystal-border">
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
                width={270}
                height={300}
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

          <OutfitsPanel loadout={loadout} rank={rank} onApply={next => { setLoadout(next); api?.setLoadout(next) }} />

          <div className="crystal-card divide-y divide-crystal-border">
            {SLOTS.map(s => {
              const label = equippedLabel(s.id)
              return (
                <div key={s.id} className="flex items-center justify-between gap-2 px-3 py-2 text-xs">
                  <span className="text-crystal-muted">{s.label}</span>
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
            {SLOTS.map(s => (
              <button
                key={s.id}
                role="tab"
                aria-selected={slot === s.id}
                onClick={() => setSlot(s.id)}
                className={`pb-2.5 -mb-px text-[13px] border-b-2 whitespace-nowrap transition-colors ${
                  slot === s.id ? 'border-crystal-accent text-crystal-text font-medium' : 'border-transparent text-crystal-muted hover:text-crystal-text'
                }`}
              >
                {s.label}
              </button>
            ))}
          </div>

          {slot === 'cape' ? (
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
                <TileGrid>
                  {visibleCapes.map(cape => {
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
                  })}
                </TileGrid>
              )}
            </>
          ) : (
            <>
              <p className="text-xs text-crystal-muted mb-3">
                Wird im Spiel an deinem Spieler angezeigt, in vereinfachter Block-Form. Wie beim Cape siehst nur du es.
              </p>
              <TileGrid>
                {COSMETICS_BY_SLOT[slot as NonCapeSlot].map(item => {
                  const locked = !!item.requiredRank && !meetsRank(rank, item.requiredRank)
                  return (
                    <Tile
                      key={item.id}
                      selected={loadout[slot] === item.id}
                      onClick={() => equip(slot, item.id, item.requiredRank)}
                      label={item.name}
                      background={`linear-gradient(135deg, ${item.color} 0 50%, ${item.secondary ?? item.color} 50% 100%)`}
                      lockedLabel={locked ? lockLabel(item.requiredRank!) : undefined}
                    />
                  )
                })}
              </TileGrid>
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
  return <div className="grid grid-cols-[repeat(auto-fill,minmax(112px,1fr))] gap-2.5">{children}</div>
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
