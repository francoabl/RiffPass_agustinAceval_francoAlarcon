import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { misEntradas } from '../api/entradas'

const fmtFecha = (iso) => (iso ? new Date(iso).toLocaleString('es-CL', { dateStyle: 'medium', timeStyle: 'short' }) : '-')

export default function MisEntradas() {
  const [entradas, setEntradas] = useState([])
  const [error, setError] = useState('')

  useEffect(() => {
    misEntradas().then(setEntradas).catch((e) => setError(e.message))
  }, [])

  return (
    <div>
      <h2>Mis entradas</h2>
      {error && <p className="error">{error}</p>}
      {entradas.map((en) => (
        <div className="ticket" key={en.id}>
          <div className="ticket-info">
            <span className={`badge ${en.estado}`}>{en.estado}</span>
            <p style={{ marginTop: 10 }}>Emitida: {fmtFecha(en.emitidaEn)}</p>
            {en.utilizadaEn && <p>Utilizada: {fmtFecha(en.utilizadaEn)}</p>}
            <Link to={`/eventos/${en.eventoId}`}>Ver concierto -&gt;</Link>
          </div>
          <div className="ticket-codigo">
            <span className="etiqueta">Codigo</span>
            <span className="codigo">{en.codigo}</span>
          </div>
        </div>
      ))}
      {entradas.length === 0 && !error && <p className="hint">Todavia no tienes entradas.</p>}
    </div>
  )
}
