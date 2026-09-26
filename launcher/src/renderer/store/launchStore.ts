import { create } from 'zustand'

/**
 * The start that is under way (step and percent), kept for the whole
 * launcher: the play page used to hold it itself, so going to another page
 * and back lost the progress bar while the start went on.
 */
interface LaunchStore {
  progress: { step: string; percent: number } | null
  setProgress: (progress: { step: string; percent: number } | null) => void
}

export const useLaunchStore = create<LaunchStore>(set => ({
  progress: null,
  setProgress: progress => set({ progress }),
}))

const api = (window as any).crystal
// Subscribed once for the app's lifetime, whichever page is open.
api?.on('launch:progress', (data: { step: string; percent: number }) => useLaunchStore.getState().setProgress(data))
api?.on('launch:started', () => useLaunchStore.getState().setProgress(null))
api?.on('launch:error', () => useLaunchStore.getState().setProgress(null))
