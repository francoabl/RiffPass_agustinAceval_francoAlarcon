import { NavLink, Outlet } from 'react-router-dom'
import { useAuthenticator } from '@aws-amplify/ui-react'
import { useAuthInfo } from '../auth/AuthContext'

export default function Layout() {
  const { signOut } = useAuthenticator((ctx) => [ctx.user])
  const { claims } = useAuthInfo()

  return (
    <div>
      <header className="topbar">
        <strong>Pedidos360</strong>
        <nav>
          <NavLink to="/productos">Productos</NavLink>
          <NavLink to="/pedidos">Pedidos</NavLink>
          <NavLink to="/perfil">Perfil</NavLink>
          {claims?.isAdmin && <NavLink to="/admin">Admin</NavLink>}
        </nav>
        <span className="user">
          {claims?.email} {claims?.isAdmin && <span className="badge">ADMIN</span>}
          <button onClick={signOut}>Cerrar sesion</button>
        </span>
      </header>
      <main className="container">
        <Outlet />
      </main>
    </div>
  )
}
