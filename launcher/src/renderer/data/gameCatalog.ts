import { BUILTIN_CAPES, canUseCape } from './capes'
import { buildGameCatalog, CosmeticVariants, DEFAULT_WHEEL, EquippedCosmetics } from './cosmetics'
import { meetsRank, RankId } from './ranks'

const api = (window as any).crystal

interface Outfit {
  name: string
  loadout: Partial<EquippedCosmetics>
  variants?: CosmeticVariants
}

/**
 * Writes cosmetics/catalog.json for the in-game Cosmetics menu. Capes are
 * offered in game when they're still ones (animated capes only exist in the
 * launcher) and their picture is in the cape cache.
 */
export async function syncCatalogToGame(rank: RankId) {
  if (!api?.syncCatalog) return
  const outfits: Outfit[] = (await api.getSetting('cosmeticOutfits')) ?? []
  const catalog = buildGameCatalog({
    canUse: def => !def.requiredRank || meetsRank(rank, def.requiredRank),
    capes: BUILTIN_CAPES.filter(c => !c.animate).map(c => ({ id: c.id, name: c.name, category: c.category, locked: !canUseCape(rank, c) })),
    outfits: Array.isArray(outfits) ? outfits : [],
  })
  await api.syncCatalog(catalog)
}

/** The emotes on the in-game wheel, as chosen on the Emotes tab. */
export async function loadEmoteWheel(): Promise<string[]> {
  const saved = await api?.getSetting('emoteWheel')
  return Array.isArray(saved) && saved.length ? saved : DEFAULT_WHEEL
}

export async function loadVariants(): Promise<CosmeticVariants> {
  const saved = await api?.getSetting('cosmeticVariants')
  return saved && typeof saved === 'object' ? saved : {}
}
