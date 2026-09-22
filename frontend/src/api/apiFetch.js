import { signOut } from 'aws-amplify/auth'
import { getTokens } from '../auth/session'

const BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

async function authHeader(forceRefresh) {
  const tokens = await getTokens(forceRefresh)
  // El backend exige ACCESS token (token_use=access) con los scopes de negocio.
  // El ID token no sirve aqui: no lleva "scope" ni los custom scopes del resource server.
  if (!tokens?.accessToken) throw new Error('No hay sesion activa')
  return { Authorization: `Bearer ${tokens.accessToken.toString()}` }
}

async function doFetch(path, options, forceRefresh) {
  const headers = { ...(options.headers ?? {}), ...(await authHeader(forceRefresh)) }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  return fetch(`${BASE_URL}${path}`, { ...options, headers })
}

/**
 * fetch con el access token de Cognito adjunto (interceptor).
 * - 401: intenta renovar la sesion una vez y reintenta; si sigue en 401, cierra sesion.
 * - Traduce los errores application/problem+json del backend (status/title/detail) a Error.
 * - 204: no hay cuerpo, devuelve null. 201/200: devuelve el JSON.
 */
export async function apiFetch(path, options = {}) {
  let res = await doFetch(path, options, false)
  if (res.status === 401) {
    res = await doFetch(path, options, true)
    if (res.status === 401) {
      await signOut()
      throw new Error('Sesion expirada o invalida. Vuelve a iniciar sesion.')
    }
  }
  if (res.status === 204) return null

  const texto = await res.text()
  const data = texto ? JSON.parse(texto) : null

  if (!res.ok) {
    const mensaje = data?.detail ?? data?.title ?? `Error ${res.status}`
    const detalleCampos = data?.errors ? ` (${JSON.stringify(data.errors)})` : ''
    const error = new Error(mensaje + detalleCampos)
    error.status = res.status
    throw error
  }
  return data
}
