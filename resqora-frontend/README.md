# Resqora Frontend

Vite + React + Tailwind CSS.

## Run it
Needs Node 20+. Start the backend on port 8080 first, then:
   ```
   npm install
   npm run dev
   ```
Open http://localhost:5173

## Structure
- `src/api.js` - backend calls, JWT header, error handling
- `src/auth.jsx` - login state (token in localStorage)
- `src/App.jsx` - routes
- `src/pages/` - Login, Register, Dashboard, ResumeDetail
- `src/components/ui.jsx` - shared Card/Button/Input

Dev server proxies `/api` to `localhost:8080` (see `vite.config.js`).
