/**
 * Estado de "vista previa" (solo desarrollo): permite navegar la app con datos
 * de ejemplo, sin Cognito ni backend real. Nunca se activa en produccion:
 * el unico lugar que lo enciende (Login.jsx) esta detras de import.meta.env.DEV.
 */
export const previewState = { enabled: false, rol: null }

export function activarPreview(rol) {
  previewState.enabled = true
  previewState.rol = rol
}

export function desactivarPreview() {
  previewState.enabled = false
  previewState.rol = null
}
