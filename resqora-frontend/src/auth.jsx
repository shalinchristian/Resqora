import { createContext, useContext, useEffect, useState } from 'react'
import { api, getToken, setToken, clearToken } from './api.js'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)

  // restore session from saved token
  useEffect(() => {
    if (!getToken()) { setLoading(false); return }
    api.me().then(setUser).catch(() => clearToken()).finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    const onLogout = () => { sessionStorage.setItem('resqora_expired', '1'); setUser(null) }
    window.addEventListener('resqora:logout', onLogout)
    return () => window.removeEventListener('resqora:logout', onLogout)
  }, [])

  const login = async (email, password) => {
    const { token } = await api.login(email.trim().toLowerCase(), password)
    setToken(token)
    setUser(await api.me())
  }

  const logout = () => { clearToken(); setUser(null) }

  return (
    <AuthContext.Provider value={{ user, setUser, loading, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => useContext(AuthContext)
