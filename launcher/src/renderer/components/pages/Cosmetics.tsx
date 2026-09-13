import React, { useState, useEffect } from 'react'
import { Sparkles, Check, Upload, Trash2, Search, X, Lock } from 'lucide-react'
import { notify } from '../../store/notificationStore'
import { BUILTIN_CAPES, CAPE_CATEGORIES, CapeCategory, capeTextureUrl, capePreviewUrl } from '../../data/capes'
import {
  SLOTS, CosmeticSlot, NonCapeSlot, COSMETICS_BY_SLOT, EMPTY_LOADOUT,
  EquippedCosmetics, findCosmetic,
} from '../../data/cosmetics'
import { SkinPreview3D } from '../ui/SkinPreview3D'
import { RankId, meetsRank, lockLabel } from '../../data/ranks'

interface CustomCape {
  id: string
  name: string
  fileName: string
}

const api = (window as any).crystal

export function Cosmetics() {
  const [slot, setSlot] = useState<CosmeticSlot>('cape')
  const [capeCategory, setCapeCategory] = useState<CapeCategory | 'mine'>('emblem')
  const [loadout, setLoadout] = useState<EquippedCosmetics>(EMPTY_LOADOUT)

  const [customCapes, setCustomCapes] = useState<CustomCape[]>([])
  const [customThumbs, setCustomThumbs] = useState<Record<string, string>>({})
  const [uploading, setUploading] = useState(false)

  const [skinUser, setSkinUser] = useState('')
  const [skinDataUrl, setSkinDataUrl] = useState<string | null>(null)
  const [skinSlim, setSkinSlim] = useState(false)
  const [loadingSkin, setLoadingSkin] = useState(false)
  const [rank, setRank] = useState<RankId>('member')

  useEffect(() => {
    api?.getRank().then(setRank)
    api?.getLoadout().then((l: EquippedCosmetics) => l && setLoadout({ ...EMPTY_LOADOUT, ...l }))
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
  useEffect(() => {
    if (loadout.cape?.startsWith('custom:') && !equippedCapeUrl) return
    api?.syncEquippedCape(equippedCapeUrl)
  }, [equippedCapeUrl])

  const visibleCapes = BUILTIN_CAPES.filter(c => c.category === capeCategory)
  const totalCapes = BUILTIN_CAPES.length + customCapes.length

  return (
    <div className="p-6 space-y-5">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Sparkles size={20} className="text-crystal-accent" />
          <h1 className="text-xl font-bold text-crystal-text">Cosmetics</h1>
          <span className="text-xs text-crystal-muted bg-crystal-border px-2 py-0.5 rounded-full">
            {totalCapes} Capes · alles kostenlos
          </span>
        </div>
      </div>

      <div className="grid grid-cols-[280px_1fr] gap-4 items-start">
        {/* Live preview */}
        <div className="crystal-card p-4 space-y-3 sticky top-0">
          <h2 className="text-crystal-text font-semibold text-sm">Vorschau</h2>

          <div className="flex justify-center py-2 bg-crystal-panel rounded-xl overflow-hidden">
            <SkinPreview3D
              skinDataUrl={skinDataUrl}
              slim={skinSlim}
              capeUrl={equippedCapeUrl}
              hat={findCosmetic('hat', loadout.hat)}
              bandana={findCosmetic('bandana', loadout.bandana)}
              mask={findCosmetic('mask', loadout.mask)}
              wings={findCosmetic('wings', loadout.wings)}
              backpack={findCosmetic('backpack', loadout.backpack)}
              aura={findCosmetic('aura', loadout.aura)}
              width={236}
              height={300}
            />
          </div>

          <div>
            <label className="text-crystal-muted text-xs block mb-1.5">Skin von Username laden</label>
            <div className="flex gap-2">
              <div className="relative flex-1">
                <Search size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-crystal-muted" />
                <input
                  type="text"
                  value={skinUser}
                  onChange={e => setSkinUser(e.target.value)}
                  onKeyDown={e => e.key === 'Enter' && loadSkin(skinUser)}
                  className="crystal-input w-full pl-7 text-sm"
                  placeholder="z.B. Notch"
                />
              </div>
              <button
                onClick={() => loadSkin(skinUser)}
                disabled={loadingSkin}
                className="crystal-btn-primary text-xs px-3 disabled:opacity-60"
              >
                {loadingSkin ? '...' : 'Laden'}
              </button>
            </div>
          </div>

          {/* Equipped summary */}
          <div className="space-y-1 pt-1 border-t border-crystal-border">
            {SLOTS.map(s => {
              const value = loadout[s.id]
              const label = value
                ? s.id === 'cape'
                  ? (value.startsWith('custom:')
                      ? customCapes.find(c => `custom:${c.id}` === value)?.name ?? 'Eigenes'
                      : BUILTIN_CAPES.find(c => `builtin:${c.id}` === value)?.name ?? '—')
                  : findCosmetic(s.id as NonCapeSlot, value)?.name ?? '—'
                : null

              return (
                <div key={s.id} className="flex items-center justify-between text-xs">
                  <span className="text-crystal-muted">{s.label}</span>
                  {label ? (
                    <button
                      onClick={() => equip(s.id, null)}
                      className="flex items-center gap-1 text-crystal-text hover:text-crystal-danger transition-colors"
                    >
                      {label} <X size={10} />
                    </button>
                  ) : (
                    <span className="text-crystal-muted">—</span>
                  )}
                </div>
              )
            })}
          </div>
        </div>

        {/* Picker */}
        <div className="space-y-3">
          <div className="flex gap-1 p-1 bg-crystal-panel rounded-lg w-fit">
            {SLOTS.map(s => (
              <button
                key={s.id}
                onClick={() => setSlot(s.id)}
                className={`px-3 py-1.5 rounded-md text-sm font-medium transition-all ${
                  slot === s.id ? 'bg-crystal-gradient text-white shadow-glow' : 'text-crystal-muted hover:text-crystal-text'
                }`}
              >
                {s.label}
              </button>
            ))}
          </div>

          {slot === 'cape' ? (
            <>
              <div className="flex gap-1 flex-wrap">
                {CAPE_CATEGORIES.map(c => (
                  <button
                    key={c.id}
                    onClick={() => setCapeCategory(c.id)}
                    className={`px-2.5 py-1 rounded-md text-xs font-medium transition-all ${
                      capeCategory === c.id ? 'bg-crystal-accent text-white' : 'bg-crystal-panel text-crystal-muted hover:text-crystal-text'
                    }`}
                  >
                    {c.label}
                  </button>
                ))}
                <button
                  onClick={() => setCapeCategory('mine')}
                  className={`px-2.5 py-1 rounded-md text-xs font-medium transition-all ${
                    capeCategory === 'mine' ? 'bg-crystal-accent text-white' : 'bg-crystal-panel text-crystal-muted hover:text-crystal-text'
                  }`}
                >
                  Meine ({customCapes.length})
                </button>
                <button
                  onClick={uploadCape}
                  disabled={uploading}
                  className="flex items-center gap-1 px-2.5 py-1 rounded-md text-xs text-crystal-muted border border-dashed border-crystal-border hover:text-crystal-accent hover:border-crystal-accent transition-colors disabled:opacity-60"
                >
                  <Upload size={11} /> {uploading ? 'Lädt...' : 'Eigenes Cape'}
                </button>
              </div>

              <div className="crystal-card p-4">
                {capeCategory === 'mine' ? (
                  customCapes.length === 0 ? (
                    <div className="text-center py-8 text-crystal-muted">
                      <Upload size={26} className="mx-auto mb-2 opacity-30" />
                      <p className="text-sm">Noch keine eigenen Capes. Lade ein PNG hoch (64x32).</p>
                    </div>
                  ) : (
                    <div className="grid grid-cols-4 gap-3">
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
                    </div>
                  )
                ) : (
                  <div className="grid grid-cols-6 gap-3">
                    {visibleCapes.map(cape => {
                      const id = `builtin:${cape.id}`
                      return (
                        <Tile
                          key={cape.id}
                          selected={loadout.cape === id}
                          onClick={() => equip('cape', id)}
                          label={cape.name}
                          background={`url(${capePreviewUrl(cape)}) center/cover`}
                          glow={cape.glow}
                        />
                      )
                    })}
                  </div>
                )}
              </div>
            </>
          ) : (
            <div className="crystal-card p-4">
              <div className="grid grid-cols-4 gap-3">
                {COSMETICS_BY_SLOT[slot as NonCapeSlot].map(item => (
                  <Tile
                    key={item.id}
                    selected={loadout[slot] === item.id}
                    onClick={() => equip(slot, item.id)}
                    label={item.name}
                    background={`linear-gradient(135deg, ${item.color}, ${item.secondary ?? item.color})`}
                    glow={slot === 'aura' ? item.color : undefined}
                  />
                ))}
              </div>
            </div>
          )}

          <p className="text-crystal-muted text-xs">
            Alle Cosmetics sind kostenlos. Themed Capes sind eigene Designs — geschützte Figuren
            (Anime, Hello Kitty &amp; Co.) darf Crystal nicht mitliefern, die kannst du aber selbst hochladen.
          </p>
        </div>
      </div>
    </div>
  )
}

function Tile({
  selected, onClick, label, background, glow, onRemove, lockedLabel,
}: {
  selected: boolean
  onClick: () => void
  label: string
  background?: string
  glow?: string
  onRemove?: (e: React.MouseEvent) => void
  lockedLabel?: string
}) {
  return (
    <div
      onClick={onClick}
      className={`relative rounded-xl overflow-hidden border-2 transition-all cursor-pointer ${
        selected ? 'border-crystal-accent shadow-glow' : 'border-crystal-border hover:border-crystal-accent/40'
      }`}
      style={glow && !selected ? { boxShadow: `0 0 14px -4px ${glow}` } : undefined}
    >
      <div className="h-16" style={{ background }} />
      <div className="p-1.5 bg-crystal-card flex items-center justify-between gap-1">
        <div className="min-w-0">
          <p className="text-crystal-text text-[11px] font-medium truncate">{label}</p>
          {lockedLabel && (
            <p className="text-crystal-muted text-[9px] flex items-center gap-0.5"><Lock size={7} /> {lockedLabel}</p>
          )}
        </div>
        {onRemove && (
          <Trash2 size={11} onClick={onRemove} className="text-crystal-muted hover:text-crystal-danger shrink-0" />
        )}
      </div>
      {selected && (
        <div className="absolute top-1.5 right-1.5 w-4 h-4 rounded-full bg-crystal-accent flex items-center justify-center">
          <Check size={9} className="text-white" />
        </div>
      )}
    </div>
  )
}
