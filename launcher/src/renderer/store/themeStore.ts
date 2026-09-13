import { create } from 'zustand'
import { DEFAULT_THEME } from '../theme/themes'

export interface CustomThemeColors {
  bg: string; panel: string; card: string; border: string
  accent: string; accent2: string; text: string; muted: string
}

export const DEFAULT_CUSTOM: CustomThemeColors = {
  bg: '#0d0f14', panel: '#131720', card: '#1a1f2e', border: '#252b3a',
  accent: '#5b8af5', accent2: '#7c6af5', text: '#e4e8f0', muted: '#6b7280',
}

interface ThemeStore {
  theme: string
  custom: CustomThemeColors
  setTheme: (id: string) => void
  setCustomColor: (key: keyof CustomThemeColors, value: string) => void
}

function hexToRgbTriplet(hex: string): string {
  const clean = hex.replace('#', '')
  const r = parseInt(clean.substring(0, 2), 16)
  const g = parseInt(clean.substring(2, 4), 16)
  const b = parseInt(clean.substring(4, 6), 16)
  return `${r} ${g} ${b}`
}

function applyTheme(id: string) {
  document.documentElement.setAttribute('data-theme', id)
}

function applyCustomColors(colors: CustomThemeColors) {
  const root = document.documentElement.style
  root.setProperty('--c-bg', hexToRgbTriplet(colors.bg))
  root.setProperty('--c-panel', hexToRgbTriplet(colors.panel))
  root.setProperty('--c-card', hexToRgbTriplet(colors.card))
  root.setProperty('--c-border', hexToRgbTriplet(colors.border))
  root.setProperty('--c-accent', hexToRgbTriplet(colors.accent))
  root.setProperty('--c-accent-2', hexToRgbTriplet(colors.accent2))
  root.setProperty('--c-text', hexToRgbTriplet(colors.text))
  root.setProperty('--c-muted', hexToRgbTriplet(colors.muted))
}

function clearCustomColors() {
  const root = document.documentElement.style
  for (const prop of ['--c-bg', '--c-panel', '--c-card', '--c-border', '--c-accent', '--c-accent-2', '--c-text', '--c-muted']) {
    root.removeProperty(prop)
  }
}

const api = (window as any).crystal

export const useThemeStore = create<ThemeStore>((set, get) => ({
  theme: DEFAULT_THEME,
  custom: DEFAULT_CUSTOM,
  setTheme(id) {
    applyTheme(id)
    if (id === 'custom') applyCustomColors(get().custom)
    else clearCustomColors()
    set({ theme: id })
    api?.setSetting('theme', id)
  },
  setCustomColor(key, value) {
    const custom = { ...get().custom, [key]: value }
    set({ custom })
    if (get().theme === 'custom') applyCustomColors(custom)
    api?.setSetting('customTheme', custom)
  },
}))

export async function initTheme() {
  const saved = (await api?.getSetting('theme')) as string | undefined
  const savedCustom = (await api?.getSetting('customTheme')) as CustomThemeColors | undefined
  const custom = savedCustom || DEFAULT_CUSTOM
  const theme = saved || DEFAULT_THEME
  applyTheme(theme)
  if (theme === 'custom') applyCustomColors(custom)
  useThemeStore.setState({ theme, custom })
}
