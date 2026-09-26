import { create } from 'zustand'
import { DEFAULT_THEME, themes } from '../theme/themes'

interface ThemeStore {
  theme: string
  setTheme: (id: string) => void
}

function applyTheme(id: string) {
  document.documentElement.setAttribute('data-theme', id)
  if (id !== 'custom') clearLegacyCustomColors()
}

/** The colours of the custom theme, as hex, by CSS token (--c-<key>). */
export const CUSTOM_KEYS = ['bg', 'panel', 'card', 'border', 'accent', 'accent-2', 'text', 'muted'] as const
export type CustomColors = Record<(typeof CUSTOM_KEYS)[number], string>
export const CUSTOM_DEFAULT: CustomColors = {
  bg: '#000000', panel: '#111113', card: '#17171a', border: '#26272a',
  accent: '#c3493a', 'accent-2': '#d6685a', text: '#f0f0f0', muted: '#8a8a8c',
}

function triplet(hex: string): string {
  const m = /^#?([0-9a-f]{6})$/i.exec(hex.trim())
  const n = m ? parseInt(m[1], 16) : 0
  return `${(n >> 16) & 255} ${(n >> 8) & 255} ${n & 255}`
}

/** Writes the custom colours onto the page; text on the accent turns dark on a light accent. */
export function applyCustomColors(colors: CustomColors) {
  const root = document.documentElement.style
  for (const key of CUSTOM_KEYS) root.setProperty(`--c-${key}`, triplet(colors[key] ?? CUSTOM_DEFAULT[key]))
  const [r, g, b] = triplet(colors.accent).split(' ').map(Number)
  const light = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255 > 0.6
  root.setProperty('--c-on-accent', light ? '12 12 13' : '255 255 255')
}

export async function loadCustomColors(): Promise<CustomColors> {
  const saved = (await api?.getSetting('customTheme')) as Partial<CustomColors> | undefined
  return { ...CUSTOM_DEFAULT, ...(saved || {}) }
}

/**
 * The Custom theme was removed in favour of the hand-designed ones. Anyone
 * still on it (or on a theme that no longer exists) is moved to the default
 * rather than left staring at an unstyled window, since a data-theme with no
 * matching CSS block leaves every colour token undefined.
 */
function resolveTheme(saved: string | undefined): string {
  if (!saved) return DEFAULT_THEME
  return themes.some(t => t.id === saved) ? saved : DEFAULT_THEME
}

/** Clears the inline overrides the old Custom theme wrote onto :root. */
function clearLegacyCustomColors() {
  const root = document.documentElement.style
  for (const prop of ['--c-bg', '--c-panel', '--c-card', '--c-border', '--c-accent', '--c-accent-2', '--c-text', '--c-muted', '--c-on-accent']) {
    root.removeProperty(prop)
  }
}

const api = (window as any).crystal

export const useThemeStore = create<ThemeStore>(set => ({
  theme: DEFAULT_THEME,
  setTheme(id) {
    applyTheme(id)
    if (id === 'custom') loadCustomColors().then(applyCustomColors)
    set({ theme: id })
    api?.setSetting('theme', id)
  },
}))

export async function initTheme() {
  const saved = (await api?.getSetting('theme')) as string | undefined
  let theme = resolveTheme(saved)
  // 1.7 made Nexora Rot the default. Everyone still on the old default is
  // moved once; picking Mono again afterwards sticks.
  if (saved === 'crystal-blue' && !(await api?.getSetting('themeRedMoved'))) {
    theme = 'nexora-red'
    api?.setSetting('themeRedMoved', true)
  }

  clearLegacyCustomColors()
  applyTheme(theme)
  if (theme === 'custom') applyCustomColors(await loadCustomColors())
  useThemeStore.setState({ theme })

  // Persist the migration so the stored value stops naming a dead theme.
  if (theme !== saved) api?.setSetting('theme', theme)
}
