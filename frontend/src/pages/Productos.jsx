import { useEffect, useState } from 'react'
import { apiFetch } from '../api/apiFetch'
import { useAuthInfo } from '../auth/AuthContext'

const clp = (n) => new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP' }).format(n)

export default function Productos() {
  const { claims } = useAuthInfo()
  const [productos, setProductos] = useState([])
  const [carrito, setCarrito] = useState({}) // { productoId: cantidad }
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')

  const cargar = () =>
    apiFetch('/api/productos').then(setProductos).catch((e) => setError(e.message))

  useEffect(() => {
    cargar()
  }, [])

  const agregar = (p) => setCarrito((c) => ({ ...c, [p.id]: (c[p.id] ?? 0) + 1 }))
  const quitar = (id) =>
    setCarrito((c) => {
      const { [id]: _, ...resto } = c
      return resto
    })

  const total = productos.reduce((s, p) => s + (carrito[p.id] ?? 0) * p.precio, 0)

  const crearPedido = async () => {
    setError('')
    setOk('')
    try {
      const items = Object.entries(carrito).map(([productoId, cantidad]) => ({
        productoId: Number(productoId),
        cantidad,
      }))
      const pedido = await apiFetch('/api/pedidos', { method: 'POST', body: JSON.stringify({ items }) })
      setOk(`Pedido #${pedido.id} creado por ${clp(pedido.total)}`)
      setCarrito({})
      cargar()
    } catch (e) {
      setError(e.message)
    }
  }

  return (
    <div>
      <h2>Catalogo</h2>
      {error && <p className="error">{error}</p>}
      {ok && <p className="ok">{ok}</p>}
      <table>
        <thead>
          <tr><th>Producto</th><th>Precio</th><th>Stock</th><th /></tr>
        </thead>
        <tbody>
          {productos.map((p) => (
            <tr key={p.id}>
              <td><strong>{p.nombre}</strong><br /><small>{p.descripcion}</small></td>
              <td>{clp(p.precio)}</td>
              <td>{p.stock}</td>
              <td>
                <button disabled={p.stock === 0} onClick={() => agregar(p)}>Agregar</button>
              </td>
            </tr>
          ))}
          {productos.length === 0 && <tr><td colSpan="4">Sin productos{claims?.isAdmin && ' (crealos en Admin)'}.</td></tr>}
        </tbody>
      </table>

      {Object.keys(carrito).length > 0 && (
        <div className="card">
          <h3>Tu pedido</h3>
          <ul>
            {Object.entries(carrito).map(([id, cant]) => {
              const p = productos.find((x) => x.id === Number(id))
              return (
                <li key={id}>
                  {cant} x {p?.nombre} <button onClick={() => quitar(id)}>Quitar</button>
                </li>
              )
            })}
          </ul>
          <p>Total estimado: <strong>{clp(total)}</strong></p>
          <button className="primary" onClick={crearPedido}>Confirmar pedido</button>
        </div>
      )}
    </div>
  )
}
