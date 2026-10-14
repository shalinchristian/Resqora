import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// The proxy forwards /api calls from the dev server (5173) to Spring Boot (8080),
// so the browser sees one origin and CORS never gets in the way during development.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' },
  },
})
