import { signOut } from 'aws-amplify/auth'
import { getTokens } from '../auth/session'

const BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')
const TOKEN_TYPE = import.meta.env.VITE_TOKEN_TYPE ?? 'id'

async function authHeader(forceRefresh) {
  const tokens = await getTokens(forceRefresh)
  const token = TOKEN_TYPE === 'access' ? tokens?.accessToken : tokens?.idToken
  if (!token) throw new Error('No hay sesion activa')
  return { Authorization: `Bearer ${token.toString()}` }
}

async function doFetch(path, options, forceRefresh) {
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers ?? {}),
    ...(await authHeader(forceRefresh)),
  }
  return fetch(`${BASE_URL}${path}`, { ...options, headers })
}

/**
 * fetch con el JWT de Cognito adjunto (interceptor).
 * - Si el API responde 401 renueva la sesion una vez y reintenta.
 * - Si sigue en 401 cierra la sesion.
 * Devuelve el JSON de la respuesta o lanza Error con el mensaje del backend.
 */
export async function apiFetch(path, options = {}) {
  let res = await doFetch(path, options, false)
  if (res.status === 401) {
    res = await doFetch(path, options, true)
    if (res.status === 401) {
      await signOut()
      throw new Error('Sesion expirada. Vuelve a iniciar sesion.')
    }
  }
  if (res.status === 204) return null
  const data = await res.json().catch(() => null)
  if (!res.ok) {
    throw new Error(data?.message ?? `Error ${res.status}`)
  }
  return data
}
