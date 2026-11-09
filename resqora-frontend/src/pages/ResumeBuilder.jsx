import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError } from '../api.js'
import { useAuth } from '../auth.jsx'
import { Button, ErrorBox, Input, TextArea } from '../components/ui.jsx'
import ResumeDocument from '../builder/ResumeDocument.jsx'
import { BulletList, SkillsInput } from '../builder/inputs.jsx'
import {
  TEMPLATES, emptyDraft, fromParsed, toPayload, fileBaseName,
  addJob, addSchool, addProject, moveItem,
  saveDraft, loadDraft, clearDraft, isEmptyDraft,
} from '../builder/model.js'

const STEPS = [
  { id: 'start', label: 'Start' },
  { id: 'format', label: 'Choose a format' },
  { id: 'form', label: 'Your details' },
  { id: 'preview', label: 'Preview and download' },
]

const MAX_SIZE = 2 * 1024 * 1024

function Stepper({ current }) {
  const idx = STEPS.findIndex((s) => s.id === current)
  return (
    <>
    <p className="sm:hidden text-[13px] mb-5">
      <span className="text-faint">Step {idx + 1} of {STEPS.length}</span>
      <span className="font-semibold"> · {STEPS[idx].label}</span>
    </p>
    <ol className="hidden sm:flex flex-wrap items-center gap-x-2 gap-y-2 text-[13px] mb-7">
      {STEPS.map((s, i) => {
        const on = i === idx
        const done = i < idx
        return (
          <li key={s.id} className="flex items-center gap-2">
            {i > 0 && <span className="w-6 h-px bg-line" aria-hidden="true" />}
            <span className={`w-[22px] h-[22px] grid place-items-center rounded-full text-xs font-semibold ${on ? 'bg-ink text-paper' : done ? 'bg-good text-paper' : 'bg-sunken text-faint'}`}>{i + 1}</span>
            <span className={on ? 'font-semibold' : 'text-faint'}>{s.label}</span>
          </li>
        )
      })}
    </ol>
    </>
  )
}

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

function SheetFrame({ payload }) {
  const [zoom, setZoom] = useState(false)
  return (
    <div className="bg-sunken border border-line rounded-xl p-3 sm:p-4">
      <div className="sm:hidden flex justify-end mb-2">
        <button type="button" onClick={() => setZoom(!zoom)} className="text-xs font-medium text-muted border border-line bg-surface rounded-md px-2.5 h-7">
          {zoom ? 'Fit to screen' : 'Zoom to read'}
        </button>
      </div>
      <div className="mx-auto shadow-md bg-white">
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

function CardList({ title, note, items, onMove, onRemove, onAdd, addLabel, children }) {
  return (
    <section className="bg-surface border border-line rounded-xl shadow-[0_1px_2px_rgba(0,0,0,0.04)]">
      <header className="px-5 sm:px-6 pt-5 pb-1 flex items-baseline gap-3">
        <h2 className="text-[15px] font-semibold tracking-tight">{title}</h2>
        {note && <span className="text-xs text-muted">{note}</span>}
      </header>
      <div className="px-5 sm:px-6 pb-5 sm:pb-6 space-y-3">
        {items.map((item, i) => (
          <div key={item.id} className="border border-line rounded-[10px] bg-sunken/60 p-3.5">
            <div className="flex items-center justify-between gap-3 mb-3">
              <p className="text-[13px] font-semibold">{title.replace(/s$/, '')} {i + 1}</p>
              <div className="flex gap-1.5">
                <button type="button" aria-label="Move up" disabled={i === 0} onClick={() => onMove(i, -1)}
                  className="h-9 w-9 sm:h-7 sm:w-7 grid place-items-center rounded-md border border-line bg-surface text-ink disabled:opacity-30">↑</button>
                <button type="button" aria-label="Move down" disabled={i === items.length - 1} onClick={() => onMove(i, 1)}
                  className="h-9 w-9 sm:h-7 sm:w-7 grid place-items-center rounded-md border border-line bg-surface text-ink disabled:opacity-30">↓</button>
                <button type="button" onClick={() => onRemove(i)}
                  className="h-9 px-2.5 sm:h-7 text-sm rounded-md border border-line bg-surface text-bad">Remove</button>
              </div>
            </div>
            {children(item, i)}
          </div>
        ))}
        <Button type="button" variant="ghost" onClick={onAdd}>{addLabel}</Button>
      </div>
    </section>
  )
}

export default function ResumeBuilder() {
  const { user } = useAuth()
  const userId = user?.id
  const [restored] = useState(() => loadDraft(userId))
  const [step, setStep] = useState(() => (restored && restored.step && restored.step !== 'start' ? restored.step : 'start'))
  const [draft, setDraft] = useState(() => (restored ? restored.draft : emptyDraft()))
  const [tab, setTab] = useState('edit')
  const [nameError, setNameError] = useState('')
  const nameRef = useRef(null)
  const payload = toPayload(draft)
  const hasSaved = !isEmptyDraft(draft)

  useEffect(() => { saveDraft(step, draft, userId) }, [step, draft, userId])
  useEffect(() => { if (payload.name) setNameError('') }, [payload.name])
  useEffect(() => { window.scrollTo(0, 0) }, [step])

  const requireName = () => {
    if (payload.name) return true
    setTab('edit')
    setStep('form')
    setNameError('Add your name. It goes at the top of the file.')
    setTimeout(() => { nameRef.current?.focus(); nameRef.current?.scrollIntoView({ block: 'center' }) }, 50)
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

  const loadFromResume = async (id) => {
    setBusy('prefill'); setError('')
    try {
      const parsed = await api.parsed(id)
      setDraft((d) => ({ ...fromParsed(parsed), templateId: d.templateId }))
      setStep('format')
    } catch (e) { setError(e.message) }
    finally { setBusy('') }
  }

  const uploadPrefill = async (file) => {
    if (!file) return
    if (!/\.(pdf|docx)$/i.test(file.name)) return setError('Only PDF and DOCX files are supported.')
    if (file.size > MAX_SIZE) return setError('File must be 2 MB or smaller.')
    setBusy('prefill'); setError('')
    try {
      const parsed = await api.parseOnly(file)
      setDraft((d) => ({ ...fromParsed(parsed), templateId: d.templateId }))
      setStep('format')
    } catch (e) { setError(e.message) }
    finally { setBusy('') }
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

  return (
    <div className="max-w-5xl">
      <Stepper current={step} />
      {step !== 'preview' && <ErrorBox message={error} />}

      {step === 'start' && (
        <div>
          <h1 className="font-serif text-3xl font-medium tracking-tight">Build a resume</h1>
          <p className="text-sm text-muted mt-2 max-w-xl">Upload a resume to fill the form, or start blank. Then pick a format, edit the details, and download the file.</p>
          {hasSaved && (
            <div className="mt-8 bg-surface border border-line rounded-xl p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div>
                <p className="font-medium">Continue where you left off</p>
                <p className="text-sm text-muted mt-1">{payload.name || 'Your draft'} is saved on this device.</p>
              </div>
              <div className="flex gap-2 shrink-0">
                <Button variant="ghost" onClick={() => { clearDraft(); setDraft(emptyDraft()) }}>Start over</Button>
                <Button onClick={() => setStep('form')}>Continue</Button>
              </div>
            </div>
          )}
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 mt-8">
            <button type="button" disabled={busy === 'prefill'} onClick={() => fileInput.current.click()}
              className="text-left bg-surface border border-line rounded-xl p-5 hover:border-faint transition-colors disabled:opacity-60">
              <p className="font-medium">{busy === 'prefill' ? 'Reading your resume...' : 'Upload to prefill'}</p>
              <p className="text-sm text-muted mt-1">PDF or DOCX, up to 2 MB. We only read it, so it doesn't use a resume slot.</p>
            </button>
            <button type="button" onClick={() => { setDraft(emptyDraft()); setError(''); setStep('format') }}
              className="text-left bg-surface border border-line rounded-xl p-5 hover:border-faint transition-colors">
              <p className="font-medium">Start blank</p>
              <p className="text-sm text-muted mt-1">Type everything in. You can still switch formats later.</p>
            </button>
          </div>
          <input ref={fileInput} type="file" accept=".pdf,.docx" className="hidden"
            onChange={(e) => { const f = e.target.files[0]; e.target.value = ''; uploadPrefill(f) }} />
          {existing.length > 0 && (
            <section className="mt-8">
              <h2 className="text-[15px] font-semibold">Or use one you already uploaded</h2>
              <ul className="mt-3 bg-surface border border-line rounded-xl divide-y divide-line">
                {existing.map((r) => (
                  <li key={r.id}>
                    <button type="button" disabled={busy === 'prefill'} onClick={() => loadFromResume(r.id)}
                      className="w-full text-left px-4 py-3 text-sm hover:bg-sunken/50 disabled:opacity-60">
                      <span className="font-medium">{r.originalFileName}</span>
                    </button>
                  </li>
                ))}
              </ul>
            </section>
          )}
          {busy === 'prefill' && <p className="text-sm text-muted mt-4">Reading your resume...</p>}
        </div>
      )}

      {step === 'format' && (
        <div>
          <h1 className="font-serif text-3xl font-medium tracking-tight">Choose a format</h1>
          <p className="text-sm text-muted mt-2 mb-7">Five ATS-safe, single-column layouts. You can switch later without losing what you typed.</p>
          <div className="flex flex-wrap justify-center gap-4">
            {TEMPLATES.map((t) => {
              const selected = draft.templateId === t.id
              return (
                <button type="button" key={t.id} onClick={() => setDraft((d) => ({ ...d, templateId: t.id }))}
                  className={`text-left bg-surface rounded-xl overflow-hidden w-[calc(50%-8px)] lg:w-[calc((100%-32px)/3)] border transition-colors ${selected ? 'border-ink ring-1 ring-ink' : 'border-line hover:border-faint'}`}>
                  <img src={t.thumb} alt="" className="w-full aspect-[3/4] object-cover object-top bg-sunken border-b border-line" />
                  <span className="block p-2.5 sm:p-3.5">
                    <span className="flex items-center justify-between gap-2">
                      <span className="font-semibold text-[15px]">{t.name}</span>
                      {selected && <span className="text-[11px] font-semibold bg-ink text-paper px-2 py-0.5 rounded-full">✓<span className="hidden sm:inline"> Selected</span></span>}
                    </span>
                    <span className="hidden sm:block text-[13px] text-muted mt-1">{t.blurb}</span>
                  </span>
                </button>
              )
            })}
          </div>
          <div className="flex justify-between gap-3 mt-6">
            <Button variant="ghost" onClick={() => setStep('start')}>Back</Button>
            <Button onClick={() => setStep('form')}>Continue</Button>
          </div>
        </div>
      )}

      {step === 'form' && (
        <div>
          <h1 className="font-serif text-3xl font-medium tracking-tight">Your details</h1>
          <p className="text-sm text-muted mt-2 mb-5 lg:mb-7">Type on the left and the page updates on the right. Empty sections are left off the file. Your draft saves on this device as you type.</p>
          <div className="lg:hidden grid grid-cols-2 p-1 mb-5 rounded-lg bg-sunken border border-line text-sm font-medium" role="tablist">
            {['edit', 'preview'].map((k) => (
              <button key={k} type="button" role="tab" aria-selected={tab === k} onClick={() => setTab(k)}
                className={`h-9 rounded-md ${tab === k ? 'bg-surface shadow-sm text-ink' : 'text-muted'}`}>
                {k === 'edit' ? 'Edit' : 'Preview'}
              </button>
            ))}
          </div>
          <div className="lg:grid lg:grid-cols-[minmax(0,1fr)_minmax(0,500px)] lg:gap-6 items-start">
          <div className={tab === 'edit' ? '' : 'hidden lg:block'}>
          <div className="space-y-4">
            <section className="bg-surface border border-line rounded-xl shadow-[0_1px_2px_rgba(0,0,0,0.04)]">
              <h2 className="text-[15px] font-semibold tracking-tight px-5 sm:px-6 pt-5">Contact</h2>
              <div className="px-5 sm:px-6 pb-5 sm:pb-6 pt-4 grid grid-cols-1 gap-3.5 sm:grid-cols-2">
                <Field label="Full name" required error={nameError} inputRef={nameRef} autoComplete="name" value={draft.contact.fullName} onChange={(v) => setContact('fullName', v)} />
                <Field label="Headline" value={draft.contact.headline} onChange={(v) => setContact('headline', v)} placeholder="Frontend Engineer" />
                <Field label="City" value={draft.contact.city} onChange={(v) => setContact('city', v)} />
                <Field label="Email" type="email" inputMode="email" autoComplete="email" maxLength={320} value={draft.contact.email} onChange={(v) => setContact('email', v)} />
                <Field label="Phone" type="tel" inputMode="tel" autoComplete="tel" maxLength={50} value={draft.contact.phone} onChange={(v) => setContact('phone', v)} />
                <Field label="LinkedIn" maxLength={500} value={draft.contact.linkedin} onChange={(v) => setContact('linkedin', v)} />
                <Field label="GitHub" maxLength={500} value={draft.contact.github} onChange={(v) => setContact('github', v)} />
              </div>
            </section>

            <section className="bg-surface border border-line rounded-xl shadow-[0_1px_2px_rgba(0,0,0,0.04)]">
              <h2 className="text-[15px] font-semibold tracking-tight px-5 sm:px-6 pt-5">Summary</h2>
              <div className="px-5 sm:px-6 pb-5 sm:pb-6 pt-4">
                <Area label="Professional summary" value={draft.summary} onChange={(v) => setField('summary', v)} rows={3} hint="Two or three sentences about you." />
              </div>
            </section>

            <section className="bg-surface border border-line rounded-xl shadow-[0_1px_2px_rgba(0,0,0,0.04)]">
              <h2 className="text-[15px] font-semibold tracking-tight px-5 sm:px-6 pt-5">Skills</h2>
              <div className="px-5 sm:px-6 pb-5 sm:pb-6 pt-4">
                <SkillsInput value={draft.skills} onChange={(v) => setField('skills', v)} />
              </div>
            </section>

            <section className="bg-surface border border-line rounded-xl shadow-[0_1px_2px_rgba(0,0,0,0.04)]">
              <h2 className="text-[15px] font-semibold tracking-tight px-5 sm:px-6 pt-5">Highlights</h2>
              <div className="px-5 sm:px-6 pb-5 sm:pb-6 pt-4">
                <BulletList label="Highlights" value={draft.highlights} onChange={(v) => setField('highlights', v)} placeholder="Cut report time from 6 hours to 30 minutes" addLabel="Add highlight" hint="Short wins that show near the top. Up to 20." />
              </div>
            </section>

            <CardList title="Experience" note="Use the arrows to reorder" items={draft.experience}
              onMove={(i, dir) => setDraft((d) => ({ ...d, experience: moveItem(d.experience, i, dir) }))}
              onRemove={(i) => setDraft((d) => ({ ...d, experience: d.experience.length > 1 ? d.experience.filter((_, j) => j !== i) : d.experience }))}
              onAdd={() => setDraft(addJob)} addLabel="+ Add another job">
              {(job, i) => (
                <div className="grid grid-cols-1 gap-3.5 sm:grid-cols-2">
                  <Field label="Title" value={job.title} onChange={(v) => setDraft((d) => ({ ...d, experience: d.experience.map((x, j) => j === i ? { ...x, title: v } : x) }))} />
                  <Field label="Company" maxLength={120} value={job.company} onChange={(v) => setDraft((d) => ({ ...d, experience: d.experience.map((x, j) => j === i ? { ...x, company: v } : x) }))} />
                  <Field label="Location" maxLength={78} value={job.location} onChange={(v) => setDraft((d) => ({ ...d, experience: d.experience.map((x, j) => j === i ? { ...x, location: v } : x) }))} />
                  <Field label="Dates" value={job.dates} onChange={(v) => setDraft((d) => ({ ...d, experience: d.experience.map((x, j) => j === i ? { ...x, dates: v } : x) }))} placeholder="Jul 2024 – Present" />
                  <BulletList label="What you did" value={job.bullets} onChange={(v) => setDraft((d) => ({ ...d, experience: d.experience.map((x, j) => j === i ? { ...x, bullets: v } : x) }))} placeholder="Built a dashboard used by 40+ managers" max={18} />
                </div>
              )}
            </CardList>

            <CardList title="Education" items={draft.education}
              onMove={(i, dir) => setDraft((d) => ({ ...d, education: moveItem(d.education, i, dir) }))}
              onRemove={(i) => setDraft((d) => ({ ...d, education: d.education.length > 1 ? d.education.filter((_, j) => j !== i) : d.education }))}
              onAdd={() => setDraft(addSchool)} addLabel="+ Add another school">
              {(school, i) => (
                <div className="grid grid-cols-1 gap-3.5 sm:grid-cols-2">
                  <Field label="Degree" value={school.degree} onChange={(v) => setDraft((d) => ({ ...d, education: d.education.map((x, j) => j === i ? { ...x, degree: v } : x) }))} />
                  <Field label="School" value={school.school} onChange={(v) => setDraft((d) => ({ ...d, education: d.education.map((x, j) => j === i ? { ...x, school: v } : x) }))} />
                  <Field label="Location" value={school.location} onChange={(v) => setDraft((d) => ({ ...d, education: d.education.map((x, j) => j === i ? { ...x, location: v } : x) }))} placeholder="Ahmedabad, Gujarat" />
                  <Field label="Dates" value={school.dates} onChange={(v) => setDraft((d) => ({ ...d, education: d.education.map((x, j) => j === i ? { ...x, dates: v } : x) }))} />
                  <Field label="CGPA" maxLength={20} value={school.gpa} onChange={(v) => setDraft((d) => ({ ...d, education: d.education.map((x, j) => j === i ? { ...x, gpa: v } : x) }))} />
                  <BulletList label="Details" value={school.details} onChange={(v) => setDraft((d) => ({ ...d, education: d.education.map((x, j) => j === i ? { ...x, details: v } : x) }))} placeholder="Relevant coursework, awards" addLabel="Add detail" max={18} hint="Optional, like coursework or awards." />
                </div>
              )}
            </CardList>

            <CardList title="Projects" items={draft.projects}
              onMove={(i, dir) => setDraft((d) => ({ ...d, projects: moveItem(d.projects, i, dir) }))}
              onRemove={(i) => setDraft((d) => ({ ...d, projects: d.projects.length > 1 ? d.projects.filter((_, j) => j !== i) : d.projects }))}
              onAdd={() => setDraft(addProject)} addLabel="+ Add another project">
              {(project, i) => (
                <div className="grid grid-cols-1 gap-3.5 sm:grid-cols-2">
                  <Field label="Name" maxLength={120} value={project.name} onChange={(v) => setDraft((d) => ({ ...d, projects: d.projects.map((x, j) => j === i ? { ...x, name: v } : x) }))} />
                  <Field label="Dates" maxLength={70} value={project.dates} onChange={(v) => setDraft((d) => ({ ...d, projects: d.projects.map((x, j) => j === i ? { ...x, dates: v } : x) }))} />
                  <Field label="Stack" value={project.stack} onChange={(v) => setDraft((d) => ({ ...d, projects: d.projects.map((x, j) => j === i ? { ...x, stack: v } : x) }))} placeholder="Java · MySQL" />
                  <BulletList label="What it does" value={project.bullets} onChange={(v) => setDraft((d) => ({ ...d, projects: d.projects.map((x, j) => j === i ? { ...x, bullets: v } : x) }))} placeholder="Predicted churn with 84% accuracy" max={18} />
                </div>
              )}
            </CardList>

            <section className="bg-surface border border-line rounded-xl shadow-[0_1px_2px_rgba(0,0,0,0.04)]">
              <h2 className="text-[15px] font-semibold tracking-tight px-5 sm:px-6 pt-5">More</h2>
              <div className="px-5 sm:px-6 pb-5 sm:pb-6 pt-4 grid grid-cols-1 gap-3.5">
                <BulletList label="Certifications" value={draft.certifications} onChange={(v) => setField('certifications', v)} placeholder="Google Data Analytics Certificate" addLabel="Add certification" hint="Optional." />
                <Field label="Languages" value={draft.languages} onChange={(v) => setField('languages', v)} placeholder="English, Hindi" />
                <BulletList label="Achievements" value={draft.achievements} onChange={(v) => setField('achievements', v)} placeholder="Winner, Smart India Hackathon 2025" addLabel="Add achievement" hint="Optional. Up to 20." />
              </div>
            </section>
          </div>
          </div>
          <aside className={`${tab === 'preview' ? '' : 'hidden'} lg:block lg:sticky lg:top-20`}>
            <SheetFrame payload={payload} />
            <p className="text-xs text-muted mt-2">Live page in the {TEMPLATES.find((t) => t.id === draft.templateId)?.name} format. The download matches it.</p>
          </aside>
          </div>
          <div className="mt-6 flex justify-between gap-3 lg:sticky lg:bottom-0 lg:-mx-6 lg:px-6 lg:py-3 lg:border-t lg:border-line lg:bg-paper/95 lg:backdrop-blur">
            <Button variant="ghost" onClick={() => setStep('format')}>Back</Button>
            <Button onClick={() => { setError(''); if (requireName()) setStep('preview') }}>Continue to download</Button>
          </div>
        </div>
      )}

      {step === 'preview' && (
        <div>
          <h1 className="font-serif text-3xl font-medium tracking-tight">Preview and download</h1>
          <p className="text-sm text-muted mt-2 mb-7">This is how the file will look. Download it, or go back to change the details.</p>
          <div className="grid gap-6 lg:grid-cols-[280px_minmax(0,1fr)] items-start">
            <aside className="space-y-3 lg:sticky lg:top-20">
              <ErrorBox message={error} />
              <Button className="w-full" disabled={busy.startsWith('download')} onClick={() => download('pdf')}>
                {busy === 'download-pdf' ? 'Preparing...' : 'Download PDF'}
              </Button>
              <Button className="w-full" variant="ghost" disabled={busy.startsWith('download')} onClick={() => download('docx')}>
                {busy === 'download-docx' ? 'Preparing...' : 'Download DOCX'}
              </Button>
              <p className="text-xs text-muted">PDF for sending as is. DOCX if you want to edit it in Word.</p>
              <Button className="w-full" variant="ghost" onClick={() => setStep('form')}>Edit details</Button>
              <p className="text-[13px] font-semibold pt-3">Switch format</p>
              <div className="grid grid-cols-2 gap-2">
                {TEMPLATES.map((t) => (
                  <button type="button" key={t.id} onClick={() => setDraft((d) => ({ ...d, templateId: t.id }))}
                    className={`text-left rounded-lg overflow-hidden border bg-surface ${draft.templateId === t.id ? 'border-ink ring-1 ring-ink' : 'border-line'}`}>
                    <img src={t.thumb} alt="" className="w-full h-[88px] object-cover object-top" />
                    <span className="block text-[11px] text-muted px-2 py-1.5">{t.name}</span>
                  </button>
                ))}
              </div>
            </aside>
            <SheetFrame payload={payload} />
          </div>
        </div>
      )}

      {step !== 'start' && (
        <p className="text-xs text-faint mt-8">
          <Link to="/" className="hover:text-ink">Back to your resumes</Link>
        </p>
      )}
    </div>
  )
}
