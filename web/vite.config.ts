import { fileURLToPath } from 'node:url'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    rolldownOptions: {
      input: { app: fileURLToPath(new URL("./index.html", import.meta.url)), home: fileURLToPath(new URL("./home.html", import.meta.url)), en: fileURLToPath(new URL("./en/index.html", import.meta.url)) },
      output: {
        manualChunks(id) {
          if (id.includes('recharts')) return 'charts'
          if (id.includes('@tanstack/react-query')) return 'query'
          if (id.includes('lucide-react')) return 'icons'
          if (id.includes('react-dom') || id.includes('react-router')) return 'react'
        },
      },
    },
  },
})
