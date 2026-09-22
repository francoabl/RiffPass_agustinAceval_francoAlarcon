import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listarEventos } from '../api/eventos'
import { useAuth } from '../auth/AuthContext'

const fmtFecha = (iso) => new Date(iso).toLocaleString('es-CL', { dateStyle: 'medium', timeStyle: 'short' })

// Solo presentacion: separa dia / mes / anio para el bloque de fecha de la tarjeta.
const partesFecha = (iso) => {
  const d = new Date(iso)
  return {
    dia: d.toLocaleString('es-CL', { day: '2-digit' }),
    mes: d.toLocaleString('es-CL', { month: 'short' }).replace('.', ''),
    anio: d.getFullYear(),
  }
}

export default function Eventos() {
  const { claims } = useAuth()
  const [eventos, setEventos] = useState([])
  const [error, setError] = useState('')
  const [cargando, setCargando] = useState(true)

  useEffect(() => {
    listarEventos()
      .then(setEventos)
      .catch((e) => setError(e.message))
      .finally(() => setCargando(false))
  }, [])

  return (
    <div>
      <h2>Proximos conciertos</h2>
      {error && <p className="error">{error}</p>}
      {cargando && <p className="hint">Cargando...</p>}
      <div className="grid">
        {cargando && [0, 1, 2].map((i) => <div className="evento cargando" key={`sk-${i}`} aria-hidden="true" />)}
        {eventos.map((ev, i) => {
          const f = partesFecha(ev.fecha)
          return (
          <Link className="evento" to={`/eventos/${ev.id}`} key={ev.id} style={{ '--i': i }}>
            <div className="evento-fecha" aria-hidden="true">
              <span className="dia">{f.dia}</span>
              <span className="mes">{f.mes}</span>
              <span className="anio">{f.anio}</span>
            </div>
            <div className="evento-body">
              {claims?.isOrganizador && claims?.sub === ev.organizadorSub && <span className="badge organizador">Tu evento</span>}
              <h3>{ev.nombre}</h3>
              <p className="artista">{ev.artista}</p>
              <p className="lugar">{ev.recinto}, {ev.ciudad}</p>
              <span className="fecha">{fmtFecha(ev.fecha)}</span>
            </div>
          </Link>
          )
        })}
        {!cargando && eventos.length === 0 && <p className="hint vacio">Todavia no hay conciertos publicados.</p>}
      </div>
    </div>
  )
}
