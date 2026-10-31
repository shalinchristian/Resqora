import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'
import { Button, Input, ErrorBox, Notice } from '../components/ui.jsx'
import AuthShell from '../components/AuthShell.jsx'
import { CodeInput, ResendButton, useCooldown } from '../components/CodeInput.jsx'

export default function ForgotPassword() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const { state } = useLocation()
  const [step, setStep] = useState('email')
  const [email, setEmail] = useState(state?.email || '')
  const [code, setCode] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [left, restart] = useCooldown(60)

  if (user) return <Navigate to="/" replace />

  const clean = email.trim().toLowerCase()

  const requestCode = async (e) => {
    e?.preventDefault()
    setError(''); setBusy(true)
    try {
      await api.forgotPassword(clean)
      setStep('reset'); restart()
    } catch (err) {
      setError(err.status === 429 ? 'Too many requests. Wait a minute and try again.' : err.message)
    } finally {
      setBusy(false)
    }
  }

  const onReset = async (e) => {
    e.preventDefault()
    if (code.length !== 6) return setError('Enter the 6-digit code from the email.')
    if (password.length < 8) return setError('Password must be at least 8 characters.')
    if (!/[A-Za-z]/.test(password) || !/\d/.test(password)) return setError('Password needs at least one letter and one number.')
    if (password !== confirm) return setError('Passwords do not match.')
    setError(''); setBusy(true)
    try {
      await api.resetPassword(clean, code, password)
      navigate('/login', { state: { reset: true } })
    } catch (err) {
      setCode('')
      setError(err.status === 429 ? 'Too many wrong tries. Request a new code.' : err.message)
    } finally {
      setBusy(false)
    }
  }

  const footer = <>Remembered it? <Link to="/login" className="text-accent hover:underline">Back to log in</Link></>

  if (step === 'email') {
    return (
      <AuthShell title="Forgot password" subtitle="We'll email you a code to reset it." footer={footer}>
        <form onSubmit={requestCode} className="space-y-5">
          <Input label="Email" type="email" required autoFocus autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} />
          <ErrorBox message={error} />
          <Button type="submit" disabled={busy} variant="accent" className="w-full h-10">{busy ? 'Sending...' : 'Send code'}</Button>
        </form>
      </AuthShell>
    )
  }

  const mismatch = confirm && password !== confirm
  return (
    <AuthShell title="Reset password" subtitle="Enter the code and choose a new password." footer={footer}>
      <div className="mb-5"><Notice tone="ok">If {clean} has an account, a code is on its way. It expires in 10 minutes.</Notice></div>
      <form onSubmit={onReset} className="space-y-5">
        <CodeInput value={code} onChange={setCode} />
        <Input label="New password" type="password" required autoComplete="new-password" hint="At least 8 characters, with a letter and a number" value={password} onChange={(e) => setPassword(e.target.value)} />
        <div>
          <Input label="Confirm new password" type="password" required autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} />
          {mismatch && <span className="block mt-1 text-xs text-bad">Passwords do not match</span>}
        </div>
        <ErrorBox message={error} />
        <Button type="submit" disabled={busy} variant="accent" className="w-full h-10">{busy ? 'Saving...' : 'Reset password'}</Button>
        <div className="flex items-center justify-between">
          <button type="button" onClick={() => { setStep('email'); setCode(''); setError('') }} className="text-sm text-muted hover:text-ink">Change email</button>
          <ResendButton left={left} busy={busy} onClick={() => requestCode()} />
        </div>
      </form>
    </AuthShell>
  )
}
