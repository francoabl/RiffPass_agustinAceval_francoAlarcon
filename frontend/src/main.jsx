import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { Authenticator } from '@aws-amplify/ui-react'
import '@aws-amplify/ui-react/styles.css'
import { configureAmplify } from './amplifyConfig'
import { AuthInfoProvider } from './auth/AuthContext'
import App from './App'
import './styles.css'

configureAmplify()

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <Authenticator.Provider>
      <AuthInfoProvider>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </AuthInfoProvider>
    </Authenticator.Provider>
  </React.StrictMode>,
)
