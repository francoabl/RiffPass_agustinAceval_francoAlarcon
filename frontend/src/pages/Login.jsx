import { Navigate, useLocation } from 'react-router-dom'
import { Authenticator } from '@aws-amplify/ui-react'

/** Pantalla de login/registro de Amazon Cognito (Amplify Authenticator). */
export default function Login() {
  const location = useLocation()
  const destino = location.state?.from?.pathname ?? '/'
  return (
    <div className="center">
      <h1>Pedidos360</h1>
      <Authenticator>{() => <Navigate to={destino} replace />}</Authenticator>
    </div>
  )
}
