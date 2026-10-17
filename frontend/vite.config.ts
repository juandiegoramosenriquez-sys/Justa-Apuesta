import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // En desarrollo, /api va al backend de Spring Boot (API_PROXY para usar otro puerto)
    proxy: {
      '/api': process.env.API_PROXY ?? 'http://localhost:8080',
    },
  },
})
