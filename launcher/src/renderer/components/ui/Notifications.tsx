import React, { useEffect } from 'react'
import { CheckCircle, XCircle, AlertCircle, Info, X } from 'lucide-react'
import { useNotificationStore, NotificationType } from '../../store/notificationStore'

const icons: Record<NotificationType, React.ReactNode> = {
  success: <CheckCircle size={16} className="text-crystal-success" />,
  error:   <XCircle    size={16} className="text-crystal-danger" />,
  warning: <AlertCircle size={16} className="text-crystal-warning" />,
  info:    <Info        size={16} className="text-crystal-accent" />,
}

export function NotificationContainer() {
  const { notifications, remove } = useNotificationStore()

  return (
    <div className="fixed bottom-4 right-4 flex flex-col gap-2 z-50 pointer-events-none">
      {notifications.map(n => (
        <div
          key={n.id}
          className="crystal-card flex items-start gap-3 p-3 pr-4 min-w-64 max-w-80 pointer-events-auto animate-slide-in shadow-card"
        >
          <div className="mt-0.5 flex-shrink-0">{icons[n.type]}</div>
          <div className="flex-1">
            {n.title && <p className="font-medium text-crystal-text text-sm">{n.title}</p>}
            <p className="text-crystal-muted text-xs">{n.message}</p>
          </div>
          <button
            onClick={() => remove(n.id)}
            className="text-crystal-muted hover:text-crystal-text transition-colors flex-shrink-0"
          >
            <X size={14} />
          </button>
        </div>
      ))}
    </div>
  )
}
