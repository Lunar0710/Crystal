import { create } from 'zustand'
import { DEFAULT_THEME, themes } from '../theme/themes'

interface ThemeStore {
  theme: string
  setTheme: (id: string) => void
}

function applyTheme(id: string) {
  document.documentElement.setAttribute('data-theme', id)
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
  for (const prop of ['--c-bg', '--c-panel', '--c-card', '--c-border', '--c-accent', '--c-accent-2', '--c-text', '--c-muted']) {
    root.removeProperty(prop)
  }
}

const api = (window as any).crystal

export const useThemeStore = create<ThemeStore>(set => ({
  theme: DEFAULT_THEME,
  setTheme(id) {
    applyTheme(id)
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
  useThemeStore.setState({ theme })

  // Persist the migration so the stored value stops naming a dead theme.
  if (theme !== saved) api?.setSetting('theme', theme)
}
