import React, { useEffect, useState } from 'react'
import { Shirt, X } from 'lucide-react'
import { notify } from '../../store/notificationStore'
import { EquippedCosmetics, COSMETICS_BY_SLOT, type NonCapeSlot } from '../../data/cosmetics'
import { meetsRank, type RankId } from '../../data/ranks'

interface Outfit {
  name: string
  loadout: EquippedCosmetics
}

const api = (window as any).crystal
const MAX_OUTFITS = 10

/**
 * Saved combinations of cape, hat, pet and the rest, put on with one click.
 * An item the rank no longer allows is left off when the outfit goes on.
 */
export function OutfitsPanel({ loadout, rank, onApply }: {
  loadout: EquippedCosmetics
  rank: RankId
  onApply: (next: EquippedCosmetics) => void
}) {
  const [outfits, setOutfits] = useState<Outfit[]>([])
  const [name, setName] = useState('')

  useEffect(() => {
    api?.getSetting('cosmeticOutfits').then((o: Outfit[] | undefined) => Array.isArray(o) && setOutfits(o))
  }, [])

  const store = (next: Outfit[]) => {
    setOutfits(next)
    api?.setSetting('cosmeticOutfits', next)
  }

  function save() {
    const n = name.trim()
    if (!n) return
    const rest = outfits.filter(o => o.name !== n)
    if (rest.length >= MAX_OUTFITS) {
      notify({ type: 'info', message: `Höchstens ${MAX_OUTFITS} Outfits. Lösch eins, um Platz zu machen.` })
      return
    }
    store([...rest, { name: n, loadout }])
    setName('')
    notify({ type: 'success', message: `Outfit „${n}“ gespeichert` })
  }

  function apply(outfit: Outfit) {
    const next = { ...outfit.loadout }
    let dropped = 0
    for (const slot of Object.keys(COSMETICS_BY_SLOT) as NonCapeSlot[]) {
      const id = next[slot]
      const def = id ? COSMETICS_BY_SLOT[slot].find(c => c.id === id) : null
      if (id && (!def || (def.requiredRank && !meetsRank(rank, def.requiredRank)))) {
        next[slot] = null
        dropped++
      }
    }
    onApply(next)
    notify({
      type: 'success',
      message: dropped ? `„${outfit.name}“ angelegt, ${dropped} Item(s) sind mit deinem Rang nicht verfügbar` : `„${outfit.name}“ angelegt`,
    })
  }

  return (
    <div className="crystal-card p-3 space-y-2">
      <p className="flex items-center gap-1.5 text-xs text-crystal-muted"><Shirt size={12} /> Outfits</p>
      {outfits.length > 0 && (
        <ul className="space-y-1">
          {outfits.map(o => (
            <li key={o.name} className="group flex items-center gap-2">
              <button onClick={() => apply(o)} className="flex-1 min-w-0 text-left text-[13px] text-crystal-text truncate hover:text-crystal-accent">
                {o.name}
              </button>
              <button
                onClick={() => store(outfits.filter(x => x.name !== o.name))}
                aria-label={`Outfit ${o.name} löschen`}
                className="text-crystal-muted opacity-0 group-hover:opacity-100 focus:opacity-100 hover:text-crystal-danger"
              >
                <X size={12} />
              </button>
            </li>
          ))}
        </ul>
      )}
      <div className="flex gap-2">
        <input
          value={name}
          onChange={e => setName(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && save()}
          maxLength={24}
          placeholder="Name für dieses Outfit"
          aria-label="Name für das aktuelle Outfit"
          className="crystal-input flex-1 min-w-0 text-[13px]"
        />
        <button onClick={save} disabled={!name.trim()} className="crystal-btn-ghost border border-crystal-border text-crystal-text text-xs disabled:opacity-50">
          Speichern
        </button>
      </div>
    </div>
  )
}
