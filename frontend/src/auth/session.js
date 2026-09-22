import { fetchAuthSession } from 'aws-amplify/auth'

/** Tokens vigentes de la sesion (Amplify renueva con el refresh token si hace falta). */
export async function getTokens(forceRefresh = false) {
  const { tokens } = await fetchAuthSession({ forceRefresh })
  return tokens // { idToken, accessToken } o undefined si no hay sesion
}

/**
 * Lee identidad, roles (grupos de Cognito) y scopes desde el ACCESS TOKEN.
 * No se usa el ID token para autorizar: los scopes de negocio solo viven en el access token.
 */
export function readClaims(tokens) {
  const access = tokens?.accessToken?.payload ?? {}
  const idPayload = tokens?.idToken?.payload ?? {}
  const groups = (access['cognito:groups'] ?? []).map((g) => String(g).toUpperCase())
  const scopes = String(access.scope ?? '').split(' ').filter(Boolean)

  return {
    sub: access.sub,
    email: idPayload.email,
    username: access.username ?? idPayload['cognito:username'],
    groups,
    scopes,
    isOrganizador: groups.includes('ORGANIZADOR'),
    isAsistente: groups.includes('ASISTENTE'),
    tokenUse: access.token_use,
    expiraEn: access.exp ? new Date(access.exp * 1000) : null,
  }
}
