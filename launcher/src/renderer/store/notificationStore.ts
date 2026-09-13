import { create } from 'zustand'

export type NotificationType = 'success' | 'error' | 'warning' | 'info'

export interface Notification {
  id: string
  type: NotificationType
  title?: string
  message: string
  duration?: number
}

interface NotificationStore {
  notifications: Notification[]
  add: (n: Omit<Notification, 'id'>) => void
  remove: (id: string) => void
}

export const useNotificationStore = create<NotificationStore>((set, get) => ({
  notifications: [],
  add(n) {
    const id = Math.random().toString(36).slice(2)
    // Errors stay longer: they usually carry something the user has to read.
    const note: Notification = { id, duration: n.type === 'error' ? 9000 : 4000, ...n }
    set(s => ({ notifications: [...s.notifications, note] }))
    if (note.duration && note.duration > 0) {
      setTimeout(() => get().remove(id), note.duration)
    }
  },
  remove(id) {
    set(s => ({ notifications: s.notifications.filter(n => n.id !== id) }))
  },
}))

export const notify = (n: Omit<Notification, 'id'>) => useNotificationStore.getState().add(n)
