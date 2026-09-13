import React from 'react'

/** Standard page frame: one max width, one set of margins, used by every route. */
export function Page({ children, wide }: { children: React.ReactNode; wide?: boolean }) {
  return (
    <div className={`mx-auto px-8 pt-7 pb-10 ${wide ? 'max-w-6xl' : 'max-w-3xl'}`}>
      {children}
    </div>
  )
}

export function PageHeader({ title, description, actions }: {
  title: string
  description?: React.ReactNode
  actions?: React.ReactNode
}) {
  return (
    <header className="flex items-end justify-between gap-6 mb-6">
      <div className="min-w-0">
        <h1 className="text-[22px] leading-tight font-semibold text-crystal-text">{title}</h1>
        {description && <p className="mt-1 text-[13px] text-crystal-muted max-w-[60ch]">{description}</p>}
      </div>
      {actions && <div className="flex items-center gap-2 shrink-0">{actions}</div>}
    </header>
  )
}

/** A titled group of related controls — a heading and a surface, nothing decorative. */
export function Section({ title, description, children, actions }: {
  title: string
  description?: React.ReactNode
  children: React.ReactNode
  actions?: React.ReactNode
}) {
  return (
    <section className="mb-6">
      <div className="flex items-baseline justify-between gap-4 mb-2 px-0.5">
        <div>
          <h2 className="text-[13px] font-semibold text-crystal-text">{title}</h2>
          {description && <p className="text-xs text-crystal-muted mt-0.5 max-w-[65ch]">{description}</p>}
        </div>
        {actions}
      </div>
      <div className="crystal-card divide-y divide-crystal-border">{children}</div>
    </section>
  )
}

/** One setting: label + optional hint on the left, control on the right. */
export function Field({ label, hint, children, stacked }: {
  label: string
  hint?: React.ReactNode
  children: React.ReactNode
  stacked?: boolean
}) {
  if (stacked) {
    return (
      <div className="px-4 py-3.5">
        <p className="text-[13px] text-crystal-text">{label}</p>
        {hint && <p className="text-xs text-crystal-muted mt-0.5 max-w-[65ch]">{hint}</p>}
        <div className="mt-2.5">{children}</div>
      </div>
    )
  }
  return (
    <div className="flex items-center justify-between gap-6 px-4 py-3">
      <div className="min-w-0">
        <p className="text-[13px] text-crystal-text">{label}</p>
        {hint && <p className="text-xs text-crystal-muted mt-0.5 max-w-[55ch]">{hint}</p>}
      </div>
      <div className="shrink-0">{children}</div>
    </div>
  )
}

export function Switch({ checked, onChange, label }: { checked: boolean; onChange: (v: boolean) => void; label?: string }) {
  return (
    <button
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={() => onChange(!checked)}
      className={`relative w-8 h-[18px] rounded-full transition-colors ${checked ? 'bg-crystal-accent' : 'bg-crystal-border'}`}
    >
      <span
        className={`absolute top-[2px] left-[2px] w-[14px] h-[14px] rounded-full bg-white shadow-sm transition-transform ${
          checked ? 'translate-x-[14px]' : ''
        }`}
      />
    </button>
  )
}

export function EmptyState({ icon, title, children, action }: {
  icon?: React.ReactNode
  title: string
  children?: React.ReactNode
  action?: React.ReactNode
}) {
  return (
    <div className="flex flex-col items-center text-center px-6 py-12 rounded-[10px] border border-dashed border-crystal-border">
      {icon && <div className="mb-3 text-crystal-muted/70">{icon}</div>}
      <p className="text-[13px] font-medium text-crystal-text">{title}</p>
      {children && <p className="text-xs text-crystal-muted mt-1 max-w-[46ch]">{children}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}
