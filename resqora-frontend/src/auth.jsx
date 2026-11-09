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

  // explicit sign-out wipes the builder draft so it doesn't sit on a shared computer;
  // an expired session (resqora:logout) keeps it so the user can pick up after signing back in
  const logout = () => { clearToken(); clearDraft(); setUser(null) }

  return (
    <AuthContext.Provider value={{ user, setUser, loading, login, loginWithToken, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => useContext(AuthContext)
