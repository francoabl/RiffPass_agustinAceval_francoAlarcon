import { apiFetch } from './apiFetch'
import { previewState } from './previewMode'
import {
  listarEventosMock,
  obtenerEventoMock,
  crearEventoMock,
  editarEventoMock,
  consultarInventarioMock,
  habilitarInventarioMock,
  obtenerEntradaParaMock,
} from './mockBackend'

export const listarEventos = (page = 0, size = 20) =>
  previewState.enabled ? listarEventosMock() : apiFetch(`/api/eventos?page=${page}&size=${size}`)

export const obtenerEvento = (id) =>
  previewState.enabled ? obtenerEventoMock(id) : apiFetch(`/api/eventos/${id}`)

export const crearEvento = (datos) =>
  previewState.enabled ? crearEventoMock(datos) : apiFetch('/api/eventos', { method: 'POST', body: JSON.stringify(datos) })

export const editarEvento = (id, datos) =>
  previewState.enabled ? editarEventoMock(id, datos) : apiFetch(`/api/eventos/${id}`, { method: 'PUT', body: JSON.stringify(datos) })

export const consultarInventario = (eventoId) =>
  previewState.enabled ? consultarInventarioMock(eventoId) : apiFetch(`/api/eventos/${eventoId}/inventario`)

export const habilitarInventario = (eventoId, capacidad) =>
  previewState.enabled
    ? habilitarInventarioMock(eventoId, capacidad)
    : apiFetch(`/api/eventos/${eventoId}/inventario`, { method: 'POST', body: JSON.stringify({ capacidad }) })

export const obtenerEntradaPara = (eventoId) =>
  previewState.enabled ? obtenerEntradaParaMock(eventoId) : apiFetch(`/api/eventos/${eventoId}/entradas`, { method: 'POST' })
