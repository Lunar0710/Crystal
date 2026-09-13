import React from 'react'

export type LogoVariantId = 'facet-hex' | 'crystal-monogram' | 'shard-mark' | 'twin-shard-duel'

export function LogoMark({ variant, size = 20 }: { variant: LogoVariantId; size?: number }) {
  switch (variant) {
    case 'crystal-monogram':
      return (
        <svg width={size} height={size} viewBox="0 0 100 100">
          <path
            d="M68 22 L40 22 L18 44 L18 56 L40 78 L68 78 L68 62 L46 62 L34 50 L46 38 L68 38 Z"
            fill="#a35bf5"
          />
        </svg>
      )
    case 'shard-mark':
      return (
        <svg width={size} height={size} viewBox="0 0 100 100">
          <g fill="#dfe6f7">
            <polygon points="50,10 64,42 46,46" opacity=".95" />
            <polygon points="82,36 74,68 52,54" opacity=".7" />
            <polygon points="66,86 34,84 44,58" opacity=".5" />
          </g>
        </svg>
      )
    case 'twin-shard-duel':
      return (
        <svg width={size} height={size} viewBox="0 0 100 100">
          <polygon points="18,18 30,10 90,70 78,82" fill="#f5455b" opacity=".9" />
          <polygon points="82,18 70,10 10,70 22,82" fill="#f57c3d" opacity=".85" />
        </svg>
      )
    case 'facet-hex':
    default:
      return (
        <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
          <polygon points="12,2 22,8 22,16 12,22 2,16 2,8" stroke="url(#cg)" strokeWidth="1.5" fill="url(#cbg)" />
          <polygon points="12,6 18,9.5 18,14.5 12,18 6,14.5 6,9.5" stroke="url(#cg)" strokeWidth="1" fill="url(#cinner)" opacity="0.7" />
          <defs>
            <linearGradient id="cg" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0%" stopColor="#5b8af5" />
              <stop offset="100%" stopColor="#7c6af5" />
            </linearGradient>
            <linearGradient id="cbg" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0%" stopColor="#5b8af520" />
              <stop offset="100%" stopColor="#7c6af520" />
            </linearGradient>
            <linearGradient id="cinner" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0%" stopColor="#5b8af540" />
              <stop offset="100%" stopColor="#7c6af540" />
            </linearGradient>
          </defs>
        </svg>
      )
  }
}
