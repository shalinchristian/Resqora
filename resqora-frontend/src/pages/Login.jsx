import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth.jsx'
import { Button, Input, ErrorBox, Notice } from '../components/ui.jsx'
import AuthShell from '../components/AuthShell.jsx'

export default function Login() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const { state } = useLocation()
  const reset = !!state?.reset
  const [expired] = useState(() => {
    const e = sessionStorage.getItem('resqora_expired')
    sessionStorage.removeItem('resqora_expired')
    return !!e
  })

  if (user) return <Navigate to="/" replace />

  const onSubmit = async (e) => {
    e.preventDefault()
    setError(''); setBusy(true)
    try {
      await login(email, password)
      navigate('/')
    } catch (err) {
      if (err.status === 403 || err.code === 'EMAIL_NOT_VERIFIED') {
        navigate('/verify-email', { state: { email: email.trim().toLowerCase() } })
        return
      }
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell
      title="Log in"
      subtitle="Welcome back."
      footer={<>No account? <Link to="/register" className="text-accent hover:underline">Create one</Link></>}
    >
      {expired && <div className="mb-5"><Notice tone="ok">Your session expired. Please log in again.</Notice></div>}
      {reset && <div className="mb-5"><Notice tone="good">Password updated. Log in with your new password.</Notice></div>}
      <form onSubmit={onSubmit} className="space-y-5">
        <Input label="Email" type="email" required autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} />
        <div>
          <Input label="Password" type="password" required autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
          <Link to="/forgot-password" state={{ email: email.trim().toLowerCase() }} className="inline-block mt-1.5 text-xs text-muted hover:text-accent">Forgot password?</Link>
        </div>
        <ErrorBox message={error} />
        <Button type="submit" disabled={busy} variant="accent" className="w-full h-10">{busy ? 'Logging in...' : 'Log in'}</Button>
      </form>
    </AuthShell>
  )
}
