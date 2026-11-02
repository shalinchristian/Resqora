import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'
import { Button, Input, ErrorBox } from '../components/ui.jsx'
import AuthShell from '../components/AuthShell.jsx'

export default function Register() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const mismatch = confirm && password !== confirm

  const onSubmit = async (e) => {
    e.preventDefault()
    if (password.length < 8) return setError('Password must be at least 8 characters.')
    if (!/[A-Za-z]/.test(password) || !/\d/.test(password)) return setError('Password needs at least one letter and one number.')
    if (password !== confirm) return setError('Passwords do not match.')
    setError(''); setBusy(true)
    try {
      const clean = email.trim().toLowerCase()
      const res = await api.register(name.trim(), clean, password)
      if (res?.token) {
        await login(clean, password)
        navigate('/')
      } else {
        navigate('/verify-email', { state: { email: clean, sent: true } })
      }
    } catch (err) {
      setError(err.status === 409 ? 'That email is already registered.' : err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell
      title="Create account"
      subtitle="It takes less than a minute."
      footer={<>Already registered? <Link to="/login" className="text-accent hover:underline">Log in</Link></>}
    >
      <form onSubmit={onSubmit} className="space-y-5">
        <Input label="Full name" required maxLength={50} autoComplete="name" value={name} onChange={(e) => setName(e.target.value)} />
        <Input label="Email" type="email" required autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} />
        <Input label="Password" type="password" required autoComplete="new-password" hint="At least 8 characters, with a letter and a number" value={password} onChange={(e) => setPassword(e.target.value)} />
        <div>
          <Input label="Confirm password" type="password" required autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} />
          {mismatch && <span className="block mt-1 text-xs text-bad">Passwords do not match</span>}
        </div>
        <ErrorBox message={error} />
        <Button type="submit" disabled={busy} variant="accent" className="w-full h-10">{busy ? 'Creating...' : 'Create account'}</Button>
        <p className="text-xs text-muted text-center">Waking the server up might take a few seconds. Please wait.</p>
      </form>
    </AuthShell>
  )
}
