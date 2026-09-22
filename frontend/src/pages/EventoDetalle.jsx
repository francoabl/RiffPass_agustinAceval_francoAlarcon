import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { obtenerEvento, editarEvento, consultarInventario, habilitarInventario, obtenerEntradaPara } from '../api/eventos'
import { useAuth } from '../auth/AuthContext'
import SubmitButton from '../components/SubmitButton'

const fmtFecha = (iso) => new Date(iso).toLocaleString('es-CL', { dateStyle: 'full', timeStyle: 'short' })

export default function EventoDetalle() {
  const { id } = useParams()
  const { claims } = useAuth()
  const [evento, setEvento] = useState(null)
  const [inventario, setInventario] = useState(null) // null = no habilitado todavia
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [capacidad, setCapacidad] = useState('')
  const [edicion, setEdicion] = useState(null) // {nombre, descripcion} mientras se edita
  const [guardando, setGuardando] = useState(false)
  const [habilitando, setHabilitando] = useState(false)
  const [obteniendo, setObteniendo] = useState(false)

  const esDueno = evento && claims?.sub === evento.organizadorSub

  const cargar = () => {
    obtenerEvento(id).then(setEvento).catch((e) => setError(e.message))
    consultarInventario(id)
      .then(setInventario)
      .catch((e) => (e.status === 404 ? setInventario(null) : setError(e.message)))
  }
  useEffect(cargar, [id])

  const guardarEdicion = async (e) => {
    e.preventDefault()
    setError('')
    setGuardando(true)
    try {
      const actualizado = await editarEvento(id, edicion)
      setEvento(actualizado)
      setEdicion(null)
      setOk('Concierto actualizado.')
    } catch (err) {
      setError(err.message)
    } finally {
      setGuardando(false)
    }
  }

  const habilitarCupos = async (e) => {
    e.preventDefault()
    setError('')
    setHabilitando(true)
    try {
      await habilitarInventario(id, Number(capacidad))
      setOk('Cupos habilitados.')
      cargar()
    } catch (err) {
      setError(err.message)
    } finally {
      setHabilitando(false)
    }
  }

  const obtenerEntrada = async () => {
    setError('')
    setOk('')
    setObteniendo(true)
    try {
      const entrada = await obtenerEntradaPara(id)
      setOk(`Entrada obtenida. Codigo: ${entrada.codigo}. Guardalo, lo pedira el organizador en la puerta.`)
      cargar()
    } catch (err) {
      setError(err.message)
    } finally {
      setObteniendo(false)
    }
  }

  if (!evento) return error ? <p className="error">{error}</p> : <p className="hint">Cargando...</p>

  const porcentaje = inventario ? Math.round((inventario.emitidas / inventario.capacidad) * 100) : 0

  return (
    <div>
      {error && <p className="error">{error}</p>}
      {ok && <p className="ok">{ok}</p>}

      <div className="hero-evento">
        <p className="artista">{evento.artista}</p>
        <h2>{evento.nombre}</h2>
        <div className="meta-grid">
          <div className="meta-item">
            <span className="etiqueta">Recinto</span>
            <span className="valor">{evento.recinto}</span>
          </div>
          <div className="meta-item">
            <span className="etiqueta">Ciudad</span>
            <span className="valor">{evento.ciudad}</span>
          </div>
          <div className="meta-item">
            <span className="etiqueta">Fecha</span>
            <span className="valor">{fmtFecha(evento.fecha)}</span>
          </div>
        </div>
        {evento.descripcion && <p>{evento.descripcion}</p>}
        {esDueno && !edicion && (
          <button onClick={() => setEdicion({ nombre: evento.nombre, descripcion: evento.descripcion ?? '' })}>
            Editar informacion
          </button>
        )}
      </div>

      {edicion && (
        <form className="card" onSubmit={guardarEdicion}>
          <h3>Editar informacion</h3>
          <label>
            Nombre
            <input value={edicion.nombre} onChange={(e) => setEdicion({ ...edicion, nombre: e.target.value })} required />
          </label>
          <label>
            Descripcion
            <textarea value={edicion.descripcion} onChange={(e) => setEdicion({ ...edicion, descripcion: e.target.value })} />
          </label>
          <div className="actions">
            <SubmitButton submitting={guardando}>Guardar</SubmitButton>
            <button type="button" onClick={() => setEdicion(null)} disabled={guardando}>Cancelar</button>
          </div>
        </form>
      )}

      <div className="card">
        <h3>Cupos</h3>
        {inventario ? (
          <div className="progreso-cupos">
            <div className="progreso-barra">
              <div className="progreso-relleno" style={{ transform: `scaleX(${porcentaje / 100})` }} />
            </div>
            <div className="progreso-texto">
              <span>{inventario.emitidas} de {inventario.capacidad} emitidas</span>
              <span>{inventario.disponibles} disponibles</span>
            </div>
          </div>
        ) : (
          <p className="hint">Este concierto todavia no tiene cupos habilitados.</p>
        )}
        {esDueno && !inventario && (
          <form onSubmit={habilitarCupos} style={{ marginTop: 14 }}>
            <label>
              Cantidad de cupos
              <input
                type="number"
                min="1"
                max="100000"
                placeholder="Ej: 100"
                value={capacidad}
                onChange={(e) => setCapacidad(e.target.value)}
                required
              />
            </label>
            <SubmitButton submitting={habilitando}>Habilitar cupos</SubmitButton>
          </form>
        )}
      </div>

      {!esDueno && (
        <div className="card">
          <h3>Tu entrada</h3>
          <p className="hint">Una entrada por persona y concierto. El codigo se pide en la puerta.</p>
          <SubmitButton
            submitting={obteniendo}
            disabled={!inventario || inventario.disponibles < 1}
            onClick={obtenerEntrada}
          >
            Obtener entrada
          </SubmitButton>
          {(!inventario || inventario.disponibles < 1) && <p className="hint">No hay cupos disponibles todavia.</p>}
        </div>
      )}
    </div>
  )
}
