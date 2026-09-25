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
        sans: ['Geist Variable', 'Segoe UI', 'system-ui', 'sans-serif'],
        mono: ['Geist Mono Variable', 'Consolas', 'monospace'],
        // Headings, as on the website: Archivo set wide and heavy.
        display: ['Archivo Variable', 'Geist Variable', 'Segoe UI', 'sans-serif'],
      },
      borderRadius: {
        lg: '8px',
        xl: '12px',
        '2xl': '18px',
      },
      // Kept as names so existing markup still resolves, but deliberately flat:
      // gradient fills and glow shadows were the loudest "template" tells.
      backgroundImage: {
        'crystal-gradient': 'linear-gradient(rgb(var(--c-accent)), rgb(var(--c-accent)))',
        'panel-gradient':   'none',
      },
      boxShadow: {
        crystal: 'none',
        card:    'none',
        glow:    'none',
        popover: '0 12px 32px -8px rgb(0 0 0 / 0.55), 0 0 0 1px rgb(var(--c-border))',
      },
      animation: {
        'fade-in':    'fadeIn 0.2s ease-out',
        'slide-in':   'slideIn 0.5s cubic-bezier(0.32, 0.72, 0, 1)',
        'pulse-slow': 'pulse 3s cubic-bezier(0.4, 0, 0.6, 1) infinite',
      },
      keyframes: {
        fadeIn:  { '0%': { opacity: '0' }, '100%': { opacity: '1' } },
        slideIn: { '0%': { transform: 'translateY(10px)', opacity: '0', filter: 'blur(4px)' }, '100%': { transform: 'translateY(0)', opacity: '1', filter: 'blur(0)' } },
      },
    },
  },
  plugins: [],
}
