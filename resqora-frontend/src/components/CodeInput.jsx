import { useEffect, useState } from 'react'

// One 6-digit field. Works with paste and with the phone's "code from email" suggestion.
export function CodeInput({ value, onChange, autoFocus = true }) {
  return (
    <label className="block">
      <span className="text-[13px] font-medium text-muted">6-digit code</span>
      <input
        value={value}
        onChange={(e) => onChange(e.target.value.replace(/\D/g, '').slice(0, 6))}
        inputMode="numeric"
        autoComplete="one-time-code"
        pattern="\d{6}"
        maxLength={6}
        required
        autoFocus={autoFocus}
        placeholder="------"
        aria-label="6-digit code"
        className="mt-1.5 w-full h-12 rounded-lg border border-line bg-surface px-3 text-center text-2xl font-mono tracking-[0.5em] outline-none transition focus:border-accent focus:ring-4 focus:ring-[var(--glow)] placeholder:text-muted/40"
      />
    </label>
  )
}

// Counts down from `seconds` after each send; returns [secondsLeft, restart].
export function useCooldown(seconds = 60, startNow = false) {
  const [left, setLeft] = useState(startNow ? seconds : 0)
  useEffect(() => {
    if (left <= 0) return
    const t = setTimeout(() => setLeft((n) => n - 1), 1000)
    return () => clearTimeout(t)
  }, [left])
  return [left, () => setLeft(seconds)]
}

export function ResendButton({ left, busy, onClick }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={left > 0 || busy}
      className="text-sm text-accent hover:underline disabled:text-muted disabled:no-underline disabled:cursor-not-allowed"
    >
      {left > 0 ? `Resend code in ${left}s` : busy ? 'Sending...' : 'Resend code'}
    </button>
  )
}
