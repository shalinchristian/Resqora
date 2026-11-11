// Resume builder draft and the JSON sent to POST /api/builder/export.
// Keep this shape stable — Ben's exporter reads the same fields.

import classicThumb from '../assets/templates/classic.png'
import combinationThumb from '../assets/templates/combination.png'
import campusThumb from '../assets/templates/campus.png'
import centeredThumb from '../assets/templates/centered.png'
import headerBandThumb from '../assets/templates/header-band.png'

export const TEMPLATES = [
  { id: 'classic', name: 'Classic', blurb: 'Clean single column. Good default.', thumb: classicThumb },
  { id: 'combination', name: 'Combination', blurb: 'Skills, then highlights, before experience.', thumb: combinationThumb },
  { id: 'campus', name: 'Campus', blurb: 'Education and projects first, for students.', thumb: campusThumb },
  { id: 'centered', name: 'Centered', blurb: 'Formal, with a centered name.', thumb: centeredThumb },
  { id: 'header-band', name: 'Header-band', blurb: 'Name sits in a navy bar.', thumb: headerBandThumb },
]

export const TEMPLATE_IDS = TEMPLATES.map((t) => t.id)

let nextId = 1
const uid = () => `item-${nextId++}`

export const lines = (text) => (text || '').split(/\r?\n/).map((s) => s.replace(/^[•\-\u2013\u2014]\s*/, '').trim()).filter(Boolean)

const emptyJob = () => ({ id: uid(), title: '', company: '', location: '', dates: '', bullets: '' })
const emptySchool = () => ({ id: uid(), degree: '', school: '', location: '', dates: '', gpa: '', details: '' })
const emptyProject = () => ({ id: uid(), name: '', dates: '', stack: '', bullets: '' })

export function emptyDraft() {
  return {
    templateId: 'classic',
    contact: { fullName: '', headline: '', city: '', email: '', phone: '', linkedin: '', github: '' },
    summary: '',
    skills: '',
    highlights: '',
    experience: [emptyJob()],
    education: [emptySchool()],
    projects: [emptyProject()],
    certifications: '',
    languages: '',
    achievements: '',
  }
}

export function addJob(draft) {
  return { ...draft, experience: [...draft.experience, emptyJob()] }
}
export function addSchool(draft) {
  return { ...draft, education: [...draft.education, emptySchool()] }
}
export function addProject(draft) {
  return { ...draft, projects: [...draft.projects, emptyProject()] }
}

export function moveItem(list, index, dir) {
  const next = index + dir
  if (next < 0 || next >= list.length) return list
  const copy = [...list]
  ;[copy[index], copy[next]] = [copy[next], copy[index]]
  return copy
}

function bulletsFromDescription(text) {
  if (!text) return ''
  const byLine = lines(text)
  if (byLine.length > 1) return byLine.join('\n')
  return text.trim()
}

// Same limits as the backend's BuilderExportRequest, so a prefilled or pasted draft never 400s on download.
export const LIMITS = { skills: 100, highlights: 20, certifications: 30, achievements: 20, languages: 20, experience: 30, education: 20, projects: 30, bullets: 18, item: 200 }
const cut = (s, n = LIMITS.item) => (s || '').trim().slice(0, n)
const capLines = (text, n, len = LIMITS.item) => lines(text).map((l) => l.slice(0, len)).slice(0, n)

// Break one long line into rows of at most `len`, at sentence breaks first, then at spaces.
export function toRows(line, len = LIMITS.item) {
  const text = (line || '').trim()
  if (text.length <= len) return text ? [text] : []
  const rows = []
  let cur = ''
  const push = () => { if (cur) rows.push(cur); cur = '' }
  for (const sentence of text.split(/(?<=[.!?;])\s+(?=[A-Z"'(])/)) {
    const joined = cur ? `${cur} ${sentence}` : sentence
    if (joined.length <= len) { cur = joined; continue }
    push()
    if (sentence.length <= len) { cur = sentence; continue }
    for (const word of sentence.split(/\s+/)) {
      const w = cur ? `${cur} ${word}` : word
      if (w.length <= len) cur = w
      else {
        // a single run longer than a row (a long URL) is split into chunks, nothing dropped;
        // fill the current row first so "Live at" doesn't end up alone
        let rest = word
        if (word.length > len && cur) {
          const room = len - cur.length - 1
          if (room > 0) { cur = `${cur} ${word.slice(0, room)}`; rest = word.slice(room) }
        }
        push()
        for (let i = 0; i < rest.length; i += len) { cur = rest.slice(i, i + len); if (i + len < rest.length) push() }
      }
    }
  }
  push()
  return rows
}

// Prefill a draft from a parsed resume, within the export limits.
// shortened is true when anything still had to be cut or dropped, so the form can say so.
export function prefillDraft(parsed) {
  const draft = emptyDraft()
  if (!parsed) return { draft, shortened: false }
  let shortened = false
  const fit = (s, n = LIMITS.item) => { const t = (s || '').trim(); if (t.length > n) shortened = true; return t.slice(0, n) }
  const take = (list, n) => { if (list.length > n) shortened = true; return list.slice(0, n) }
  const bullets = (text) => take(lines(bulletsFromDescription(text)).flatMap((l) => toRows(l)), LIMITS.bullets).join('\n')

  draft.contact.fullName = fit(parsed.name)
  draft.contact.email = fit(parsed.email, 320)
  draft.contact.phone = fit(parsed.phone, 50)
  draft.contact.city = fit(parsed.location)
  const seen = new Set()
  draft.skills = take((parsed.skills || []).map((x) => fit(x)).filter((x) => x && !seen.has(x.toLowerCase()) && seen.add(x.toLowerCase())), LIMITS.skills).join('\n')
  draft.certifications = take((parsed.certifications || []).filter(Boolean).flatMap((x) => toRows(x)), LIMITS.certifications).join('\n')
  const jobs = take(parsed.experience || [], LIMITS.experience).map((e) => ({
    ...emptyJob(),
    title: fit(e.title),
    company: fit(e.company, 120),
    dates: fit(e.dates),
    bullets: bullets(e.description),
  }))
  draft.experience = jobs.length ? jobs : [emptyJob()]
  const schools = take(parsed.education || [], LIMITS.education).map((e) => ({
    ...emptySchool(),
    degree: fit(e.degree),
    school: fit(e.institution),
    location: fit(e.location),
    dates: fit(e.dates),
    details: bullets(e.details),
  }))
  draft.education = schools.length ? schools : [emptySchool()]
  const projects = take(parsed.projects || [], LIMITS.projects).map((p) => ({
    ...emptyProject(),
    name: fit(p.name, 120),
    bullets: bullets(p.description),
  }))
  draft.projects = projects.length ? projects : [emptyProject()]
  return { draft, shortened }
}

export function fromParsed(parsed) {
  return prefillDraft(parsed).draft
}

// POST /api/builder/export — parsed-resume shape plus templateId, summary, headline, linkedin, github,
// highlights, achievements and languages. The live preview renders this same object.
export function toPayload(draft) {
  const c = draft.contact || {}
  const filled = (row) => Object.values(row).some((v) => (typeof v === 'string' ? v.trim() : v))
  return {
    templateId: TEMPLATE_IDS.includes(draft.templateId) ? draft.templateId : 'classic',
    name: (c.fullName || '').trim(),
    email: (c.email || '').trim(),
    phone: (c.phone || '').trim(),
    location: (c.city || '').trim(),
    headline: (c.headline || '').trim(),
    linkedin: (c.linkedin || '').trim(),
    github: (c.github || '').trim(),
    summary: (draft.summary || '').trim(),
    // split only on new lines and · so "AWS (EC2, S3, RDS)" stays one skill
    skills: splitList(draft.skills).slice(0, LIMITS.skills),
    highlights: capLines(draft.highlights, LIMITS.highlights),
    experience: (draft.experience || []).map((j) => ({
      company: [j.company, j.location].map((s) => (s || '').trim()).filter(Boolean).join(', '),
      title: (j.title || '').trim(),
      dates: (j.dates || '').trim(),
      description: lines(j.bullets).join('\n'),
    })).filter(filled).slice(0, LIMITS.experience),
    education: (draft.education || []).map((e) => ({
      institution: (e.school || '').trim(),
      degree: (e.degree || '').trim(),
      dates: (e.dates || '').trim(),
      details: [e.gpa ? `CGPA ${e.gpa.trim()}` : '', ...lines(e.details)].filter(Boolean).join('\n'),
      location: (e.location || '').trim(),
    })).filter(filled).slice(0, LIMITS.education),
    projects: (draft.projects || []).map((p) => {
      const name = (p.name || '').trim()
      const dates = (p.dates || '').trim()
      const bits = [(p.stack || '').trim(), ...lines(p.bullets)].filter(Boolean)
      return {
        name: name && dates ? `${name} (${dates})` : name,
        description: bits.join('\n'),
      }
    }).filter(filled).slice(0, LIMITS.projects),
    certifications: capLines(draft.certifications, LIMITS.certifications),
    achievements: capLines(draft.achievements, LIMITS.achievements),
    languages: splitList(draft.languages, true).map((l) => l.slice(0, LIMITS.item)).slice(0, LIMITS.languages),
  }
}

// one item per line or per ·; languages also allow commas ("English, Hindi")
export function splitList(text, commas = false) {
  return (text || '').split(commas ? /[·,\r\n]/ : /[·\r\n]/).map((s) => s.trim()).filter(Boolean)
}

// Draft autosave, so a refresh or an expired session doesn't wipe the form.
// The draft is tagged with the signed-in user's id and only restored for that same user,
// so someone else logging in on the same browser never sees it.
const DRAFT_KEY = 'resqora_builder_draft'
export function saveDraft(step, draft, userId, section = 0, reached = 0) {
  if (userId == null) return
  try { localStorage.setItem(DRAFT_KEY, JSON.stringify({ userId, step, section, reached, draft, savedAt: Date.now() })) } catch { /* storage full or blocked */ }
}
export function loadDraft(userId) {
  try {
    const saved = JSON.parse(localStorage.getItem(DRAFT_KEY) || 'null')
    if (!saved || !saved.draft || !saved.draft.contact) return null
    if (userId == null || saved.userId !== userId) { clearDraft(); return null }
    const base = emptyDraft()
    const d = { ...base, ...saved.draft, contact: { ...base.contact, ...saved.draft.contact } }
    // give restored rows fresh ids so new rows never collide with them
    for (const key of ['experience', 'education', 'projects']) {
      d[key] = (Array.isArray(d[key]) && d[key].length ? d[key] : base[key]).map((row) => ({ ...row, id: uid() }))
    }
    return { step: saved.step, section: Number.isInteger(saved.section) ? saved.section : 0, reached: Number.isInteger(saved.reached) ? saved.reached : 0, draft: d }
  } catch { return null }
}
export function clearDraft() {
  try { localStorage.removeItem(DRAFT_KEY) } catch { /* ignore */ }
}
export function isEmptyDraft(draft) {
  return JSON.stringify(toPayload(draft)) === JSON.stringify(toPayload(emptyDraft()))
}

export function fileBaseName(draft) {
  const name = (draft.contact?.fullName || '').trim()
  if (!name) return 'resume'
  return name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'resume'
}
