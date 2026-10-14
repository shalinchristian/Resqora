import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../api.js'
import { FEATURES } from '../features.js'
import Jobs from '../components/Jobs.jsx'
import { Button, ErrorBox, Notice, Toast, toneFor } from '../components/ui.jsx'

const MAX_SIZE = 2 * 1024 * 1024
const RING = { good: 'stroke-good', ok: 'stroke-ok', bad: 'stroke-bad' }
const TEXT = { good: 'text-good', ok: 'text-ok', bad: 'text-bad' }

function formatDate(iso) {
  return new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
}

function ScoreRing({ score }) {
  if (score == null) return <span title="Not scored yet" className="shrink-0 w-11 h-11 grid place-items-center rounded-full border-2 border-dashed border-line text-sm text-faint">–</span>
  const r = 15, c = 2 * Math.PI * r, tone = toneFor(score)
  return (
    <span className="relative shrink-0 w-11 h-11" title={`Score ${score} out of 100`}>
      <svg viewBox="0 0 36 36" className="-rotate-90 w-full h-full">
        <circle cx="18" cy="18" r={r} fill="none" strokeWidth="3" className="stroke-sunken" />
        <circle cx="18" cy="18" r={r} fill="none" strokeWidth="3" strokeLinecap="round" className={RING[tone]} strokeDasharray={c} strokeDashoffset={c * (1 - Math.min(score, 100) / 100)} />
      </svg>
      <span className={`absolute inset-0 grid place-items-center text-xs font-semibold tabular-nums ${TEXT[tone]}`}>{score}</span>
    </span>
  )
}

// The "⋯" menu on each row keeps Delete out of the way.
function RowMenu({ onDelete }) {
  const [open, setOpen] = useState(false)
  const ref = useRef(null)
  useEffect(() => {
    const close = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false) }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [])
  return (
    <div ref={ref} className="relative" onClick={(e) => e.stopPropagation()}>
      <button aria-label="More actions" onClick={() => setOpen(!open)} className="w-8 h-8 grid place-items-center rounded-md text-muted hover:text-ink hover:bg-sunken">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><circle cx="5" cy="12" r="1.6" /><circle cx="12" cy="12" r="1.6" /><circle cx="19" cy="12" r="1.6" /></svg>
      </button>
      {open && (
        <div className="absolute right-0 mt-1 w-36 bg-surface border border-line rounded-lg shadow-lg p-1 z-10">
          <button onClick={() => { setOpen(false); onDelete() }} className="w-full text-left text-sm text-bad rounded-md px-2.5 py-2 hover:bg-bad-soft">Delete</button>
        </div>
      )}
    </div>
  )
}

function Welcome() {
  const steps = [
    ['Upload your resume', 'PDF or DOCX, up to 2 MB.'],
    ['Check your score', 'See what is strong and what to fix.'],
    ['Match a job', 'Paste a job description and ask whether you should apply.'],
  ]
  return (
    <ol className="grid gap-3 sm:grid-cols-3">
      {steps.map(([t, d], i) => (
        <li key={t} className="bg-surface border border-line rounded-xl p-4">
          <span className="w-6 h-6 grid place-items-center rounded-full bg-sunken text-accent text-xs font-semibold">{i + 1}</span>
          <p className="font-medium mt-3">{t}</p>
          <p className="text-sm text-muted mt-1">{d}</p>
        </li>
      ))}
    </ol>
  )
}

// Big score for the best resume, plus the most important thing to fix in it.
function BestScore({ resume }) {
  const tip = resume.topFinding
  const tone = toneFor(resume.overallScore)
  return (
    <Link to={`/resumes/${resume.id}`} className="block bg-surface border border-line rounded-xl p-5 hover:border-faint transition-colors">
      <p className="text-sm text-muted">Best score</p>
      <p className="mt-1 flex items-baseline gap-1.5">
        <span className={`font-serif text-5xl font-medium tabular-nums ${TEXT[tone]}`}>{resume.overallScore}</span>
        <span className="text-sm text-muted">/ 100</span>
      </p>
      <p className="text-xs text-muted mt-1 truncate">{resume.originalFileName}</p>
      {tip && (
        <div className="mt-4 pt-4 border-t border-line">
          <p className="text-xs font-medium text-accent">Fix first</p>
          <p className="text-sm mt-1">{tip}</p>
        </div>
      )}
    </Link>
  )
}

export default function Dashboard() {
  const navigate = useNavigate()
  const [data, setData] = useState(null)
  const [usage, setUsage] = useState(null)
  const [error, setError] = useState('')
  const [uploading, setUploading] = useState(false)
  const [dragging, setDragging] = useState(false)
  const [toast, setToast] = useState('')
  const fileInput = useRef(null)
  const toastTimer = useRef(null)
  const flash = (msg) => { clearTimeout(toastTimer.current); setToast(msg); toastTimer.current = setTimeout(() => setToast(''), 2500) }

  const [loadError, setLoadError] = useState('')
  const load = () => api.listResumes().then((d) => { setData(d); setLoadError('') }).catch((e) => setLoadError(e.message))
  useEffect(() => { load(); api.aiUsage().then(setUsage).catch(() => {}) }, [])

  const upload = async (file) => {
    if (!file) return
    if (data && data.count >= data.limit) return
    if (!/\.(pdf|docx)$/i.test(file.name)) return setError('Only PDF and DOCX files are supported.')
    if (file.size > MAX_SIZE) return setError('File must be 2 MB or smaller.')
    setError(''); setUploading(true)
    try { await api.uploadResume(file); await load(); flash(`Uploaded ${file.name}`) }
    catch (err) { setError(err.message) }
    finally { setUploading(false) }
  }

  const onDelete = async (r) => {
    if (!confirm(`Delete ${r.originalFileName}? Its analysis is deleted too.`)) return
    try { await api.deleteResume(r.id); await load(); flash('Resume deleted') } catch (err) { setError(err.message) }
  }

  // Dropping a file anywhere on the page uploads it.
  const dropProps = {
    onDragOver: (e) => { e.preventDefault(); setDragging(true) },
    onDragLeave: (e) => { if (!e.currentTarget.contains(e.relatedTarget)) setDragging(false) },
    onDrop: (e) => { e.preventDefault(); setDragging(false); upload(e.dataTransfer.files[0]) },
  }

  if (!data) return (
    <div className="max-w-xl">
      <h1 className="font-serif text-3xl">Your resumes</h1>
      {loadError ? (
        <div className="mt-6 bg-surface border border-line rounded-xl p-6">
          <p className="text-sm font-medium">Couldn't load your resumes</p>
          <p className="text-sm text-muted mt-1">{loadError}</p>
          <Button variant="ghost" className="mt-4" onClick={() => { setLoadError(''); load() }}>Try again</Button>
        </div>
      ) : <p className="text-muted mt-4">Loading resumes...</p>}
    </div>
  )
  const full = data.count >= data.limit
  const empty = data.resumes.length === 0
  const best = [...data.resumes].filter((r) => r.overallScore != null).sort((a, b) => b.overallScore - a.overallScore)[0]
  const jobsFor = best || data.resumes[0]

  return (
    <div {...dropProps} className="space-y-6">
      <input ref={fileInput} type="file" accept=".pdf,.docx" className="hidden"
        onChange={(e) => { const f = e.target.files[0]; e.target.value = ''; upload(f) }} />

      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="font-serif text-3xl font-medium tracking-tight">Your resumes</h1>
          <p className="text-sm text-muted mt-1">{data.count} / {data.limit} resumes</p>
        </div>
        {!empty && (
          <Button variant="accent" disabled={uploading || full} onClick={() => fileInput.current.click()}
            title={full ? 'Delete a resume to upload another' : undefined}>
            {uploading ? 'Uploading...' : 'Upload resume'}
          </Button>
        )}
      </div>

      <ErrorBox message={error} />
      {full && <Notice tone="ok">You've used all {data.limit} slots. Delete a resume to upload another.</Notice>}

      {empty ? (
        <div className="space-y-6">
          <button type="button" disabled={uploading} onClick={() => fileInput.current.click()}
            className={`w-full rounded-xl border border-dashed px-6 py-12 text-center transition-colors ${dragging ? 'border-accent bg-surface' : 'border-line hover:border-accent hover:bg-surface'}`}>
            <p className="font-medium">{uploading ? 'Uploading...' : 'Upload your first resume'}</p>
            <p className="text-sm text-muted mt-1">Drop a PDF or DOCX here, or click to choose one. Max 2 MB.</p>
          </button>
          <Welcome />
        </div>
      ) : (
        <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_380px] items-start">
          <div className="space-y-6 min-w-0 order-1 lg:col-start-1 lg:row-start-1">
            <ul className={`bg-surface border rounded-xl divide-y divide-line transition-colors ${dragging ? 'border-accent' : 'border-line'}`}>
              {data.resumes.map((r) => (
                <li key={r.id} onClick={() => navigate(`/resumes/${r.id}`)}
                  className="group flex items-center gap-4 p-4 sm:px-5 cursor-pointer hover:bg-sunken/50 transition-colors first:rounded-t-xl last:rounded-b-xl">
                  <ScoreRing score={r.overallScore} />
                  <div className="min-w-0 flex-1">
                    <Link to={`/resumes/${r.id}`} onClick={(e) => e.stopPropagation()} className="block font-medium truncate group-hover:text-accent transition-colors">{r.originalFileName}</Link>
                    <p className="text-xs text-muted mt-0.5">{r.documentType}, uploaded {formatDate(r.uploadedAt)}</p>
                  </div>
                  <span className="hidden sm:block text-sm text-muted group-hover:text-ink transition-colors">Open</span>
                  <RowMenu onDelete={() => onDelete(r)} />
                </li>
              ))}
            </ul>
            {!full && <p className="text-xs text-muted text-center">{dragging ? 'Drop to upload' : 'or drop a file here'}</p>}
          </div>

          <aside className="space-y-6 lg:sticky lg:top-20 order-2 lg:col-start-2 lg:row-start-1 lg:row-span-2">
            {best && <BestScore resume={best} />}
            {usage && (
              <div className="bg-surface border border-line rounded-xl p-5">
                <div className="flex items-baseline justify-between">
                  <p className="text-sm text-muted">AI requests today</p>
                  <p className="text-sm tabular-nums"><span className="font-semibold">{usage.remainingToday}</span> <span className="text-muted">of {usage.limit} left</span></p>
                </div>
                <div className="h-1.5 bg-sunken rounded-full mt-3 overflow-hidden">
                  <div className="h-full bg-faint rounded-full" style={{ width: `${(usage.remainingToday / usage.limit) * 100}%` }} />
                </div>
              </div>
            )}
          </aside>
          <div className="order-3 min-w-0 lg:col-start-1">{FEATURES.jobs && jobsFor && <Jobs resumeId={jobsFor.id} />}</div>
        </div>
      )}
      <Toast>{toast}</Toast>
    </div>
  )
}
