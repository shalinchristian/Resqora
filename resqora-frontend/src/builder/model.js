// Resume builder draft and the JSON sent to POST /api/builder/export.
// Keep this shape stable — Ben's exporter reads the same fields.

import classicThumb from '../assets/templates/classic.png'
import combinationThumb from '../assets/templates/combination.png'
import campusThumb from '../assets/templates/campus.png'
import centeredThumb from '../assets/templates/centered.png'
import headerBandThumb from '../assets/templates/header-band.png'

export const TEMPLATES = [
  { id: 'classic', name: 'Classic', blurb: 'Clean single column. Good default.', thumb: classicThumb },
  { id: 'combination', name: 'Combination', blurb: 'Skills and highlights first.', thumb: combinationThumb },
  { id: 'campus', name: 'Campus', blurb: 'Projects first, for students.', thumb: campusThumb },
  { id: 'centered', name: 'Centered', blurb: 'Formal, with a centered name.', thumb: centeredThumb },
  { id: 'header-band', name: 'Header-band', blurb: 'Name sits in a color bar.', thumb: headerBandThumb },
]

export const TEMPLATE_IDS = TEMPLATES.map((t) => t.id)

let nextId = 1
const uid = () => `item-${nextId++}`

export const lines = (text) => (text || '').split(/\r?\n/).map((s) => s.replace(/^[•\-\u2013\u2014]\s*/, '').trim()).filter(Boolean)

const emptyJob = () => ({ id: uid(), title: '', company: '', location: '', dates: '', bullets: '' })
const emptySchool = () => ({ id: uid(), degree: '', school: '', dates: '', gpa: '', details: '' })
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

export function fromParsed(parsed) {
  const draft = emptyDraft()
  if (!parsed) return draft
  draft.contact.fullName = parsed.name || ''
  draft.contact.email = parsed.email || ''
  draft.contact.phone = parsed.phone || ''
  draft.contact.city = parsed.location || ''
  draft.skills = (parsed.skills || []).filter(Boolean).join(' · ')
  draft.certifications = (parsed.certifications || []).filter(Boolean).join('\n')
  const jobs = (parsed.experience || []).map((e) => ({
    ...emptyJob(),
    title: e.title || '',
    company: e.company || '',
    dates: e.dates || '',
    bullets: bulletsFromDescription(e.description),
  }))
  draft.experience = jobs.length ? jobs : [emptyJob()]
  const schools = (parsed.education || []).map((e) => ({
    ...emptySchool(),
    degree: e.degree || '',
    school: e.institution || '',
    dates: e.dates || '',
    details: e.details || '',
  }))
  draft.education = schools.length ? schools : [emptySchool()]
  const projects = (parsed.projects || []).map((p) => ({
    ...emptyProject(),
    name: p.name || '',
    bullets: bulletsFromDescription(p.description),
  }))
  draft.projects = projects.length ? projects : [emptyProject()]
  return draft
}

// POST /api/builder/export — parsed-resume shape plus templateId, summary, headline, linkedin, github.
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
    skills: (draft.skills || '').split(/[·,\n]/).map((s) => s.trim()).filter(Boolean),
    experience: (draft.experience || []).map((j) => ({
      company: [j.company, j.location].map((s) => (s || '').trim()).filter(Boolean).join(', '),
      title: (j.title || '').trim(),
      dates: (j.dates || '').trim(),
      description: lines(j.bullets).join('\n'),
    })).filter(filled),
    education: (draft.education || []).map((e) => ({
      institution: (e.school || '').trim(),
      degree: (e.degree || '').trim(),
      dates: (e.dates || '').trim(),
      details: [e.gpa ? `CGPA ${e.gpa.trim()}` : '', ...lines(e.details)].filter(Boolean).join('\n'),
      location: '',
    })).filter(filled),
    projects: (draft.projects || []).map((p) => {
      const name = (p.name || '').trim()
      const dates = (p.dates || '').trim()
      const bits = [(p.stack || '').trim(), ...lines(p.bullets)].filter(Boolean)
      return {
        name: name && dates ? `${name} (${dates})` : name,
        description: bits.join('\n'),
      }
    }).filter(filled),
    certifications: lines(draft.certifications),
  }
}

export function fileBaseName(draft) {
  const name = (draft.contact?.fullName || '').trim()
  if (!name) return 'resume'
  return name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'resume'
}
