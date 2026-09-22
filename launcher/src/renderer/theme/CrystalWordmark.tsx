import React, { useId } from 'react'

// The Nexora monogram, sized and baseline-aligned to stand in for the literal
// capital "N" of the word "Nexora" — so the wordmark reads as the drawn N
// directly fused into the text, not an icon placed next to it.
export function CrystalWordmark({ size = 20, className = '' }: { size?: number; className?: string }) {
  // The gradient needs a document-unique id: several wordmarks render at once
  // (title bar, dashboard, launch page) and a shared id makes them all resolve
  // to whichever <defs> happens to come first in the DOM.
  const gradientId = useId()

  return (
    <span
      className={`inline-flex items-baseline font-semibold tracking-wide ${className}`}
      style={{ fontSize: size, lineHeight: 1 }}
    >
      <svg
        width={size * 1.12}
        height={size * 1.12}
        viewBox="0 0 100 100"
        style={{ transform: `translateY(${size * 0.2}px)`, marginRight: -size * 0.06 }}
        aria-hidden="true"
      >
        <defs>
          <linearGradient id={gradientId} x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="rgb(var(--c-accent))" />
            <stop offset="100%" stopColor="rgb(var(--c-accent-2))" />
          </linearGradient>
        </defs>
        <path
          d="M18 22 L33 22 L33 78 L18 78 Z M33 22 L48 22 L82 78 L67 78 Z M67 22 L82 22 L82 78 L67 78 Z"
          fill={`url(#${gradientId})`}
        />
      </svg>
      exora
    </span>
  )
}
