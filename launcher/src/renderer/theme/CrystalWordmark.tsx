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
        {/* Ring with a gap, the angular N inside it, and the spark sitting in the gap. */}
        <path
          d="M82.6 34.8 A36 36 0 1 1 65.2 17.4"
          fill="none"
          stroke={`url(#${gradientId})`}
          strokeWidth="7"
          strokeLinecap="round"
        />
        <path
          d="M30 30 L30 70 L40 70 L40 48 L60 70 L70 70 L70 30 L60 30 L60 52 L40 30 Z M75.5 11.5 L79.5 20.5 L88.5 24.5 L79.5 28.5 L75.5 37.5 L71.5 28.5 L62.5 24.5 L71.5 20.5 Z"
          fill={`url(#${gradientId})`}
        />
      </svg>
      exora
    </span>
  )
}
