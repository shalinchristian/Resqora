import { lines } from './model.js'
import './document.css'

function hasText(s) { return Boolean(s && String(s).trim()) }

function contactLines(c) {
  const top = [c.city, c.phone, c.email].filter(hasText)
  const bottom = [c.linkedin, c.github].filter(hasText)
  return { top: top.join('  |  '), bottom: bottom.join('  |  ') }
}

function Section({ title, show, children }) {
  if (!show) return null
  return (
    <>
      <h2 className="rs-section">{title}</h2>
      {children}
    </>
  )
}

function Job({ title, org, dates, stack, bullets }) {
  const items = Array.isArray(bullets) ? bullets.filter(hasText) : lines(bullets)
  if (!hasText(title) && !hasText(org) && !items.length) return null
  return (
    <div className="rs-job">
      {(hasText(title) || hasText(dates)) && (
        <div className="rs-row">
          <p className="rs-role">{title}</p>
          {hasText(dates) && <span className="rs-dates">{dates}</span>}
        </div>
      )}
      {hasText(org) && <p className="rs-org">{org}</p>}
      {hasText(stack) && <p className="rs-stack">{stack}</p>}
      {items.length > 0 && (
        <ul className="rs-list">
          {items.map((b, i) => <li key={i}>{b}</li>)}
        </ul>
      )}
    </div>
  )
}

function BulletList({ items }) {
  const list = (items || []).filter(hasText)
  if (!list.length) return null
  return (
    <ul className="rs-list">
      {list.map((b, i) => <li key={i}>{b}</li>)}
    </ul>
  )
}

function Header({ data, center, extraClass }) {
  const c = data.contact || {}
  const { top, bottom } = contactLines(c)
  return (
    <header className={extraClass || undefined} style={center ? { textAlign: 'center' } : undefined}>
      <h1 className="rs-name">{c.fullName || 'Your name'}</h1>
      {hasText(c.headline) && <p className="rs-headline">{c.headline}</p>}
      <p className="rs-contact">
        {top}{top && bottom ? <br /> : null}{bottom}
        {!top && !bottom ? '\u00a0' : null}
      </p>
    </header>
  )
}

function ExperienceList({ jobs }) {
  return (jobs || []).map((j, i) => (
    <Job key={i} title={j.title} org={[j.company, j.location].filter(hasText).join(', ')} dates={j.dates} bullets={j.bullets} />
  ))
}

function EducationList({ schools }) {
  return (schools || []).map((e, i) => {
    const org = [e.school, e.gpa ? `CGPA ${e.gpa}` : ''].filter(hasText).join('  ·  ')
    return <Job key={i} title={e.degree} org={org} dates={e.dates} bullets={e.details} />
  })
}

function ProjectList({ projects, showStack }) {
  return (projects || []).map((p, i) => (
    <Job key={i} title={p.name} dates={p.dates} stack={showStack ? p.stack : ''} bullets={p.bullets} />
  ))
}

function Sections({ data, templateId }) {
  const highlights = Array.isArray(data.highlights) ? data.highlights : lines(data.highlights)
  const achievements = Array.isArray(data.achievements) ? data.achievements : lines(data.achievements)
  const jobs = data.experience || []
  const schools = data.education || []
  const projects = data.projects || []
  const hasJobs = jobs.some((j) => hasText(j.title) || hasText(j.company) || lines(j.bullets).length)
  const hasSchools = schools.some((e) => hasText(e.degree) || hasText(e.school) || lines(e.details).length)
  const hasProjects = projects.some((p) => hasText(p.name) || lines(p.bullets).length)

  const experience = <Section title="Experience" show={hasJobs}><ExperienceList jobs={jobs} /></Section>
  const education = <Section title="Education" show={hasSchools}><EducationList schools={schools} /></Section>
  const projectBlock = <Section title="Projects" show={hasProjects}><ProjectList projects={projects} showStack={templateId === 'campus'} /></Section>
  const skillsTitle = templateId === 'classic' || templateId === 'header-band' ? 'Technical Skills' : 'Skills'
  const skills = <Section title={skillsTitle} show={hasText(data.skills)}><p className="rs-line">{data.skills}</p></Section>
  const languages = <Section title="Languages" show={hasText(data.languages)}><p className="rs-line">{data.languages}</p></Section>
  const certs = <Section title="Certifications" show={hasText(data.certifications)}><p className="rs-line">{data.certifications}</p></Section>
  const highlightBlock = <Section title="Highlights" show={highlights.length > 0}><BulletList items={highlights} /></Section>
  const achievementBlock = <Section title="Achievements" show={achievements.length > 0}><BulletList items={achievements} /></Section>
  const summary = (
    <Section title={templateId === 'centered' ? 'Professional Summary' : 'Summary'} show={hasText(data.summary)}>
      <p className="rs-sum">{data.summary}</p>
    </Section>
  )

  if (templateId === 'combination') {
    return <>{skills}{highlightBlock}{experience}{education}{projectBlock}{languages}</>
  }
  if (templateId === 'campus') {
    return <>{education}{projectBlock}{experience}{skills}{achievementBlock}{languages}</>
  }
  return <>{summary}{highlightBlock}{experience}{education}{projectBlock}{skills}{certs}{achievementBlock}{languages}</>
}

export default function ResumeDocument({ data, templateId = 'classic' }) {
  const id = templateId || 'classic'

  if (id === 'header-band') {
    return (
      <article className="resume-sheet rs-header-band rs-band-wrap">
        <Header data={data} extraClass="rs-band" />
        <div className="rs-band-inner">
          <Sections data={data} templateId={id} />
        </div>
      </article>
    )
  }

  const cls = id === 'combination' ? 'rs-combo' : id === 'campus' ? 'rs-campus' : id === 'centered' ? 'rs-centered' : ''
  return (
    <article className={`resume-sheet ${cls}`.trim()}>
      <Header data={data} center={id === 'centered'} extraClass={id === 'centered' ? 'rs-head' : undefined} />
      {id === 'campus' && <div className="rs-accent" />}
      <Sections data={data} templateId={id} />
    </article>
  )
}
