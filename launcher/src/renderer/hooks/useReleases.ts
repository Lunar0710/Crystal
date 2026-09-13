import { useEffect, useState } from 'react'

export interface Release {
  id: string
  title: string
  tag: string
  body: string
  publishedAt: string
  url: string
}

const api = (window as any).crystal

// Module-level cache so switching between Dashboard and News doesn't refetch.
let cached: { ok: boolean; items: Release[] } | null = null

export function useReleases() {
  const [state, setState] = useState<{ loading: boolean; ok: boolean; items: Release[] }>(
    cached ? { loading: false, ...cached } : { loading: true, ok: true, items: [] },
  )

  useEffect(() => {
    if (cached) return
    let alive = true
    api?.listNews().then((result: { ok: boolean; items: Release[] } | undefined) => {
      cached = result ?? { ok: false, items: [] }
      if (alive) setState({ loading: false, ...cached })
    })
    return () => { alive = false }
  }, [])

  return state
}

/** "vor 3 Tagen" style, without pulling in a date library. */
export function relativeDate(iso: string): string {
  const then = new Date(iso).getTime()
  if (Number.isNaN(then)) return ''
  const minutes = Math.round((Date.now() - then) / 60000)
  if (minutes < 1) return 'gerade eben'
  if (minutes < 60) return `vor ${minutes} Min.`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `vor ${hours} Std.`
  const days = Math.round(hours / 24)
  if (days < 30) return days === 1 ? 'gestern' : `vor ${days} Tagen`
  return new Date(iso).toLocaleDateString('de-DE', { day: 'numeric', month: 'short', year: 'numeric' })
}

/** Release notes are Markdown; this keeps the text readable without rendering it. */
export function plainNotes(body: string): string[] {
  return body
    .split(/\r?\n/)
    .map(line => line.replace(/^#+\s*/, '').replace(/^\s*[-*]\s+/, '• ').replace(/\*\*(.+?)\*\*/g, '$1').trim())
    .filter(Boolean)
}
