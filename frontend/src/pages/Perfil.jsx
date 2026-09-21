import { useEffect, useState } from 'react'
import { apiFetch } from '../api/apiFetch'
import { useAuthInfo } from '../auth/AuthContext'

export default function Perfil() {
  const { claims } = useAuthInfo()
  const [perfil, setPerfil] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    apiFetch('/api/usuarios/me').then(setPerfil).catch((e) => setError(e.message))
  }, [])

  return (
    <div className="card">
      <h2>Mi perfil</h2>
      {error && <p className="error">{error}</p>}
      <h3>Claims del token (Cognito)</h3>
      <ul>
        <li>Usuario: {claims?.username}</li>
        <li>Email: {claims?.email}</li>
        <li>Roles (cognito:groups): {claims?.groups.join(', ') || 'CLIENTE (sin grupo)'}</li>
        <li>Scopes: {claims?.scopes.join(', ') || '(ninguno)'}</li>
        <li>Token expira: {claims?.expiraEn?.toLocaleString()}</li>
      </ul>
      <h3>Perfil en el backend (usuarios-service)</h3>
      <pre>{perfil ? JSON.stringify(perfil, null, 2) : 'Cargando...'}</pre>
    </div>
  )
}
