import { apiFetch } from './apiFetch'
import { previewState } from './previewMode'
import { misEntradasMock, validarEntradaMock } from './mockBackend'

export const misEntradas = (page = 0, size = 20) =>
  previewState.enabled ? misEntradasMock() : apiFetch(`/api/entradas/mias?page=${page}&size=${size}`)

export const validarEntrada = (codigo) =>
  previewState.enabled ? validarEntradaMock(codigo) : apiFetch('/api/entradas/validar', { method: 'POST', body: JSON.stringify({ codigo }) })
