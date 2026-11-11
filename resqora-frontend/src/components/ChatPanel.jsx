import { useEffect, useId, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { useAuth } from '../auth.jsx'
import { api } from '../api.js'
import { Section, Button, ErrorBox } from './ui.jsx'

// Shared chat box, used for one resume and for the dashboard chat across all resumes.
// send(text, history) must return { reply, remainingToday, limit }.
// loadHistory() returns { messages: [{ role: 'user' | 'assistant', content }] } (last 20, oldest first); clearHistory() deletes it.
// kind picks this chat's own daily allowance from GET /api/ai/usage: 'resumeChat' or 'dashboardChat'.
export default function ChatPanel({ kind = 'resumeChat', title, placeholder, starters = [], send: sendFn, beforeSend, onUsage, showUsage = true, style, historyKey, loadHistory, clearHistory }) {
  const { user } = useAuth()
  const onDashboard = useLocation().pathname === '/'
  const aiOn = user?.aiEnabled !== false
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [usage, setUsage] = useState(null)
  const [loading, setLoading] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [clearing, setClearing] = useState(false)
  const end = useRef(null)
  const box = useRef(null)
  const inputId = useId()

  // show today's allowance before the first message
  useEffect(() => {
    if (aiOn) api.aiUsage().then((u) => {
      const mine = u?.[kind] || u
      if (mine?.remainingToday != null) setUsage({ left: mine.remainingToday, limit: mine.limit })
    }).catch(() => {})
  }, [aiOn, kind])

  // saved chat: load the last messages when the chat opens (doesn't count toward the daily chats)
  useEffect(() => {
    if (!aiOn || !loadHistory) return
    let live = true
    setLoading(true); setMessages([]); setConfirming(false)
    loadHistory()
      .then((d) => {
        if (!live) return
        // server sends { role: 'user' | 'assistant', content }; the chat uses 'model' and text
        const saved = (d?.messages || [])
          .map((m) => ({ role: m?.role === 'user' ? 'user' : m?.role === 'assistant' || m?.role === 'model' ? 'model' : null, text: m?.content ?? m?.text ?? '' }))
          .filter((m) => m.role && m.text)
        setMessages((now) => (now.length ? now : saved.slice(-20)))
      })
      .catch(() => {}) // no saved chat yet, or history isn't available: start empty
      .finally(() => { if (live) setLoading(false) })
    return () => { live = false }
  }, [aiOn, historyKey]) // eslint-disable-line react-hooks/exhaustive-deps

  // keep the newest message in view inside the chat box only, never scroll the page
  useEffect(() => { const el = box.current; if (el) el.scrollTop = el.scrollHeight }, [messages, busy])

  const clear = async () => {
    setClearing(true); setError('')
    try {
      await clearHistory?.()
      setMessages([]); setConfirming(false)
    } catch (e) {
      setError(e.status === 0 ? e.message : "Couldn't clear the chat. Please try again.")
    } finally { setClearing(false) }
  }

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
          {loading && messages.length === 0 && <p className="text-sm text-muted">Loading your chat...</p>}
          {!loading && messages.length === 0 && starters.length > 0 && (
            <div className="flex flex-wrap gap-2">
              {starters.map((st) => (
                <button key={st.text} onClick={() => send(st.text, st)} disabled={busy}
                  className="text-sm text-left rounded-lg border border-line px-3 py-1.5 hover:bg-sunken transition-colors disabled:opacity-40">{st.text}</button>
              ))}
            </div>
          )}
          {messages.length > 0 && (
            <div ref={box} className="space-y-3 max-h-[28rem] overflow-y-auto overscroll-contain pr-1" aria-live="polite">
              {messages.map((m, i) => (
                <div key={i} className={`flex ${m.role === 'user' ? 'justify-end' : ''}`}>
                  <p className={`max-w-[85%] text-sm rounded-xl px-3.5 py-2 whitespace-pre-line break-words ${m.role === 'user' ? 'bg-ink text-paper' : 'bg-sunken'}`}>{m.text}</p>
                </div>
              ))}
              {busy && <p className="text-xs text-muted">Writing a reply...</p>}
              <div ref={end} />
            </div>
          )}
          {messages.length > 0 && clearHistory && (
            confirming ? (
              <div className="mt-3 flex flex-wrap items-center gap-2 rounded-lg border border-line bg-sunken px-3 py-2" role="alertdialog" aria-label="Clear this chat?">
                <p className="text-sm flex-1 min-w-[10rem]">Clear this chat? Your saved messages are deleted for good.</p>
                <Button variant="ghost" className="h-11 px-4" onClick={() => setConfirming(false)} disabled={clearing}>Cancel</Button>
                <Button variant="danger" className="h-11 px-4" onClick={clear} disabled={clearing}>{clearing ? 'Clearing...' : 'Clear chat'}</Button>
              </div>
            ) : (
              <div className="mt-2 flex justify-end">
                <button type="button" onClick={() => setConfirming(true)} disabled={busy}
                  className="text-[13px] text-muted hover:text-ink px-2 h-11 -mr-2 disabled:opacity-40">Clear chat</button>
              </div>
            )
          )}
          <form onSubmit={(e) => { e.preventDefault(); send(input) }} className={`${messages.length && clearHistory ? "mt-1" : "mt-4"} flex gap-2`}>
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
