import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Desarrollo local sin API Gateway: cada ruta se redirige a su microservicio.
// Entradas atiende ademas /api/eventos/{id}/inventario y /api/eventos/{id}/entradas
// (el mismo prefijo /api/eventos, pero es Entradas quien la resuelve), por eso
// esas reglas van antes que la general de /api/eventos.
// Con VITE_API_BASE_URL definido (API Gateway) este proxy no se usa.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '^/api/eventos/[^/]+/(inventario|entradas)': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
      '^/api/entradas': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
      '^/api/eventos': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
})
