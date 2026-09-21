import { createContext, useContext, useEffect, useState } from 'react'
import { useAuthenticator } from '@aws-amplify/ui-react'
import { getTokens, readClaims } from './session'

const AuthContext = createContext({ claims: null, loading: true })

/** Expone los claims (roles/scopes) del usuario autenticado a toda la aplicacion. */
export function AuthInfoProvider({ children }) {
  const { authStatus } = useAuthenticator((ctx) => [ctx.authStatus])
  const [claims, setClaims] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let vivo = true
    if (authStatus === 'authenticated') {
      getTokens().then((t) => {
        if (vivo) {
          setClaims(t ? readClaims(t) : null)
          setLoading(false)
        }
      })
    } else if (authStatus === 'unauthenticated') {
      setClaims(null)
      setLoading(false)
    }
    return () => {
      vivo = false
    }
  }, [authStatus])

  return <AuthContext.Provider value={{ claims, loading }}>{children}</AuthContext.Provider>
}

export const useAuthInfo = () => useContext(AuthContext)
