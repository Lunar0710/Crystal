import Store from 'electron-store'

declare function fetch(url: string, init?: {
  method?: string
  headers?: Record<string, string>
  body?: string
}): Promise<{ ok: boolean; status: number; json(): Promise<any> }>

const API_URL = 'https://api.anthropic.com/v1/messages'
const MODEL = 'claude-opus-5'

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
          // Lets a declined request run again on the fallback model
          // Anthropic picks for that kind of refusal, instead of failing.
          'anthropic-beta': 'server-side-fallback-2026-07-01',
        },
        body: JSON.stringify({
          model: MODEL,
          fallbacks: 'default',
          // Thinking is on by default and counts against max_tokens; low effort
          // keeps it short for a three-line answer.
          max_tokens: 4000,
          output_config: { effort: 'low' },
          messages: [{
            role: 'user',
            content: `You are analyzing a Minecraft Java Edition crash report from a Fabric modded client ("Nexora Client"). Given the crash report below, respond with exactly three sections in this format, nothing else:\n\nSUMMARY: <one sentence, what crashed>\nCAUSE: <one or two sentences, most likely root cause>\nFIX: <concrete, actionable steps the user can take — e.g. remove a specific mod, update Java, lower render distance>\n\nCrash report:\n${trimmed}`,
          }],
        }),
      })

      if (!res.ok) {
        const body = await res.json().catch(() => ({}))
        return { success: false, error: `Anthropic API Fehler (HTTP ${res.status}): ${body?.error?.message || 'unbekannt'}` }
      }

      const data = await res.json()
      if (data?.stop_reason === 'refusal') {
        return { success: false, error: 'Claude hat die Analyse dieses Crash-Reports abgelehnt.' }
      }
      // content also holds thinking blocks now, so collect the text blocks
      // instead of reading content[0].
      const text: string = (data?.content || [])
        .filter((b: any) => b?.type === 'text')
        .map((b: any) => b.text)
        .join('\n')

      const summary = text.match(/SUMMARY:\s*(.+)/i)?.[1]?.trim()
      const likelyCause = text.match(/CAUSE:\s*(.+)/i)?.[1]?.trim()
      const suggestedFix = text.match(/FIX:\s*([\s\S]+)/i)?.[1]?.trim()

      return { success: true, summary, likelyCause, suggestedFix }
    } catch (err) {
      return { success: false, error: err instanceof Error ? err.message : 'Netzwerkfehler' }
    }
  }
}
