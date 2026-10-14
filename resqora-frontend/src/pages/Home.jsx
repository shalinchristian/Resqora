import { Link } from 'react-router-dom'
import heroLight from '../assets/home-hero-light.png'
import heroDark from '../assets/home-hero-dark.png'

const PARTS = [
  ['Completeness', 'Your name, email, phone, skills, education and projects are all there.'],
  ['Skills', 'How many technical skills you list. Soft skills like Leadership are not counted.'],
  ['Projects', 'Whether your projects say what you built, not just their names.'],
  ['Experience', 'Whether each role has dates and a line about what you did. Freshers still get 10 of 20.'],
  ['Education', 'Your college, degree, dates and details like CGPA.'],
]

const FAQ = [
  ['Is it free?', 'Yes. There is no paid plan.'],
  ['Which files work?', 'PDF and Word (DOCX) files up to 2 MB. Scanned PDFs do not work, because there is no text in them to read.'],
  ['Does AI decide my score?', 'No. The score comes from fixed rules, so the same resume always gets the same score. The AI only writes the summary and answers your questions.'],
  ['How many resumes can I keep?', 'Up to 5. Delete one to upload another.'],
  ['Who can see my resume?', 'Only you. Other accounts cannot open it. To write the summary and answer chat questions, the resume text is sent to Google\'s Gemini.'],
]

function Heading({ kicker, title }) {
  return (
    <div className="max-w-2xl">
      {kicker && <p className="text-xs uppercase tracking-wide text-muted mb-2">{kicker}</p>}
      <h2 className="font-serif text-2xl sm:text-3xl tracking-tight">{title}</h2>
    </div>
  )
}

export default function Home() {
  return (
    <div className="max-w-5xl mx-auto">
      <section className="text-center pt-2 sm:pt-8">
        <h1 className="font-serif text-4xl sm:text-5xl leading-[1.1] tracking-tight max-w-3xl mx-auto">Know what your resume is missing before a recruiter does.</h1>
        <p className="mt-5 text-muted text-base sm:text-lg max-w-2xl mx-auto">Upload your resume and Resqora scores it out of 100, quotes the exact lines that need work, and lets you ask questions about it.</p>
        <div className="mt-8 flex flex-wrap justify-center gap-3">
          <Link to="/register" className="inline-flex items-center h-10 px-5 rounded-md bg-accent text-accent-ink text-sm font-medium hover:brightness-110 transition">Create a free account</Link>
          <Link to="/login" className="inline-flex items-center h-10 px-5 rounded-md border border-line text-sm font-medium hover:bg-sunken transition-colors">Log in</Link>
        </div>
        <div className="mt-12 rounded-xl border border-line overflow-hidden shadow-sm">
          <img src={heroLight} alt="A Resqora results page showing a score of 86, section scores and a finding quoting a resume line" className="block w-full dark:hidden" width="1600" height="1000" />
          <img src={heroDark} alt="A Resqora results page showing a score of 86, section scores and a finding quoting a resume line" className="hidden w-full dark:block" width="1600" height="1000" />
        </div>
      </section>

      <section className="mt-20 sm:mt-28">
        <Heading kicker="What it checks" title="Five parts, 20 points each." />
        <div className="mt-8 grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {PARTS.map(([t, d]) => (
            <div key={t} className="bg-surface border border-line rounded-xl p-5">
              <h3 className="font-medium">{t}</h3>
              <p className="mt-1.5 text-sm text-muted">{d}</p>
            </div>
          ))}
          <div className="rounded-xl border border-dashed border-line p-5">
            <h3 className="font-medium">Added up</h3>
            <p className="mt-1.5 text-sm text-muted">The five parts make your score out of 100. Each result page explains how every part was scored.</p>
          </div>
        </div>
      </section>

      <section className="mt-20 sm:mt-28 grid lg:grid-cols-2 gap-8 lg:gap-14 items-center">
        <div>
          <Heading kicker="A real example" title="It points at the line, not just the section." />
          <p className="mt-4 text-muted">Resqora quotes the line from your resume and says what to add to it.</p>
        </div>
        <div className="bg-surface border border-line rounded-xl p-5 text-sm">
          <p className="text-xs uppercase tracking-wide text-muted">Finding</p>
          <p className="mt-2"><span className="font-medium">Experience.</span> <span className="text-muted">Add a number to a bullet under "TCS | Associate Engineer | Jul 2019 - Dec 2020" (users, %, time saved, money).</span></p>
          <p className="mt-2 text-xs font-mono text-muted bg-sunken border-l-2 border-ok rounded-r px-2 py-1">TCS | Associate Engineer | Jul 2019 - Dec 2020</p>
        </div>
      </section>

      <section className="mt-20 sm:mt-28">
        <Heading title="Ask questions about your resume." />
        <div className="mt-8 grid sm:grid-cols-3 gap-4">
          <div className="bg-surface border border-line rounded-xl p-5"><h3 className="font-medium">Summary</h3><p className="mt-1.5 text-sm text-muted">A short note at the top of each resume on what's strong and what to fix first.</p></div>
          <div className="bg-surface border border-line rounded-xl p-5"><h3 className="font-medium">Chat</h3><p className="mt-1.5 text-sm text-muted">Ask for interview questions, a rewrite of a weak line, or whether to apply for a job you paste in.</p></div>
          <div className="bg-surface border border-line rounded-xl p-5"><h3 className="font-medium">Preview</h3><p className="mt-1.5 text-sm text-muted">See your original file next to its score while you read the notes.</p></div>
        </div>
        <p className="mt-4 text-sm text-muted">You get 10 AI messages a day. The score itself never uses AI.</p>
      </section>

      <section className="mt-20 sm:mt-28 grid lg:grid-cols-[1fr_1.4fr] gap-8 lg:gap-14">
        <Heading title="Where your resume goes" />
        <ul className="space-y-3 text-sm text-muted">
          <li>Your file is stored so you can preview it. Only your account can open it.</li>
          <li>The AI summary is saved with your resume so it loads instantly next time.</li>
          <li>Deleting a resume removes the file, its score and its summary. Deleting your account removes everything.</li>
          <li>For the summary and chat, your resume text and any job description you paste are sent to Google's Gemini. The score is worked out on our server and isn't sent anywhere.</li>
        </ul>
      </section>

      <section className="mt-20 sm:mt-28">
        <Heading title="Common questions" />
        <div className="mt-6 divide-y divide-line border-y border-line">
          {FAQ.map(([q, a]) => (
            <details key={q} className="group py-4">
              <summary className="cursor-pointer list-none flex items-center justify-between font-medium">
                {q}
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="text-muted shrink-0 ml-4 transition-transform duration-200 group-open:rotate-180"><path d="m6 9 6 6 6-6" /></svg>
              </summary>
              <p className="mt-2 text-sm text-muted max-w-2xl">{a}</p>
            </details>
          ))}
        </div>
      </section>

      <section className="mt-20 sm:mt-28 mb-4 text-center bg-surface border border-line rounded-2xl px-6 py-12">
        <h2 className="font-serif text-2xl sm:text-3xl tracking-tight">Upload a resume and see your score.</h2>
        <Link to="/register" className="mt-6 inline-flex items-center h-10 px-5 rounded-md bg-accent text-accent-ink text-sm font-medium hover:brightness-110 transition">Create a free account</Link>
      </section>
    </div>
  )
}
