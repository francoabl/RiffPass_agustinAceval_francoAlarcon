# RiffPass - Backend EP1 DSY1107

Backend de entradas gratuitas para conciertos, implementado con **Java 21, Spring Boot 3.5.16, Spring Security y PostgreSQL**. La identidad la emite Amazon Cognito. No hay un login ni contraseñas propios.

Los comandos de este documento se ejecutan tomando `backend/` como directorio de trabajo. Para una vista general, consulta el [README principal](../README.md).

## Empieza aquí

1. [Guía AWS paso a paso](docs/AWS_PASO_A_PASO.md): configura Cognito, RDS, EC2, API Gateway y Postman.
2. [Contrato de endpoints](docs/API.md): cuerpos, permisos y respuestas.
3. [Matriz de cumplimiento EP1](../docs/CUMPLIMIENTO_EP1.md): estado y evidencia de cada punto de la pauta.
4. [Contexto académico](../CONTEXTO_PROYECTO.md): requisitos del profesor y decisiones.
5. [Colección Postman](postman/RiffPass.postman_collection.json): importar y completar tokens locales.

## Estructura

| Carpeta | Función |
|---|---|
| riffpass-eventos-service | Microservicio ejecutable: conciertos, organizador y edición de información |
| riffpass-entradas-service | Microservicio ejecutable: inventario, emisión y validación de entradas |
| riffpass-common | Biblioteca Java compartida de seguridad y errores; NO es un tercer microservicio |
| deploy | Configuración systemd, variables de ejemplo, SQL e importación API Gateway |
| scripts | Generación de configuración Gateway y prueba HTTP de los JAR |
| postman | Solicitudes listas para usar |

Los dos servicios tienen base de datos y proceso propios. Se construyen juntos para compartir las mismas reglas JWT; cada JAR incluye la biblioteca y se ejecuta por separado.

## Compilar y probar

Necesitas JDK 21 o 25, acceso a Maven Central y JAVA_HOME apuntando al JDK. El Maven Wrapper incluido descarga Maven la primera vez.

Windows PowerShell, desde esta carpeta:

~~~powershell
.\mvnw.cmd clean verify
~~~

Linux/macOS:

~~~bash
chmod +x mvnw
./mvnw clean verify
~~~

Resultados:

- `riffpass-eventos-service/target/riffpass-eventos-service-1.0.0.jar`
- `riffpass-entradas-service/target/riffpass-entradas-service-1.0.0.jar`

No ejecutar los archivos `.jar.original`. No subir `target/` ni credenciales a GitHub.

## Ejecutar localmente con Cognito real

Después de crear Cognito según la guía, abre dos terminales dentro de `backend/`. En **cada terminal**, configura estos valores reales:

~~~powershell
$env:COGNITO_ISSUER = "https://cognito-idp.us-east-1.amazonaws.com/TU_POOL_ID"
$env:COGNITO_CLIENT_ID = "TU_APP_CLIENT_ID"
~~~

Terminal 1:

~~~powershell
java -jar riffpass-eventos-service/target/riffpass-eventos-service-1.0.0.jar --spring.profiles.active=local
~~~

Terminal 2:

~~~powershell
java -jar riffpass-entradas-service/target/riffpass-entradas-service-1.0.0.jar --spring.profiles.active=local
~~~

Este perfil guarda H2 en `./data/` y **mantiene la seguridad Cognito**. No es la base cloud de la evaluación. Localmente las URLs son `http://localhost:8081/api/eventos` y `http://localhost:8082/api/entradas/mias`; una petición sin token responde 401. `GET /actuator/health` es público para supervisión.

Para PostgreSQL usa el perfil normal y define `DB_URL`, `DB_USERNAME` y `DB_PASSWORD`. Flyway crea las tablas y Hibernate valida que correspondan al modelo. En AWS la guía configura además `SERVER_SERVLET_CONTEXT_PATH=/test`, porque la integración privada REST reenvía el stage.

## Qué implementa

- Crear y consultar conciertos; editar nombre/descripción solo como organizador propietario.
- Habilitar una cantidad fija de cupos, una sola vez por evento.
- Emitir una entrada por usuario y concierto, sin sobreventa concurrente.
- Listar únicamente entradas propias.
- Validar una entrada una sola vez, solo por el organizador de su evento.
- Scopes + grupos Cognito + comprobación de propiedad usando `sub`.
- Validación de firma RS256/JWKS, emisor, fechas, tipo access y App Client.
- `401`, `403`, `404`, `409`, validación de JSON y respuestas de error sin secretos.
- CORS configurable, paginación limitada y timeouts entre servicios.

El cliente que llama al servicio Entradas para abrir cupos, emitir o validar necesita también `eventos-read`: Entradas reenvía su mismo access token a Eventos. No se realiza comunicación anónima entre servicios.

No se implementaron pagos, QR, cancelación, reprogramación ni modificación de capacidad: esas operaciones requieren reglas adicionales. Los conciertos quedan publicados al crearse, y dejan de emitir entradas cuando llega su fecha.

## Pruebas

`mvnw verify` ejecuta 23 pruebas: firma y claims JWT, roles/scopes, propiedad, validación, persistencia JPA, comunicación HTTP, emisión y validación concurrentes. Las pruebas de repositorios usan H2 en modo PostgreSQL; no sustituyen las pruebas con RDS real.

Prueba adicional con ambos JAR levantados, JWT firmados en memoria y HTTP real:

~~~bash
python -m pip install cryptography
python scripts/smoke_test.py
~~~

La prueba genera su propio emisor local temporal, ejecuta el flujo y reinicia Entradas para comprobar persistencia. No usa ni crea recursos AWS y no incorpora una puerta de acceso de prueba al backend.

## Despliegue previsto

`Postman/React -> HTTPS API Gateway REST -> Cognito Authorizer -> VPC Link V2 -> ALB interno -> EC2 -> RDS`.

Se propone una EC2 con dos procesos, dos target groups y dos bases independientes dentro de una instancia RDS PostgreSQL. Se puede separar en dos EC2 sin cambiar el código; habrá que ajustar red y `EVENTOS_BASE_URL`.

La integración privada es una adaptación de despliegue para no exponer HTTP con tokens en Internet. Añade recursos y permisos respecto del laboratorio del profesor. La guía detalla esos recursos y el coste debe revisarse en la cuenta académica.

El código está preparado; Cognito, RDS y el despliegue real se validan cuando completes los pasos de AWS. Este repositorio no contiene recursos cloud creados ni credenciales.

Compatibilidad Java verificada en [Spring Boot 3.5](https://docs.spring.io/spring-boot/3.5/system-requirements.html).
