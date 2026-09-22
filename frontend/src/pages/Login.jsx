import { signInWithRedirect } from 'aws-amplify/auth'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

/**
 * Pantalla de login. Usa el Hosted UI de Cognito (Authorization Code + PKCE)
 * en vez del formulario directo: es la unica forma de que el access token
 * traiga los scopes de resource-server-riffpass que exige el backend.
 */
export default function Login() {
  const { status, activarVistaPrevia } = useAuth()

  if (status === 'authenticated') return <Navigate to="/eventos" replace />

  return (
    <div className="center">
      <h1>RiffPass</h1>
      <p>Descubre conciertos, reserva tu entrada digital y muestra tu codigo en la puerta.</p>
      <button className="primary" onClick={() => signInWithRedirect()} style={{ marginTop: 18 }}>
        Iniciar sesion con Cognito
      </button>
      <p className="hint" style={{ marginTop: 10 }}>
        Te vamos a redirigir al login seguro de Amazon Cognito. Si no tienes cuenta, puedes crearla ahi mismo.
      </p>

      {import.meta.env.DEV && (
        <div className="card preview-box">
          <h3>Vista previa (solo desarrollo)</h3>
          <p className="hint">
            Estos botones cargan datos de ejemplo en memoria para revisar las pantallas cuando el
            laboratorio AWS no esta activo. No usan Cognito ni el backend y no aparecen en produccion.
          </p>
          <div className="actions">
            <button onClick={() => activarVistaPrevia('ASISTENTE')}>Ver como Asistente</button>
            <button onClick={() => activarVistaPrevia('ORGANIZADOR')}>Ver como Organizador</button>
          </div>
        </div>
      )}
    </div>
  )
}
