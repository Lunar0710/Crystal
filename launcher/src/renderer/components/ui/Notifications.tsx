import React from 'react'
import { CheckCircle2, XCircle, AlertTriangle, Info, X } from 'lucide-react'
import { useNotificationStore, NotificationType } from '../../store/notificationStore'

const icons: Record<NotificationType, React.ReactNode> = {
  success: <CheckCircle2  size={15} strokeWidth={2} className="text-crystal-success" />,
  error:   <XCircle       size={15} strokeWidth={2} className="text-crystal-danger" />,
  warning: <AlertTriangle size={15} strokeWidth={2} className="text-crystal-warning" />,
  info:    <Info          size={15} strokeWidth={2} className="text-crystal-muted" />,
}

export function NotificationContainer() {
  const { notifications, remove } = useNotificationStore()

  return (
    <div className="fixed bottom-4 right-4 flex flex-col gap-2 z-50 pointer-events-none" role="status" aria-live="polite">
      {notifications.map(n => (
        <div
          key={n.id}
          className="flex items-start gap-2.5 w-80 pl-3 pr-2 py-2.5 rounded-lg bg-crystal-panel shadow-popover pointer-events-auto animate-slide-in"
        >
          <span className="mt-px shrink-0">{icons[n.type]}</span>
          <div className="flex-1 min-w-0">
            {n.title && <p className="text-[13px] font-medium text-crystal-text">{n.title}</p>}
            <p className={`text-xs text-crystal-muted break-words ${n.title ? 'mt-0.5' : ''} line-clamp-4`}>{n.message}</p>
          </div>
          <button onClick={() => remove(n.id)} aria-label="Schließen" className="p-1 rounded text-crystal-muted hover:text-crystal-text">
            <X size={13} />
          </button>
        </div>
      ))}
    </div>
  )
}
