import { useState } from 'react'
import { Routes, Route, Navigate, Link, useLocation } from 'react-router-dom'
import AccountMenu from './components/AccountMenu.jsx'
import { useAuth } from './auth.jsx'
import mark from './assets/resqora-mark.svg'
import Login from './pages/Login.jsx'
import Register from './pages/Register.jsx'
import Dashboard from './pages/Dashboard.jsx'
import ResumeDetail from './pages/ResumeDetail.jsx'
import Home from './pages/Home.jsx'
import Account from './pages/Account.jsx'
import NotFound from './pages/NotFound.jsx'

function HomeOrDashboard() {
  const { user, loading } = useAuth()
  if (loading) return <p className="text-muted">Loading...</p>
  return user ? <Dashboard /> : <Home />
}

function Protected({ children }) {
  const { user, loading } = useAuth()
  if (loading) return <p className="text-muted">Loading...</p>
  return user ? children : <Navigate to="/login" replace />
}

function ThemeToggle() {
  const [dark, setDark] = useState(() => document.documentElement.classList.contains('dark'))
  const toggle = () => {
    const next = !dark
    document.documentElement.classList.toggle('dark', next)
    localStorage.setItem('resqora_theme', next ? 'dark' : 'light')
    setDark(next)
  }
  return (
    <button
      onClick={toggle}
      aria-label="Toggle dark mode"
      title={dark ? 'Light mode' : 'Dark mode'}
      className="w-9 h-9 grid place-items-center rounded-md text-muted hover:text-ink hover:bg-sunken transition active:scale-90 [&>svg]:transition-transform [&>svg]:duration-200 hover:[&>svg]:rotate-45"
    >
      {dark ? (
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"><circle cx="12" cy="12" r="4" /><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" /></svg>
      ) : (
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" /></svg>
      )}
    </button>
  )
}

export function Logo() {
  return (
    <span className="group flex items-center gap-2.5">
      <img src={mark} alt="" className="w-7 h-7 rounded-md ring-1 ring-line" />
      <span className="font-semibold tracking-tight">Resqora</span>
    </span>
  )
}

export default function App() {
  const { user } = useAuth()
  const { pathname } = useLocation()

  return (
    <div className="min-h-screen flex flex-col">
      <header className="border-b border-line bg-paper sticky top-0 z-10">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 h-14 flex items-center justify-between gap-3">
          <div className="flex items-center gap-6">
            <Link to="/" className="shrink-0"><Logo /></Link>
          </div>
          <div className="flex items-center gap-1 sm:gap-2 min-w-0">
            <ThemeToggle />
            {user ? <AccountMenu /> : !['/login', '/register'].includes(pathname) && (
              <Link to="/login" className="text-sm px-3 h-9 inline-flex items-center rounded-md hover:bg-sunken transition-colors">Log in</Link>
            )}
          </div>
        </div>
      </header>

      <main key={pathname} className="fade-up flex-1 w-full max-w-7xl mx-auto px-4 sm:px-6 py-8 sm:py-12">
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/" element={<HomeOrDashboard />} />
          <Route path="/account" element={<Protected><Account /></Protected>} />
          <Route path="/resumes/:id" element={<Protected><ResumeDetail /></Protected>} />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </main>

      <footer className="border-t border-line">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 py-5 text-xs text-faint">© 2026 Resqora</div>
      </footer>
    </div>
  )
}
