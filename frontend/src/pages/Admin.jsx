import { useEffect, useState } from 'react'
import { apiFetch } from '../api/apiFetch'

const vacio = { nombre: '', descripcion: '', precio: '', stock: '' }

/** Solo rol ADMIN: gestion de productos y listado de usuarios. */
export default function Admin() {
  const [form, setForm] = useState(vacio)
  const [productos, setProductos] = useState([])
  const [usuarios, setUsuarios] = useState([])
  const [error, setError] = useState('')

  const cargar = () => {
    apiFetch('/api/productos').then(setProductos).catch((e) => setError(e.message))
    apiFetch('/api/usuarios').then(setUsuarios).catch((e) => setError(e.message))
  }
  useEffect(cargar, [])

  const cambiar = (e) => setForm({ ...form, [e.target.name]: e.target.value })

  const crear = async (e) => {
    e.preventDefault()
    setError('')
    try {
      await apiFetch('/api/productos', {
        method: 'POST',
        body: JSON.stringify({ ...form, precio: Number(form.precio), stock: Number(form.stock), activo: true }),
      })
      setForm(vacio)
      cargar()
    } catch (err) {
      setError(err.message)
    }
  }

  const eliminar = async (id) => {
    try {
      await apiFetch(`/api/productos/${id}`, { method: 'DELETE' })
      cargar()
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <div>
      <h2>Administracion</h2>
      {error && <p className="error">{error}</p>}
      <form className="card" onSubmit={crear}>
        <h3>Nuevo producto</h3>
        <input name="nombre" placeholder="Nombre" value={form.nombre} onChange={cambiar} required />
        <input name="descripcion" placeholder="Descripcion" value={form.descripcion} onChange={cambiar} />
        <input name="precio" type="number" min="1" placeholder="Precio" value={form.precio} onChange={cambiar} required />
        <input name="stock" type="number" min="0" placeholder="Stock" value={form.stock} onChange={cambiar} required />
        <button className="primary">Crear</button>
      </form>
      <h3>Productos</h3>
      <ul>
        {productos.map((p) => (
          <li key={p.id}>{p.nombre} - stock {p.stock} <button onClick={() => eliminar(p.id)}>Desactivar</button></li>
        ))}
      </ul>
      <h3>Usuarios registrados</h3>
      <ul>
        {usuarios.map((u) => (
          <li key={u.id}>{u.email ?? u.nombre} ({u.rol})</li>
        ))}
      </ul>
    </div>
  )
}
