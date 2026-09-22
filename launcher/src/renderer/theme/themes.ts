import { RankId } from '../data/ranks'

export interface CrystalTheme {
  id: string
  name: string
  preview: [string, string]
  /** Omitted = free for everyone. */
  requiredRank?: RankId
}

export const themes: CrystalTheme[] = [
  // Free
  { id: 'crystal-blue',     name: 'Nexora Mono',    preview: ['#ffffff', '#8a8a93'] },
  { id: 'crystal-crimson',  name: 'Nexora Crimson', preview: ['#f5455b', '#f57c3d'] },
  { id: 'crystal-bloom',    name: 'Nexora Bloom',   preview: ['#f56ba0', '#f5a3c7'] },
  { id: 'crystal-amethyst', name: 'Nexora Amethyst',preview: ['#a35bf5', '#7c3df5'] },
  { id: 'crystal-emerald',  name: 'Nexora Emerald', preview: ['#34d399', '#5bf5c9'] },
  { id: 'crystal-light',    name: 'Nexora Light',   preview: ['#5b8af5', '#7c6af5'] },

  // Nexora+
  { id: 'midnight',  name: 'Midnight',  preview: ['#1e3a8a', '#312e81'], requiredRank: 'crystal_plus' },
  { id: 'sunset',    name: 'Sunset',    preview: ['#f97316', '#e11d48'], requiredRank: 'crystal_plus' },
  { id: 'ocean',     name: 'Ocean',     preview: ['#0ea5e9', '#14b8a6'], requiredRank: 'crystal_plus' },
  { id: 'rose',      name: 'Rosé',      preview: ['#fb7185', '#f9a8d4'], requiredRank: 'crystal_plus' },
  { id: 'carbon',    name: 'Carbon',    preview: ['#52525b', '#18181b'], requiredRank: 'crystal_plus' },
  { id: 'matcha',    name: 'Matcha',    preview: ['#84cc16', '#4d7c0f'], requiredRank: 'crystal_plus' },

  { id: 'void', name: 'Void', preview: ['#ffffff', '#b4b4d2'], requiredRank: 'crystal_plus' },
  { id: 'aurora',    name: 'Aurora',    preview: ['#34d399', '#818cf8'], requiredRank: 'crystal_plus' },
  { id: 'sakura',    name: 'Sakura',    preview: ['#f472b6', '#7e22ce'], requiredRank: 'crystal_plus' },
  { id: 'terminal',  name: 'Terminal',  preview: ['#22c55e', '#052e16'], requiredRank: 'crystal_plus' },
  { id: 'sandstorm', name: 'Sandstorm', preview: ['#d97706', '#78350f'], requiredRank: 'crystal_plus' },

  // Team only
  { id: 'prism',     name: 'Prism',     preview: ['#22d3ee', '#a855f7'], requiredRank: 'developer' },
  { id: 'obsidian',  name: 'Obsidian Gold', preview: ['#fbbf24', '#18181b'], requiredRank: 'developer' },
  { id: 'nebula',    name: 'Nebula',    preview: ['#c084fc', '#22d3ee'], requiredRank: 'developer' },
  { id: 'blueprint', name: 'Blueprint', preview: ['#38bdf8', '#0c2340'], requiredRank: 'developer' },
]

export const DEFAULT_THEME = 'crystal-blue'
