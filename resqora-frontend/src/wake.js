// Render's free plan puts the backend to sleep after 15 minutes without traffic.
// Pinging it as soon as the site opens starts it up while the visitor is still reading or typing.
const API_BASE = (import.meta.env.VITE_API_URL || '').replace(/\/+$/, '').replace(/\/api$/, '')

let started = false

export function wakeServer() {
  if (started) return
  started = true
  const deadline = Date.now() + 5 * 60 * 1000
  const ping = async () => {
    try {
      const res = await fetch(`${API_BASE}/api/health`, { cache: 'no-store' })
      // any answer from the app itself means it's up; 502/503 come from Render while it boots
      if (res.status < 500) return
    } catch { /* still starting */ }
    if (Date.now() < deadline) setTimeout(ping, 5000)
  }
  ping()
}
