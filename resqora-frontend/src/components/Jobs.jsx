import { useEffect, useState } from 'react'
import { api } from '../api.js'
import { Section, toneFor } from './ui.jsx'

const TONE = { good: 'text-good bg-good-soft', ok: 'text-ok bg-ok-soft', bad: 'text-bad bg-bad-soft' }

export default function Jobs({ resumeId, title = 'Matching jobs', note, style }) {
  const [state, setState] = useState({ loading: true })

  useEffect(() => {
    if (!resumeId) return
    setState({ loading: true })
    api.jobs(resumeId)
      .then((d) => setState({ jobs: d.jobs || [] }))
      .catch((e) => setState({ error: e.status === 503 ? 'Job search is not connected yet. Matching jobs will show here once it is.' : e.message }))
  }, [resumeId])

  return (
    <Section title={title} note={note} style={style}>
      {state.loading && (
        <div className="space-y-2">
          {[0, 1, 2].map((i) => <div key={i} className="h-16 rounded-lg bg-sunken animate-pulse" />)}
        </div>
      )}
      {state.error && <p className="text-sm text-muted">{state.error}</p>}
      {state.jobs && state.jobs.length === 0 && <p className="text-sm text-muted">No matches. Add more skills to your resume.</p>}
      {state.jobs && state.jobs.length > 0 && (
        <ul className="divide-y divide-line -mx-2">
          {state.jobs.map((j, i) => (
            <li key={j.url || i} style={{ animationDelay: `${i * 40}ms` }} className="fade-up">
              <a href={j.url} target="_blank" rel="noreferrer"
                className="group flex items-center gap-3 px-2 py-3 rounded-lg hover:bg-sunken/60 transition-colors">
                <span className={`shrink-0 w-12 text-center text-xs font-semibold tabular-nums rounded-md py-1 ${TONE[toneFor(j.matchScore)]}`}>
                  {j.matchScore}%
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block font-medium line-clamp-2 group-hover:text-accent transition-colors">{j.title}</span>
                  <span className="block text-xs text-muted truncate">{[j.company, j.location].filter(Boolean).join(', ')}</span>
                  {j.matchedSkills?.length > 0 && (
                    <span className="hidden sm:block text-xs text-faint mt-0.5">Matches {j.matchedSkills.slice(0, 4).join(', ')}</span>
                  )}
                </span>
                <span className="shrink-0 text-sm text-muted group-hover:text-ink transition-colors">
                  View job
                </span>
              </a>
            </li>
          ))}
        </ul>
      )}
      {state.jobs && state.jobs.length > 0 && (
        <p className="mt-3 text-xs text-faint">
          Remote jobs via <a href="https://jobicy.com" target="_blank" rel="noreferrer" className="underline hover:text-ink">Jobicy</a>. Each job opens its original listing.
        </p>
      )}
    </Section>
  )
}
