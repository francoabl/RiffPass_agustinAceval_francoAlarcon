import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listarEventos } from '../api/eventos'
import { useAuth } from '../auth/AuthContext'

const fmtFecha = (iso) => new Date(iso).toLocaleString('es-CL', { dateStyle: 'medium', timeStyle: 'short' })

/** Genera un degrade unico y estable por concierto, a partir de su nombre. */
function bandaColor(texto) {
  let hash = 0
  for (let i = 0; i < texto.length; i++) hash = texto.charCodeAt(i) + ((hash << 5) - hash)
  const h1 = Math.abs(hash) % 360
  const h2 = (h1 + 45) % 360
  return `linear-gradient(120deg, hsl(${h1} 80% 55%), hsl(${h2} 80% 50%))`
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
        {eventos.map((ev) => (
          <Link className="card evento" to={`/eventos/${ev.id}`} key={ev.id}>
            <div className="evento-banda" style={{ background: bandaColor(ev.nombre) }} />
            <div className="evento-body">
              {claims?.isOrganizador && claims?.sub === ev.organizadorSub && <span className="badge organizador">Tu evento</span>}
              <h3>{ev.nombre}</h3>
              <p className="artista">{ev.artista}</p>
              <p className="lugar">{ev.recinto}, {ev.ciudad}</p>
              <span className="fecha">{fmtFecha(ev.fecha)}</span>
            </div>
          </Link>
        ))}
        {!cargando && eventos.length === 0 && <p className="hint">Todavia no hay conciertos publicados.</p>}
      </div>
    </div>
  )
}
