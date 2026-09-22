/**
 * Boton de submit reutilizable con estado "enviando" (deshabilitado + spinner).
 * Puramente presentacional: no toca logica de negocio, nombres de campos ni
 * contratos de API. Cada formulario sigue llamando a su propia funcion async;
 * solo le pasa `submitting` para reflejar el estado mientras espera.
 */
export default function SubmitButton({ submitting = false, submittingLabel, className = 'primary', disabled = false, children, ...props }) {
  return (
    <button className={className} {...props} disabled={submitting || disabled}>
      {submitting && <span className="btn-spinner" aria-hidden="true" />}
      {submitting ? (submittingLabel ?? 'Enviando...') : children}
    </button>
  )
}
