// Datos de ejemplo para el modo vista previa. Imitan las respuestas reales
// del backend (mismos campos, mismos codigos de error) para que las pantallas
// se comporten igual que con la API real.
import { previewState } from './previewMode'

const SUB_ORGANIZADOR = 'preview-organizador-sub'
const SUB_ASISTENTE = 'preview-asistente-sub'
const OTRO_ORGANIZADOR = 'otro-organizador-sub'

// El "yo" del demo cambia segun el rol elegido en el login, igual que cambiaria
// el sub real segun con que usuario de Cognito se inicie sesion.
const miSub = () => (previewState.rol === 'ORGANIZADOR' ? SUB_ORGANIZADOR : SUB_ASISTENTE)
const soyOrganizador = () => previewState.rol === 'ORGANIZADOR'

let eventos = [
  {
    id: 'evt-1',
    nombre: 'Noche de Rock Local',
    artista: 'Banda Sur',
    recinto: 'Sala Independiente',
    ciudad: 'Santiago',
    descripcion: 'Concierto de artistas emergentes',
    fecha: '2030-12-10T23:00:00Z',
    organizadorSub: SUB_ORGANIZADOR, // tuyo si entras como Organizador
    creadoEn: '2026-09-01T12:00:00Z',
  },
  {
    id: 'evt-2',
    nombre: 'Festival Indie',
    artista: 'Varios artistas',
    recinto: 'Parque Central',
    ciudad: 'Valparaiso',
    descripcion: 'Un dia completo de musica independiente',
    fecha: '2030-11-05T20:00:00Z',
    organizadorSub: OTRO_ORGANIZADOR, // de otro organizador, ninguno de los dos demos lo edita
    creadoEn: '2026-08-15T09:00:00Z',
  },
]

const inventarios = {
  'evt-1': { eventoId: 'evt-1', capacidad: 50, emitidas: 12, disponibles: 38 },
  // evt-2 no tiene inventario habilitado todavia (simula 404)
}

// El asistente demo ya tiene una entrada para el concierto que organiza el organizador demo.
let entradas = [
  {
    id: 'ent-1',
    eventoId: 'evt-1',
    codigo: 'AB12-CD34',
    estado: 'EMITIDA',
    emitidaEn: '2026-09-10T18:00:00Z',
    utilizadaEn: null,
    titularSub: SUB_ASISTENTE,
  },
]

const espera = (ms = 300) => new Promise((r) => setTimeout(r, ms))
const error = (status, detail) => {
  const e = new Error(detail)
  e.status = status
  return e
}

export async function listarEventosMock() {
  await espera()
  return eventos
}

export async function obtenerEventoMock(id) {
  await espera()
  const ev = eventos.find((e) => e.id === id)
  if (!ev) throw error(404, 'Concierto no encontrado')
  return ev
}

export async function crearEventoMock(datos) {
  await espera()
  if (!soyOrganizador()) throw error(403, 'Solo un organizador puede crear conciertos')
  const nuevo = { id: `evt-${eventos.length + 1}`, organizadorSub: miSub(), creadoEn: new Date().toISOString(), ...datos }
  eventos = [...eventos, nuevo]
  return nuevo
}

export async function editarEventoMock(id, datos) {
  await espera()
  const ev = eventos.find((e) => e.id === id)
  if (!ev) throw error(404, 'Concierto no encontrado')
  if (ev.organizadorSub !== miSub()) throw error(403, 'No eres el organizador de este concierto')
  Object.assign(ev, datos)
  return ev
}

export async function consultarInventarioMock(eventoId) {
  await espera()
  const inv = inventarios[eventoId]
  if (!inv) throw error(404, 'Todavia no se han habilitado cupos')
  return inv
}

export async function habilitarInventarioMock(eventoId, capacidad) {
  await espera()
  if (inventarios[eventoId]) throw error(409, 'Los cupos ya fueron habilitados')
  inventarios[eventoId] = { eventoId, capacidad, emitidas: 0, disponibles: capacidad }
  return { capacidad }
}

export async function obtenerEntradaParaMock(eventoId) {
  await espera()
  const inv = inventarios[eventoId]
  if (!inv || inv.disponibles < 1) throw error(409, 'No hay cupos disponibles')
  if (entradas.some((e) => e.eventoId === eventoId && e.titularSub === miSub())) {
    throw error(409, 'Ya tienes una entrada para este concierto')
  }
  inv.disponibles -= 1
  inv.emitidas += 1
  const entrada = {
    id: `ent-${entradas.length + 1}`,
    eventoId,
    codigo: Math.random().toString(36).slice(2, 10).toUpperCase(),
    estado: 'EMITIDA',
    emitidaEn: new Date().toISOString(),
    utilizadaEn: null,
    titularSub: miSub(),
  }
  entradas = [...entradas, entrada]
  return entrada
}

export async function misEntradasMock() {
  await espera()
  return entradas.filter((e) => e.titularSub === miSub())
}

export async function validarEntradaMock(codigo) {
  await espera()
  if (!soyOrganizador()) throw error(403, 'Solo el organizador puede validar entradas')
  const entrada = entradas.find((e) => e.codigo === codigo)
  if (!entrada) throw error(404, 'Codigo no encontrado')
  const evento = eventos.find((e) => e.id === entrada.eventoId)
  if (evento && evento.organizadorSub !== miSub()) throw error(403, 'No eres el organizador de este concierto')
  if (entrada.estado === 'UTILIZADA') throw error(409, 'La entrada ya fue utilizada')
  entrada.estado = 'UTILIZADA'
  entrada.utilizadaEn = new Date().toISOString()
  return entrada
}
