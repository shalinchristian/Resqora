import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api.js'
import { FEATURES } from '../features.js'
import { useAuth } from '../auth.jsx'
import { Section, Button, Input, ErrorBox, Notice } from '../components/ui.jsx'

const strong = (p) => p.length >= 8 && /[a-z]/i.test(p) && /\d/.test(p)

function ChangePassword() {
  const [f, setF] = useState({ current: '', next: '', confirm: '' })
  const [error, setError] = useState('')
  const [done, setDone] = useState(false)
  const [busy, setBusy] = useState(false)
  const set = (k) => (e) => { setF({ ...f, [k]: e.target.value }); setDone(false) }

  const submit = async (e) => {
    e.preventDefault(); setError('')
    if (!strong(f.next)) return setError('New password needs at least 8 characters, with a letter and a number.')
    if (f.next !== f.confirm) return setError("The new passwords don't match.")
    setBusy(true)
    try {
      await api.changePassword(f.current, f.next)
      setF({ current: '', next: '', confirm: '' }); setDone(true)
    } catch (err) { setError(err.message) } finally { setBusy(false) }
  }

  return (
    <Section title="Change password">
      <form onSubmit={submit} className="space-y-4 max-w-sm">
        <Input label="Current password" type="password" required autoComplete="current-password" value={f.current} onChange={set('current')} />
        <Input label="New password" type="password" required autoComplete="new-password" hint="At least 8 characters, with a letter and a number" value={f.next} onChange={set('next')} />
        <Input label="Confirm new password" type="password" required autoComplete="new-password" value={f.confirm} onChange={set('confirm')} />
        <ErrorBox message={error} />
        {done && <Notice tone="good">Password changed.</Notice>}
        <Button type="submit" variant="accent" disabled={busy}>{busy ? 'Saving...' : 'Change password'}</Button>
      </form>
    </Section>
  )
}

function DeleteAccount() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [typed, setTyped] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (e) => {
    e.preventDefault(); setError(''); setBusy(true)
    try {
      await api.deleteAccount(password)
      logout(); navigate('/', { replace: true })
    } catch (err) { setError(err.message); setBusy(false) }
  }

  return (
    <Section title="Delete account">
      <p className="text-sm text-muted max-w-prose">This permanently deletes your account, all your resumes and their scores. It can't be undone.</p>
      {!open ? (
        <button onClick={() => setOpen(true)} className="mt-4 h-9 px-4 rounded-md border border-bad text-bad text-sm font-medium hover:bg-bad hover:text-white transition-colors">Delete my account</button>
      ) : (
        <form onSubmit={submit} className="mt-4 space-y-4 max-w-sm">
          <Input label="Password" type="password" required autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
          <Input label={`Type ${user.email} to confirm`} value={typed} onChange={(e) => setTyped(e.target.value)} />
          <ErrorBox message={error} />
          <div className="flex gap-2">
            <button disabled={busy || typed.trim().toLowerCase() !== user.email} className="h-9 px-4 rounded-md bg-bad text-white text-sm font-medium disabled:opacity-40">{busy ? 'Deleting...' : 'Delete forever'}</button>
            <Button type="button" variant="ghost" onClick={() => { setOpen(false); setTyped(''); setPassword('') }}>Cancel</Button>
          </div>
        </form>
      )}
    </Section>
  )
}

export default function Account() {
  const { user } = useAuth()
  return (
    <div className="max-w-2xl space-y-6">
      <div>
        <h1 className="font-serif text-3xl">Account</h1>
        <p className="text-sm text-muted mt-1">{user.email}</p>
      </div>
      {FEATURES.changePassword && <ChangePassword />}
      <DeleteAccount />
    </div>
  )
}
