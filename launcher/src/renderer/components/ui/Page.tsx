import React from 'react'

/** Standard page frame: one max width, one set of margins, used by every route. */
export function Page({ children, wide }: { children: React.ReactNode; wide?: boolean }) {
  return (
    <div className={`mx-auto px-8 pt-9 pb-12 animate-slide-in ${wide ? 'max-w-6xl' : 'max-w-3xl'}`}>
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
    <header className="flex items-end justify-between gap-6 mb-8">
      <div className="min-w-0">
        <h1 className="nexora-display text-[34px] leading-[0.95] text-crystal-text">{title}</h1>
        {description && <p className="mt-2.5 text-[13.5px] font-light text-crystal-muted max-w-[60ch]">{description}</p>}
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
    <section className="mb-8">
      <div className="flex items-baseline justify-between gap-4 mb-3 px-0.5">
        <div>
          <h2 className="nexora-display text-[15px] text-crystal-text">{title}</h2>
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
      className={`relative w-9 h-5 rounded-full nexora-ease ring-1 ring-inset ${
        checked ? 'bg-crystal-accent ring-white/20 shadow-[0_0_16px_-4px_rgb(var(--c-accent)/0.7)]' : 'bg-white/[0.08] ring-white/10'
      }`}
    >
      {/* Knob colours come from theme tokens, not white: on Void the accent
          itself is white, and a white knob on it simply disappeared. */}
      <span
        className={`absolute top-[3px] left-[3px] w-[14px] h-[14px] rounded-full shadow-[0_1px_3px_rgb(0_0_0/0.5)] nexora-ease ${
          checked ? 'translate-x-[16px] bg-crystal-bg' : 'bg-crystal-text/85'
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
    <div className="flex flex-col items-center text-center px-6 py-12 rounded-[20px] border border-dashed border-white/10 bg-white/[0.015]">
      {icon && <div className="mb-3 text-crystal-muted/70">{icon}</div>}
      <p className="text-[13px] font-medium text-crystal-text">{title}</p>
      {children && <p className="text-xs text-crystal-muted mt-1 max-w-[46ch]">{children}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}
