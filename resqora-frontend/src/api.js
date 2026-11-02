// Where the backend lives. Empty in development (Vite forwards /api to :8080);
// set VITE_API_URL to the backend's address, e.g. https://your-backend.onrender.com, when building for hosting.
const API_BASE = (import.meta.env.VITE_API_URL || '').replace(/\/+$/, '').replace(/\/api$/, '')

const TOKEN_KEY = 'resqora_token'

export const getToken = () => localStorage.getItem(TOKEN_KEY)
export const setToken = (t) => localStorage.setItem(TOKEN_KEY, t)
export const clearToken = () => localStorage.removeItem(TOKEN_KEY)

export class ApiError extends Error {
  constructor(status, message, data = null) {
    super(message)
    this.status = status
    this.code = data?.code || data?.error || null
  }
}

async function request(path, { method = 'GET', body, isForm = false } = {}) {
  const headers = {}
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  if (body && !isForm) headers['Content-Type'] = 'application/json'

  let res
  try {
    res = await fetch(API_BASE + path, { method, headers, body: body ? (isForm ? body : JSON.stringify(body)) : undefined })
  } catch {
    throw new ApiError(0, "Can't reach the server. Check your connection and try again.")
  }

  if (res.status === 204) return null
  const text = await res.text()
  let data = null
  try { data = text ? JSON.parse(text) : null } catch { data = null }
  if (!res.ok && !data && res.status >= 500) throw new ApiError(res.status, 'Something went wrong on the server. Please try again.')

  if (!res.ok) {
    const message = data?.message || `Request failed (${res.status})`
    if (res.status === 401 && token) {
      clearToken()
      window.dispatchEvent(new Event('resqora:logout'))
    }
    throw new ApiError(res.status, message, data)
  }
  return data
}

export const api = {
  register: (name, email, password) => request('/api/auth/register', { method: 'POST', body: { name, email, password } }),
  login: (email, password) => request('/api/auth/login', { method: 'POST', body: { email, password } }),
  verifyEmail: (email, code) => request('/api/auth/verify-email', { method: 'POST', body: { email, code } }),
  resendCode: (email) => request('/api/auth/resend-code', { method: 'POST', body: { email } }),
  forgotPassword: (email) => request('/api/auth/forgot-password', { method: 'POST', body: { email } }),
  resetPassword: (email, code, newPassword) => request('/api/auth/reset-password', { method: 'POST', body: { email, code, newPassword } }),
  me: () => request('/api/auth/me'),
  updateMe: (name) => request('/api/auth/me', { method: 'PATCH', body: { name } }),
  setAiEnabled: (aiEnabled) => request('/api/auth/me', { method: 'PATCH', body: { aiEnabled } }),
  changePassword: (currentPassword, newPassword) => request('/api/auth/password', { method: 'PUT', body: { currentPassword, newPassword } }),
  deleteAccount: (password) => request('/api/auth/me', { method: 'DELETE', body: { password } }),

  listResumes: () => request('/api/resumes'),
  aiUsage: () => request('/api/ai/usage'),
  jobs: (id) => request(`/api/resumes/${id}/jobs`),
  uploadResume: (file) => {
    const form = new FormData()
    form.append('file', file)
    return request('/api/resumes/upload', { method: 'POST', body: form, isForm: true })
  },
  deleteResume: (id) => request(`/api/resumes/${id}`, { method: 'DELETE' }),
  parsed: (id) => request(`/api/resumes/${id}/parsed`),
  analysis: (id) => request(`/api/resumes/${id}/analysis`),
  match: (id, jobDescription) => request(`/api/resumes/${id}/match`, { method: 'POST', body: { jobDescription } }),
  summary: (id) => request(`/api/resumes/${id}/summary`),
  // asks across all of the user's resumes
  dashboardChat: (message, history) => request('/api/chat', { method: 'POST', body: { message, history } }),
  chat: (id, message, history, jobDescription) =>
    request(`/api/resumes/${id}/chat`, { method: 'POST', body: { message, history, jobDescription } }),
  // The original file comes back as raw bytes, not JSON, so it skips request().
  file: async (id) => {
    const res = await fetch(`${API_BASE}/api/resumes/${id}/file`, { headers: { Authorization: `Bearer ${getToken()}` } })
    if (!res.ok) throw new ApiError(res.status, 'File not available')
    return res.blob()
  },
}
