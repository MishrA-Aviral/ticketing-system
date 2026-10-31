import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const target = env.VITE_API_BASE_URL || 'https://ticketing-system-6dsy.onrender.com'

  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        '/events': {
          target,
          changeOrigin: true,
          secure: true,
        },
        '/bookings': {
          target,
          changeOrigin: true,
          secure: true,
        },
      },
    },
  }
})
