# Guía de estudio de RiffPass

Esta guía explica qué hace el proyecto completo, cómo se conectan sus partes y qué conviene saber para presentarlo. No reemplaza el contrato técnico de la API ni la guía de despliegue; su objetivo es ayudarte a comprender y defender las decisiones del proyecto.

## 1. RiffPass en 30 segundos

RiffPass es una plataforma de entradas gratuitas para conciertos. Tiene dos tipos de usuarios:

- **ASISTENTE:** consulta conciertos, obtiene una entrada y revisa sus propias entradas.
- **ORGANIZADOR:** crea conciertos, habilita cupos, edita sus eventos y valida entradas en la puerta.

El frontend está construido con React y usa Amazon Cognito para iniciar sesión. Las peticiones pasan por API Gateway y llegan a dos microservicios Spring Boot desplegados en EC2. Los datos se guardan en PostgreSQL RDS.

La idea central de seguridad es esta:

> El frontend ayuda a mostrar la interfaz correcta, pero la autorización verdadera siempre se vuelve a comprobar en API Gateway y en cada microservicio.

## 2. Problema que resuelve

Sin RiffPass, un organizador tendría que administrar conciertos, cupos y accesos manualmente. RiffPass centraliza un flujo mínimo:

1. Un organizador publica un concierto.
2. El organizador define una capacidad una sola vez.
3. Un asistente obtiene como máximo una entrada para ese concierto.
4. El sistema descuenta el cupo sin permitir sobreventa.
5. El organizador valida el código de la entrada.
6. La misma entrada no puede utilizarse dos veces.

La EP1 no necesita pagos, selección de asientos ni códigos QR. En el MVP, la entrada es gratuita y su código único es un UUID.

## 3. Qué está dentro y fuera del alcance

### Implementado

- Login y logout con Cognito.
- Dos roles mediante grupos de Cognito.
- Cuatro scopes personalizados para la API.
- Catálogo y detalle de conciertos.
- Creación y edición limitada de conciertos.
- Habilitación y consulta de cupos.
- Emisión de una entrada por usuario y evento.
- Listado de entradas del usuario autenticado.
- Validación única de una entrada.
- Seguridad JWT en Gateway y backend.
- Persistencia en PostgreSQL RDS.
- Frontend público en Vercel.
- Pruebas Java y colección Postman.

### No implementado deliberadamente

- Pagos reales.
- Asientos numerados.
- Cancelación o reprogramación de conciertos.
- Cambio de capacidad después de abrir el inventario.
- Envío de correos o notificaciones.
- Generación visual de QR.
- Panel administrativo general.

Estas exclusiones reducen el alcance sin eliminar los requisitos de autenticación, autorización, microservicios y persistencia de la evaluación.

## 4. Arquitectura completa

```text
Navegador
   |
   | HTTPS
   v
Vercel: React + rewrite /api/*
   |
   | HTTPS + Authorization: Bearer <access token>
   v
Amazon API Gateway REST, stage test
   |
   | Cognito Authorizer + scope del método
   v
VPC Link V2
   |
   v
Application Load Balancer interno
   |                               |
   | rutas de Eventos              | rutas de Entradas
   v                               v
EC2: servicio Eventos :8081        EC2: servicio Entradas :8082
   |                               |
   |                               | HTTP interno hacia Eventos
   |                               | reenviando el access token
   v                               v
RDS / base riffpass_eventos        RDS / base riffpass_entradas
```

### Responsabilidad de cada componente

| Componente | Responsabilidad |
|---|---|
| Vercel | Publica los archivos estáticos del frontend y reenvía `/api/*` hacia API Gateway. |
| React | Muestra vistas, controla navegación y consume la API. |
| Amplify Auth | Ejecuta el flujo OAuth con Cognito y administra la sesión/tokens. |
| Cognito User Pool | Registra usuarios, autentica y emite JWT. |
| Cognito groups | Representan los roles `ASISTENTE` y `ORGANIZADOR`. |
| Resource Server | Define los scopes de negocio. |
| API Gateway | Expone la API HTTPS, valida Cognito y exige un scope por método. |
| VPC Link V2 | Conecta Gateway con recursos privados dentro de la VPC. |
| ALB interno | Envía cada ruta al target group del microservicio correcto. |
| EC2 | Ejecuta los dos JAR como procesos systemd independientes. |
| Eventos | Administra conciertos y su propietario. |
| Entradas | Administra inventarios, emisión y validación de entradas. |
| RDS PostgreSQL | Conserva los datos aunque los procesos Java se reinicien. |

## 5. Por qué hay dos microservicios

La pauta solicita varios microservicios Java con Spring Boot. RiffPass divide el dominio por responsabilidad:

### `riffpass-eventos-service`

Es la autoridad sobre:

- Datos del concierto.
- Fecha.
- Artista, recinto y ciudad.
- Descripción.
- Propietario organizador.

### `riffpass-entradas-service`

Es la autoridad sobre:

- Capacidad y cupos emitidos.
- Entradas de los asistentes.
- Código único.
- Estado `EMITIDA` o `UTILIZADA`.
- Validación de ingreso.

No comparten tablas ni hacen consultas SQL cruzadas. Cuando Entradas necesita conocer la fecha o el propietario de un evento, llama a Eventos por HTTP y reenvía el mismo access token. Esto conserva la separación entre servicios.

`riffpass-common` no es un tercer microservicio: es una biblioteca compartida que se incorpora dentro de ambos JAR para reutilizar seguridad y manejo de errores.

## 6. Capas del backend

Cada microservicio sigue una separación similar:

```text
Controller -> Service -> Repository -> PostgreSQL
     |           |
     |           +-- reglas de negocio y transacciones
     +-- rutas HTTP, DTO, validación y autorización declarativa
```

### Controller

Recibe la petición HTTP, valida el cuerpo y declara permisos con `@PreAuthorize`.

Ejemplos:

- `EventoController`: rutas `/api/eventos`.
- `EntradaController`: inventarios, emisión, listado y validación.

### Service

Contiene las reglas de negocio y delimita las transacciones.

- `EventoService` comprueba que solo el dueño edite su evento.
- `EntradaService` controla capacidad, duplicados, fecha, propiedad y cambio de estado.

### Repository

Usa Spring Data JPA para consultar y guardar entidades.

- `EventoRepository` administra conciertos.
- `InventarioRepository` administra cupos y bloquea una fila al emitir.
- `EntradaRepository` busca entradas propias y bloquea una entrada durante la validación.

### Entity

Representa el modelo persistido:

- `Evento` -> tabla `eventos`.
- `Inventario` -> tabla `inventarios`.
- `Entrada` -> tabla `entradas`.

### DTO

Define los datos que entran y salen de la API. Evita exponer directamente las entidades y agrega validaciones como `@NotBlank`, `@Future`, `@Min` y `@Max`.

## 7. Modelo de datos

### Base de Eventos

Tabla `eventos`:

| Campo | Significado |
|---|---|
| `id` | UUID del concierto. |
| `nombre` | Nombre público. |
| `artista` | Artista o banda. |
| `recinto` | Lugar físico. |
| `ciudad` | Ciudad del evento. |
| `descripcion` | Información adicional. |
| `fecha` | Instante del concierto en UTC. |
| `organizador_sub` | `sub` de Cognito del propietario. |
| `creado_en` | Momento de creación. |

### Base de Entradas

Tabla `inventarios`:

| Campo | Significado |
|---|---|
| `evento_id` | UUID del concierto y clave primaria. |
| `capacidad` | Cantidad máxima de entradas. |
| `emitidas` | Entradas entregadas hasta el momento. |

Tabla `entradas`:

| Campo | Significado |
|---|---|
| `id` | Identificador interno. |
| `evento_id` | Evento al que pertenece. |
| `titular_sub` | Identidad Cognito del titular. |
| `codigo` | Código único utilizado para validar. |
| `estado` | `EMITIDA` o `UTILIZADA`. |
| `emitida_en` | Momento de emisión. |
| `utilizada_en` | Momento de validación, inicialmente nulo. |

Restricciones importantes:

- `UNIQUE(evento_id, titular_sub)` impide dos entradas para la misma persona y evento.
- `codigo` es único.
- `emitidas` nunca puede superar `capacidad`.
- Las migraciones Flyway crean las tablas; Hibernate usa `ddl-auto: validate`, por lo que valida el esquema pero no lo modifica improvisadamente.

## 8. Flujo de autenticación

RiffPass usa Authorization Code Grant con PKCE:

1. React llama `signInWithRedirect()` de Amplify.
2. El navegador abre el Managed Login de Cognito.
3. El usuario inicia sesión.
4. Cognito redirige a la callback permitida.
5. Amplify intercambia el código por tokens.
6. React lee la sesión con `fetchAuthSession()`.
7. `apiFetch` adjunta el access token a cada petición.

PKCE protege el intercambio del código sin necesitar un client secret. Esto es apropiado para una SPA, porque cualquier secreto incluido en JavaScript sería visible para el usuario.

### Tipos de token

| Token | Uso en RiffPass |
|---|---|
| ID token | Datos de identidad, por ejemplo email. No autoriza la API. |
| Access token | Se envía a la API; contiene scopes, grupos y datos necesarios para autorización. |
| Refresh token | Amplify lo utiliza para renovar la sesión. No se envía como Bearer al backend. |

La API acepta únicamente access tokens con `token_use=access`.

## 9. Autenticación y autorización no son lo mismo

- **Autenticación:** comprobar quién es el usuario y si su token es válido.
- **Autorización:** decidir si ese usuario puede ejecutar una operación específica.

RiffPass combina tres niveles de autorización:

### Scope

Indica qué clase de operación permite el token:

- `resource-server-riffpass/eventos-read`
- `resource-server-riffpass/eventos-write`
- `resource-server-riffpass/entradas-read`
- `resource-server-riffpass/entradas-write`

### Rol o grupo

Indica el tipo de usuario:

- `ASISTENTE`
- `ORGANIZADOR`

### Propiedad

Comprueba si el recurso pertenece a ese usuario. El propietario se compara mediante el claim estable `sub`.

Ejemplo: tener `eventos-write` no basta para editar cualquier concierto. El backend también exige rol `ORGANIZADOR` y que el `sub` coincida con `organizador_sub`.

## 10. Doble validación del JWT

El token se valida en dos lugares:

### API Gateway

- Usa Cognito Authorizer.
- Rechaza peticiones sin una sesión válida.
- Exige el scope configurado en cada método.

### Cada microservicio

Spring Security vuelve a comprobar:

- Firma RS256 con las claves JWKS de Cognito.
- `iss` o issuer correcto.
- Fechas de emisión y vencimiento.
- `token_use=access`.
- `client_id` del App Client esperado.
- `sub` presente.
- `aud` opcional si se configura resource binding.
- Scopes, grupo y propiedad requeridos por la operación.

La segunda validación evita confiar únicamente en la red o en Gateway. Si alguien consiguiera llegar directamente al backend, todavía necesitaría un token correcto.

## 11. Cómo se convierten claims en permisos Spring

`SecurityConfig` hace dos conversiones:

- Los scopes del claim `scope` se convierten en autoridades `SCOPE_<scope>`.
- Los grupos de `cognito:groups` se convierten en `ROLE_ASISTENTE` o `ROLE_ORGANIZADOR`.

Por eso funciona una expresión como:

```java
@PreAuthorize("hasRole('ORGANIZADOR') and hasAuthority('SCOPE_resource-server-riffpass/eventos-write')")
```

El frontend también lee los grupos para mostrar la navegación correcta, pero ocultar un botón no es una medida de seguridad. La comprobación definitiva ocurre en el backend.

## 12. Endpoints y permisos

Todas las rutas de negocio requieren `Authorization: Bearer <ACCESS_TOKEN>`.

| Método y ruta | Scope backend | Regla adicional |
|---|---|---|
| `GET /api/eventos` | `eventos-read` | Usuario autenticado. |
| `GET /api/eventos/{id}` | `eventos-read` | Usuario autenticado. |
| `POST /api/eventos` | `eventos-write` | ORGANIZADOR. |
| `PUT /api/eventos/{id}` | `eventos-write` | ORGANIZADOR propietario. |
| `POST /api/eventos/{id}/inventario` | `entradas-write` + `eventos-read` | ORGANIZADOR propietario. |
| `GET /api/eventos/{id}/inventario` | `entradas-read` | Usuario autenticado. |
| `POST /api/eventos/{id}/entradas` | `entradas-write` + `eventos-read` | ASISTENTE u ORGANIZADOR. |
| `GET /api/entradas/mias` | `entradas-read` | Filtra por el `sub` autenticado. |
| `POST /api/entradas/validar` | `entradas-write` + `eventos-read` | ORGANIZADOR propietario del evento. |

`GET /actuator/health` es público dentro de cada servicio para supervisión. No se publica como endpoint de negocio en Gateway.

## 13. Recorrido: crear un concierto

1. React muestra `/eventos/nuevo` solamente a un organizador.
2. `apiFetch` obtiene el access token y envía el formulario.
3. API Gateway valida Cognito y `eventos-write`.
4. `EventoController` exige además `ROLE_ORGANIZADOR`.
5. El DTO exige campos válidos y una fecha futura.
6. `EventoService` crea el evento.
7. El propietario se toma de `jwt.getSubject()`, no del cuerpo enviado por el navegador.
8. PostgreSQL guarda el concierto.
9. La API responde `201 Created`.

Tomar el propietario desde el token evita que el cliente suplante a otro usuario.

## 14. Recorrido: habilitar cupos

1. El organizador envía una capacidad entre 1 y 100000.
2. Entradas consulta el evento llamando a Eventos con el mismo Bearer token.
3. Eventos vuelve a validar el JWT y el scope `eventos-read`.
4. Entradas comprueba que el `sub` sea el propietario y que el concierto sea futuro.
5. Inserta el inventario con un `INSERT` estricto.
6. Si ya existía, responde `409 Conflict`; nunca reinicia la cantidad emitida.

## 15. Recorrido: obtener una entrada

1. El usuario solicita la entrada sin enviar su identificador.
2. Entradas toma el titular desde `sub`.
3. Consulta el evento y verifica que todavía no haya comenzado.
4. Bloquea la fila del inventario con `PESSIMISTIC_WRITE`.
5. Comprueba que el usuario no tenga una entrada previa.
6. Comprueba que queden cupos.
7. Incrementa `emitidas` y crea la entrada dentro de la misma transacción.
8. Devuelve `201 Created` con un código UUID.

### Cómo evita la sobreventa

El bloqueo pesimista hace que dos solicitudes concurrentes no modifiquen al mismo tiempo el último cupo. Una espera a la otra; cuando continúa, vuelve a observar el estado actualizado. La restricción de base de datos agrega una segunda protección contra duplicados.

## 16. Recorrido: validar una entrada

1. El organizador envía el código.
2. Entradas bloquea la fila correspondiente.
3. Consulta Eventos para conocer el propietario.
4. Compara el `sub` del organizador con el propietario real.
5. Comprueba que el estado todavía sea `EMITIDA`.
6. Cambia el estado a `UTILIZADA` y registra `utilizada_en`.
7. Una segunda validación responde `409 Conflict`.

El bloqueo también garantiza que dos dispositivos no validen exitosamente el mismo código de manera simultánea.

## 17. Frontend React

### Inicio de la aplicación

`main.jsx` ejecuta tres tareas principales:

1. Configura Amplify.
2. Monta `AuthProvider`, fuente global de la sesión.
3. Monta `BrowserRouter` para las rutas.

### Rutas

| Ruta | Vista | Acceso |
|---|---|---|
| `/login` | Inicio de sesión. | Público. |
| `/eventos` | Catálogo. | Autenticado. |
| `/eventos/:id` | Detalle, cupos y acciones. | Autenticado. |
| `/eventos/nuevo` | Crear concierto. | ORGANIZADOR. |
| `/mis-entradas` | Entradas propias. | Autenticado. |
| `/validar` | Validación en puerta. | ORGANIZADOR. |

`ProtectedRoute` redirige al login si no hay sesión y bloquea las vistas exclusivas del organizador.

### Estado de autenticación

`AuthContext` mantiene:

- `loading`: todavía se revisa la sesión.
- `authenticated`: existe una sesión válida.
- `unauthenticated`: debe iniciar sesión.
- Claims del usuario.
- Modo preview para desarrollo.

También escucha eventos de Amplify como login, logout y renovación de token.

### `apiFetch`

Es el interceptor común para todas las llamadas:

1. Obtiene el access token.
2. Agrega `Authorization: Bearer ...`.
3. Agrega `Content-Type` cuando hay cuerpo JSON.
4. Si recibe `401`, fuerza una renovación y reintenta una vez.
5. Si vuelve a recibir `401`, cierra la sesión.
6. Convierte errores `application/problem+json` en mensajes para la interfaz.

Esto evita repetir la lógica de autenticación en cada página.

### Modo de vista previa

En desarrollo se pueden simular ambos roles con datos en memoria. Sirve para trabajar la apariencia sin AWS, pero no demuestra seguridad ni reemplaza las pruebas reales.

## 18. Vercel y el rewrite de producción

El archivo `frontend/vercel.json` tiene dos reglas:

1. `/api/:path*` se reenvía al stage `test` de API Gateway.
2. Las demás rutas sirven `index.html` para que `BrowserRouter` pueda recargarse sin un 404.

El proxy hace que el navegador vea frontend y API bajo el mismo origen `riffpass-ep1.vercel.app`. Vercel no reemplaza API Gateway: solo reenvía las llamadas.

La URL pública es:

```text
https://riffpass-ep1.vercel.app/
```

## 19. Despliegue del backend

En EC2, systemd ejecuta:

- `riffpass@eventos` -> JAR de Eventos.
- `riffpass@entradas` -> JAR de Entradas.

Cada proceso:

- Usa un usuario de sistema sin login.
- Lee variables desde `/etc/riffpass/<servicio>.env`.
- Se reinicia automáticamente ante fallos.
- Tiene memoria Java limitada.
- No guarda credenciales en el repositorio.

Los JAR se ubican en `/opt/riffpass`. Las contraseñas y URLs reales permanecen bajo `/etc/riffpass` con permisos restringidos.

En AWS se usa el contexto `/test` porque API Gateway incluye el nombre del stage en la ruta reenviada. Entradas se comunica internamente con Eventos por `127.0.0.1:8081/test`.

## 20. PostgreSQL, Flyway y conexión segura

- Existe una instancia RDS PostgreSQL con dos bases y usuarios separados.
- Eventos no puede escribir las tablas de Entradas y viceversa.
- Flyway ejecuta las migraciones versionadas al iniciar.
- JPA valida que las entidades coincidan con el esquema.
- La conexión usa TLS y el certificado global de RDS.
- RDS no está abierto públicamente; acepta tráfico según los security groups definidos.

Separar bases reduce el acoplamiento. Compartir una misma instancia RDS ahorra recursos en el laboratorio, pero mantiene una separación lógica por servicio.

## 21. Códigos HTTP que debes saber

| Código | Significado en el proyecto |
|---|---|
| `200 OK` | Lectura, edición o validación correcta. |
| `201 Created` | Evento, inventario o entrada creados. |
| `400 Bad Request` | Campos, JSON, UUID o paginación inválidos. |
| `401 Unauthorized` | Falta token o el JWT no es válido. |
| `403 Forbidden` | Token válido, pero sin scope, rol o propiedad. |
| `404 Not Found` | Evento, inventario o entrada inexistente. |
| `409 Conflict` | Duplicado, sin cupos, inventario abierto o entrada utilizada. |
| `502 Bad Gateway` | Eventos respondió de una forma inesperada. |
| `503 Service Unavailable` | Entradas no pudo comunicarse con Eventos. |

Diferencia clave:

- `401`: no se pudo aceptar la identidad/token.
- `403`: la identidad es válida, pero no tiene permiso.

## 22. Manejo de errores

`ApiErrors` centraliza excepciones con `@RestControllerAdvice` y responde usando Problem Details:

```json
{
  "title": "Forbidden",
  "status": 403,
  "detail": "No tienes permiso para realizar esta operación."
}
```

La aplicación no devuelve stack traces ni contraseñas. Las validaciones de campos pueden agregar una lista `errors`.

API Gateway puede rechazar una petición antes de que llegue al backend; por eso su cuerpo de error puede ser diferente aunque el código HTTP sea correcto.

## 23. Pruebas

### Pruebas Java

`mvnw clean verify` ejecuta 23 pruebas que cubren:

- Token correcto.
- Firma falsificada.
- Token vencido.
- Issuer o cliente incorrectos.
- Rechazo de ID token.
- Roles y scopes.
- Propiedad de eventos.
- Validación de DTO.
- CORS y health.
- Reenvío del Bearer token entre servicios.
- Entrada duplicada.
- Validación única.
- Sobreventa concurrente.
- Validación concurrente.
- Apertura concurrente de inventario.

### Smoke test

Levanta ambos JAR, genera JWT locales firmados, realiza llamadas HTTP y reinicia Entradas para comprobar persistencia.

### Postman real

La colección ejecutó 13 casos contra AWS y verificó respuestas exitosas y rechazos esperados, incluidos `401`, `403` y `409`.

### Frontend real

Se verificaron login, logout, navegación y consumo de API con usuarios `ASISTENTE` y `ORGANIZADOR`, tanto localmente como desde la URL pública.

## 24. Variables de entorno importantes

### Frontend

- `VITE_COGNITO_USER_POOL_ID`
- `VITE_COGNITO_APP_CLIENT_ID`
- `VITE_COGNITO_DOMAIN`
- `VITE_OAUTH_SCOPES`
- `VITE_REDIRECT_SIGNIN`
- `VITE_REDIRECT_SIGNOUT`
- `VITE_API_BASE_URL`

Los identificadores públicos de Cognito no son contraseñas. Los tokens y credenciales AWS sí deben protegerse.

### Backend

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `COGNITO_ISSUER`, `COGNITO_CLIENT_ID`
- `COGNITO_AUDIENCE`, si se usa resource binding
- `CORS_ALLOWED_ORIGINS`
- `EVENTOS_BASE_URL`, solo Entradas
- `PORT`
- `SERVER_SERVLET_CONTEXT_PATH`

Nunca se deben subir passwords, access keys, session tokens, JWT ni archivos `.pem`.

## 25. Mapa de archivos para estudiar

| Archivo o carpeta | Qué aprender allí |
|---|---|
| `frontend/src/App.jsx` | Rutas y protección por rol. |
| `frontend/src/amplifyConfig.js` | Configuración Cognito/OAuth. |
| `frontend/src/auth/AuthContext.jsx` | Estado global de sesión. |
| `frontend/src/auth/session.js` | Lectura de tokens, grupos y scopes. |
| `frontend/src/api/apiFetch.js` | Bearer token, refresh y errores. |
| `frontend/src/api/eventos.js` | Operaciones del frontend sobre eventos. |
| `frontend/src/api/entradas.js` | Operaciones sobre entradas. |
| `frontend/vercel.json` | Proxy de API y fallback SPA. |
| `backend/riffpass-common/.../SecurityConfig.java` | Cadena de filtros y conversión de claims. |
| `backend/riffpass-common/.../CognitoTokenValidator.java` | Claims obligatorios del JWT. |
| `backend/riffpass-eventos-service/.../EventoController.java` | Rutas y permisos de Eventos. |
| `backend/riffpass-eventos-service/.../EventoService.java` | Reglas y propiedad del concierto. |
| `backend/riffpass-entradas-service/.../EntradaController.java` | Rutas y permisos de Entradas. |
| `backend/riffpass-entradas-service/.../EntradaService.java` | Cupos, emisión, concurrencia y validación. |
| `backend/riffpass-entradas-service/.../EventoClient.java` | Comunicación segura entre servicios. |
| `backend/**/db/migration/` | Esquema SQL versionado. |
| `backend/docs/API.md` | Contrato exacto de endpoints. |
| `backend/docs/AWS_PASO_A_PASO.md` | Construcción de la infraestructura. |
| `docs/CUMPLIMIENTO_EP1.md` | Relación entre pauta y evidencia. |

## 26. Decisiones técnicas que debes poder defender

### ¿Por qué access token y no ID token?

Porque los scopes personalizados de la API están en el access token. El ID token describe identidad, pero no es el token elegido para autorizar estos endpoints.

### ¿Por qué Managed Login con `signInWithRedirect()`?

Porque el flujo OAuth Authorization Code + PKCE permite solicitar los custom scopes que exige el backend.

### ¿Por qué no hay un servicio propio de usuarios?

Porque Cognito administra registro, credenciales, sesiones y emisión de JWT. Guardar contraseñas en RiffPass duplicaría responsabilidad y aumentaría el riesgo.

### ¿Por qué se usa `sub`?

Porque es el identificador estable del usuario dentro del User Pool. No se confía en un id enviado por el navegador.

### ¿Por qué VPC Link y ALB interno?

Para que los puertos 8081 y 8082 no estén abiertos a Internet. La entrada pública controlada es API Gateway por HTTPS.

### ¿Por qué dos bases?

Cada microservicio es dueño de sus datos. Esto evita acoplamiento mediante tablas compartidas.

### ¿Por qué bloqueo pesimista?

Para serializar operaciones críticas sobre el último cupo o la misma entrada y mantener consistencia incluso con varias instancias del servicio.

### ¿Por qué Flyway más `ddl-auto: validate`?

Flyway conserva un historial explícito del esquema. Hibernate solamente confirma que el código y las tablas coinciden.

## 27. Riesgo pendiente de la pauta

La pauta nombra expresamente **AWS Amplify Authenticator**. El proyecto utiliza la biblioteca Amplify Auth, pero el login visible es Cognito Managed Login mediante `signInWithRedirect()`, no el componente visual `<Authenticator>` de `@aws-amplify/ui-react`.

La solución funciona correctamente y conserva los custom scopes, pero no se debe afirmar que renderiza `<Authenticator>`. Para cerrar este punto hay dos caminos:

1. Confirmar con el docente que Amplify Auth + Managed Login satisface el criterio.
2. Integrar el componente solicitado sin perder el flujo OAuth y los scopes.

Este es un riesgo de interpretación de la rúbrica, no una falla del login desplegado.

## 28. Guion breve para la presentación

### Introducción

> RiffPass es una plataforma de entradas gratuitas para conciertos con dos roles. El asistente obtiene y consulta sus entradas; el organizador publica eventos, habilita cupos y valida accesos.

### Arquitectura

> El frontend React está en Vercel. Cognito autentica mediante OAuth con PKCE. Las llamadas pasan por API Gateway, un VPC Link y un ALB interno hacia dos microservicios Spring Boot en EC2. Cada servicio valida nuevamente el JWT y persiste en una base PostgreSQL propia dentro de RDS.

### Seguridad

> No confiamos solo en ocultar botones. Combinamos scopes, grupos Cognito y propiedad mediante el claim `sub`. Gateway valida antes de entrar a la VPC y cada backend verifica firma, issuer, vigencia, tipo de token y cliente esperado.

### Consistencia

> Para impedir sobreventa, Entradas bloquea la fila del inventario dentro de una transacción. Para impedir doble uso, bloquea la entrada al validarla. PostgreSQL también impone restricciones únicas.

### Demostración sugerida

1. Mostrar la URL pública.
2. Entrar como ORGANIZADOR.
3. Crear un concierto futuro.
4. Habilitar cupos.
5. Cerrar sesión.
6. Entrar como ASISTENTE.
7. Obtener la entrada y verla en “Mis entradas”.
8. Copiar su código.
9. Volver como ORGANIZADOR.
10. Validar el código.
11. Intentar validarlo otra vez y mostrar `409`.

## 29. Preguntas probables del profesor

### ¿Dónde se valida el JWT?

En API Gateway mediante Cognito Authorizer y nuevamente en cada microservicio mediante Spring Security y JWKS.

### ¿Qué pasa si un asistente llama manualmente a crear evento?

Aunque el frontend oculte el botón, el backend exige `ROLE_ORGANIZADOR`; responde `403`.

### ¿Cómo se evita que un organizador edite eventos ajenos?

El servicio compara el `sub` validado del JWT con `organizador_sub` almacenado.

### ¿Cómo sabe Entradas quién es dueño del evento?

Consulta Eventos por HTTP y reenvía el access token original. No consulta directamente la base de Eventos.

### ¿Qué ocurre si Eventos está caído?

Las operaciones que necesitan verificar evento o propietario fallan con `503`. Consultar las entradas propias todavía puede funcionar porque usa únicamente la base de Entradas.

### ¿Qué impide emitir más entradas que la capacidad?

Una transacción con bloqueo pesimista sobre el inventario y restricciones de PostgreSQL.

### ¿Qué diferencia hay entre scope y rol?

El scope permite una categoría de operación; el rol representa el tipo de usuario. RiffPass exige ambos cuando corresponde.

### ¿Dónde están las contraseñas de los usuarios?

En Cognito. RiffPass nunca las recibe ni las almacena.

### ¿Por qué el App Client no tiene secret?

Porque una SPA no puede proteger un secreto incluido en JavaScript. Se usa PKCE.

### ¿Qué persiste si se reinicia EC2?

Los datos permanecen en RDS. Los procesos Java pueden reiniciarse sin perder conciertos, inventarios ni entradas.

### ¿Vercel reemplaza API Gateway?

No. Vercel publica React y actúa como proxy para `/api`; API Gateway sigue siendo la puerta de entrada protegida al backend.

## 30. Autoevaluación rápida

Intenta responder sin mirar la guía:

1. ¿Cuáles son los dos microservicios y qué datos controla cada uno?
2. ¿Por qué la API usa access token y no ID token?
3. ¿Qué diferencia hay entre `401` y `403`?
4. ¿Qué tres niveles de autorización combina RiffPass?
5. ¿Dónde se valida el JWT?
6. ¿Cómo se impide que un usuario suplante a otro al crear una entrada?
7. ¿Cómo se evita la sobreventa?
8. ¿Cómo se evita validar dos veces la misma entrada?
9. ¿Por qué Entradas llama a Eventos por HTTP?
10. ¿Qué función cumple VPC Link?
11. ¿Por qué RDS no debe ser público?
12. ¿Qué hace `apiFetch` ante un `401`?
13. ¿Qué diferencia hay entre Flyway y Hibernate `validate`?
14. ¿Qué parte de la pauta sigue teniendo riesgo de interpretación?
15. ¿Qué pasa con el backend cuando se apaga el Learner Lab?

## 31. Resumen para memorizar

```text
Caso: entradas gratuitas para conciertos.
Roles: ASISTENTE y ORGANIZADOR.
Frontend: React + Vite + Amplify Auth, publicado en Vercel.
Identidad: Cognito con Authorization Code + PKCE.
API: REST API Gateway con Cognito Authorizer y scopes.
Red privada: VPC Link V2 + ALB interno.
Backend: dos microservicios Spring Boot Java 21 en EC2.
Persistencia: PostgreSQL RDS, dos bases y Flyway.
Seguridad: firma + issuer + fechas + access token + client_id + scope + rol + propiedad.
Consistencia: transacciones, bloqueos pesimistas y restricciones únicas.
Pruebas: 23 Java, smoke test, Postman 13/13 y frontend con ambos roles.
Pendiente clave: confirmar la exigencia literal de Amplify UI Authenticator.
```

## 32. Documentos de referencia

- `backend/docs/API.md`: contrato exacto de la API.
- `backend/docs/AWS_PASO_A_PASO.md`: despliegue completo.
- `backend/README.md`: compilación y ejecución.
- `frontend/README.md`: autenticación y ejecución de React.
- `docs/CUMPLIMIENTO_EP1.md`: trazabilidad con la pauta.
- `CONTEXTO_PROYECTO.md`: decisiones y estado histórico del trabajo.

