import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError } from '../api.js'
import { Button, ErrorBox, Input, TextArea } from '../components/ui.jsx'
import ResumeDocument from '../builder/ResumeDocument.jsx'
import {
  TEMPLATES, emptyDraft, fromParsed, toPayload, fileBaseName,
  addJob, addSchool, addProject, moveItem,
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
    <ol className="flex flex-wrap items-center gap-x-2 gap-y-2 text-[13px] mb-7">
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
  )
}

function ScaledSheet({ children }) {
  const outer = useRef(null)
  const inner = useRef(null)
  const [scale, setScale] = useState(1)
  const [height, setHeight] = useState(1123)
  useEffect(() => {
    const el = outer.current
    if (!el) return
    const measure = () => {
      const next = Math.min(1, el.clientWidth / 794)
      setScale(next)
      setHeight(inner.current ? inner.current.offsetHeight : 1123)
    }
    measure()
    const ro = new ResizeObserver(measure)
    ro.observe(el)
    return () => ro.disconnect()
  }, [children])
  return (
    <div ref={outer} className="w-full overflow-hidden" style={{ height: height * scale }}>
      <div ref={inner} style={{ width: 794, transform: `scale(${scale})`, transformOrigin: 'top left' }}>
        {children}
      </div>
    </div>
  )
}

function Field({ label, value, onChange, placeholder }) {
  return <Input label={label} value={value} placeholder={placeholder} onChange={(e) => onChange(e.target.value)} />
}

function Area({ label, value, onChange, hint, rows = 4 }) {
  return (
    <label className="block sm:col-span-2">
      <span className="text-[13px] font-medium text-muted">{label}</span>
      <TextArea className="mt-1.5" rows={rows} value={value} onChange={(e) => onChange(e.target.value)} />
      {hint && <span className="block mt-1 text-xs text-muted">{hint}</span>}
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
  const [step, setStep] = useState('start')
  const [draft, setDraft] = useState(emptyDraft)
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
      const uploaded = await api.uploadResume(file)
      const parsed = await api.parsed(uploaded.id)
      setDraft((d) => ({ ...fromParsed(parsed), templateId: d.templateId }))
      setStep('format')
    } catch (e) { setError(e.message) }
    finally { setBusy('') }
  }

  const download = async (format) => {
    const payload = { ...toPayload(draft), format }
    if (!payload.name) return setError('Add your name before downloading.')
    setBusy(`download-${format}`); setError('')
    try {
      const { blob, filename } = await api.exportResume(payload)
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
        ? 'Download is not available yet. The preview above is how the file will look.'
        : e.message)
    } finally { setBusy('') }
  }

  return (
    <div className="max-w-5xl">
      <Stepper current={step} />
      <ErrorBox message={error} />

      {step === 'start' && (
        <div>
          <h1 className="font-serif text-3xl font-medium tracking-tight">Build a resume</h1>
          <p className="text-sm text-muted mt-2 max-w-xl">Upload a resume to fill the form, or start blank. Then pick a format, edit the details, and download the file.</p>
          <div className="grid gap-4 sm:grid-cols-2 mt-8">
            <button type="button" disabled={busy === 'prefill'} onClick={() => fileInput.current.click()}
              className="text-left bg-surface border border-line rounded-xl p-5 hover:border-faint transition-colors disabled:opacity-60">
              <p className="font-medium">Upload to prefill</p>
              <p className="text-sm text-muted mt-1">PDF or DOCX, up to 2 MB. This also saves it under Your resumes.</p>
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
                  className={`text-left bg-surface rounded-xl overflow-hidden w-full sm:w-[calc(50%-8px)] lg:w-[calc((100%-32px)/3)] border transition-colors ${selected ? 'border-ink ring-1 ring-ink' : 'border-line hover:border-faint'}`}>
                  <img src={t.thumb} alt="" className="w-full aspect-[3/4] object-cover object-top bg-sunken border-b border-line" />
                  <span className="block p-3.5">
                    <span className="flex items-center justify-between gap-2">
                      <span className="font-semibold text-[15px]">{t.name}</span>
                      {selected && <span className="text-[11px] font-semibold bg-ink text-paper px-2 py-0.5 rounded-full">Selected</span>}
                    </span>
                    <span className="block text-[13px] text-muted mt-1">{t.blurb}</span>
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
          <p className="text-sm text-muted mt-2 mb-7">This is a form, not a document editor. The file is built when you download. Empty sections are left off the page.</p>
          <div className="space-y-4 pb-20">
            <section className="bg-surface border border-line rounded-xl shadow-[0_1px_2px_rgba(0,0,0,0.04)]">
              <h2 className="text-[15px] font-semibold tracking-tight px-5 sm:px-6 pt-5">Contact</h2>
              <div className="px-5 sm:px-6 pb-5 sm:pb-6 pt-4 grid gap-3.5 sm:grid-cols-2">
                <Field label="Full name" value={draft.contact.fullName} onChange={(v) => setContact('fullName', v)} />
                <Field label="Headline" value={draft.contact.headline} onChange={(v) => setContact('headline', v)} placeholder="Frontend Engineer" />
                <Field label="City" value={draft.contact.city} onChange={(v) => setContact('city', v)} />
                <Field label="Email" value={draft.contact.email} onChange={(v) => setContact('email', v)} />
                <Field label="Phone" value={draft.contact.phone} onChange={(v) => setContact('phone', v)} />
                <Field label="LinkedIn" value={draft.contact.linkedin} onChange={(v) => setContact('linkedin', v)} />
                <Field label="GitHub" value={draft.contact.github} onChange={(v) => setContact('github', v)} />
              </div>
            </section>

            <section className="bg-surface border border-line rounded-xl shadow-[0_1px_2px_rgba(0,0,0,0.04)]">
              <h2 className="text-[15px] font-semibold tracking-tight px-5 sm:px-6 pt-5">Summary</h2>
              <div className="px-5 sm:px-6 pb-5 sm:pb-6 pt-4">
                <Area label="Professional summary" value={draft.summary} onChange={(v) => setField('summary', v)} />
              </div>
            </section>

            <section className="bg-surface border border-line rounded-xl shadow-[0_1px_2px_rgba(0,0,0,0.04)]">
              <h2 className="text-[15px] font-semibold tracking-tight px-5 sm:px-6 pt-5">Skills</h2>
              <div className="px-5 sm:px-6 pb-5 sm:pb-6 pt-4">
                <Field label="Skills" value={draft.skills} onChange={(v) => setField('skills', v)} placeholder="Java · Spring Boot · REST APIs" />
              </div>
            </section>

            <section className="bg-surface border border-line rounded-xl shadow-[0_1px_2px_rgba(0,0,0,0.04)]">
              <h2 className="text-[15px] font-semibold tracking-tight px-5 sm:px-6 pt-5">Highlights</h2>
              <div className="px-5 sm:px-6 pb-5 sm:pb-6 pt-4">
                <Area label="Highlights" value={draft.highlights} onChange={(v) => setField('highlights', v)} hint="One per line. Shown on the Combination format." rows={3} />
              </div>
            </section>

            <CardList title="Experience" note="Use the arrows to reorder" items={draft.experience}
              onMove={(i, dir) => setDraft((d) => ({ ...d, experience: moveItem(d.experience, i, dir) }))}
              onRemove={(i) => setDraft((d) => ({ ...d, experience: d.experience.filter((_, j) => j !== i).length ? d.experience.filter((_, j) => j !== i) : d.experience }))}
              onAdd={() => setDraft(addJob)} addLabel="+ Add another job">
              {(job, i) => (
                <div className="grid gap-3.5 sm:grid-cols-2">
                  <Field label="Title" value={job.title} onChange={(v) => setDraft((d) => ({ ...d, experience: d.experience.map((x, j) => j === i ? { ...x, title: v } : x) }))} />
                  <Field label="Company" value={job.company} onChange={(v) => setDraft((d) => ({ ...d, experience: d.experience.map((x, j) => j === i ? { ...x, company: v } : x) }))} />
                  <Field label="Location" value={job.location} onChange={(v) => setDraft((d) => ({ ...d, experience: d.experience.map((x, j) => j === i ? { ...x, location: v } : x) }))} />
                  <Field label="Dates" value={job.dates} onChange={(v) => setDraft((d) => ({ ...d, experience: d.experience.map((x, j) => j === i ? { ...x, dates: v } : x) }))} placeholder="Jul 2024 – Present" />
                  <Area label="Bullets" value={job.bullets} onChange={(v) => setDraft((d) => ({ ...d, experience: d.experience.map((x, j) => j === i ? { ...x, bullets: v } : x) }))} hint="One bullet per line." />
                </div>
              )}
            </CardList>

            <CardList title="Education" items={draft.education}
              onMove={(i, dir) => setDraft((d) => ({ ...d, education: moveItem(d.education, i, dir) }))}
              onRemove={(i) => setDraft((d) => ({ ...d, education: d.education.length > 1 ? d.education.filter((_, j) => j !== i) : d.education }))}
              onAdd={() => setDraft(addSchool)} addLabel="+ Add another school">
              {(school, i) => (
                <div className="grid gap-3.5 sm:grid-cols-2">
                  <Field label="Degree" value={school.degree} onChange={(v) => setDraft((d) => ({ ...d, education: d.education.map((x, j) => j === i ? { ...x, degree: v } : x) }))} />
                  <Field label="School" value={school.school} onChange={(v) => setDraft((d) => ({ ...d, education: d.education.map((x, j) => j === i ? { ...x, school: v } : x) }))} />
                  <Field label="Dates" value={school.dates} onChange={(v) => setDraft((d) => ({ ...d, education: d.education.map((x, j) => j === i ? { ...x, dates: v } : x) }))} />
                  <Field label="CGPA" value={school.gpa} onChange={(v) => setDraft((d) => ({ ...d, education: d.education.map((x, j) => j === i ? { ...x, gpa: v } : x) }))} />
                  <Area label="Details" value={school.details} onChange={(v) => setDraft((d) => ({ ...d, education: d.education.map((x, j) => j === i ? { ...x, details: v } : x) }))} hint="One point per line, such as coursework." rows={2} />
                </div>
              )}
            </CardList>

            <CardList title="Projects" items={draft.projects}
              onMove={(i, dir) => setDraft((d) => ({ ...d, projects: moveItem(d.projects, i, dir) }))}
              onRemove={(i) => setDraft((d) => ({ ...d, projects: d.projects.length > 1 ? d.projects.filter((_, j) => j !== i) : d.projects }))}
              onAdd={() => setDraft(addProject)} addLabel="+ Add another project">
              {(project, i) => (
                <div className="grid gap-3.5 sm:grid-cols-2">
                  <Field label="Name" value={project.name} onChange={(v) => setDraft((d) => ({ ...d, projects: d.projects.map((x, j) => j === i ? { ...x, name: v } : x) }))} />
                  <Field label="Dates" value={project.dates} onChange={(v) => setDraft((d) => ({ ...d, projects: d.projects.map((x, j) => j === i ? { ...x, dates: v } : x) }))} />
                  <Field label="Stack" value={project.stack} onChange={(v) => setDraft((d) => ({ ...d, projects: d.projects.map((x, j) => j === i ? { ...x, stack: v } : x) }))} placeholder="Java · MySQL" />
                  <Area label="Bullets" value={project.bullets} onChange={(v) => setDraft((d) => ({ ...d, projects: d.projects.map((x, j) => j === i ? { ...x, bullets: v } : x) }))} hint="One bullet per line." rows={3} />
                </div>
              )}
            </CardList>

            <section className="bg-surface border border-line rounded-xl shadow-[0_1px_2px_rgba(0,0,0,0.04)]">
              <h2 className="text-[15px] font-semibold tracking-tight px-5 sm:px-6 pt-5">More</h2>
              <div className="px-5 sm:px-6 pb-5 sm:pb-6 pt-4 grid gap-3.5">
                <Area label="Certifications" value={draft.certifications} onChange={(v) => setField('certifications', v)} rows={2} />
                <Field label="Languages" value={draft.languages} onChange={(v) => setField('languages', v)} placeholder="English · Hindi" />
                <Area label="Achievements" value={draft.achievements} onChange={(v) => setField('achievements', v)} hint="One per line. Shown on the Campus format." rows={3} />
              </div>
            </section>
          </div>
          <div className="sticky bottom-0 -mx-4 sm:-mx-6 px-4 sm:px-6 py-3 border-t border-line bg-paper/95 backdrop-blur flex justify-between">
            <Button variant="ghost" onClick={() => setStep('format')}>Back</Button>
            <Button onClick={() => { setError(''); setStep('preview') }}>Continue to preview</Button>
          </div>
        </div>
      )}

      {step === 'preview' && (
        <div>
          <h1 className="font-serif text-3xl font-medium tracking-tight">Preview and download</h1>
          <p className="text-sm text-muted mt-2 mb-7">This is how the file will look. Download it, or go back to change the details.</p>
          <div className="grid gap-6 lg:grid-cols-[280px_minmax(0,1fr)] items-start">
            <aside className="space-y-3 lg:sticky lg:top-20">
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
            <div className="bg-[#e8e8ea] border border-line rounded-xl p-3 sm:p-5">
              <div className="mx-auto shadow-lg bg-white">
                <ScaledSheet>
                  <ResumeDocument data={draft} templateId={draft.templateId} />
                </ScaledSheet>
              </div>
            </div>
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
