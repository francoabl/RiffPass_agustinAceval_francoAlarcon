import { Navigate, useLocation } from 'react-router-dom'
import { useAuthenticator } from '@aws-amplify/ui-react'
import { useAuthInfo } from './AuthContext'

/** Ruta protegida: exige sesion y, opcionalmente, el rol ADMIN. */
export default function ProtectedRoute({ children, adminOnly = false }) {
  const { authStatus } = useAuthenticator((ctx) => [ctx.authStatus])
  const { claims, loading } = useAuthInfo()
  const location = useLocation()

  if (authStatus === 'configuring' || (authStatus === 'authenticated' && loading)) {
    return <p className="center">Cargando sesion...</p>
  }
  if (authStatus !== 'authenticated') {
    return <Navigate to="/login" state={{ from: location }} replace />
  }
  if (adminOnly && !claims?.isAdmin) {
    return (
      <div className="card">
        <h2>Acceso denegado</h2>
        <p>Esta seccion requiere el rol ADMIN.</p>
      </div>
    )
  }
  return children
}
