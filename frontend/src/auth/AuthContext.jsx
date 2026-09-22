import { createContext, useContext, useEffect, useState } from 'react'
import { Hub } from 'aws-amplify/utils'
import { getCurrentUser } from 'aws-amplify/auth'
import { getTokens, readClaims } from './session'
import { activarPreview, desactivarPreview } from '../api/previewMode'

const AuthContext = createContext({
  status: 'loading',
  claims: null,
  preview: false,
  refresh: () => {},
  activarVistaPrevia: () => {},
  salirDeVistaPrevia: () => {},
})

/**
 * Fuente de verdad de la sesion para toda la app.
 * status: 'loading' | 'authenticated' | 'unauthenticated'
 * Escucha los eventos de Amplify (Hub) para reaccionar cuando Cognito
 * redirige de vuelta tras el login/logout via Hosted UI.
 *
 * Tambien soporta un modo de "vista previa" (solo desarrollo, ver Login.jsx):
 * simula una sesion con datos de ejemplo, sin tocar Amplify ni la red.
 */
export function AuthProvider({ children }) {
  const [state, setState] = useState({ status: 'loading', claims: null, preview: false })

  const cargarSesion = async () => {
    try {
      await getCurrentUser()
      const tokens = await getTokens()
      setState({ status: tokens ? 'authenticated' : 'unauthenticated', claims: tokens ? readClaims(tokens) : null, preview: false })
    } catch {
      setState({ status: 'unauthenticated', claims: null, preview: false })
    }
  }

  useEffect(() => {
    cargarSesion()
    const unsubscribe = Hub.listen('auth', ({ payload }) => {
      if (['signedIn', 'signInWithRedirect', 'tokenRefresh'].includes(payload.event)) {
        cargarSesion()
      }
      if (['signedOut', 'signInWithRedirect_failure'].includes(payload.event)) {
        setState({ status: 'unauthenticated', claims: null, preview: false })
      }
    })
    return unsubscribe
  }, [])

  const activarVistaPrevia = (rol) => {
    activarPreview(rol)
    setState({
      status: 'authenticated',
      preview: true,
      claims: {
        sub: rol === 'ORGANIZADOR' ? 'preview-organizador-sub' : 'preview-asistente-sub',
        email: rol === 'ORGANIZADOR' ? 'organizador@ejemplo.cl' : 'asistente@ejemplo.cl',
        username: rol === 'ORGANIZADOR' ? 'organizador-demo' : 'asistente-demo',
        groups: [rol],
        scopes: [],
        isOrganizador: rol === 'ORGANIZADOR',
        isAsistente: rol === 'ASISTENTE',
        tokenUse: null,
        expiraEn: null,
      },
    })
  }

  const salirDeVistaPrevia = () => {
    desactivarPreview()
    setState({ status: 'unauthenticated', claims: null, preview: false })
  }

  return (
    <AuthContext.Provider value={{ ...state, refresh: cargarSesion, activarVistaPrevia, salirDeVistaPrevia }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => useContext(AuthContext)
