import { NavLink, Outlet } from 'react-router-dom'
import { signOut } from 'aws-amplify/auth'
import { useAuth } from '../auth/AuthContext'

function LogoMark() {
  return (
    <span className="brand-mark" aria-hidden="true">
      <svg width="16" height="16" viewBox="0 0 32 32" fill="none">
        <path d="M12 9v10.2a3 3 0 1 0 2 2.83V13l6-1.4v6.6a3 3 0 1 0 2 2.83V7l-10 2Z" fill="white" />
      </svg>
    </span>
  )
}

export default function Layout() {
  const { claims, preview, salirDeVistaPrevia } = useAuth()

  const cerrarSesion = () => (preview ? salirDeVistaPrevia() : signOut())

  return (
    <div>
      {preview && (
        <div className="preview-banner">Modo vista previa: datos de ejemplo, sin backend ni Cognito real.</div>
      )}
      <header className="topbar">
        <span className="brand">
          <LogoMark />
          RiffPass
        </span>
        <nav>
          <NavLink to="/eventos">Conciertos</NavLink>
          <NavLink to="/mis-entradas">Mis entradas</NavLink>
          {claims?.isOrganizador && <NavLink to="/eventos/nuevo">Crear concierto</NavLink>}
          {claims?.isOrganizador && <NavLink to="/validar">Validar entrada</NavLink>}
        </nav>
        <span className="user">
          {claims?.email ?? claims?.username}
          {claims?.isOrganizador && <span className="badge organizador">Organizador</span>}
          {claims?.isAsistente && <span className="badge asistente">Asistente</span>}
          <button onClick={cerrarSesion}>Cerrar sesion</button>
        </span>
      </header>
      <main className="container">
        <Outlet />
      </main>
    </div>
  )
}
