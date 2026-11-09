import { createContext, useContext, useEffect, useState } from 'react'
import { api, getToken, setToken, clearToken } from './api.js'
import { clearDraft } from './builder/model.js'

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

  // used after email verification, which returns a token like /login
  const loginWithToken = async (token) => {
    setToken(token)
    setUser(await api.me())
  }

  // logging out clears the builder draft so it doesn't stay on a shared computer.
  // an expired session keeps it, so you can continue after logging back in
  const logout = () => { clearToken(); clearDraft(); setUser(null) }

  return (
    <AuthContext.Provider value={{ user, setUser, loading, login, loginWithToken, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => useContext(AuthContext)
