import { Amplify } from 'aws-amplify'

// Configuracion de Amazon Cognito (User Pool + App Client) via variables de entorno.
export function configureAmplify() {
  Amplify.configure({
    Auth: {
      Cognito: {
        userPoolId: import.meta.env.VITE_COGNITO_USER_POOL_ID,
        userPoolClientId: import.meta.env.VITE_COGNITO_APP_CLIENT_ID,
        loginWith: { email: true },
        signUpVerificationMethod: 'code',
      },
    },
  })
}
