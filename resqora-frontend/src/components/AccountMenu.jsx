import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'

export default function AccountMenu() {
  const { user, setUser, logout } = useAuth()
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState(user.name || '')
  const [error, setError] = useState('')
  const ref = useRef(null)

  // Close the menu when clicking anywhere outside it.
  useEffect(() => {
    const close = (e) => { if (ref.current && !ref.current.contains(e.target)) { setOpen(false); setEditing(false) } }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [])

  const save = async (e) => {
    e.preventDefault()
    try {
      setUser(await api.updateMe(name.trim()))
      setEditing(false); setError('')
    } catch (err) { setError(err.message) }
  }

  const label = user.name || 'Account'
  const initial = (user.name || user.email || '?').trim()[0].toUpperCase()

  return (
    <div ref={ref} className="relative">
      <button onClick={() => setOpen(!open)} aria-expanded={open}
        className="flex items-center gap-2 rounded-md pl-1 pr-2 py-1 hover:bg-sunken transition-colors">
        <span className="w-7 h-7 grid place-items-center rounded-full bg-accent text-accent-ink text-xs font-semibold">{initial}</span>
        <span className="hidden sm:block text-sm max-w-40 truncate">{label}</span>
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="text-muted"><path d="m6 9 6 6 6-6" /></svg>
      </button>
      {open && (
        <div className="absolute right-0 mt-2 w-64 bg-surface border border-line rounded-lg shadow-lg p-1.5 z-20 fade-up">
          <div className="px-2.5 py-2">
            <p className="text-sm font-medium truncate">{user.name || 'No name set'}</p>
            <p className="text-xs text-muted truncate">{user.email}</p>
          </div>
          {editing ? (
            <form onSubmit={save} className="px-2.5 pb-2 space-y-2">
              <input autoFocus maxLength={50} value={name} onChange={(e) => setName(e.target.value)} placeholder="Your name"
                className="w-full rounded-md border border-line bg-paper px-2.5 py-1.5 text-sm" />
              {error && <p className="text-xs text-bad">{error}</p>}
              <div className="flex gap-2">
                <button className="flex-1 rounded-md bg-accent text-accent-ink text-sm py-1.5" disabled={!name.trim()}>Save</button>
                <button type="button" onClick={() => setEditing(false)} className="flex-1 rounded-md border border-line text-sm py-1.5">Cancel</button>
              </div>
            </form>
          ) : (
            <button onClick={() => setEditing(true)} className="w-full text-left text-sm rounded-md px-2.5 py-2 hover:bg-sunken">
              {user.name ? 'Change name' : 'Add your name'}
            </button>
          )}
          <Link to="/account" onClick={() => setOpen(false)} className="block text-sm rounded-md px-2.5 py-2 hover:bg-sunken">Account settings</Link>
          <div className="h-px bg-line my-1" />
          <button onClick={logout} className="w-full text-left text-sm rounded-md px-2.5 py-2 hover:bg-sunken">Log out</button>
        </div>
      )}
    </div>
  )
}
