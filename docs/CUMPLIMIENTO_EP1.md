# Matriz de cumplimiento EP1 DSY1107

Esta matriz traduce el encargo y la rúbrica de `EP1_DSY1107_Estudiante_AWS (1).md` a entregables y evidencias concretas de RiffPass. Su objetivo es evitar omisiones; la calificación final corresponde al docente.

Estados:

- **Implementado:** existe código y prueba local.
- **Preparado:** existe configuración o guía, pero falta comprobarla en AWS.
- **Pendiente:** todavía debe construirse o ejecutarse.
- **Verificado AWS:** solo se marca después de una prueba real guardando evidencia.

## Estado global

| Área | Ponderación de la pauta | Estado actual |
|---|---:|---|
| React + Amplify Authenticator + tokens | 60% | Implementado localmente (codigo); Cognito real y verificacion de scopes pendientes |
| Backend + validación JWT Cognito | 40% | Implementado y probado localmente; despliegue AWS pendiente |

La EP1 completa aún no está al 100%. El backend está preparado para buscar el nivel máximo del segundo indicador, pero deben completarse AWS, frontend, GitHub y presentación.

## Requisitos formales del encargo

| Requisito | Implementación RiffPass | Estado | Evidencia necesaria |
|---|---|---|---|
| Varios microservicios Java Spring Boot | `riffpass-eventos-service` y `riffpass-entradas-service` | Implementado | JAR separados, procesos y endpoints de ambos |
| Backend compila y sigue buenas prácticas | Maven multi-módulo, capas, DTO, validación, transacciones, errores | Implementado | `mvnw clean verify` con BUILD SUCCESS |
| Pruebas básicas | 23 pruebas Java y smoke test de dos JAR | Implementado | Salida de Maven y `SMOKE OK` |
| Backend desplegado en EC2 | Dos procesos systemd en una EC2, con posibilidad de separar instancias | Preparado | `systemctl status`, health y logs sin secretos |
| Base de datos cloud | PostgreSQL RDS, dos bases/usuarios, entidades, repositorios, propiedades y Flyway | Preparado | RDS conectado, tablas creadas y persistencia tras reinicio |
| Backend protegido por API Gateway | REST API Regional, Cognito Authorizer, scopes y VPC Link V2 | Preparado | Invoke URL con 401/éxito y configuración de métodos |
| Filtro JWT en cada backend | Spring Security Resource Server genera la cadena de filtros en ambos JAR | Implementado | Código, prueba directa a cada servicio y prueba por Gateway |
| `.gitignore` correcto | Ignora target, datos, secretos, logs, IDE y configuración generada | Implementado | `git status` sin artefactos ni credenciales |
| Código entregado vía GitHub | Repositorio publicado (privado) con backend y frontend | Implementado | URL GitHub accesible al docente |
| Enlaces enviados por AVA y correo | Acción final del estudiante | Pendiente | Confirmación de entrega dentro del plazo |

`riffpass-common` es una biblioteca incluida dentro de ambos JAR; no se presenta como un tercer microservicio.

## Indicador backend - 40%

### Objetivo de nivel “Muy buen desempeño”

| Elemento textual de la rúbrica | Cómo se cumple | Estado y prueba |
|---|---|---|
| Validar issuer | `JwtValidators.createDefaultWithIssuer(COGNITO_ISSUER)` | Implementado; prueba rechaza otro issuer. Falta token Cognito real |
| Validar audience correctamente | Para access token Cognito se valida el App Client esperado mediante `client_id`; si el pool emite `aud` por resource binding, `COGNITO_AUDIENCE` permite exigirlo adicionalmente | Implementado y probado en ambas variantes; documentar claims reales para el profesor |
| Verificar firma | Nimbus valida RS256 contra `issuer/.well-known/jwks.json` y `kid` | Implementado; prueba rechaza firma falsificada. Falta JWKS Cognito real |
| Verificar vigencia | Validadores estándar verifican `exp` y el validador exige `iat`/`exp` | Implementado; prueba rechaza token vencido |
| Autorizar por rol | Grupos Cognito `ASISTENTE` y `ORGANIZADOR` se convierten a roles; se comprueba además la propiedad por `sub` | Implementado; pruebas de rol y propietario |
| Autorizar por scope | Métodos usan `resource-server-riffpass/...` y API Gateway exige el scope principal | Implementado en backend y plantilla; Gateway pendiente |
| Solo permitir endpoints con token válido | Todas las rutas de negocio requieren autenticación; solo health y OPTIONS son públicas | Implementado; prueba sin token 401 |
| Códigos adecuados | 400, 401, 403, 404, 409, 502 y 503 con Problem Details | Implementado; colección Postman preparada |

### Evidencias obligatorias en AWS

- [ ] Access token real contiene `token_use=access`, `iss`, `client_id`, `exp`, `scope`, `sub` y `cognito:groups`.
- [ ] Petición sin token a API Gateway responde 401.
- [ ] Token alterado o vencido es rechazado.
- [ ] Token válido sin el scope del método es rechazado por API Gateway.
- [ ] Backend directo también rechaza token incorrecto, demostrando doble validación.
- [ ] Asistente con scope de escritura no puede crear un evento: 403 por rol.
- [ ] Organizador no puede editar ni validar recursos de otro organizador: 403.
- [ ] Entrada válida se utiliza una vez; segundo intento: 409.
- [ ] Reiniciar ambos servicios conserva datos en RDS.
- [ ] Entidades, repositorios, Flyway y propiedades apuntan a la base cloud real.

### Nota sobre audience en Cognito

La rúbrica usa el término `audience`. Debe explicarse con el tipo de token elegido:

- En ID token, el App Client aparece en `aud`.
- En access token usado para custom scopes, Cognito identifica el App Client mediante `client_id` y puede incluir un `aud` de recurso cuando se configura resource binding.
- RiffPass usa access tokens porque la guía del profesor exige access token para métodos con custom scopes. Por eso siempre valida `client_id`; además puede validar `aud` configurando `COGNITO_AUDIENCE` cuando el token real lo incluya.

No se debe enviar un ID token a los endpoints finales solo para obtener un claim `aud`: perderíamos el modelo de autorización por scopes enseñado por el profesor.

## Indicador frontend - 60%

Codigo implementado en `frontend/` (React + Vite + Amplify, login via Hosted UI). No probado aun contra un User Pool real ni contra API Gateway; estados de la tabla describen el requisito, no una verificacion en AWS:

| Elemento textual de la rúbrica | Implementación requerida | Evidencia final |
|---|---|---|
| React completo y modular | Vistas de catálogo, detalle, mis entradas y panel organizador | Build sin errores y navegación funcional |
| AWS Amplify Authenticator | Registro, confirmación, login y UI autenticada con Cognito | Demostración con usuario real |
| Inicio y cierre de sesión | La sesión habilita rutas y logout elimina acceso | Flujo en navegador |
| Todos los tokens necesarios | Obtener sesión de Cognito y utilizar access token correcto | Claims inspeccionados sin mostrar el JWT completo |
| Rutas protegidas | Bloquear catálogo privado/panel según sesión y rol | Acceso anónimo redirigido al login |
| `apiFetch` | Agregar `Authorization: Bearer`, manejar 401/403 y renovación mediante Amplify | Llamadas visibles en Network |
| Consumir API Gateway | Base URL del stage `test`, nunca los puertos EC2 desde React | Respuesta real del Gateway |
| Leer roles y scopes | `cognito:groups` y `scope` controlan interfaz, sin reemplazar seguridad backend | Usuario ve solo acciones permitidas |
| Vistas funcionales | Crear evento, habilitar cupos, obtener entrada, listar y validar | Recorrido completo |
| CORS correcto | Origen exacto del frontend y OPTIONS sin sesión | Navegador sin error CORS |

Punto crítico pendiente: verificar que el flujo usado por Amplify Authenticator obtenga un access token con los custom scopes configurados. Debe probarse con Cognito real; declarar scopes en código sin inspeccionar el token no constituye evidencia.

## Estructura de trabajo del profesor aplicada

| Secuencia enseñada | Aplicación en RiffPass | Estado |
|---|---|---|
| Construir backend funcional | Dos JAR y smoke test | Completo localmente |
| Probar primero el backend | Health, seguridad local y flujo entre servicios | Completo localmente |
| Crear Cognito User Pool y App Client público | `riffpass-pool`, `riffpass-web`, sin client secret | Pendiente en AWS |
| Configurar dominio, callback y Authorization Code + PKCE | Callback Postman exacta | Pendiente en AWS |
| Crear Resource Server y scope completo | Cuatro scopes bajo `resource-server-riffpass` | Pendiente en AWS |
| Obtener access token desde Postman | `openid email profile` + custom scopes | Pendiente en AWS |
| Inspeccionar JWT y claims | Distinguir decodificar de verificar | Pendiente con token real |
| Crear REST API y Cognito Authorizer | Token source `Authorization` | Plantilla lista; pendiente AWS |
| Proteger métodos con scopes | Scope completo en Method Request | Plantilla lista; pendiente AWS |
| Redeploy al stage `test` | Repetir tras cualquier cambio | Pendiente AWS |
| Probar 401, permiso incorrecto y 200/201 | Colección Postman de 13 solicitudes | Preparada; pendiente AWS |

La guía del profesor utiliza PokeAPI como backend didáctico. RiffPass conserva el mismo flujo de identidad y autorización, sustituyendo PokeAPI por los microservicios Spring Boot exigidos en la evaluación.

## Learner Lab y control de sesiones

- [ ] Iniciar Learner Lab y confirmar indicador verde.
- [ ] Mantener todos los recursos en la misma región.
- [ ] Renovar credenciales temporales solo cuando se use AWS CLI.
- [ ] No guardar `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` ni `AWS_SESSION_TOKEN`.
- [ ] Registrar IDs, ARN, endpoints y progreso antes del límite de cuatro horas.
- [ ] Revisar recursos existentes al comenzar una sesión, evitando duplicados.
- [ ] Revisar presupuesto disponible sin alterar requisitos de la pauta.

Los microservicios no dependen de credenciales AWS CLI: validan Cognito por JWKS y acceden a PostgreSQL con usuarios propios. La rotación de las credenciales del Learner Lab no obliga a recompilar los JAR.

## Checklist final antes de entregar

- [ ] `mvnw clean verify` termina en BUILD SUCCESS.
- [ ] Ambos microservicios corren en EC2 y responden health.
- [ ] RDS conserva eventos, inventarios y entradas.
- [ ] Cognito, API Gateway y backend rechazan los casos inválidos esperados.
- [ ] React compila y todas sus vistas funcionan.
- [ ] Amplify Authenticator, logout, rutas protegidas y `apiFetch` funcionan.
- [ ] Roles y scopes se leen desde claims reales.
- [ ] Flujo completo navegador -> Gateway -> EC2 -> RDS demostrado.
- [ ] Repositorios GitHub sin secretos ni artefactos generados.
- [ ] README permite reproducir ejecución y configuración.
- [ ] Capturas/evidencias no muestran JWT, passwords ni credenciales temporales.
- [ ] Enlaces enviados en AVA y al correo del docente.

Actualizar esta matriz marcando solo aquello que haya sido comprobado. No declarar el 100% basándose únicamente en código local.
