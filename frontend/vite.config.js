import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// En desarrollo (sin API Gateway) se redirige cada ruta al microservicio local.
// Con VITE_API_BASE_URL definido (API Gateway) el proxy no se usa.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api/usuarios': 'http://localhost:8081',
      '/api/productos': 'http://localhost:8082',
      '/api/pedidos': 'http://localhost:8083',
    },
  },
})
