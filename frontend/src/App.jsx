import { Routes, Route, Navigate } from 'react-router-dom'
import ProtectedRoute from './auth/ProtectedRoute'
import Layout from './components/Layout'
import Login from './pages/Login'
import Eventos from './pages/Eventos'
import EventoDetalle from './pages/EventoDetalle'
import CrearEvento from './pages/CrearEvento'
import MisEntradas from './pages/MisEntradas'
import ValidarEntrada from './pages/ValidarEntrada'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route element={<ProtectedRoute><Layout /></ProtectedRoute>}>
        <Route path="/" element={<Navigate to="/eventos" replace />} />
        <Route path="/eventos" element={<Eventos />} />
        <Route path="/eventos/nuevo" element={<ProtectedRoute role="ORGANIZADOR"><CrearEvento /></ProtectedRoute>} />
        <Route path="/eventos/:id" element={<EventoDetalle />} />
        <Route path="/mis-entradas" element={<MisEntradas />} />
        <Route path="/validar" element={<ProtectedRoute role="ORGANIZADOR"><ValidarEntrada /></ProtectedRoute>} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
