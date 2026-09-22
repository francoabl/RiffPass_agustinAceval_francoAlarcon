import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { crearEvento } from '../api/eventos'
import SubmitButton from '../components/SubmitButton'

const vacio = { nombre: '', artista: '', recinto: '', ciudad: '', descripcion: '', fecha: '' }

export default function CrearEvento() {
  const [form, setForm] = useState(vacio)
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)
  const navigate = useNavigate()

  const cambiar = (e) => setForm({ ...form, [e.target.name]: e.target.value })

  const enviar = async (e) => {
    e.preventDefault()
    setError('')
    setEnviando(true)
    try {
      // El input datetime-local no trae zona horaria; se interpreta como hora local
      // y se convierte a ISO-UTC, que es lo que pide el backend.
      const fechaIso = new Date(form.fecha).toISOString()
      const evento = await crearEvento({ ...form, fecha: fechaIso })
      navigate(`/eventos/${evento.id}`)
    } catch (err) {
      setError(err.message)
      setEnviando(false)
    }
  }

  return (
    <div>
      <h2>Publicar concierto</h2>
      {error && <p className="error">{error}</p>}
      <form className="card" onSubmit={enviar}>
        <label>
          Nombre del concierto
          <input name="nombre" value={form.nombre} onChange={cambiar} required />
        </label>
        <label>
          Artista o banda
          <input name="artista" value={form.artista} onChange={cambiar} required />
        </label>
        <label>
          Recinto
          <input name="recinto" value={form.recinto} onChange={cambiar} required />
        </label>
        <label>
          Ciudad
          <input name="ciudad" value={form.ciudad} onChange={cambiar} required />
        </label>
        <label>
          Descripcion
          <textarea name="descripcion" value={form.descripcion} onChange={cambiar} />
        </label>
        <label>
          Fecha y hora
          <input type="datetime-local" name="fecha" value={form.fecha} onChange={cambiar} required />
        </label>
        <SubmitButton submitting={enviando}>Publicar concierto</SubmitButton>
      </form>
    </div>
  )
}
