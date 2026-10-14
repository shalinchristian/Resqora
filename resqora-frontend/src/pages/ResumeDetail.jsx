import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { createPortal } from 'react-dom'
import { api } from '../api.js'
import { FEATURES } from '../features.js'
import Jobs from '../components/Jobs.jsx'
import { useCountUp, useMounted } from '../hooks.js'
import { Section, Button, TextArea, ErrorBox, Chip, toneFor } from '../components/ui.jsx'

const BAR = { good: 'bg-good', ok: 'bg-ok', bad: 'bg-bad' }
const TEXT = { good: 'text-good', ok: 'text-ok', bad: 'text-bad' }

function ScoreRow({ label, value, max = 20 }) {
  const pct = Math.round((value / max) * 100)
  const tone = toneFor(pct)
  const mounted = useMounted()
  return (
    <div>
      <div className="flex justify-between text-sm">
        <span>{label}</span>
        <span className="tabular-nums text-muted">{value}<span className="text-faint">/{max}</span></span>
      </div>
      <div className="h-1 bg-sunken rounded-full mt-1.5 overflow-hidden">
        <div className={`h-full rounded-full ${BAR[tone]}`} style={{ width: mounted ? `${Math.min(pct, 100)}%` : 0, transition: 'width .3s cubic-bezier(.2,.7,.2,1)' }} />
      </div>
    </div>
  )
}

const RING = { good: 'stroke-good', ok: 'stroke-ok', bad: 'stroke-bad' }

function Ring({ value, tone, size = 88 }) {
  const r = 38, c = 2 * Math.PI * r
  const shown = useCountUp(value)
  const mounted = useMounted()
  return (
    <div className="relative shrink-0" style={{ width: size, height: size }}>
      <svg viewBox="0 0 88 88" className="-rotate-90 w-full h-full">
        <circle cx="44" cy="44" r={r} fill="none" strokeWidth="7" className="stroke-sunken" />
        <circle cx="44" cy="44" r={r} fill="none" strokeWidth="7" strokeLinecap="round"
          className={RING[tone]} strokeDasharray={c} strokeDashoffset={mounted ? c * (1 - Math.min(value, 100) / 100) : c}
          style={{ transition: 'stroke-dashoffset .3s cubic-bezier(.2,.7,.2,1)' }} />
      </svg>
      <span className={`absolute inset-0 grid place-items-center text-2xl font-semibold tabular-nums tracking-tight ${TEXT[tone] || ''}`}>{shown}</span>
    </div>
  )
}

function Summary({ parsed, analysis }) {
  const tone = toneFor(analysis.overallScore)
  const label = { good: 'Strong', ok: 'Decent', bad: 'Needs work' }[tone]
  return (
    <Section>
      <h1 className="text-xl font-semibold tracking-tight break-words">{parsed.name || 'Unnamed candidate'}</h1>
      <div className="text-sm text-muted mt-1 space-y-0.5 break-words">
        {parsed.email && <p>{parsed.email}</p>}
        {(parsed.phone || parsed.location) && <p>{[parsed.phone, parsed.location].filter(Boolean).join(', ')}</p>}
      </div>

      <div className="mt-6 pt-6 border-t border-line flex items-center gap-5">
        <Ring value={analysis.overallScore} tone={tone} />
        <div>
          <p className={`text-sm font-medium ${TEXT[tone]}`}>{label}</p>
          <p className="text-xs text-muted mt-0.5">Overall score out of 100</p>
        </div>
      </div>

      <div className="mt-6 space-y-4">
        <ScoreRow label="Completeness" value={analysis.completenessScore} />
        <ScoreRow label="Skills" value={analysis.skillsScore} />
        <ScoreRow label="Projects" value={analysis.projectsScore} />
        <ScoreRow label="Experience" value={analysis.experienceScore} />
        <ScoreRow label="Education" value={analysis.educationScore} />
      </div>
      <ScoreGuide />
    </Section>
  )
}

const GUIDE = [
  ['Completeness', 'Name and email (4 each), phone, skills, education and projects (3 each).'],
  ['Skills', 'Technical skills only. 1 to 4 gives 8, 5 to 9 gives 14, 10 or more gives 20. Soft skills like Leadership are not counted.'],
  ['Projects', 'Any project gives 8. Describing what you built gives 14. Two or more described projects give 20.'],
  ['Experience', 'No experience still gets 10, so students are not punished. Described roles raise it to 16, and two or more give 20.'],
  ['Education', 'Listing education gives 5. Institution and degree add 5 each, dates add 3, and details like CGPA add 2.'],
]

function ScoreGuide() {
  return (
    <details className="group mt-6 pt-5 border-t border-line">
      <summary className="cursor-pointer list-none flex items-center justify-between text-sm font-medium select-none">
        How this score works
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="text-muted transition-transform duration-200 group-open:rotate-180"><path d="m6 9 6 6 6-6" /></svg>
      </summary>
      <p className="text-xs text-muted mt-3">Five parts, each out of 20, added up to 100. The same resume always gets the same score. No AI is used for scoring.</p>
      <dl className="mt-3 space-y-2.5">
        {GUIDE.map(([k, v]) => (
          <div key={k} className="text-xs"><dt className="font-medium">{k}</dt><dd className="text-muted mt-0.5">{v}</dd></div>
        ))}
      </dl>
    </details>
  )
}

function Findings({ findings }) {
  return (
    <Section title="Findings">
      {findings.length === 0 ? <p className="text-sm text-muted">No findings.</p> : (
        <ul className="space-y-3">
          {findings.map((f, i) => (
            <li key={i} className="fade-up flex gap-3 text-sm" style={{ animationDelay: `${120 + i * 60}ms` }}>
              <span className={`mt-1.5 w-2 h-2 shrink-0 rounded-full ${f.type === 'WARNING' ? 'bg-ok' : 'bg-good'}`} />
              <div className="min-w-0">
                <p><span className="font-medium">{f.category}.</span> <span className="text-muted">{f.message}</span></p>
                {f.line != null && f.line !== '' && (
                  typeof f.line === 'number'
                    ? <p className="text-xs text-faint mt-1">Line {f.line}</p>
                    : <p className="mt-1.5 text-xs font-mono text-muted bg-sunken border-l-2 border-ok rounded-r px-2 py-1 break-words">{f.line}</p>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </Section>
  )
}

function MatchBox({ id, jd, setJd, open, setOpen }) {
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const run = async () => {
    setError(''); setBusy(true)
    try { setResult(await api.match(id, jd)) } catch (e) { setError(e.message) } finally { setBusy(false) }
  }

  return (
    <div id="job-box">
      <button onClick={() => setOpen(!open)} aria-expanded={open} className="text-sm text-muted hover:text-ink inline-flex items-center gap-1.5">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className={`transition-transform duration-200 ${open ? 'rotate-90' : ''}`}><path d="m9 6 6 6-6 6" /></svg>
        Compare with a job (optional)
      </button>
      {open && (
        <div className="mt-3 bg-surface border border-line rounded-xl p-4 sm:p-5 fade-up">
          <label htmlFor="jd-match" className="block text-sm font-medium mb-1.5">Job description</label>
          <TextArea id="jd-match" rows={4} maxLength={10000} placeholder="Paste the job post here" value={jd} onChange={(e) => setJd(e.target.value)} />
          <p className="text-xs text-muted mt-1.5">The chat also uses this when you ask "Should I apply?".</p>
          <div className="mt-3 flex flex-col sm:flex-row sm:items-center gap-3">
            <Button onClick={run} disabled={busy || !jd.trim()}>{busy ? 'Matching...' : 'Check match'}</Button>
            {error && <div className="flex-1"><ErrorBox message={error} /></div>}
          </div>
          {result && (
            <div className="fade-up mt-5 pt-4 border-t border-line">
              {result.matchedSkills.length === 0 && result.missingSkills.length === 0 ? (
                <p className="text-sm text-muted">No known skills were recognised in this job description.</p>
              ) : (
                <div className="grid sm:grid-cols-[auto_1fr] gap-5 sm:gap-8 items-start">
                  <div>
                    <Ring value={result.matchScore} tone={toneFor(result.matchScore)} size={72} />
                    <p className="text-xs text-muted mt-1 text-center">match %</p>
                  </div>
                  <div className="space-y-3">
                    <div>
                      <p className="text-xs uppercase tracking-wide text-muted mb-1.5">You have</p>
                      {result.matchedSkills.length ? result.matchedSkills.map((x) => <Chip key={x} tone="good">{x}</Chip>) : <span className="text-sm text-muted">None</span>}
                    </div>
                    <div>
                      <p className="text-xs uppercase tracking-wide text-muted mb-1.5">Missing</p>
                      {result.missingSkills.length ? result.missingSkills.map((x) => <Chip key={x} tone="bad">{x}</Chip>) : <span className="text-sm text-muted">None</span>}
                    </div>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  )
}

function AiSummary({ id }) {
  const [state, setState] = useState({ loading: true })
  useEffect(() => {
    api.summary(id).then((d) => setState({ text: d.summary })).catch((e) => setState({ hidden: e.status === 503, error: e.status !== 503 }))
  }, [id])
  if (state.hidden || state.error) return null
  return (
    <div className="bg-surface border border-line rounded-xl px-5 py-4">
      <p className="text-xs uppercase tracking-wide text-muted">Summary</p>
      {state.loading
        ? <div className="mt-2 space-y-2"><div className="h-3 bg-sunken rounded w-full animate-pulse" /><div className="h-3 bg-sunken rounded w-2/3 animate-pulse" /></div>
        : <p className="mt-1.5 text-sm leading-relaxed whitespace-pre-line">{state.text}</p>}
    </div>
  )
}

// Word files are drawn by docx-preview, then shown in a sandboxed iframe with links removed,
// so nothing inside an uploaded file can run on our page. The CSP line also stops it loading
// anything from the internet (only images stored inside the file itself can show).
const CSP = "default-src 'none'; img-src data: blob:; style-src 'unsafe-inline'; font-src data:"
async function docxToHtml(blob) {
  const { renderAsync } = await import('docx-preview')
  const body = document.createElement('div')
  const style = document.createElement('div')
  await renderAsync(blob, body, style, { inWrapper: false, ignoreLastRenderedPageBreak: true })
  for (const el of body.querySelectorAll('script, iframe, object, embed, link')) el.remove()
  for (const a of body.querySelectorAll('a')) a.replaceWith(...a.childNodes)
  for (const el of body.querySelectorAll('*')) for (const at of [...el.attributes]) if (at.name.startsWith('on')) el.removeAttribute(at.name)
  return `<!doctype html><html><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="${CSP}">${style.innerHTML}<style>html,body{margin:0;background:#fff;overflow:hidden}.docx-wrapper{background:#fff!important;padding:0!important}section.docx{margin:0!important;box-shadow:none!important;min-height:0!important}</style></head><body>${body.innerHTML}</body></html>`
}

// Scales the Word page to the frame's width and sizes the frame to the page.
// A frame that is hidden has 0 width, so we wait until it is visible.
function fitDocx(frame) {
  const doc = frame.contentDocument
  if (!doc?.body || !frame.clientWidth) return
  doc.body.style.zoom = 1
  const page = doc.querySelector('section.docx') || doc.body
  const scale = Math.min(1, frame.clientWidth / (page.scrollWidth || 794))
  doc.body.style.zoom = scale
  const r = page.getBoundingClientRect() // measured after zoom, so no extra multiply
  frame.style.height = `${Math.ceil(r.top + r.height) + 2}px`
}

// One Word frame that re-fits whenever its width changes (window resize, pop-up opening, etc).
function DocxFrame({ html, className = '' }) {
  const ref = useRef(null)
  useEffect(() => {
    const f = ref.current
    if (!f) return
    const ro = new ResizeObserver(() => fitDocx(f))
    ro.observe(f)
    return () => ro.disconnect()
  }, [html])
  return <iframe ref={ref} title="Resume preview" sandbox="allow-same-origin" srcDoc={html} tabIndex={-1}
    onLoad={(e) => fitDocx(e.currentTarget)} className={`block w-full bg-white ${className}`} />
}

function ResumeModal({ view, onClose }) {
  const closeRef = useRef(null)
  useEffect(() => {
    const key = (e) => {
      if (e.key === 'Escape') onClose()
      if (e.key === 'Tab') { e.preventDefault(); closeRef.current?.focus() } // Close is the only control, so Tab stays on it
    }
    document.addEventListener('keydown', key)
    const prev = document.body.style.overflow
    const opener = document.activeElement
    const page = document.getElementById('root')
    document.body.style.overflow = 'hidden'
    page?.setAttribute('inert', '') // the page behind can't be tabbed to or clicked
    closeRef.current?.focus()
    return () => {
      document.removeEventListener('keydown', key)
      document.body.style.overflow = prev
      page?.removeAttribute('inert')
      opener?.focus?.()
    }
  }, [onClose])
  return (
  createPortal(
    <div className="fixed inset-0 z-[100] bg-black/60 flex items-start justify-center sm:p-6" onClick={onClose}>
      <div role="dialog" aria-modal="true" aria-label="Your resume" onClick={(e) => e.stopPropagation()}
        className="relative w-full max-w-3xl h-full sm:h-auto sm:max-h-full overflow-y-auto bg-surface sm:rounded-xl border border-line">
        <div className="sticky top-0 z-10 flex items-center justify-between px-4 py-3 bg-surface border-b border-line">
          <p className="font-medium text-sm">Your resume</p>
          <button ref={closeRef} onClick={onClose} aria-label="Close" className="h-8 w-8 grid place-items-center rounded-md hover:bg-sunken">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M18 6 6 18M6 6l12 12" /></svg>
          </button>
        </div>
        <div className="p-3 sm:p-4">
          {view.pdf && <iframe title="Resume" src={`${view.pdf}#view=FitH`} className="w-full h-[80vh] rounded bg-white" />}
          {view.html && <DocxFrame html={view.html} className="rounded" />}
        </div>
      </div>
    </div>,
    document.body)
  )
}

function Preview({ id }) {
  const [view, setView] = useState({ loading: true })
  const [open, setOpen] = useState(false)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let url
    setView({ loading: true })
    api.file(id).then(async (blob) => {
      if (blob.type.includes('pdf')) { url = URL.createObjectURL(blob); setView({ pdf: url }) }
      else setView({ html: await docxToHtml(blob) })
    }).catch((e) => setView(e.status === 404 ? { missing: true } : { failed: true }))
    return () => url && URL.revokeObjectURL(url)
  }, [id, attempt])

  return (
    <div className="bg-surface border border-line rounded-xl p-4">
      <p className="text-sm font-medium mb-3">Your resume</p>
      {view.loading && <div className="h-56 rounded-lg bg-sunken animate-pulse" />}
      {(view.pdf || view.html) && (
        <>
          <button onClick={() => setOpen(true)} aria-label="View full resume"
            className="relative block w-full h-56 overflow-hidden rounded-lg border border-line bg-white text-left">
            {view.pdf && <iframe title="Resume thumbnail" tabIndex={-1} src={`${view.pdf}#view=FitH&toolbar=0`} className="w-full h-[600px] pointer-events-none" />}
            {view.html && <div className="pointer-events-none"><DocxFrame html={view.html} /></div>}
            <span className="absolute inset-x-0 bottom-0 h-16 bg-gradient-to-t from-white to-transparent" />
          </button>
          <button onClick={() => setOpen(true)} className="mt-3 w-full h-9 rounded-md border border-line text-sm font-medium hover:bg-sunken transition-colors">View full resume</button>
          {open && <ResumeModal view={view} onClose={() => setOpen(false)} />}
        </>
      )}
      {view.missing && <p className="text-sm text-muted">Preview isn't available for this resume. Upload it again to see it here.</p>}
      {view.failed && (
        <p className="text-sm text-muted">Couldn't load the preview. <button onClick={() => setAttempt((n) => n + 1)} className="text-accent">Try again</button></p>
      )}
    </div>
  )
}

const STARTERS = [
  { text: 'Which line should I fix first?' },
  { text: 'Give me 5 interview questions based on this resume.' },
  { text: 'Rewrite my weakest bullet point.' },
  { text: 'Should I apply for this job?', needsJd: true },
]

function Chat({ id, jd, openJobBox }) {
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [left, setLeft] = useState(null)
  const end = useRef(null)

  useEffect(() => { end.current?.scrollIntoView({ block: 'nearest', behavior: 'smooth' }) }, [messages, busy])

  const send = async (text, needsJd) => {
    text = text.trim()
    if (!text || busy) return
    if (needsJd && !jd.trim()) { openJobBox(); return setError('Paste the job post in "Compare with a job" first.') }
    setError(''); setInput('')
    const history = messages.slice(-6).map((m) => ({ role: m.role, text: m.text.slice(0, 500) }))
    setMessages((m) => [...m, { role: 'user', text }])
    setBusy(true)
    try {
      const r = await api.chat(id, text, history, jd.trim().slice(0, 5000) || undefined)
      setMessages((m) => [...m, { role: 'model', text: r.reply }])
      setLeft(r.remainingToday)
    } catch (e) {
      setMessages((m) => m.slice(0, -1)); setInput(text)
      setError(e.status === 429 ? "You've used today's AI messages. Try again tomorrow."
        : e.status === 503 ? 'AI chat not available right now.' : e.message)
    } finally { setBusy(false) }
  }

  return (
    <Section title="Ask about this resume" note={left != null ? `${left} messages left today` : '10 AI messages a day'}>
      {messages.length === 0 && (
        <div className="flex flex-wrap gap-2">
          {STARTERS.map((st) => (
            <button key={st.text} onClick={() => send(st.text, st.needsJd)} disabled={busy}
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
        <label htmlFor="chat-input" className="sr-only">Your question</label>
        <input id="chat-input" value={input} maxLength={500} onChange={(e) => setInput(e.target.value)} placeholder="Ask a question about this resume"
          className="flex-1 min-w-0 rounded-lg border border-line bg-surface px-3 h-10 text-sm outline-none focus:border-accent" />
        <Button type="submit" variant="accent" className="h-10" disabled={busy || !input.trim()}>Send</Button>
      </form>
      {error && <div className="mt-3"><ErrorBox message={error} /></div>}
    </Section>
  )
}

function Parsed({ parsed }) {
  const groups = [
    ['Experience', parsed.experience.map((x) => [x.title, x.company, x.dates])],
    ['Education', parsed.education.map((x) => [x.degree, x.institution, x.dates])],
    ['Projects', parsed.projects.map((x) => [x.name, x.description])],
  ].filter(([, items]) => items.length)

  return (
    <Section title="Parsed details" style={{ animationDelay: '240ms' }}>
      <p className="text-xs uppercase tracking-wide text-muted mb-1.5">Skills</p>
      <div>{parsed.skills.length ? parsed.skills.map((s) => <Chip key={s}>{s}</Chip>) : <span className="text-sm text-muted">None detected</span>}</div>

      {groups.map(([title, items]) => (
        <div key={title} className="mt-5">
          <p className="text-xs uppercase tracking-wide text-muted mb-2">{title}</p>
          <ul className="space-y-2 text-sm">
            {items.map(([main, sub, extra], i) => (
              <li key={i} className="break-words">
                <span className="font-medium">{main || 'Untitled'}</span>
                {sub && <span className="text-muted">, {sub}</span>}
                {extra && <span className="text-faint">, {extra}</span>}
              </li>
            ))}
          </ul>
        </div>
      ))}

      {parsed.certifications.length > 0 && (
        <div className="mt-5">
          <p className="text-xs uppercase tracking-wide text-muted mb-1.5">Certifications</p>
          {parsed.certifications.map((c) => <Chip key={c}>{c}</Chip>)}
        </div>
      )}
    </Section>
  )
}

export default function ResumeDetail() {
  const { id } = useParams()
  const [parsed, setParsed] = useState(null)
  const [analysis, setAnalysis] = useState(null)
  const [error, setError] = useState('')
  const [jd, setJd] = useState('')
  const [jobOpen, setJobOpen] = useState(false)

  useEffect(() => {
    Promise.all([api.parsed(id), api.analysis(id)])
      .then(([p, a]) => { setParsed(p); setAnalysis(a) })
      .catch((e) => setError(e.message))
  }, [id])

  const back = <Link to="/" className="text-sm text-muted hover:text-ink">← All resumes</Link>

  if (error) return <div className="space-y-4">{back}<ErrorBox message={error} /></div>
  if (!parsed || !analysis) return <p className="text-muted">Loading...</p>

  return (
    <div className="space-y-6">
      {back}
      {analysis.findings.find((f) => f.category === 'Layout' && f.type === 'WARNING') && (
        <div role="alert" className="rounded-xl border border-ok bg-ok-soft px-4 py-3 text-sm">
          <p className="font-medium text-ok">This resume couldn't be read properly</p>
          <p className="text-muted mt-0.5">{analysis.findings.find((f) => f.category === 'Layout').message}</p>
        </div>
      )}
      <AiSummary id={id} />
      <div className="grid lg:grid-cols-[minmax(0,1fr)_340px] gap-6 items-start">
        <div className="space-y-6 lg:col-start-2 lg:row-start-1">
          <Summary parsed={parsed} analysis={analysis} />
          <Preview id={id} />
        </div>
        <div className="space-y-6 min-w-0 lg:col-start-1 lg:row-start-1">
          <Findings findings={analysis.findings} />
          <Chat id={id} jd={jd} openJobBox={() => setJobOpen(true)} />
          <MatchBox id={id} jd={jd} setJd={setJd} open={jobOpen} setOpen={setJobOpen} />
          {FEATURES.jobs && <Jobs resumeId={id} />}
          <Parsed parsed={parsed} />
        </div>
      </div>
    </div>
  )
}
