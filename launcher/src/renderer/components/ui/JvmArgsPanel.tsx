import React, { useEffect, useState } from 'react'
import { ChevronDown, Terminal } from 'lucide-react'
import { notify } from '../../store/notificationStore'

const api = (window as any).crystal

/** Splits "-a -b=\"x y\"" into its arguments; quotes keep spaces together. */
function splitArgs(text: string): string[] {
  const out: string[] = []
  for (const m of text.matchAll(/"([^"]*)"|'([^']*)'|(\S+)/g)) out.push(m[1] ?? m[2] ?? m[3])
  return out
}

function joinArgs(args: string[]): string {
  return args.map(a => (/\s/.test(a) ? `"${a}"` : a)).join(' ')
}

/** The instance's own Java arguments, for mods that need a -javaagent or a -D switch. */
export function JvmArgsPanel({ instanceId, jvmArgs }: { instanceId: string; jvmArgs?: string[] }) {
  const [open, setOpen] = useState(false)
  const [text, setText] = useState(joinArgs(jvmArgs ?? []))
  const [saved, setSaved] = useState(joinArgs(jvmArgs ?? []))

  // Read fresh: the instance object handed in can be older than the last save.
  useEffect(() => {
    let alive = true
    api?.getInstances().then((list: { id: string; jvmArgs?: string[] }[] | undefined) => {
      const current = joinArgs(list?.find(i => i.id === instanceId)?.jvmArgs ?? [])
      if (!alive) return
      setText(current)
      setSaved(current)
    })
    return () => { alive = false }
  }, [instanceId])

  async function save() {
    const wanted = splitArgs(text)
    const updated = await api?.updateInstance(instanceId, { jvmArgs: wanted })
    const kept: string[] = updated?.jvmArgs ?? []
    const dropped = wanted.filter(a => !kept.includes(a.trim()))
    setText(joinArgs(kept))
    setSaved(joinArgs(kept))
    notify(dropped.length
      ? { type: 'warning', title: 'Java-Argumente', message: `Gespeichert, ohne: ${dropped.join(' ')}. RAM stellst du in den Einstellungen ein, der Klassenpfad bleibt Nexoras.` }
      : { type: 'success', title: 'Java-Argumente', message: kept.length ? 'Gilt ab dem nächsten Start.' : 'Geleert.' })
  }

  const count = splitArgs(saved).length

  return (
    <section className="crystal-card mb-4">
      <button onClick={() => setOpen(!open)} aria-expanded={open} className="w-full flex items-center gap-3 px-4 py-3 text-left">
        <Terminal size={15} strokeWidth={1.75} className="text-crystal-muted shrink-0" />
        <span className="flex-1">
          <span className="block text-[13px] text-crystal-text">Java-Argumente{count ? ` (${count})` : ''}</span>
          <span className="block text-xs text-crystal-muted">Für Mods, die einen -javaagent oder einen eigenen -D-Schalter brauchen.</span>
        </span>
        <ChevronDown size={14} className={`text-crystal-muted transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>
      {open && (
        <div className="border-t border-crystal-border px-4 py-3 space-y-2">
          <textarea
            value={text}
            onChange={e => setText(e.target.value)}
            rows={2}
            spellCheck={false}
            className="crystal-input w-full font-mono text-xs"
            placeholder="-javaagent:C:\Pfad\agent.jar -Dbeispiel=true"
            aria-label="Java-Argumente"
          />
          <div className="flex items-center justify-between gap-3">
            <p className="text-xs text-crystal-muted">Jedes Argument beginnt mit "-". Pfade mit Leerzeichen in Anführungszeichen. RAM stellst du in den Einstellungen ein.</p>
            <button onClick={save} disabled={text === saved} className="crystal-btn-primary text-xs px-3 py-1.5 shrink-0 disabled:opacity-50">
              Speichern
            </button>
          </div>
        </div>
      )}
    </section>
  )
}
