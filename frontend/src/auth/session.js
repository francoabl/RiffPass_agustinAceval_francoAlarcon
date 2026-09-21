import { fetchAuthSession } from 'aws-amplify/auth'

/** Devuelve los tokens vigentes (Amplify renueva con el refresh token si hace falta). */
export async function getTokens(forceRefresh = false) {
  const { tokens } = await fetchAuthSession({ forceRefresh })
  return tokens // { idToken, accessToken } o undefined si no hay sesion
}

/** Lee roles (grupos de Cognito) y scopes desde los claims de los tokens. */
export function readClaims(tokens) {
  const idPayload = tokens?.idToken?.payload ?? {}
  const accessPayload = tokens?.accessToken?.payload ?? {}
  const groups = (idPayload['cognito:groups'] ?? accessPayload['cognito:groups'] ?? []).map((g) =>
    String(g).toUpperCase(),
  )
  const scopes = String(accessPayload.scope ?? '').split(' ').filter(Boolean)
  return {
    sub: idPayload.sub ?? accessPayload.sub,
    email: idPayload.email,
    username: accessPayload.username ?? idPayload['cognito:username'],
    groups,
    scopes,
    isAdmin: groups.includes('ADMIN'),
    expiraEn: idPayload.exp ? new Date(idPayload.exp * 1000) : null,
  }
}
