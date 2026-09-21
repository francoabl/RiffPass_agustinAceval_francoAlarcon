import { useEffect, useState } from 'react'
import { apiFetch } from '../api/apiFetch'
import { useAuthInfo } from '../auth/AuthContext'

const clp = (n) => new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP' }).format(n)
const SIGUIENTE = { CREADO: 'CONFIRMADO', CONFIRMADO: 'ENVIADO' }

export default function Pedidos() {
  const { claims } = useAuthInfo()
  const [pedidos, setPedidos] = useState([])
  const [error, setError] = useState('')

  const cargar = () => apiFetch('/api/pedidos').then(setPedidos).catch((e) => setError(e.message))
  useEffect(() => {
    cargar()
  }, [])

  const accion = async (fn) => {
    setError('')
    try {
      await fn()
      await cargar()
    } catch (e) {
      setError(e.message)
    }
  }

  const avanzar = (p) =>
    accion(() => apiFetch(`/api/pedidos/${p.id}/estado`, { method: 'PATCH', body: JSON.stringify({ estado: SIGUIENTE[p.estado] }) }))
  const cancelar = (p) => accion(() => apiFetch(`/api/pedidos/${p.id}/cancelar`, { method: 'POST' }))

  return (
    <div>
      <h2>{claims?.isAdmin ? 'Todos los pedidos' : 'Mis pedidos'}</h2>
      {error && <p className="error">{error}</p>}
      {pedidos.map((p) => (
        <div className="card" key={p.id}>
          <h3>Pedido #{p.id} <span className={`badge ${p.estado}`}>{p.estado}</span></h3>
          <small>{new Date(p.fecha).toLocaleString()} {claims?.isAdmin && `- ${p.clienteEmail ?? p.clienteSub}`}</small>
          <ul>
            {p.detalles.map((d) => (
              <li key={d.id}>{d.cantidad} x {d.nombreProducto} ({clp(d.precioUnitario)})</li>
            ))}
          </ul>
          <strong>Total: {clp(p.total)}</strong>
          <div className="actions">
            {claims?.isAdmin && SIGUIENTE[p.estado] && (
              <button className="primary" onClick={() => avanzar(p)}>Marcar {SIGUIENTE[p.estado]}</button>
            )}
            {(p.estado === 'CREADO' || (claims?.isAdmin && p.estado === 'CONFIRMADO')) && (
              <button onClick={() => cancelar(p)}>Cancelar</button>
            )}
          </div>
        </div>
      ))}
      {pedidos.length === 0 && <p>No hay pedidos todavia.</p>}
    </div>
  )
}
