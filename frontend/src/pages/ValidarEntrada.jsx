import { useState } from 'react'
import { validarEntrada } from '../api/entradas'

/** Uso en la puerta del concierto: solo el organizador dueno del evento puede validar. */
export default function ValidarEntrada() {
  const [codigo, setCodigo] = useState('')
  const [resultado, setResultado] = useState(null)
  const [error, setError] = useState('')

  const enviar = async (e) => {
    e.preventDefault()
    setError('')
    setResultado(null)
    try {
      const entrada = await validarEntrada(codigo.trim())
      setResultado(entrada)
      setCodigo('')
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <div>
      <h2>Validar entrada en puerta</h2>
      <form className="card" onSubmit={enviar}>
        <label>
          Codigo de la entrada
          <input value={codigo} onChange={(e) => setCodigo(e.target.value)} placeholder="Ej: AB12-CD34" required />
        </label>
        <button className="primary">Validar</button>
      </form>
      {error && <p className="error">{error}</p>}
      {resultado && (
        <div className="card ok">
          <p>Entrada valida. Estado: <strong>{resultado.estado}</strong></p>
          <p>Utilizada en: {new Date(resultado.utilizadaEn).toLocaleString('es-CL')}</p>
        </div>
      )}
    </div>
  )
}
