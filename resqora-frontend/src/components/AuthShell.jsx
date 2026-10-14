export default function AuthShell({ title, subtitle, children, footer }) {
  const track = (e) => {
    const r = e.currentTarget.getBoundingClientRect()
    e.currentTarget.style.setProperty('--mx', `${e.clientX - r.left}px`)
    e.currentTarget.style.setProperty('--my', `${e.clientY - r.top}px`)
  }
  return (
    <div className="relative grid lg:grid-cols-2 gap-10 lg:gap-16 items-center max-w-4xl mx-auto lg:min-h-[60vh]">
      <div className="hidden lg:block relative">
        <div className="grid-bg absolute -inset-16 -z-10 opacity-60" />
        <p className="font-serif italic text-4xl leading-tight">
          Upload your resume.<br />See what's working<br />and what isn't.
        </p>
        <ul className="mt-8 space-y-3 text-sm text-muted">
          <li className="fade-up flex gap-3" style={{ animationDelay: '150ms' }}><span className="text-accent">01</span>Scores for skills, projects, experience and education</li>
          <li className="fade-up flex gap-3" style={{ animationDelay: '250ms' }}><span className="text-accent">02</span>Match it against a job description</li>
          <li className="fade-up flex gap-3" style={{ animationDelay: '350ms' }}><span className="text-accent">03</span>Interview questions and rewrite suggestions</li>
        </ul>
      </div>
      <div onMouseMove={track} className="spotlight fade-up w-full max-w-sm mx-auto lg:mx-0 bg-surface border border-line rounded-2xl p-6 sm:p-8 shadow-sm">
        <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
        {subtitle && <p className="text-sm text-muted mt-1">{subtitle}</p>}
        <div className="mt-8">{children}</div>
        {footer && <p className="text-sm text-muted mt-6">{footer}</p>}
      </div>
    </div>
  )
}
