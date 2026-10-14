import { useState } from 'react'

export function Section({ title, note, children, className = '', style }) {
  return (
    <section className={`fade-up bg-surface border border-line rounded-xl shadow-[0_1px_2px_rgba(0,0,0,0.04)] transition-colors hover:border-faint ${className}`} style={style}>
      {title && (
        <header className="px-5 sm:px-6 pt-5 pb-3 flex items-baseline justify-between gap-3">
          <h2 className="text-[15px] font-semibold tracking-tight shrink-0 whitespace-nowrap">{title}</h2>
          {note && <span className="text-xs text-muted truncate min-w-0">{note}</span>}
        </header>
      )}
      <div className={title ? 'px-5 sm:px-6 pb-5 sm:pb-6' : 'p-5 sm:p-6'}>{children}</div>
    </section>
  )
}

export function Button({ children, variant = 'primary', className = '', ...props }) {
  const styles = {
    primary: 'bg-ink text-paper hover:opacity-90 shadow-sm',
    accent: 'bg-accent text-accent-ink hover:brightness-110',
    ghost: 'bg-surface text-ink border border-line hover:bg-sunken',
    danger: 'bg-transparent text-bad border border-line hover:border-bad',
  }
  return (
    <button
      className={`inline-flex items-center justify-center gap-2 h-9 px-3.5 rounded-lg text-sm font-medium transition active:scale-[0.97] disabled:opacity-40 disabled:cursor-not-allowed ${styles[variant]} ${className}`}
      {...props}
    >
      {children}
    </button>
  )
}

export function Input({ label, type, hint, ...props }) {
  const [show, setShow] = useState(false)
  const isPassword = type === 'password'
  return (
    <label className="block">
      <span className="text-[13px] font-medium text-muted">{label}</span>
      <div className="relative mt-1.5">
        <input
          type={isPassword && show ? 'text' : type}
          className={`w-full h-10 rounded-lg border border-line bg-surface px-3 outline-none transition focus:border-accent focus:ring-4 focus:ring-[var(--glow)] ${isPassword ? 'pr-16' : ''}`}
          {...props}
        />
        {isPassword && (
          <button
            type="button"
            onClick={() => setShow(!show)}
            className="absolute inset-y-0 right-0 px-3 text-xs text-muted hover:text-ink"
          >
            {show ? 'Hide' : 'Show'}
          </button>
        )}
      </div>
      {hint && <span className="block mt-1 text-xs text-muted">{hint}</span>}
    </label>
  )
}

export function TextArea(props) {
  return (
    <textarea
      className="w-full rounded-lg border border-line bg-surface p-3 text-sm outline-none transition focus:border-accent focus:ring-4 focus:ring-[var(--glow)] resize-y"
      {...props}
    />
  )
}

export function Toast({ children }) {
  if (!children) return null
  return (
    <div className="toast fixed bottom-6 left-1/2 z-50 rounded-lg border border-line bg-ink text-paper text-sm px-4 py-2.5 shadow-lg">
      {children}
    </div>
  )
}

export function Notice({ tone = 'bad', children }) {
  if (!children) return null
  const tones = {
    bad: 'bg-bad-soft text-bad',
    ok: 'bg-ok-soft text-ok',
    good: 'bg-good-soft text-good',
  }
  return <p className={`rounded-lg border border-line px-3 py-2 text-sm ${tones[tone]}`}>{children}</p>
}

export const ErrorBox = ({ message }) => <Notice>{message}</Notice>

export function Chip({ children, tone }) {
  const tones = {
    good: 'bg-good-soft text-good',
    bad: 'bg-bad-soft text-bad',
  }
  return (
    <span className={`inline-block text-xs px-2 py-0.5 rounded-md border border-line mr-1.5 mb-1.5 ${tones[tone] || 'bg-sunken text-ink'}`}>
      {children}
    </span>
  )
}

export const toneFor = (pct) => (pct >= 75 ? 'good' : pct >= 45 ? 'ok' : 'bad')
