import { Navigate } from 'react-router-dom'
import { useAuth } from './AuthContext'

/** Ruta protegida: exige sesion y, opcionalmente, un rol (grupo de Cognito). */
export default function ProtectedRoute({ children, role }) {
  const { status, claims } = useAuth()

  if (status === 'loading') return <p className="center">Cargando sesion...</p>
  if (status !== 'authenticated') return <Navigate to="/login" replace />
  if (role === 'ORGANIZADOR' && !claims?.isOrganizador) {
    return (
      <div className="card">
        <h2>Acceso denegado</h2>
        <p>Esta seccion es solo para organizadores.</p>
      </div>
    )
  }
  return children
}
