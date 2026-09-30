/// <reference types="vite/client" />

/**
 * The item pictures rendered at build time (scripts/cosmetic-models/showcase/
 * thumbs.cjs): assets/cosmetic-thumbs/<item id>/<variant>.png, one per item
 * and colour variant. Vite bundles them as files; the page only looks them up.
 */
let files: Record<string, string> = {}
try {
  files = import.meta.glob('../assets/cosmetic-thumbs/*/*.png', { eager: true, query: '?url', import: 'default' }) as Record<string, string>
} catch {
  // Bundled without Vite (the thumbnail renderer itself): no pictures yet.
}

const byKey = new Map<string, string>()
for (const [file, url] of Object.entries(files)) {
  const m = /cosmetic-thumbs\/([^/]+)\/([^/]+)\.png$/.exec(file)
  if (m) byKey.set(`${m[1]}/${m[2]}`, url)
}

/** The shipped picture of an item in a variant (its first when not given). */
export function pictureOf(itemId: string, variant: string | undefined): string | undefined {
  return byKey.get(`${itemId}/${variant ?? 'default'}`)
}
