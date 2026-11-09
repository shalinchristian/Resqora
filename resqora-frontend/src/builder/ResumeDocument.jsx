// Live preview of the downloaded file. It renders the exact JSON sent to POST /api/builder/export
// and mirrors ResumeTemplate / ResumeDocxExporter / ResumePdfExporter: same section order, titles,
// contact line, fonts (Carlito = Calibri metrics, Tinos = Times metrics) and spacing in pt.
import '@fontsource/carlito/400.css'
import '@fontsource/carlito/400-italic.css'
import '@fontsource/carlito/700.css'
import '@fontsource/tinos/400.css'
import '@fontsource/tinos/400-italic.css'
import '@fontsource/tinos/700.css'
import './document.css'

const has = (s) => typeof s === 'string' && s.trim() !== ''
const clean = (s) => (s || '').trim()

// same rule as ResumeDocxExporter.bullets(): one per line, leading bullet marks stripped
const bullets = (text) => (text || '').split(/\r?\n/)
  .map((l) => l.trim().replace(/^[•\-*–·▪●]\s*/, '').trim()).filter(Boolean)

const ORDER = {
  classic: ['summary', 'highlights', 'experience', 'education', 'projects', 'skills', 'certifications', 'achievements', 'languages'],
  combination: ['summary', 'skills', 'highlights', 'experience', 'projects', 'education', 'certifications', 'achievements', 'languages'],
  campus: ['summary', 'highlights', 'education', 'projects', 'experience', 'skills', 'certifications', 'achievements', 'languages'],
}
ORDER.centered = ORDER.classic
ORDER['header-band'] = ORDER.classic

function Heading({ children }) {
  return <h2 className="rs-h">{children}</h2>
}

function TitleLine({ title, dates, first }) {
  return (
    <div className={`rs-title${first ? ' rs-first' : ''}`}>
      <span className="rs-b">{clean(title)}</span>
      {has(dates) && <span className="rs-dates">{clean(dates)}</span>}
    </div>
  )
}

function Bullets({ items }) {
  if (!items.length) return null
  return <ul className="rs-ul">{items.map((b, i) => <li key={i}>{b}</li>)}</ul>
}

const strings = (list) => (list || []).filter(has).map((s) => s.trim())

const SECTIONS = {
  summary: (p) => {
    if (!has(p.summary)) return null
    const ls = p.summary.split(/\r?\n/).map((l) => l.trim()).filter(Boolean)
    return <><Heading>Summary</Heading>{ls.map((l, i) => <p key={i} className="rs-p rs-sumline">{l}</p>)}</>
  },
  highlights: (p) => {
    const v = strings(p.highlights)
    return v.length ? <><Heading>Highlights</Heading><Bullets items={v} /></> : null
  },
  experience: (p) => {
    const e = (p.experience || []).filter((x) => x && (has(x.title) || has(x.company) || has(x.description)))
    if (!e.length) return null
    return (
      <><Heading>Experience</Heading>
        {e.map((x, i) => (
          <div key={i} className="rs-entry">
            <TitleLine title={has(x.title) ? x.title : x.company} dates={x.dates} first={i === 0} />
            {has(x.title) && has(x.company) && <p className="rs-p rs-i">{clean(x.company)}</p>}
            <Bullets items={bullets(x.description)} />
          </div>
        ))}
      </>
    )
  },
  education: (p) => {
    const e = (p.education || []).filter((x) => x && (has(x.institution) || has(x.degree)))
    if (!e.length) return null
    return (
      <><Heading>Education</Heading>
        {e.map((x, i) => {
          const degreeLine = has(x.institution) && has(x.degree)
          return (
            <div key={i} className="rs-entry">
              <TitleLine title={has(x.institution) ? x.institution : x.degree} dates={x.dates} first={i === 0} />
              {(degreeLine || has(x.location)) && (
                <div className="rs-title rs-i">
                  <span>{degreeLine ? clean(x.degree) : ''}</span>
                  {has(x.location) && <span>{clean(x.location)}</span>}
                </div>
              )}
              <Bullets items={bullets(x.details)} />
            </div>
          )
        })}
      </>
    )
  },
  projects: (p) => {
    const e = (p.projects || []).filter((x) => x && (has(x.name) || has(x.description)))
    if (!e.length) return null
    return (
      <><Heading>Projects</Heading>
        {e.map((x, i) => (
          <div key={i} className="rs-entry">
            {has(x.name) && <p className={`rs-p rs-b${i === 0 ? ' rs-first' : ''}`}>{clean(x.name)}</p>}
            <Bullets items={bullets(x.description)} />
          </div>
        ))}
      </>
    )
  },
  skills: (p) => {
    const v = strings(p.skills)
    return v.length ? <><Heading>Skills</Heading><p className="rs-p">{v.join(', ')}</p></> : null
  },
  certifications: (p) => {
    const v = strings(p.certifications)
    return v.length ? <><Heading>Certifications</Heading><Bullets items={v} /></> : null
  },
  achievements: (p) => {
    const v = strings(p.achievements)
    return v.length ? <><Heading>Achievements</Heading><Bullets items={v} /></> : null
  },
  languages: (p) => {
    const v = strings(p.languages)
    return v.length ? <><Heading>Languages</Heading><p className="rs-p">{v.join(', ')}</p></> : null
  },
}

// email · phone · location · linkedin · github, blanks skipped (ResumeDocxExporter.contactLine)
const contactLine = (p) => [p.email, p.phone, p.location, p.linkedin, p.github].filter(has).map((s) => s.trim()).join('  ·  ')

function Header({ p, style }) {
  const contact = contactLine(p)
  const name = clean(p.name) || 'Your name'
  const inner = (
    <>
      <p className="rs-name">{name}</p>
      {has(p.headline) && <p className="rs-headline">{clean(p.headline)}</p>}
      {contact && <p className="rs-p rs-contact">{contact}</p>}
    </>
  )
  if (style === 'band') return <header className="rs-band">{inner}</header>
  return <header className={style === 'center' ? 'rs-center' : undefined}>{inner}</header>
}

export default function ResumeDocument({ payload }) {
  const id = ORDER[payload.templateId] ? payload.templateId : 'classic'
  const style = id === 'header-band' ? 'band' : id === 'centered' ? 'center' : 'left'
  return (
    <article className={`resume-sheet${id === 'centered' ? ' rs-serif' : ''}`}>
      <Header p={payload} style={style} />
      {ORDER[id].map((key) => {
        const node = SECTIONS[key](payload)
        return node ? <section key={key}>{node}</section> : null
      })}
    </article>
  )
}
