import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Link } from 'react-router-dom'
import { api, ApiError } from '../api.js'
import { useAuth } from '../auth.jsx'
import { Button, ErrorBox, Input, TextArea } from '../components/ui.jsx'
import ResumeDocument from '../builder/ResumeDocument.jsx'
import { BulletList, SkillsInput } from '../builder/inputs.jsx'
import {
  TEMPLATES, emptyDraft, prefillDraft, toPayload, fileBaseName,
  addJob, addSchool, addProject, moveItem,
  saveDraft, loadDraft, clearDraft, isEmptyDraft, LIMITS,
} from '../builder/model.js'

// numbered steps after the start screen
const STEPS = [
  { id: 'format', label: 'Format' },
  { id: 'form', label: 'Details' },
  { id: 'preview', label: 'Download' },
]

const SECTIONS = [
  { id: 'contact', label: 'Contact' },
  { id: 'summary', label: 'Summary' },
  { id: 'skills', label: 'Skills' },
  { id: 'experience', label: 'Experience' },
  { id: 'education', label: 'Education' },
  { id: 'projects', label: 'Projects' },
  { id: 'extras', label: 'Extras' },
]

const LISTS = {
  experience: { noun: 'job', add: addJob, max: LIMITS.experience,
    title: (x) => x.title || x.company || 'New job',
    sub: (x) => [x.title && x.company, x.dates].filter(Boolean).join(' · '), bullets: (x) => x.bullets },
  education: { noun: 'school', add: addSchool, max: LIMITS.education,
    title: (x) => x.degree || x.school || 'New school',
    sub: (x) => [x.degree && x.school, x.dates].filter(Boolean).join(' · '), bullets: (x) => x.details },
  projects: { noun: 'project', add: addProject, max: LIMITS.projects,
    title: (x) => x.name || 'New project',
    sub: (x) => [x.stack, x.dates].filter(Boolean).join(' · '), bullets: (x) => x.bullets },
}
const filledRow = (x) => Object.entries(x).some(([k, v]) => k !== 'id' && typeof v === 'string' && v.trim())
const countLines = (t) => (t || '').split('\n').filter((l) => l.trim()).length

const MAX_SIZE = 2 * 1024 * 1024

function ScaledSheet({ children, zoom = false }) {
  const outer = useRef(null)
  const inner = useRef(null)
  const [scale, setScale] = useState(1)
  const [height, setHeight] = useState(1123)
  useEffect(() => {
    const el = outer.current
    if (!el) return
    const measure = () => {
      const fit = Math.min(1, el.clientWidth / 794)
      // zoomed: real-size-ish text (11pt reads as ~11px) and the page scrolls sideways
      const next = zoom ? Math.max(fit, 0.75) : fit
      setScale(next)
      setHeight(inner.current ? inner.current.offsetHeight : 1123)
    }
    measure()
    const ro = new ResizeObserver(measure)
    ro.observe(el)
    return () => ro.disconnect()
  }, [children, zoom])
  return (
    <div ref={outer} className="w-full overflow-x-auto overflow-y-hidden" style={{ height: height * scale + (zoom ? 12 : 0) }}>
      <div style={{ width: 794 * scale, height: height * scale, overflow: 'hidden' }}>
        <div ref={inner} style={{ width: 794, transform: `scale(${scale})`, transformOrigin: 'top left' }}>
          {children}
        </div>
      </div>
    </div>
  )
}

// The page on its own paper-coloured background, so it reads as a sheet and not part of the form.
function Paper({ payload }) {
  const [zoom, setZoom] = useState(false)
  return (
    <div className="rounded-2xl border border-line bg-[#e9e7e2] dark:bg-[#1c1b19] p-3 sm:p-6">
      <div className="lg:hidden flex justify-end mb-2">
        <button type="button" onClick={() => setZoom(!zoom)} className="text-[13px] font-medium text-ink border border-line bg-surface rounded-lg px-3 h-9">
          {zoom ? 'Fit to screen' : 'Zoom to read'}
        </button>
      </div>
      <div className="mx-auto bg-white shadow-[0_1px_2px_rgba(0,0,0,0.08),0_12px_32px_rgba(0,0,0,0.14)]">
        <ScaledSheet zoom={zoom}><ResumeDocument payload={payload} /></ScaledSheet>
      </div>
    </div>
  )
}

function Field({ label, value, onChange, placeholder, required, error, inputRef, maxLength = 200, ...rest }) {
  return (
    <div>
      <Input label={required ? <>{label} <span className="text-bad">*</span></> : label} value={value} placeholder={placeholder}
        ref={inputRef} aria-invalid={error ? true : undefined} required={required} maxLength={maxLength}
        onChange={(e) => onChange(e.target.value)} {...rest} />
      {error && <p className="mt-1 text-xs text-bad">{error}</p>}
    </div>
  )
}

function Area({ label, value, onChange, hint, rows = 4, maxLength = 2000 }) {
  return (
    <label className="block sm:col-span-2">
      <span className="text-[13px] font-medium text-muted">{label}</span>
      <TextArea className="mt-1.5" rows={rows} value={value} maxLength={maxLength} onChange={(e) => onChange(e.target.value)} />
      <span className="flex justify-between gap-3 mt-1 text-xs text-muted">
        <span>{hint}</span>
        {(value || '').length > maxLength * 0.8 && <span className="shrink-0 tabular-nums">{(value || '').length}/{maxLength}</span>}
      </span>
    </label>
  )
}

// Keeps the phone's bottom bar above the on-screen keyboard (iOS doesn't resize the layout viewport).
function useKeyboardOffset() {
  const [offset, setOffset] = useState(0)
  useEffect(() => {
    const vv = window.visualViewport
    if (!vv) return
    const update = () => setOffset(Math.max(0, Math.round(window.innerHeight - vv.height - vv.offsetTop)))
    vv.addEventListener('resize', update)
    vv.addEventListener('scroll', update)
    return () => { vv.removeEventListener('resize', update); vv.removeEventListener('scroll', update) }
  }, [])
  return offset
}

// Phone only: a bar pinned to the bottom of the screen. The page adds bottom padding so it never covers an input.
function PhoneBar({ children }) {
  const offset = useKeyboardOffset()
  return createPortal(
    <div className="lg:hidden fixed inset-x-0 z-30 border-t border-line bg-surface/95 backdrop-blur px-4 pt-2.5"
      style={{ bottom: offset, paddingBottom: offset ? 10 : 'max(14px, env(safe-area-inset-bottom))' }}>
      <div className="flex gap-2.5 max-w-xl mx-auto">{children}</div>
    </div>,
    document.body,
  )
}

const barBtn = 'h-12 text-[15px] rounded-[10px]'

function Progress({ step }) {
  const idx = STEPS.findIndex((s) => s.id === step)
  return (
    <div className="mb-5 lg:mb-7">
      <div className="flex items-center justify-between text-[13px]">
        <ol className="hidden sm:flex items-center gap-2">
          {STEPS.map((s, i) => (
            <li key={s.id} className="flex items-center gap-2">
              {i > 0 && <span className="w-6 h-px bg-line" aria-hidden="true" />}
              <span className={`w-[22px] h-[22px] grid place-items-center rounded-full text-xs font-semibold ${i === idx ? 'bg-ink text-paper' : i < idx ? 'bg-good text-paper' : 'bg-sunken text-faint'}`}>{i < idx ? '✓' : i + 1}</span>
              <span className={i === idx ? 'font-semibold' : 'text-faint'}>{s.label}</span>
            </li>
          ))}
        </ol>
        <span className="sm:hidden font-semibold">{STEPS[idx].label}</span>
        <span className="sm:hidden text-faint">Step {idx + 1} of {STEPS.length}</span>
      </div>
      <div className="sm:hidden flex gap-1 mt-2.5" aria-hidden="true">
        {STEPS.map((s, i) => <span key={s.id} className={`flex-1 h-1 rounded-full ${i < idx ? 'bg-ink' : i === idx ? 'bg-accent' : 'bg-line'}`} />)}
      </div>
    </div>
  )
}

// One row per job / school / project. Tap to edit; arrows reorder.
function ItemRows({ kind, items, onOpen, onMove, onAdd }) {
  const cfg = LISTS[kind]
  return (
    <div className="space-y-2.5">
      {items.map((x, i) => {
        const n = countLines(cfg.bullets(x))
        const sub = [cfg.sub(x), n ? `${n} bullet${n === 1 ? '' : 's'}` : ''].filter(Boolean).join(' · ')
        const empty = !filledRow(x)
        return (
          <div key={x.id} className="flex items-stretch gap-2">
            <button type="button" onClick={() => onOpen(x.id)}
              className="flex-1 min-w-0 flex items-center gap-3 text-left bg-surface border border-line rounded-xl px-4 py-3.5 hover:border-faint transition-colors">
              <span className="flex-1 min-w-0">
                <span className={`block text-[15px] font-semibold truncate ${empty ? 'text-muted' : ''}`}>{cfg.title(x)}</span>
                <span className="block text-[13px] text-muted truncate mt-0.5">{empty ? 'Tap to fill in' : sub || 'Tap to add more'}</span>
              </span>
              <span className="text-[13px] text-faint shrink-0">Edit ›</span>
            </button>
            {items.length > 1 && (
              <span className="flex flex-col gap-1 shrink-0">
                <button type="button" aria-label="Move up" disabled={i === 0} onClick={() => onMove(i, -1)}
                  className="flex-1 w-10 grid place-items-center rounded-lg border border-line bg-surface text-muted disabled:opacity-30">↑</button>
                <button type="button" aria-label="Move down" disabled={i === items.length - 1} onClick={() => onMove(i, 1)}
                  className="flex-1 w-10 grid place-items-center rounded-lg border border-line bg-surface text-muted disabled:opacity-30">↓</button>
              </span>
            )}
          </div>
        )
      })}
      {items.length >= cfg.max
        ? <p className="text-xs text-muted">That's the limit of {cfg.max}.</p>
        : <button type="button" onClick={onAdd}
            className="w-full h-12 rounded-xl border border-dashed border-line text-[15px] font-medium hover:border-faint">+ Add a {cfg.noun}</button>}
      <p className="text-xs text-muted">Empty ones are left off the file.</p>
    </div>
  )
}

export default function ResumeBuilder() {
  const { user } = useAuth()
  const userId = user?.id
  const [restored] = useState(() => loadDraft(userId))
  const [step, setStep] = useState(() => (restored && restored.step && restored.step !== 'start' ? restored.step : 'start'))
  const [draft, setDraft] = useState(() => (restored ? restored.draft : emptyDraft()))
  const [sec, setSec] = useState(() => Math.min(Math.max(restored?.section || 0, 0), SECTIONS.length - 1))
  const [open, setOpen] = useState(null) // { kind, id } while editing one job, school or project
  const [tab, setTab] = useState('edit')
  const [nameError, setNameError] = useState('')
  const [shortened, setShortened] = useState(false)
  const nameRef = useRef(null)
  const carousel = useRef(null)
  const payload = toPayload(draft)
  const hasSaved = !isEmptyDraft(draft)
  const template = TEMPLATES.find((t) => t.id === draft.templateId) || TEMPLATES[0]

  useEffect(() => { saveDraft(step, draft, userId, sec) }, [step, draft, userId, sec])
  useEffect(() => { if (payload.name) setNameError('') }, [payload.name])
  useEffect(() => { window.scrollTo(0, 0) }, [step, sec, open, tab])

  // phone format picker: start on the chosen card
  useEffect(() => {
    if (step !== 'format' || !carousel.current) return
    const el = carousel.current
    const i = TEMPLATES.findIndex((t) => t.id === draft.templateId)
    const card = el.children[Math.max(0, i)]
    if (card) el.scrollLeft = card.offsetLeft - el.offsetLeft - 16
  }, [step]) // eslint-disable-line react-hooks/exhaustive-deps

  const onCarouselScroll = () => {
    const el = carousel.current
    if (!el || !el.children.length) return
    const w = el.children[0].offsetWidth + 12
    const i = Math.min(TEMPLATES.length - 1, Math.max(0, Math.round(el.scrollLeft / w)))
    if (TEMPLATES[i].id !== draft.templateId) setDraft((d) => ({ ...d, templateId: TEMPLATES[i].id }))
  }
  const scrollToCard = (i) => {
    const el = carousel.current
    const card = el?.children[i]
    if (card) el.scrollTo({ left: card.offsetLeft - el.offsetLeft - 16, behavior: 'smooth' })
  }

  const requireName = () => {
    if (payload.name) return true
    setTab('edit'); setOpen(null); setSec(0)
    setStep('form')
    setNameError('Add your name. It goes at the top of the file.')
    setTimeout(() => { nameRef.current?.focus(); nameRef.current?.scrollIntoView({ block: 'center' }) }, 80)
    return false
  }
  const [error, setError] = useState('')
  const [busy, setBusy] = useState('')
  const [existing, setExisting] = useState([])
  const fileInput = useRef(null)

  useEffect(() => {
    api.listResumes().then((d) => setExisting(d.resumes || [])).catch(() => {})
  }, [])

  const setContact = (key, value) => setDraft((d) => ({ ...d, contact: { ...d.contact, [key]: value } }))
  const setField = (key, value) => setDraft((d) => ({ ...d, [key]: value }))
  const setItem = (kind, id, key, value) => setDraft((d) => ({ ...d, [kind]: d[kind].map((x) => (x.id === id ? { ...x, [key]: value } : x)) }))

  const addItem = (kind) => {
    const next = LISTS[kind].add(draft)
    setDraft(next)
    setOpen({ kind, id: next[kind][next[kind].length - 1].id })
  }
  const removeItem = (kind, id) => {
    setDraft((d) => {
      const rest = d[kind].filter((x) => x.id !== id)
      return { ...d, [kind]: rest.length ? rest : LISTS[kind].add({ ...d, [kind]: [] })[kind] }
    })
    setOpen(null)
  }

  const afterPrefill = (parsed) => {
    const { draft: next, shortened } = prefillDraft(parsed)
    setDraft((d) => ({ ...next, templateId: d.templateId }))
    setShortened(shortened)
    setSec(0); setOpen(null)
    setStep('format')
  }
  const loadFromResume = async (id) => {
    setBusy('prefill'); setError('')
    try { afterPrefill(await api.parsed(id)) } catch (e) { setError(e.message) } finally { setBusy('') }
  }
  const uploadPrefill = async (file) => {
    if (!file) return
    if (!/\.(pdf|docx)$/i.test(file.name)) return setError('Only PDF and DOCX files are supported.')
    if (file.size > MAX_SIZE) return setError('File must be 2 MB or smaller.')
    setBusy('prefill'); setError('')
    try { afterPrefill(await api.parseOnly(file)) } catch (e) { setError(e.message) } finally { setBusy('') }
  }

  const download = async (format) => {
    if (!requireName()) return
    setBusy(`download-${format}`); setError('')
    try {
      const { blob, filename } = await api.exportResume({ ...payload, format })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = filename || `${fileBaseName(draft)}-resume.${format}`
      document.body.appendChild(a)
      a.click()
      a.remove()
      URL.revokeObjectURL(url)
    } catch (e) {
      setError(e instanceof ApiError && e.status === 404
        ? 'Download is not available yet. Your details are saved, so try again in a minute.'
        : e.status === 0 || e.status >= 500
          ? 'The server is waking up or busy. Your details are saved, so try again in a minute.'
          : e.message)
    } finally { setBusy('') }
  }

  const goSection = (i) => { setOpen(null); setSec(i) }
  const next = () => {
    if (sec === 0 && !requireName()) return
    if (sec < SECTIONS.length - 1) goSection(sec + 1)
    else if (requireName()) { setTab('edit'); setStep('preview') }
  }
  const back = () => { if (sec > 0) goSection(sec - 1); else setStep('format') }
  const nextLabel = sec < SECTIONS.length - 1 ? `Next: ${SECTIONS[sec + 1].label}` : 'Next: Download'

  const openRow = open && draft[open.kind]?.find((x) => x.id === open.id)
  const cur = SECTIONS[sec].id

  const sectionBody = () => {
    if (openRow) return itemEditor(open.kind, openRow)
    switch (cur) {
      case 'contact': return (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Field label="Full name" required error={nameError} inputRef={nameRef} autoComplete="name" value={draft.contact.fullName} onChange={(v) => setContact('fullName', v)} />
          <Field label="Headline" value={draft.contact.headline} onChange={(v) => setContact('headline', v)} placeholder="Frontend Engineer" />
          <Field label="Email" type="email" inputMode="email" autoComplete="email" maxLength={320} value={draft.contact.email} onChange={(v) => setContact('email', v)} />
          <Field label="Phone" type="tel" inputMode="tel" autoComplete="tel" maxLength={50} value={draft.contact.phone} onChange={(v) => setContact('phone', v)} />
          <Field label="City" value={draft.contact.city} onChange={(v) => setContact('city', v)} placeholder="Ahmedabad, Gujarat" />
          <Field label="LinkedIn" maxLength={500} value={draft.contact.linkedin} onChange={(v) => setContact('linkedin', v)} placeholder="linkedin.com/in/…" />
          <Field label="GitHub" maxLength={500} value={draft.contact.github} onChange={(v) => setContact('github', v)} placeholder="github.com/…" />
        </div>
      )
      case 'summary': return (
        <div className="space-y-6">
          <Area label="Professional summary" value={draft.summary} onChange={(v) => setField('summary', v)} rows={4} hint="Two or three sentences about you." />
          <BulletList label="Highlights" value={draft.highlights} onChange={(v) => setField('highlights', v)} placeholder="Cut report time from 6 hours to 30 minutes" addLabel="Add highlight" hint="Optional. Short wins that show near the top." />
        </div>
      )
      case 'skills': return <SkillsInput value={draft.skills} onChange={(v) => setField('skills', v)} />
      case 'experience': case 'education': case 'projects': return (
        <ItemRows kind={cur} items={draft[cur]} onOpen={(id) => setOpen({ kind: cur, id })} onAdd={() => addItem(cur)}
          onMove={(i, dir) => setDraft((d) => ({ ...d, [cur]: moveItem(d[cur], i, dir) }))} />
      )
      default: return (
        <div className="space-y-6">
          <BulletList label="Certifications" value={draft.certifications} onChange={(v) => setField('certifications', v)} placeholder="Google Data Analytics Certificate" addLabel="Add certification" max={LIMITS.certifications} hint="Optional." />
          <BulletList label="Achievements" value={draft.achievements} onChange={(v) => setField('achievements', v)} placeholder="Winner, Smart India Hackathon 2025" addLabel="Add achievement" hint="Optional." />
          <Field label="Languages" value={draft.languages} onChange={(v) => setField('languages', v)} placeholder="English, Hindi" hint="Separate with commas. Up to 20." />
        </div>
      )
    }
  }

  const itemEditor = (kind, x) => {
    const set = (key) => (v) => setItem(kind, x.id, key, v)
    if (kind === 'experience') return (
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Field label="Job title" value={x.title} onChange={set('title')} placeholder="Software Intern" />
        <Field label="Company" maxLength={120} value={x.company} onChange={set('company')} />
        <Field label="Location" maxLength={78} value={x.location} onChange={set('location')} />
        <Field label="Dates" value={x.dates} onChange={set('dates')} placeholder="Jul 2024 – Present" />
        <BulletList label="What you did" value={x.bullets} onChange={set('bullets')} placeholder="Built a dashboard used by 40+ managers" max={18} />
      </div>
    )
    if (kind === 'education') return (
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Field label="Degree" value={x.degree} onChange={set('degree')} placeholder="B.Tech, Computer Science" />
        <Field label="School" value={x.school} onChange={set('school')} />
        <Field label="Location" value={x.location} onChange={set('location')} placeholder="Ahmedabad, Gujarat" />
        <Field label="Dates" value={x.dates} onChange={set('dates')} placeholder="2021 – 2025" />
        <Field label="CGPA" maxLength={20} value={x.gpa} onChange={set('gpa')} placeholder="8.4" />
        <BulletList label="Details" value={x.details} onChange={set('details')} placeholder="Relevant coursework, awards" addLabel="Add detail" max={18} hint="Optional, like coursework or awards." />
      </div>
    )
    return (
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Field label="Name" maxLength={120} value={x.name} onChange={set('name')} />
        <Field label="Dates" maxLength={70} value={x.dates} onChange={set('dates')} />
        <Field label="Stack" value={x.stack} onChange={set('stack')} placeholder="Java · MySQL" />
        <BulletList label="What it does" value={x.bullets} onChange={set('bullets')} placeholder="Predicted churn with 84% accuracy" max={18} />
      </div>
    )
  }

  const downloadButtons = (cls = '', grow = true) => (
    <>
      <Button variant="ghost" className={cls} disabled={busy.startsWith('download')} onClick={() => download('docx')}>
        {busy === 'download-docx' ? 'Preparing...' : 'Word'}
      </Button>
      <Button className={`${cls} ${grow ? 'flex-1' : ''}`} disabled={busy.startsWith('download')} onClick={() => download('pdf')}>
        {busy === 'download-pdf' ? 'Preparing...' : 'Download PDF'}
      </Button>
    </>
  )

  const paper = <Paper payload={payload} />

  // On phones, a tapped field that sits under the bottom bar (or the keyboard) scrolls up into view.
  // Once now for the bar, and again after the keyboard has finished opening.
  const keepClearOfBar = (e) => {
    const el = e.target
    if (window.innerWidth >= 1024 || !el.matches('input, textarea, select')) return
    requestAnimationFrame(() => el.scrollIntoView({ block: 'nearest' }))
    setTimeout(() => { if (document.activeElement === el) el.scrollIntoView({ block: 'nearest' }) }, 350)
  }

  return (
    <div className={`rb max-w-6xl ${step === 'start' ? '' : 'pb-28 lg:pb-0'}`} onFocusCapture={keepClearOfBar}>
      {step !== 'start' && <Progress step={step} />}
      {step === 'start' && <ErrorBox message={error} />}

      {step === 'start' && (
        <div className="max-w-2xl">
          <h1 className="font-serif text-3xl font-medium tracking-tight">Build your resume</h1>
          <p className="text-[15px] text-muted mt-2">Fill in your details and download a clean PDF or Word file. Start from a resume you have, or from scratch.</p>
          <div className="mt-7 space-y-3">
            {hasSaved && (
              <div className="bg-surface border-2 border-accent/70 rounded-xl p-4 sm:p-5">
                <p className="text-[15px] font-semibold">Continue where you left off</p>
                <p className="text-[13px] text-muted mt-0.5">{payload.name || 'Your draft'} · {template.name} · saved on this device</p>
                <div className="flex gap-2 mt-3">
                  <Button className="h-11 px-5 text-[15px]" onClick={() => setStep('form')}>Continue</Button>
                  <Button variant="ghost" className="h-11 px-4 text-[15px]" onClick={() => { clearDraft(); setDraft(emptyDraft()); setShortened(false); setSec(0) }}>Start over</Button>
                </div>
              </div>
            )}
            <button type="button" disabled={busy === 'prefill'} onClick={() => fileInput.current.click()}
              className="w-full text-left bg-surface border border-line rounded-xl p-4 sm:p-5 hover:border-faint transition-colors disabled:opacity-60">
              <p className="text-[15px] font-semibold">{busy === 'prefill' ? 'Reading your resume...' : 'Upload a PDF or Word file'}</p>
              <p className="text-[13px] text-muted mt-0.5">We fill the form for you. Up to 2 MB, and it doesn't use a resume slot.</p>
            </button>
            {existing.map((r) => (
              <button key={r.id} type="button" disabled={busy === 'prefill'} onClick={() => loadFromResume(r.id)}
                className="w-full text-left bg-surface border border-line rounded-xl p-4 sm:p-5 hover:border-faint transition-colors disabled:opacity-60">
                <p className="text-[15px] font-semibold">Start from a resume you uploaded</p>
                <p className="text-[13px] text-muted mt-0.5 truncate">{r.originalFileName}</p>
              </button>
            ))}
            <button type="button" onClick={() => { setDraft(emptyDraft()); setError(''); setSec(0); setOpen(null); setStep('format') }}
              className="w-full text-left bg-surface border border-line rounded-xl p-4 sm:p-5 hover:border-faint transition-colors">
              <p className="text-[15px] font-semibold">Start blank</p>
              <p className="text-[13px] text-muted mt-0.5">Type everything yourself.</p>
            </button>
          </div>
          <input ref={fileInput} type="file" accept=".pdf,.docx" className="hidden"
            onChange={(e) => { const f = e.target.files[0]; e.target.value = ''; uploadPrefill(f) }} />
        </div>
      )}

      {step === 'format' && (
        <div>
          <h1 className="font-serif text-3xl font-medium tracking-tight">Pick a format</h1>
          <p className="text-[15px] text-muted mt-2 mb-6">All five are single-column and ATS-safe. You can switch later without losing anything.</p>
          {/* phone: swipe one card at a time */}
          <div className="lg:hidden">
            <div ref={carousel} onScroll={onCarouselScroll}
              className="-mx-4 px-4 flex gap-3 overflow-x-auto snap-x snap-mandatory scroll-px-4 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
              {TEMPLATES.map((t, i) => {
                const selected = draft.templateId === t.id
                return (
                  <button type="button" key={t.id} onClick={() => scrollToCard(i)}
                    className={`snap-start shrink-0 w-[78%] text-left bg-surface rounded-2xl p-2.5 border-2 transition-colors ${selected ? 'border-accent' : 'border-line'}`}>
                    <img src={t.thumb} alt="" className="w-full aspect-[480/679] object-cover object-top rounded-md border border-line bg-white" />
                    <span className="block px-1 pt-2.5 pb-1">
                      <span className="block text-[15px] font-semibold">{t.name}</span>
                      <span className="block text-[13px] text-muted mt-0.5">{t.blurb}</span>
                    </span>
                  </button>
                )
              })}
            </div>
            <div className="flex justify-center gap-1.5 mt-4" aria-hidden="true">
              {TEMPLATES.map((t) => <span key={t.id} className={`h-[7px] rounded-full transition-all ${draft.templateId === t.id ? 'w-[18px] bg-ink' : 'w-[7px] bg-line'}`} />)}
            </div>
            <PhoneBar>
              <Button variant="ghost" className={`${barBtn} w-24`} onClick={() => setStep('start')}>Back</Button>
              <Button className={`${barBtn} flex-1`} onClick={() => setStep('form')}>Use {template.name}</Button>
            </PhoneBar>
          </div>
          {/* desktop: all five side by side */}
          <div className="hidden lg:grid grid-cols-5 gap-4">
            {TEMPLATES.map((t) => {
              const selected = draft.templateId === t.id
              return (
                <button type="button" key={t.id} onClick={() => setDraft((d) => ({ ...d, templateId: t.id }))}
                  className={`text-left bg-surface rounded-xl p-2 border-2 transition-colors ${selected ? 'border-accent' : 'border-line hover:border-faint'}`}>
                  <img src={t.thumb} alt="" className="w-full aspect-[480/679] object-cover object-top rounded-md border border-line bg-white" />
                  <span className="block px-1 pt-2 pb-1">
                    <span className="block text-[14px] font-semibold">{t.name}</span>
                    <span className="block text-[12px] text-muted mt-0.5 leading-snug">{t.blurb}</span>
                  </span>
                </button>
              )
            })}
          </div>
          <div className="hidden lg:flex justify-between gap-3 mt-7">
            <Button variant="ghost" className="h-10 px-5" onClick={() => setStep('start')}>Back</Button>
            <Button className="h-10 px-5" onClick={() => setStep('form')}>Use {template.name}</Button>
          </div>
        </div>
      )}

      {step === 'form' && (
        <div className="lg:grid lg:grid-cols-[minmax(0,1fr)_minmax(0,540px)] lg:gap-8 items-start">
          <div>
            {/* phone: Edit / Preview tabs */}
            <div className="lg:hidden grid grid-cols-2 p-1 mb-4 rounded-[10px] bg-sunken border border-line text-sm font-medium" role="tablist">
              {['edit', 'preview'].map((k) => (
                <button key={k} type="button" role="tab" aria-selected={tab === k} onClick={() => setTab(k)}
                  className={`h-10 rounded-lg ${tab === k ? 'bg-surface shadow-sm text-ink' : 'text-muted'}`}>
                  {k === 'edit' ? 'Edit' : 'Preview'}
                </button>
              ))}
            </div>

            <div className={tab === 'edit' ? '' : 'hidden lg:block'}>
              <div className="lg:bg-surface lg:border lg:border-line lg:rounded-2xl lg:shadow-[0_1px_2px_rgba(0,0,0,0.04)]">
                <div className="lg:px-6 lg:pt-5 lg:pb-4 lg:border-b lg:border-line">
                  <h1 className="hidden lg:block text-lg font-semibold tracking-tight">Your details</h1>
                  <p className="hidden lg:block text-[13px] text-muted mt-0.5">The page on the right updates as you type. Your draft saves on this device.</p>
                  {/* desktop: section pills */}
                  <div className="hidden lg:flex flex-wrap gap-1.5 mt-4">
                    {SECTIONS.map((s, i) => (
                      <button key={s.id} type="button" onClick={() => goSection(i)}
                        className={`h-8 px-3 rounded-full text-[13px] border transition-colors ${i === sec ? 'bg-ink text-paper border-ink' : i < sec ? 'border-line text-ink' : 'border-line text-muted hover:text-ink'}`}>
                        {i < sec && <span className="text-good mr-1">✓</span>}{s.label}
                      </button>
                    ))}
                  </div>
                  {/* phone: section name with a jump menu */}
                  <div className="lg:hidden flex items-center justify-between gap-3">
                    {openRow ? (
                      <button type="button" onClick={() => setOpen(null)} className="h-10 -ml-1 px-1 text-[16px] font-semibold">‹ {SECTIONS[sec].label}</button>
                    ) : (
                      <label className="relative flex items-center gap-1.5 h-10 text-[18px] font-semibold">
                        {SECTIONS[sec].label} <span className="text-faint text-sm">▾</span>
                        <select aria-label="Jump to section" value={sec} onChange={(e) => goSection(Number(e.target.value))}
                          className="absolute inset-0 opacity-0">
                          {SECTIONS.map((s, i) => <option key={s.id} value={i}>{s.label}</option>)}
                        </select>
                      </label>
                    )}
                    <span className="text-[13px] text-faint">{sec + 1} of {SECTIONS.length}</span>
                  </div>
                </div>

                <div className="pt-3 lg:p-6">
                  {shortened && (
                    <p role="status" className="flex items-start justify-between gap-3 mb-4 rounded-lg border border-line bg-sunken px-3.5 py-2.5 text-sm">
                      <span>Some text from your file was shortened to fit the limits. Check your bullets before you download.</span>
                      <button type="button" onClick={() => setShortened(false)} aria-label="Dismiss" className="shrink-0 text-faint hover:text-ink">×</button>
                    </p>
                  )}
                  {openRow && <p className="hidden lg:block text-[15px] font-semibold mb-4">
                    <button type="button" onClick={() => setOpen(null)} className="text-muted hover:text-ink">{SECTIONS[sec].label}</button>
                    <span className="text-faint"> / </span>{LISTS[open.kind].title(openRow)}</p>}
                  {sectionBody()}
                </div>

                {/* desktop footer */}
                <div className="hidden lg:flex justify-between gap-3 px-6 py-4 border-t border-line">
                  {openRow ? (
                    <>
                      <Button variant="danger" className="h-10 px-4" onClick={() => removeItem(open.kind, open.id)}>Delete {LISTS[open.kind].noun}</Button>
                      <Button className="h-10 px-5" onClick={() => setOpen(null)}>Save {LISTS[open.kind].noun}</Button>
                    </>
                  ) : (
                    <>
                      <Button variant="ghost" className="h-10 px-5" onClick={back}>Back</Button>
                      <Button className="h-10 px-5" onClick={next}>{nextLabel}</Button>
                    </>
                  )}
                </div>
              </div>
            </div>

            {/* phone preview tab */}
            {tab === 'preview' && <div className="lg:hidden">{paper}<p className="text-xs text-muted mt-2 text-center">{template.name} format. The download matches this page.</p></div>}

            <PhoneBar>
              {tab === 'preview' ? downloadButtons(`${barBtn} px-5`) : openRow ? (
                <>
                  <Button variant="danger" className={`${barBtn} w-28`} onClick={() => removeItem(open.kind, open.id)}>Delete</Button>
                  <Button className={`${barBtn} flex-1`} onClick={() => setOpen(null)}>Save {LISTS[open.kind].noun}</Button>
                </>
              ) : (
                <>
                  <Button variant="ghost" className={`${barBtn} w-24`} onClick={back}>Back</Button>
                  <Button className={`${barBtn} flex-1`} onClick={next}>{nextLabel}</Button>
                </>
              )}
            </PhoneBar>
            {tab === 'preview' && error && <div className="lg:hidden mt-3"><ErrorBox message={error} /></div>}
          </div>

          {/* desktop preview: its own panel on a paper background */}
          <aside className="hidden lg:block lg:sticky lg:top-20">
            <div className="flex items-baseline justify-between mb-3">
              <p className="text-[14px] font-semibold">Preview</p>
              <button type="button" onClick={() => setStep('format')} className="text-[13px] text-muted hover:text-ink">{template.name} · Change format</button>
            </div>
            {paper}
            <div className="mt-3"><ErrorBox message={error} /></div>
            <div className="flex justify-end gap-2.5 mt-3">{downloadButtons('h-10 px-5', false)}</div>
          </aside>
        </div>
      )}

      {step === 'preview' && (
        <div className="lg:grid lg:grid-cols-[300px_minmax(0,1fr)] lg:gap-8 items-start">
          <aside className="lg:sticky lg:top-20">
            <h1 className="font-serif text-3xl font-medium tracking-tight">Download</h1>
            <p className="text-[15px] text-muted mt-2 mb-5">This is exactly how your file will look.</p>
            <div className="hidden lg:block space-y-2.5">
              <ErrorBox message={error} />
              <Button className="w-full h-11" disabled={busy.startsWith('download')} onClick={() => download('pdf')}>{busy === 'download-pdf' ? 'Preparing...' : 'Download PDF'}</Button>
              <Button variant="ghost" className="w-full h-11" disabled={busy.startsWith('download')} onClick={() => download('docx')}>{busy === 'download-docx' ? 'Preparing...' : 'Download Word (DOCX)'}</Button>
              <p className="text-xs text-muted">PDF to send as it is. Word if you want to edit it.</p>
              <Button variant="ghost" className="w-full h-11" onClick={() => setStep('form')}>Edit details</Button>
            </div>
            <p className="text-[13px] font-semibold mt-1 lg:mt-6 mb-2">Format</p>
            <div className="flex gap-2 overflow-x-auto -mx-4 px-4 lg:mx-0 lg:px-0 lg:grid lg:grid-cols-3 pb-1 [scrollbar-width:none]">
              {TEMPLATES.map((t) => (
                <button type="button" key={t.id} onClick={() => setDraft((d) => ({ ...d, templateId: t.id }))}
                  className={`shrink-0 w-[88px] lg:w-auto text-left rounded-lg overflow-hidden border-2 bg-surface ${draft.templateId === t.id ? 'border-accent' : 'border-line'}`}>
                  <img src={t.thumb} alt="" className="w-full h-[80px] object-cover object-top bg-white" />
                  <span className="block text-[11px] text-muted px-2 py-1.5 truncate">{t.name}</span>
                </button>
              ))}
            </div>
          </aside>
          <div className="mt-5 lg:mt-0">{paper}</div>
          {error && <div className="lg:hidden mt-3"><ErrorBox message={error} /></div>}
          <PhoneBar>
            <Button variant="ghost" className={`${barBtn} px-4`} onClick={() => setStep('form')}>Edit</Button>
            {downloadButtons(`${barBtn} px-4`)}
          </PhoneBar>
        </div>
      )}

      {step !== 'start' && (
        <p className="hidden lg:block text-xs text-faint mt-8">
          <Link to="/" className="hover:text-ink">Back to your resumes</Link>
        </p>
      )}
    </div>
  )
}
