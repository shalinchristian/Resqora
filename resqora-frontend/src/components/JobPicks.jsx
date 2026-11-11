import { useEffect, useState } from 'react'
import { api } from '../api.js'
import { Section } from './ui.jsx'

// Up to 3 picks from JobsForce India. Shows nothing while loading, on error, or with no picks,
// so the Jobicy list below always carries on as before.
// Expects { picks: [{ title, hiring: { display }, location, why, url, applyUrl }] }
export default function JobPicks({ resumeId }) {
  const [picks, setPicks] = useState([])

  useEffect(() => {
    if (!resumeId) return
    let live = true
    setPicks([])
    api.jobPicks(resumeId)
      .then((d) => { if (live) setPicks((d?.picks || []).filter((p) => p && p.title && (p.url || p.applyUrl)).slice(0, 3)) })
      .catch(() => {})
    return () => { live = false }
  }, [resumeId])

  if (!picks.length) return null

  return (
    <Section title="Recommended for you" note="Picked from your resume">
      <ul className="grid gap-3">
        {picks.map((p, i) => {
          const link = p.url || p.applyUrl
          const hiring = p.hiring?.display
          return (
            <li key={link} style={{ animationDelay: `${i * 40}ms` }} className="fade-up">
              <a href={link} target="_blank" rel="noreferrer"
                className="group block rounded-lg border border-line p-4 hover:border-faint hover:bg-sunken/60 transition-colors">
                <span className="block font-medium line-clamp-2 group-hover:text-accent transition-colors">{p.title}</span>
                {(hiring || p.location) && (
                  <span className="block text-sm text-muted mt-0.5 truncate">{[hiring, p.location].filter(Boolean).join(', ')}</span>
                )}
                {p.why && <span className="block text-sm mt-2">{p.why}</span>}
                <span className="mt-3 flex items-center justify-between gap-3 text-xs">
                  <span className="text-faint">via JobsForce India</span>
                  <span className="inline-flex items-center min-h-11 -my-3 text-sm font-medium text-muted group-hover:text-ink transition-colors">
                    {p.url ? 'View job' : 'Apply'}
                  </span>
                </span>
              </a>
            </li>
          )
        })}
      </ul>
    </Section>
  )
}
