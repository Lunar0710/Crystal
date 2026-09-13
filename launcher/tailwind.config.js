/** @type {import('tailwindcss').Config} */
export default {
  content: ['./src/renderer/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        crystal: {
          bg:         'rgb(var(--c-bg) / <alpha-value>)',
          panel:      'rgb(var(--c-panel) / <alpha-value>)',
          card:       'rgb(var(--c-card) / <alpha-value>)',
          border:     'rgb(var(--c-border) / <alpha-value>)',
          accent:     'rgb(var(--c-accent) / <alpha-value>)',
          'accent-2': 'rgb(var(--c-accent-2) / <alpha-value>)',
          text:       'rgb(var(--c-text) / <alpha-value>)',
          muted:      'rgb(var(--c-muted) / <alpha-value>)',
          success:    'rgb(var(--c-success) / <alpha-value>)',
          danger:     'rgb(var(--c-danger) / <alpha-value>)',
          warning:    'rgb(var(--c-warning) / <alpha-value>)',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'monospace'],
      },
      borderRadius: {
        xl: '0.875rem',
        '2xl': '1.25rem',
      },
      backgroundImage: {
        'crystal-gradient': 'linear-gradient(135deg, rgb(var(--c-accent)) 0%, rgb(var(--c-accent-2)) 100%)',
        'panel-gradient':   'linear-gradient(180deg, rgb(var(--c-card)) 0%, rgb(var(--c-panel)) 100%)',
      },
      boxShadow: {
        crystal: '0 4px 24px rgb(var(--c-accent) / 0.15)',
        card:    '0 2px 16px rgba(0, 0, 0, 0.4)',
        glow:    '0 0 20px rgb(var(--c-accent) / 0.3)',
      },
      animation: {
        'fade-in':    'fadeIn 0.2s ease-out',
        'slide-in':   'slideIn 0.3s ease-out',
        'pulse-slow': 'pulse 3s cubic-bezier(0.4, 0, 0.6, 1) infinite',
      },
      keyframes: {
        fadeIn:  { '0%': { opacity: '0' }, '100%': { opacity: '1' } },
        slideIn: { '0%': { transform: 'translateX(-10px)', opacity: '0' }, '100%': { transform: 'translateX(0)', opacity: '1' } },
      },
    },
  },
  plugins: [],
}
