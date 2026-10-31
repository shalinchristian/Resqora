import { useEffect, useRef, useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'
import { Button, Input, ErrorBox, Notice } from '../components/ui.jsx'
import AuthShell from '../components/AuthShell.jsx'
import { CodeInput, ResendButton, useCooldown } from '../components/CodeInput.jsx'

export default function VerifyEmail() {
  const { user, loginWithToken } = useAuth()
  const navigate = useNavigate()
  const { state } = useLocation()
  const [email, setEmail] = useState(state?.email || '')
  const [code, setCode] = useState('')
  const [error, setError] = useState('')
  const [info, setInfo] = useState(state?.sent ? `We sent a code to ${state.email}.` : '')
  const [busy, setBusy] = useState(false)
  const [sending, setSending] = useState(false)
  const [left, restart] = useCooldown(60, !!state?.sent)
  const autoSent = useRef(false)

  const send = async () => {
    if (!email) return setError('Enter your email first.')
    setError(''); setSending(true)
    try {
      await api.resendCode(email.trim().toLowerCase())
      setInfo(`We sent a code to ${email.trim().toLowerCase()}.`)
      restart()
    } catch (err) {
      setError(err.status === 429 ? 'Too many requests. Wait a minute and try again.' : err.message)
    } finally {
      setSending(false)
    }
  }

  // Came here from login with an unverified account: send a fresh code once.
  useEffect(() => {
    if (state?.email && !state?.sent && !autoSent.current) { autoSent.current = true; send() }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  if (user) return <Navigate to="/" replace />

  const onSubmit = async (e) => {
    e.preventDefault()
    if (code.length !== 6) return setError('Enter the 6-digit code from the email.')
    setError(''); setBusy(true)
    try {
      const res = await api.verifyEmail(email.trim().toLowerCase(), code)
      if (res?.token) { await loginWithToken(res.token); navigate('/') }
      else navigate('/login')
    } catch (err) {
      setCode('')
      setError(err.status === 429 ? 'Too many wrong tries. Request a new code.' : err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell
      title="Verify your email"
      subtitle="Enter the code we emailed you. It expires in 10 minutes."
      footer={<>Wrong account? <Link to="/login" className="text-accent hover:underline">Back to log in</Link></>}
    >
      {info && <div className="mb-5"><Notice tone="ok">{info} Check spam if you don't see it.</Notice></div>}
      <form onSubmit={onSubmit} className="space-y-5">
        {!state?.email && <Input label="Email" type="email" required autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} />}
        <CodeInput value={code} onChange={setCode} autoFocus={!!state?.email} />
        <ErrorBox message={error} />
        <Button type="submit" disabled={busy || code.length !== 6} variant="accent" className="w-full h-10">{busy ? 'Verifying...' : 'Verify email'}</Button>
        <div className="text-center"><ResendButton left={left} busy={sending} onClick={send} /></div>
      </form>
    </AuthShell>
  )
}
