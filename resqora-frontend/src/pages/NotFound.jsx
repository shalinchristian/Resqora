import { Link } from 'react-router-dom'

export default function NotFound() {
  return (
    <div className="max-w-md mx-auto text-center py-16">
      <p className="font-serif text-7xl text-faint">404</p>
      <h1 className="mt-4 text-xl font-medium">This page doesn't exist</h1>
      <p className="mt-2 text-sm text-muted">The link may be old, or the resume may have been deleted.</p>
      <Link to="/" className="mt-8 inline-flex items-center h-10 px-5 rounded-md bg-accent text-accent-ink text-sm font-medium hover:opacity-90 transition-opacity">Go home</Link>
    </div>
  )
}
