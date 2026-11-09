import { useEffect, useId, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { useAuth } from '../auth.jsx'
import { api } from '../api.js'
import { Section, Button, ErrorBox } from './ui.jsx'

// Shared chat box, used for one resume and for the dashboard chat across all resumes.
// send(text, history) must return { reply, remainingToday, limit }.
// kind picks this chat's own daily allowance from GET /api/ai/usage: 'resumeChat' or 'dashboardChat'.
export default function ChatPanel({ kind = 'resumeChat', title, placeholder, starters = [], send: sendFn, beforeSend, onUsage, showUsage = true, style }) {
  const { user } = useAuth()
  const onDashboard = useLocation().pathname === '/'
  const aiOn = user?.aiEnabled !== false
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [usage, setUsage] = useState(null)
  const end = useRef(null)
  const inputId = useId()

  // show today's allowance before the first message
  useEffect(() => {
    if (aiOn) api.aiUsage().then((u) => {
      const mine = u?.[kind] || u
      if (mine?.remainingToday != null) setUsage({ left: mine.remainingToday, limit: mine.limit })
    }).catch(() => {})
  }, [aiOn, kind])

  useEffect(() => { end.current?.scrollIntoView({ block: 'nearest', behavior: 'smooth' }) }, [messages, busy])

  const send = async (text, starter) => {
    text = text.trim()
    if (!text || busy) return
    const blocked = beforeSend?.(starter)
    if (blocked) return setError(blocked)
    setError(''); setInput('')
    const history = messages.slice(-6).map((m) => ({ role: m.role, text: m.text.slice(0, 500) }))
    setMessages((m) => [...m, { role: 'user', text }])
    setBusy(true)
    try {
      const r = await sendFn(text, history)
      setMessages((m) => [...m, { role: 'model', text: r.reply }])
      if (r.remainingToday != null) { setUsage({ left: r.remainingToday, limit: r.limit }); onUsage?.({ remainingToday: r.remainingToday, limit: r.limit }) }
    } catch (e) {
      setMessages((m) => m.slice(0, -1)); setInput(text)
      if (e.status === 429 && usage) setUsage({ ...usage, left: 0 })
      setError(e.status === 429 ? (e.message && !/^Request failed/.test(e.message) ? e.message : "You've used today's chats here. They reset tomorrow.")
        : e.code === 'AI_DISABLED' ? 'AI is turned off for your account.'
        : e.status === 503 ? 'AI chat is not available right now.' : e.message)
    } finally { setBusy(false) }
  }

  const note = !aiOn ? 'AI is off' : usage && showUsage ? `${usage.left} of ${usage.limit} chats left today` : null

  return (
    <Section title={title} note={note} style={style}>
      {!aiOn ? (
        <p className="text-sm text-muted">AI is turned off, so nothing from your resumes is sent to an AI service. {onDashboard ? 'Turn it back on with the switch above.' : <>You can turn it back on from <Link to="/" className="text-accent hover:underline">your dashboard</Link>.</>}</p>
      ) : (
        <>
          {messages.length === 0 && starters.length > 0 && (
            <div className="flex flex-wrap gap-2">
              {starters.map((st) => (
                <button key={st.text} onClick={() => send(st.text, st)} disabled={busy}
                  className="text-sm text-left rounded-lg border border-line px-3 py-1.5 hover:bg-sunken transition-colors disabled:opacity-40">{st.text}</button>
              ))}
            </div>
          )}
          {messages.length > 0 && (
            <div className="space-y-3 max-h-[28rem] overflow-y-auto pr-1" aria-live="polite">
              {messages.map((m, i) => (
                <div key={i} className={`flex ${m.role === 'user' ? 'justify-end' : ''}`}>
                  <p className={`max-w-[85%] text-sm rounded-xl px-3.5 py-2 whitespace-pre-line break-words ${m.role === 'user' ? 'bg-ink text-paper' : 'bg-sunken'}`}>{m.text}</p>
                </div>
              ))}
              {busy && <p className="text-xs text-muted">Writing a reply...</p>}
              <div ref={end} />
            </div>
          )}
          <form onSubmit={(e) => { e.preventDefault(); send(input) }} className="mt-4 flex gap-2">
            <label className="sr-only" htmlFor={inputId}>Your question</label>
            <input id={inputId} name="resqora-chat-message" autoComplete="off" autoCorrect="off" data-1p-ignore data-lpignore="true" data-form-type="other" value={input} maxLength={500} onChange={(e) => setInput(e.target.value)} placeholder={placeholder}
              className="flex-1 min-w-0 rounded-lg border border-line bg-surface px-3 h-10 text-sm outline-none focus:border-accent" />
            <Button type="submit" variant="accent" className="h-10" disabled={busy || !input.trim()}>Send</Button>
          </form>
          {error && <div className="mt-3"><ErrorBox message={error} /></div>}
        </>
      )}
    </Section>
  )
}
