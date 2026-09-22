# Contrato del backend RiffPass

## Direcciones y autenticación

Local: Eventos `http://localhost:8081`; Entradas `http://localhost:8082`.
AWS: una base común `https://<api-id>.execute-api.<region>.amazonaws.com/test`.

Añadir las rutas de la tabla a la base, sin repetir `/test`.
Todas requieren `Authorization: Bearer <ACCESS_TOKEN>`. No usar ID token ni refresh token.

Scopes completos: prefijo `resource-server-riffpass/` seguido del nombre en la tabla.
Gateway exige el scope principal. El backend exige todos los scopes indicados y además rol/propiedad.

| Servicio | Método y ruta | Scopes backend | Permisos |
|---|---|---|---|
| Eventos | GET /api/eventos | eventos-read | Autenticado |
| Eventos | GET /api/eventos/{id} | eventos-read | Autenticado |
| Eventos | POST /api/eventos | eventos-write | ORGANIZADOR |
| Eventos | PUT /api/eventos/{id} | eventos-write | ORGANIZADOR dueño |
| Entradas | POST /api/eventos/{id}/inventario | entradas-write + eventos-read | ORGANIZADOR dueño |
| Entradas | GET /api/eventos/{id}/inventario | entradas-read | Autenticado |
| Entradas | POST /api/eventos/{id}/entradas | entradas-write + eventos-read | ASISTENTE u ORGANIZADOR |
| Entradas | GET /api/entradas/mias | entradas-read | Solo registros del usuario |
| Entradas | POST /api/entradas/validar | entradas-write + eventos-read | ORGANIZADOR dueño del evento |

`GET /actuator/health` es público en cada servicio, pero no se publica como método de negocio en Gateway. En EC2 con contexto `/test`, la ruta interna es `/test/actuator/health`.

## Crear concierto

`POST /api/eventos`, devuelve 201:

~~~json
{
  "nombre": "Noche de Rock Local",
  "artista": "Banda Sur",
  "recinto": "Sala Independiente",
  "ciudad": "Santiago",
  "descripcion": "Concierto de artistas emergentes",
  "fecha": "2030-12-10T23:00:00Z"
}
~~~

Usar una fecha futura real para tu demostración. El instante incluye zona horaria; `Z` indica UTC. No enviar identificador del organizador: se obtiene del token. Guardar `id` de la respuesta.

La respuesta contiene id, los datos del concierto, organizadorSub y creadoEn. Fecha, artista, recinto, ciudad y propietario no se modifican en este MVP. No existe endpoint para eliminar o cancelar.

## Editar información

`PUT /api/eventos/{id}`, devuelve 200:

~~~json
{"nombre":"Noche de Rock Local - edición especial","descripcion":"Apertura de puertas 19:00"}
~~~

## Listar

`GET /api/eventos?page=0&size=20`: arreglo de conciertos, ordenado por fecha e id.
`GET /api/entradas/mias?page=0&size=20`: arreglo de entradas del usuario autenticado, más recientes primero.
Página desde 0, tamaño de 1 a 100. Se devuelve un arreglo, no un objeto Spring Page.

## Abrir y consultar cupos

`POST /api/eventos/{id}/inventario`, devuelve 201:

~~~json
{"capacidad":50}
~~~

Solo una vez, por el organizador propietario. Capacidad de 1 a 100000. Un segundo intento responde 409 y no reinicia los cupos.

`GET /api/eventos/{id}/inventario`, devuelve 200:

~~~json
{"eventoId":"<uuid>","capacidad":50,"emitidas":0,"disponibles":50}
~~~

Antes de abrir inventario, esta consulta devuelve 404.

## Obtener entrada

`POST /api/eventos/{id}/entradas`, **sin cuerpo**, devuelve 201.

~~~json
{
  "id":"<uuid>",
  "eventoId":"<uuid>",
  "codigo":"<uuid-aleatorio>",
  "estado":"EMITIDA",
  "emitidaEn":"2030-01-01T10:00:00Z",
  "utilizadaEn":null
}
~~~

Una entrada por persona y evento. La identidad del titular sale del token.
Guardar `codigo`. Si no quedan cupos, ya tienes entrada, no se habilitó inventario o el evento ya comenzó: 409.

Inventario y entrada se actualizan dentro de la misma transacción. La fila de inventario se bloquea en la base, por lo que la protección funciona también con varias instancias del servicio.

## Validar ingreso

`POST /api/entradas/validar`, devuelve 200:

~~~json
{"codigo":"<codigo-de-la-entrada>"}
~~~

Debe llamar el organizador dueño del concierto. La respuesta contiene `estado: UTILIZADA` y `utilizadaEn`.
No se exige esperar la hora del concierto: el encargado puede abrir puertas antes. Volver a validar devuelve 409. La entrada se bloquea durante la transición.

## Errores

| Código | Caso |
|---|---|
| 400 | JSON/campos inválidos, fecha pasada al crear, UUID inválido, paginación fuera de rango |
| 401 | Ausencia de token, JWT alterado/vencido o destinado a otro pool/cliente |
| 403 | Scope/grupo insuficiente o evento de otro organizador |
| 404 | Evento, inventario o código inexistente |
| 409 | Cupos agotados, duplicado, evento iniciado, inventario ya creado, entrada utilizada |
| 502 | Respuesta inesperada del servicio de eventos |
| 503 | Servicio de eventos inaccesible o con error de servidor |

Los errores del backend usan `application/problem+json` con `status`, `title` y `detail`; los de validación añaden `errors`. API Gateway puede responder otro cuerpo/código al rechazar antes de llegar al backend. No usar su botón de prueba interna como evidencia de autorización: probar el Invoke URL desplegado.

## Modelo y consistencia

- Eventos DB: tabla `eventos`.
- Entradas DB: tablas `inventarios` y `entradas`, con FK local.
- No hay FK ni consultas SQL cruzadas entre bases.
- El propietario y la fecha se consultan por HTTP con el JWT original. Son inmutables para evitar desincronización del inventario en esta etapa.
- Una caída de Eventos impide emitir/validar y no consume cupos; consultar entradas propias sigue disponible.
- Los scopes no equivalen a roles. Aunque un asistente consiga `eventos-write`, el backend no le permite crear conciertos.
