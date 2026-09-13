import Store from 'electron-store'

declare function fetch(url: string, init?: {
  method?: string
  headers?: Record<string, string>
  body?: string
}): Promise<{ ok: boolean; status: number; json(): Promise<any> }>

const API_URL = 'https://api.anthropic.com/v1/messages'
const MODEL = 'claude-sonnet-4-5'

export interface CrashAnalysis {
  success: boolean
  summary?: string
  likelyCause?: string
  suggestedFix?: string
  error?: string
}

export class ClaudeService {
  private store: Store

  constructor(store: Store) {
    this.store = store
  }

  getApiKey(): string {
    return (this.store.get('claude.apiKey') as string) || ''
  }

  setApiKey(key: string) {
    this.store.set('claude.apiKey', key)
  }

  hasApiKey(): boolean {
    return this.getApiKey().trim().length > 0
  }

  async analyzeCrash(crashText: string): Promise<CrashAnalysis> {
    const apiKey = this.getApiKey()
    if (!apiKey) {
      return { success: false, error: 'Kein Anthropic API-Key hinterlegt (Settings → Crash-Analyse).' }
    }

    // Crash reports can be huge; the stack trace + first exception is what
    // actually matters for diagnosis, so trim from the front.
    const trimmed = crashText.length > 12_000 ? crashText.slice(0, 12_000) : crashText

    try {
      const res = await fetch(API_URL, {
        method: 'POST',
        headers: {
          'content-type': 'application/json',
          'x-api-key': apiKey,
          'anthropic-version': '2023-06-01',
        },
        body: JSON.stringify({
          model: MODEL,
          max_tokens: 800,
          messages: [{
            role: 'user',
            content: `You are analyzing a Minecraft Java Edition crash report from a Fabric modded client ("Crystal Client"). Given the crash report below, respond with exactly three sections in this format, nothing else:\n\nSUMMARY: <one sentence, what crashed>\nCAUSE: <one or two sentences, most likely root cause>\nFIX: <concrete, actionable steps the user can take — e.g. remove a specific mod, update Java, lower render distance>\n\nCrash report:\n${trimmed}`,
          }],
        }),
      })

      if (!res.ok) {
        const body = await res.json().catch(() => ({}))
        return { success: false, error: `Anthropic API Fehler (HTTP ${res.status}): ${body?.error?.message || 'unbekannt'}` }
      }

      const data = await res.json()
      const text: string = data?.content?.[0]?.text || ''

      const summary = text.match(/SUMMARY:\s*(.+)/i)?.[1]?.trim()
      const likelyCause = text.match(/CAUSE:\s*(.+)/i)?.[1]?.trim()
      const suggestedFix = text.match(/FIX:\s*([\s\S]+)/i)?.[1]?.trim()

      return { success: true, summary, likelyCause, suggestedFix }
    } catch (err) {
      return { success: false, error: err instanceof Error ? err.message : 'Netzwerkfehler' }
    }
  }
}
