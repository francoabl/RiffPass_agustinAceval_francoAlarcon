import { Amplify } from 'aws-amplify'

/**
 * Configura Amplify contra el User Pool de Cognito (riffpass-pool / riffpass-web).
 *
 * Importante: el login se hace via Hosted UI (OAuth Authorization Code + PKCE),
 * NO con el formulario directo de usuario/clave. Solo el flujo OAuth entrega los
 * scopes personalizados (resource-server-riffpass/...) dentro del access token;
 * el login directo de Cognito solo trae el scope interno de administracion del
 * propio User Pool. El backend de RiffPass exige esos scopes en cada endpoint
 * de negocio, asi que sin Hosted UI las llamadas a la API fallarian con 403.
 */
export function configureAmplify() {
  const domain = import.meta.env.VITE_COGNITO_DOMAIN
  const scopes = (import.meta.env.VITE_OAUTH_SCOPES ?? 'openid email profile')
    .split(' ')
    .filter(Boolean)
  const redirectSignIn = (import.meta.env.VITE_REDIRECT_SIGNIN ?? `${window.location.origin}/`)
    .split(',')
  const redirectSignOut = (import.meta.env.VITE_REDIRECT_SIGNOUT ?? `${window.location.origin}/`)
    .split(',')

  Amplify.configure({
    Auth: {
      Cognito: {
        userPoolId: import.meta.env.VITE_COGNITO_USER_POOL_ID,
        userPoolClientId: import.meta.env.VITE_COGNITO_APP_CLIENT_ID,
        loginWith: {
          email: true,
          oauth: {
            domain,
            scopes,
            redirectSignIn,
            redirectSignOut,
            responseType: 'code',
          },
        },
      },
    },
  })
}
