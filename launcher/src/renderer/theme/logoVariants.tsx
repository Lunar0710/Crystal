import React, { useId } from 'react'

export type LogoVariantId = 'facet-hex' | 'crystal-monogram' | 'shard-mark' | 'twin-shard-duel'

/**
 * The four app icons to choose from, all built from the Nexora mark: the ring
 * with the N and the spark, and three reductions of it. They take their colour
 * from the current theme, so they fit whichever one is picked.
 *
 * The ids are the old ones on purpose — they are stored in the settings, and
 * renaming them would reset everyone's chosen icon.
 */
export function LogoMark({ variant, size = 20 }: { variant: LogoVariantId; size?: number }) {
  const gradientId = useId()
  const stroke = `url(#${gradientId})`

  const defs = (
    <defs>
      <linearGradient id={gradientId} x1="0" y1="0" x2="1" y2="1">
        <stop offset="0%" stopColor="rgb(var(--c-accent))" />
        <stop offset="100%" stopColor="rgb(var(--c-accent-2))" />
      </linearGradient>
    </defs>
  )

  switch (variant) {
    // The N on its own.
    case 'crystal-monogram':
      return (
        <svg width={size} height={size} viewBox="0 0 100 100">
          {defs}
          <path d="M22 20 L22 80 L36 80 L36 48 L64 80 L78 80 L78 20 L64 20 L64 52 L36 20 Z" fill={stroke} />
        </svg>
      )
    // Just the spark.
    case 'shard-mark':
      return (
        <svg width={size} height={size} viewBox="0 0 100 100">
          {defs}
          <path d="M50 6 L58 42 L94 50 L58 58 L50 94 L42 58 L6 50 L42 42 Z" fill={stroke} />
        </svg>
      )
    // The closed ring with the N, without the spark.
    case 'twin-shard-duel':
      return (
        <svg width={size} height={size} viewBox="0 0 100 100">
          {defs}
          <circle cx="50" cy="50" r="36" fill="none" stroke={stroke} strokeWidth="7" />
          <path d="M34 32 L34 68 L42 68 L42 49 L58 68 L66 68 L66 32 L58 32 L58 51 L42 32 Z" fill={stroke} />
        </svg>
      )
    // The full mark: ring with a gap, N inside, spark in the gap.
    case 'facet-hex':
    default:
      return (
        <svg width={size} height={size} viewBox="0 0 100 100">
          {defs}
          <path d="M82.6 34.8 A36 36 0 1 1 65.2 17.4" fill="none" stroke={stroke} strokeWidth="7" strokeLinecap="round" />
          <path
            d="M30 30 L30 70 L40 70 L40 48 L60 70 L70 70 L70 30 L60 30 L60 52 L40 30 Z M75.5 11.5 L79.5 20.5 L88.5 24.5 L79.5 28.5 L75.5 37.5 L71.5 28.5 L62.5 24.5 L71.5 20.5 Z"
            fill={stroke}
          />
        </svg>
      )
  }
}
