# RiffPass: paso a paso para AWS

Esta guía corresponde al código ubicado en `backend/`. Los comandos relativos se ejecutan desde esa carpeta. Sigue el recorrido del profesor: Cognito, Postman, REST API, authorizer, scopes y pruebas. Añade el despliegue de nuestros servicios Spring Boot y la base de datos que exige EP1.

**Primero compila; después realiza estos pasos en tu cuenta AWS. No se han creado recursos AWS automáticamente.** Los nombres propuestos son de RiffPass. Sustituye los textos `REEMPLAZAR`, `TU_...` y los IDs de ejemplo.

### Condiciones del AWS Academy Learner Lab

- El usuario confirmó acceso a Cognito, API Gateway, EC2, RDS y los recursos adicionales de esta arquitectura.
- El presupuesto disponible es USD 50. La prioridad es cumplir la pauta y seguir la estructura del profesor, no eliminar componentes evaluados para reducir el consumo.
- Cada sesión del Learner Lab dura cuatro horas. Inicia el laboratorio, espera el indicador verde y verifica la región antes de continuar.
- Las credenciales de AWS CLI son temporales y cambian al reiniciar el laboratorio. Si usas CLI, vuelve a copiar `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` y `AWS_SESSION_TOKEN` desde AWS Details al comenzar cada sesión.
- No guardes esas credenciales en archivos del proyecto, EC2, Postman ni GitHub. Este procedimiento usa la consola para la configuración principal y SSH/SCP con la key pair para EC2.
- Antes de cerrar una sesión, anota IDs, ARN, endpoints, stage e Invoke URL. Al volver, comprueba el estado de los recursos existentes antes de crear otros con el mismo nombre.
- La rotación de credenciales del CLI no obliga a cambiar `COGNITO_ISSUER`, `COGNITO_CLIENT_ID`, usuarios PostgreSQL o configuración de los JAR.

La lista control de toda la evaluación está en `../../docs/CUMPLIMIENTO_EP1.md` respecto de este archivo. Un backend local correcto no equivale por sí solo al 100%: se necesitan pruebas reales en AWS y completar el frontend que pondera 60%.

## 0. Qué vas a tener

~~~text
Postman (después React)
  | HTTPS + access token de Cognito
  v
API Gateway REST /test + Cognito Authorizer + scope
  |
VPC Link V2
  |
ALB interno, listener HTTP 80
  |                         |
EC2 puerto 8081          EC2 puerto 8082
Eventos                  Entradas
  |                         |
  +---- RDS PostgreSQL -----+
      2 bases / 2 usuarios
~~~

Una EC2 ejecuta dos JAR independientes. La biblioteca `riffpass-common` ya va dentro de ellos: no se despliega aparte. RDS contiene `riffpass_eventos` y `riffpass_entradas`, con credenciales distintas.

**Por qué ALB y VPC Link:** el laboratorio usa una API externa pública. Aquí necesitamos llegar a EC2; la conexión privada permite cerrar 8081/8082 a Internet y conservar API Gateway como entrada. Es una decisión de este proyecto, no un requisito textual del profesor. API Gateway soporta REST con ALB mediante VPC Link **V2**. [Referencia AWS](https://docs.aws.amazon.com/apigateway/latest/developerguide/private-integration.html).

Estos recursos pueden generar costes (EC2, RDS, ALB, IPv4 y API Gateway). Revisa presupuesto y permisos de tu cuenta académica antes de crearlos. No se presupone gratuidad. Si el laboratorio deniega ALB/VPC Link V2, conserva el código y pide al docente la topología permitida; no abras puertos públicos para compensarlo.

### Datos que debes anotar

| Dato | Dónde lo obtienes |
|---|---|
| Región | Selector de región de AWS; usar la misma en todos los servicios |
| VPC ID y dos subnet IDs | Consola VPC |
| User Pool ID y ARN | Cognito / Overview |
| App Client ID | Cognito / App clients |
| Dominio de Cognito | Branding / Domain |
| Endpoint RDS | RDS / Connectivity & security |
| EC2 ID, IP pública y privada | EC2 / Instances |
| ALB DNS y ARN | EC2 / Load Balancers |
| VPC Link V2 ID | API Gateway / VPC links |
| Invoke URL | API Gateway / Stages / test |

En los ejemplos se usa `us-east-1`. Si eliges otra región, ajusta todas las URLs. Nunca confundas el dominio de login con el issuer ni con el Invoke URL.

## 1. Compilar el backend en tu PC

Abre PowerShell en la carpeta del proyecto:

~~~powershell
java -version
.\mvnw.cmd clean verify
~~~

Debe terminar en `BUILD SUCCESS`. Las pruebas no necesitan una cuenta AWS.

Comprueba que existan:

~~~text
riffpass-eventos-service/target/riffpass-eventos-service-1.0.0.jar
riffpass-entradas-service/target/riffpass-entradas-service-1.0.0.jar
~~~

Si JAVA_HOME no está configurado, asígnalo al directorio de tu JDK (no a su carpeta bin). Java 21 es la versión objetivo; también se probó compilación con JDK 25. No necesitas instalar Maven aparte.

Conserva el proyecto completo: el POM padre y la biblioteca compartida son necesarios para compilar desde cero.

## 2. Crear Cognito

### 2.1 User Pool y App Client

1. Abre **Amazon Cognito > User pools > Create user pool**. Si la interfaz comienza por crear una aplicación, selecciona una aplicación **SPA / pública**.
2. Nombre del pool: `riffpass-pool`.
3. Configura inicio de sesión con email.
4. Habilita autorregistro y verificación del email.
5. Atributo requerido: email. No necesitas teléfono, dirección ni fecha de nacimiento.
6. Conserva una política de contraseña adecuada; el anexo usa la predeterminada.
7. El anexo desactiva MFA para simplificar la práctica. Sigue las restricciones de tu cuenta si exige MFA.
8. Configura envío de emails con Cognito para el laboratorio.
9. Nombre del App Client: `riffpass-web`.
10. **No generar Client Secret**.
11. Callback permitido: `https://oauth.pstmn.io/v1/callback`.
12. Crea los recursos y anota User Pool ID, ARN y App Client ID.

No necesitas Identity Pool ni claves IAM de usuario dentro de los microservicios para verificar los JWT.

### 2.2 Dominio y flujo OAuth

1. Dentro del pool, entra a **Branding > Domain**.
2. Crea un dominio Cognito con prefijo disponible, por ejemplo `riffpass-tuidentificador`.
3. Copia el dominio completo que muestra AWS.
4. En **Applications > App clients > riffpass-web > Login pages > Edit**, habilita el proveedor Cognito User Pool.
5. Habilita **Authorization code grant**.
6. Revisa el callback exacto de Postman.
7. Habilita `openid`, `email` y `profile`.
8. Guarda.

Las URLs quedan así:

~~~text
Auth URL:   https://TU_DOMINIO_COGNITO/oauth2/authorize
Token URL:  https://TU_DOMINIO_COGNITO/oauth2/token
Issuer:     https://cognito-idp.us-east-1.amazonaws.com/TU_USER_POOL_ID
~~~

El issuer usado por los archivos de ejemplo corresponde al formato de las guías. Copia el valor real del pool/configuración OIDC; no pegues el dominio de login en COGNITO_ISSUER.

### 2.3 Resource Server y cuatro scopes

1. En **Applications > Resource servers**, crea uno.
2. Nombre: `RiffPass API`.
3. Identifier: `resource-server-riffpass`.
4. Agrega estos scopes:

| Nombre | Descripción |
|---|---|
| eventos-read | Consultar conciertos |
| eventos-write | Crear o editar conciertos |
| entradas-read | Consultar cupos y entradas propias |
| entradas-write | Habilitar cupos, emitir y validar entradas |

5. Guarda.
6. Vuelve al App Client, **Login pages > Edit**.
7. Habilita los cuatro custom scopes y guarda.

Los valores completos son:

~~~text
resource-server-riffpass/eventos-read
resource-server-riffpass/eventos-write
resource-server-riffpass/entradas-read
resource-server-riffpass/entradas-write
~~~

Se deben escribir igual en Postman y API Gateway. Un scope disponible para la aplicación no transforma a una persona en organizador: los grupos y la propiedad se verifican en Spring Boot.

### 2.4 Usuarios de prueba y grupos

1. En el pool, abre **Groups** y crea `ASISTENTE` y `ORGANIZADOR`, respetando las mayúsculas.
2. Registra dos usuarios con emails que puedas verificar: uno será organizador y el otro asistente.
3. Puedes registrarlos desde el login administrado que abre Postman en el paso siguiente.
4. En **Users**, abre cada usuario y agrega el grupo correspondiente.
5. Para probar propiedad, puedes crear después un segundo organizador.
6. Después de cambiar grupos, obtiene tokens nuevos.

El autorregistro no asigna automáticamente `ASISTENTE`; en esta primera entrega lo asignas desde la consola. Tampoco asigna `ORGANIZADOR`. No habilites que el cliente elija por sí mismo ese rol.

## 3. Obtener tokens con Postman

Hazlo primero para el organizador y después para el asistente:

1. Crea una petición de trabajo en Postman.
2. Authorization > OAuth 2.0 > Get New Access Token.
3. Completa:

| Campo | Valor |
|---|---|
| Token Name | RiffPass Organizador o RiffPass Asistente |
| Grant Type | Authorization Code (With PKCE) |
| Callback URL | https://oauth.pstmn.io/v1/callback |
| Authorize using browser | Activado |
| Auth URL | https://TU_DOMINIO_COGNITO/oauth2/authorize |
| Access Token URL | https://TU_DOMINIO_COGNITO/oauth2/token |
| Client ID | Tu App Client ID |
| Client Secret | Vacío |
| Code Challenge Method | SHA-256 |
| Client Authentication | Send client credentials in body |

Scope, en una sola línea:

~~~text
openid email profile resource-server-riffpass/eventos-read resource-server-riffpass/eventos-write resource-server-riffpass/entradas-read resource-server-riffpass/entradas-write
~~~

4. Selecciona Get New Access Token, inicia sesión y completa la verificación de cuenta si se solicita.
5. Obtén el **access token**. El refresh token y el ID token tienen otra finalidad.
6. Comprueba localmente los claims: `token_use=access`, `client_id`, `iss`, `exp`, `scope`, `sub` y `cognito:groups`.
7. Repite con el otro usuario. Si el navegador conserva la sesión del organizador, cierra sesión en el login administrado o usa otro perfil de navegador. Confirma que los `sub` de ambos tokens sean diferentes.

La colección pide ambos tokens como variables **locales**. No los incluyas en capturas ni exportaciones compartidas.

Los scopes personalizados se solicitan por el flujo OAuth de las guías. Un login directo a la API de Cognito no obtiene automáticamente esos mismos scopes. Por eso la integración posterior de Amplify Authenticator debe revisarse expresamente. [Access tokens en AWS](https://docs.aws.amazon.com/cognito/latest/developerguide/amazon-cognito-user-pools-using-the-access-token.html).

**Comprobación opcional antes de AWS:** ejecuta los dos JAR localmente con el perfil `local`, como indica README, y usa los tokens reales desde Postman contra los puertos 8081/8082. Eso verifica el backend antes de agregar la red cloud.

## 4. Preparar VPC y grupos de seguridad

### 4.1 VPC

Puedes usar una VPC existente del laboratorio si tiene al menos dos subredes en zonas de disponibilidad distintas y salida a Internet para EC2.

Para esta guía se puede usar la VPC predeterminada y dos de sus subredes. El ALB será **interno** y RDS tendrá **Public access: No**, aunque las subredes tengan ruta al Internet Gateway. EC2 tendrá IP pública solo para SSH y salida a repositorios/Cognito.

Si no tienes VPC disponible, crea una con **VPC and more**:

- Dos zonas de disponibilidad.
- Dos subredes públicas.
- Para este laboratorio no necesitas NAT Gateway.
- Habilita DNS resolution y DNS hostnames.
- La tabla de rutas de las subredes públicas debe tener `0.0.0.0/0 -> Internet Gateway`.

No cambies una VPC compartida del curso sin conocer sus reglas; utiliza una nueva si corresponde y tienes permisos.

### 4.2 Security groups

En la misma VPC crea estos grupos. Primero crea los cuatro y luego agrega las referencias entre ellos:

| Grupo | Entradas (inbound) |
|---|---|
| riffpass-vpclink-sg | Ninguna |
| riffpass-alb-sg | TCP 80 desde riffpass-vpclink-sg; TCP 80 desde riffpass-ec2-sg para diagnóstico interno |
| riffpass-ec2-sg | TCP 22 desde tu IP pública /32; TCP 8081 y 8082 desde riffpass-alb-sg |
| riffpass-rds-sg | TCP 5432 desde riffpass-ec2-sg |

Para el laboratorio puedes conservar outbound predeterminado. La comunicación Entradas -> Eventos será por localhost, ya que ambos viven en la misma EC2.

No agregues 8081/8082 ni 5432 desde `0.0.0.0/0`. API Gateway no necesita esos puertos públicos en esta arquitectura. Si tu IP cambia, actualiza solo la regla SSH.

## 5. Crear RDS PostgreSQL

1. RDS > Databases > Create database > Standard create.
2. Motor: **PostgreSQL**, versión mayor 17 disponible en tu laboratorio.
3. Plantilla: Dev/Test o la permitida por tu cuenta.
4. Deployment: una instancia **Single-AZ** para esta práctica.
5. Identifier: `riffpass-db`.
6. Master username: `riffpass_admin`.
7. Define y guarda una contraseña propia; el backend no usará esta cuenta administradora.
8. Selecciona una clase pequeña permitida por el laboratorio y almacenamiento acorde a la práctica. Revisa el coste antes de crear.
9. Connectivity: misma VPC y DB subnet group con dos subredes de zonas distintas.
10. **Public access: No**.
11. Security group: `riffpass-rds-sg`.
12. Puerto 5432.
13. Base inicial: `postgres` o dejar el valor que ofrezca la consola. Crearemos las dos bases de aplicación con SQL.
14. Evita habilitar complementos de coste que no necesites para la práctica; conserva los requisitos obligatorios de tu cuenta.
15. Crea y espera `Available`.
16. Anota su **Endpoint**, sin protocolo ni `:5432`.

Usaremos `sslmode=verify-full` y el certificado raíz de RDS en la conexión JDBC. Esto verifica cifrado e identidad del servidor, además de restringir la red. [TLS de PostgreSQL en RDS](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/PostgreSQL.Concepts.General.SSL.html).

## 6. Crear EC2

1. EC2 > Instances > Launch instance.
2. Nombre: `riffpass-backend`.
3. AMI: **Amazon Linux 2023**, arquitectura x86_64.
4. Tipo: `t3.small` como punto de partida para los dos procesos, o una instancia permitida con al menos 2 GB de RAM.
5. Crea/selecciona una key pair. Guarda el archivo `.pem` fuera del repositorio.
6. VPC: la misma.
7. Subred pública.
8. Auto-assign public IP: Enable.
9. Security group: `riffpass-ec2-sg`.
10. Disco: un volumen de laboratorio suficiente, por ejemplo 16-20 GiB.
11. Lanza y espera sus comprobaciones.
12. Anota IP pública, IP privada e Instance ID.

No se requiere un IAM role para que los JAR validen firmas Cognito o accedan por contraseña a PostgreSQL. Si usas Session Manager, aplica el rol permitido por tu laboratorio; esta guía utiliza SSH.

En PowerShell:

~~~powershell
ssh -i "C:\RUTA\riffpass-key.pem" ec2-user@TU_IP_PUBLICA
~~~

Si SSH rechaza los permisos del archivo, utiliza las instrucciones que EC2 muestra en **Connect > SSH client** para tu sistema; no compartas la clave.

## 7. Preparar EC2 y copiar los archivos

### 7.1 Instalar herramientas, dentro de EC2

~~~bash
sudo dnf install -y java-21-amazon-corretto-headless postgresql17 git nano
java -version
psql --version
~~~

Si `postgresql17` no existe en la imagen de tu laboratorio, consulta `dnf search postgresql` e instala un cliente disponible compatible; no necesitas instalar un servidor PostgreSQL en EC2.

Descarga el certificado público de RDS:

~~~bash
sudo install -d -m 755 /etc/riffpass
sudo curl --fail --location https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem --output /etc/riffpass/global-bundle.pem
sudo chmod 644 /etc/riffpass/global-bundle.pem
mkdir -p ~/riffpass/riffpass-eventos-service/target
mkdir -p ~/riffpass/riffpass-entradas-service/target
~~~

### 7.2 Copiar desde tu PC

Abre otra terminal PowerShell dentro de **`backend/`**, sin cerrar SSH:

~~~powershell
scp -i "C:\RUTA\riffpass-key.pem" -r deploy ec2-user@TU_IP_PUBLICA:/home/ec2-user/riffpass/
scp -i "C:\RUTA\riffpass-key.pem" riffpass-eventos-service/target/riffpass-eventos-service-1.0.0.jar ec2-user@TU_IP_PUBLICA:/home/ec2-user/riffpass/riffpass-eventos-service/target/
scp -i "C:\RUTA\riffpass-key.pem" riffpass-entradas-service/target/riffpass-entradas-service-1.0.0.jar ec2-user@TU_IP_PUBLICA:/home/ec2-user/riffpass/riffpass-entradas-service/target/
~~~

Esta opción no requiere hacer público el repositorio ni instalar Maven en EC2. Entregar el código por GitHub es un paso separado.

## 8. Crear las dos bases y usuarios

Dentro de EC2, conéctate como administrador. Sustituye el endpoint real:

~~~bash
psql "host=TU_ENDPOINT_RDS port=5432 dbname=postgres user=riffpass_admin sslmode=verify-full sslrootcert=/etc/riffpass/global-bundle.pem" -W
~~~

Escribe la contraseña cuando la solicite. Dentro de `psql`:

~~~sql
\i /home/ec2-user/riffpass/deploy/crear-bases.sql
\password riffpass_eventos
\password riffpass_entradas
\q
~~~

Cada comando `\password` pide una contraseña que después escribirás en el archivo de entorno del servicio correspondiente.

El SQL crea:

- Usuario `riffpass_eventos`, propietario de la base `riffpass_eventos`.
- Usuario `riffpass_entradas`, propietario de la base `riffpass_entradas`.
- Acceso de conexión restringido a cada usuario.

Ejecuta el script **una sola vez**. Si la conexión se corta a mitad, revisa `\du` y `\l` antes de repetir: no borres bases para volver a empezar.

Las tablas de negocio se crean al iniciar cada servicio mediante Flyway. No ejecutes manualmente las migraciones SQL del código ni uses `ddl-auto=create` en AWS.

## 9. Instalar y configurar los servicios

Dentro de EC2:

~~~bash
cd ~/riffpass
sudo bash deploy/install.sh
sudo nano /etc/riffpass/eventos.env
~~~

Completa:

~~~dotenv
PORT=8081
SERVER_SERVLET_CONTEXT_PATH=/test
DB_URL=jdbc:postgresql://TU_ENDPOINT_RDS:5432/riffpass_eventos?sslmode=verify-full&sslrootcert=/etc/riffpass/global-bundle.pem
DB_USERNAME=riffpass_eventos
DB_PASSWORD=TU_PASSWORD_EVENTOS
COGNITO_ISSUER=https://cognito-idp.us-east-1.amazonaws.com/TU_POOL_ID
COGNITO_CLIENT_ID=TU_APP_CLIENT_ID
CORS_ALLOWED_ORIGINS=http://localhost:5173
~~~

Edita Entradas:

~~~bash
sudo nano /etc/riffpass/entradas.env
~~~

~~~dotenv
PORT=8082
SERVER_SERVLET_CONTEXT_PATH=/test
DB_URL=jdbc:postgresql://TU_ENDPOINT_RDS:5432/riffpass_entradas?sslmode=verify-full&sslrootcert=/etc/riffpass/global-bundle.pem
DB_USERNAME=riffpass_entradas
DB_PASSWORD=TU_PASSWORD_ENTRADAS
COGNITO_ISSUER=https://cognito-idp.us-east-1.amazonaws.com/TU_POOL_ID
COGNITO_CLIENT_ID=TU_APP_CLIENT_ID
EVENTOS_BASE_URL=http://127.0.0.1:8081/test
CORS_ALLOWED_ORIGINS=http://localhost:5173
~~~

Usa una línea por variable, sin `export`. Si un valor contiene espacios, escríbelo entre comillas dobles respetando el formato de EnvironmentFile de systemd. No pegues estas contraseñas en GitHub.

No definas `SPRING_PROFILES_ACTIVE=local` en EC2: queremos RDS. Deja `COGNITO_AUDIENCE` sin definir para el flujo del profesor, que comprueba el App Client mediante `client_id`. Si más adelante configuras resource binding, esa variable permite exigir también el `aud` de la API.

**El contexto /test es intencional:** una integración REST privada reenvía el stage a su backend. Ambos JAR aceptarán `/test/api/...`, y Entradas utiliza `/test` al consultar Eventos. No añadas simultáneamente reglas para quitar el stage. [Comportamiento de integraciones privadas](https://docs.aws.amazon.com/apigateway/latest/developerguide/private-integration.html).

Inicia:

~~~bash
sudo systemctl enable --now riffpass@eventos riffpass@entradas
sudo systemctl status riffpass@eventos --no-pager
sudo systemctl status riffpass@entradas --no-pager
curl -i http://127.0.0.1:8081/test/actuator/health
curl -i http://127.0.0.1:8082/test/actuator/health
curl -i http://127.0.0.1:8081/test/api/eventos
~~~

Esperado: servicios `active (running)`, health `200 {"status":"UP"}`, y petición de negocio sin token `401`.

Los JAR corren como usuario de sistema `riffpass`, sin permisos de administrador. Los archivos de entorno se conservan con permisos 600 y systemd los carga antes de ejecutar Java.

Si falla:

~~~bash
sudo journalctl -u riffpass@eventos -n 80 --no-pager
sudo journalctl -u riffpass@entradas -n 80 --no-pager
~~~

Busca conexión RDS, contraseña, migración o variables faltantes. No publiques el log completo sin revisar si contiene datos de configuración.

## 10. Crear Target Groups y ALB interno

### 10.1 Target Groups

EC2 > Target Groups > Create target group:

| Campo | Eventos | Entradas |
|---|---|---|
| Target type | Instances | Instances |
| Name | riffpass-eventos-tg | riffpass-entradas-tg |
| Protocol / port | HTTP / 8081 | HTTP / 8082 |
| VPC | La del proyecto | La del proyecto |
| Protocol version | HTTP1 | HTTP1 |
| Health check path | /test/actuator/health | /test/actuator/health |
| Success codes | 200 | 200 |

En cada grupo registra la EC2 RiffPass en el puerto correspondiente.

### 10.2 Load Balancer

1. EC2 > Load Balancers > Create > Application Load Balancer.
2. Nombre: `riffpass-internal-alb`.
3. **Scheme: Internal**.
4. IPv4.
5. Misma VPC y dos subredes de zonas distintas.
6. Security group: `riffpass-alb-sg`.
7. Listener HTTP 80.
8. Acción predeterminada: fixed response 404 (si el asistente exige un target group inicialmente, selecciónalo y cambia la regla predeterminada después).
9. Crea el ALB.
10. Edita las reglas del listener, en este orden:

| Prioridad | Path patterns | Acción |
|---|---|---|
| 10 | /test/api/eventos/*/inventario y /test/api/eventos/*/entradas | Forward a riffpass-entradas-tg |
| 20 | /test/api/entradas/* | Forward a riffpass-entradas-tg |
| 30 | /test/api/eventos y /test/api/eventos/* | Forward a riffpass-eventos-tg |
| Default | Cualquier otro | Fixed response 404 |

La regla 10 debe ir antes de la 30: hay rutas que empiezan por `/eventos` pero pertenecen al servicio Entradas.

Espera que ambos targets estén **Healthy**. Guarda DNS y ARN del **load balancer**, no del listener ni del target group.

Desde EC2:

~~~bash
curl -i http://TU_DNS_ALB/test/api/eventos
~~~

Debe devolver 401, demostrando que alcanza el servicio protegido. No debes poder abrir el ALB interno directamente desde Internet.

## 11. Crear VPC Link V2

1. API Gateway > VPC links > Create.
2. Versión **V2**.
3. Nombre: `riffpass-vpclink`.
4. Selecciona la VPC y dos subredes del proyecto.
5. Security group: `riffpass-vpclink-sg`.
6. Crea y espera `Available`.
7. Guarda el ID.

Esta variante utiliza interfaces de red dentro de tu VPC. Todos los recursos deben estar en la misma cuenta. Si solo ves V1 o permisos denegados, verifica región y restricciones del laboratorio. [Creación de VPC Link V2 y REST API](https://docs.aws.amazon.com/apigateway/latest/developerguide/getting-started-with-private-integration.html).

## 12. Crear REST API e importar sus métodos

El proyecto incluye una plantilla OpenAPI con los **9 métodos de negocio**, Cognito Authorizer, scopes, integraciones VPC Link y preflight CORS.

### 12.1 Preparar el archivo en tu PC

En PowerShell, desde la raíz:

~~~powershell
.\scripts\render-gateway.ps1 -CognitoPoolArn "TU_ARN_USER_POOL" -VpcLinkId "TU_VPC_LINK_ID" -AlbArn "TU_ARN_ALB" -AlbDns "TU_DNS_ALB"
~~~

Los argumentos son identificadores públicos, no tokens ni contraseñas. Se genera:

~~~text
deploy/api-gateway.generated.json
~~~

Si PowerShell bloquea scripts locales, abre el archivo en tu editor y revisa su contenido. También puedes copiar `deploy/api-gateway.template.json` y reemplazar sus seis placeholders manualmente. No cambies una política de ejecución global solo para este proyecto.

### 12.2 Importar en AWS

1. API Gateway > Create API.
2. **REST API > Import** (no HTTP API ni REST Private API).
3. Importa el archivo generado.
4. Endpoint type: **Regional**.
5. Nombre: `api-riffpass-rest`.
6. Revisa los avisos de importación antes de continuar.

La API es pública en HTTPS; lo que es privado es su enlace hacia EC2. No necesitas crear una API cuyo endpoint type sea Private.

### 12.3 Qué revisar después de importar

En **Authorizers**:

- Name: `Cognito-riffpass`.
- Type: Cognito.
- User Pool: `riffpass-pool`.
- Token Source: `Authorization`.
- Token validation expression: vacía para este flujo access token.

En **Resources > cada método > Method Request**:

| Método | Authorization scope principal |
|---|---|
| GET /api/eventos | resource-server-riffpass/eventos-read |
| GET /api/eventos/{id} | resource-server-riffpass/eventos-read |
| POST /api/eventos | resource-server-riffpass/eventos-write |
| PUT /api/eventos/{id} | resource-server-riffpass/eventos-write |
| POST /api/eventos/{id}/inventario | resource-server-riffpass/entradas-write |
| GET /api/eventos/{id}/inventario | resource-server-riffpass/entradas-read |
| POST /api/eventos/{id}/entradas | resource-server-riffpass/entradas-write |
| GET /api/entradas/mias | resource-server-riffpass/entradas-read |
| POST /api/entradas/validar | resource-server-riffpass/entradas-write |

API Gateway trata varios scopes de un método como alternativas; el backend exige además `eventos-read` en las operaciones que consultan Eventos. No agregues scopes esperando una comprobación AND del Gateway.

En **Integration Request** verifica:

- Tipo VPC Link, proxy habilitado.
- VPC Link V2 correcto y ALB correcto.
- Método HTTP igual al método de entrada.
- Endpoint `http://TU_DNS_ALB/api/...`, sin añadir `/test` en la plantilla.
- Para `{id}`, mapping `integration.request.path.id <- method.request.path.id`.
- Reenvío del header Authorization mediante la integración proxy.

Las integraciones HTTP proxy conservan el cuerpo y respuesta del backend. No se añade una API key ni autorización IAM al cliente. La plantilla sigue el formato de integración privada de [AWS](https://docs.aws.amazon.com/apigateway/latest/developerguide/set-up-private-integration.html).

El botón de prueba del authorizer no equivale al flujo con access token y scopes. La evidencia final se obtiene en el Invoke URL desplegado. [Authorizer Cognito de REST API](https://docs.aws.amazon.com/apigateway/latest/developerguide/apigateway-enable-cognito-user-pool.html).

### 12.4 Alternativa manual para aprender

Si deseas configurar los formularios como en la clase, crea la REST API Regional vacía y los recursos `/api/eventos`, `/{id}`, `/inventario`, `/entradas`, `/api/entradas/mias` y `/api/entradas/validar`. Cada segmento se crea como recurso hijo de su padre correcto.

Por cada fila de la tabla:

1. Create Method, selecciona GET/POST/PUT.
2. Integration type: VPC Link.
3. Activa VPC proxy integration.
4. Integration HTTP method: el mismo.
5. Elige VPC Link V2 y ALB.
6. Endpoint URL: `http://TU_DNS_ALB` + ruta de esa fila.
7. Si lleva `{id}`, en Method Request decláralo y en Integration Request mapea `id` desde `method.request.path.id`.
8. Crea el authorizer Cognito descrito arriba y asócialo al método.
9. Agrega el scope principal completo.
10. Guarda.

Para el frontend se necesitan además OPTIONS y headers CORS. La importación ya los prepara; si lo haces manual, configura OPTIONS sin authorizer, con respuestas para origen `http://localhost:5173`, métodos GET/POST/PUT/OPTIONS y headers Authorization/Content-Type. El backend responde CORS a las peticiones reales.

## 13. Desplegar la API

1. Deploy API.
2. New stage: **test**.
3. Deploy.
4. Stages > test > copia Invoke URL.

Ejemplo:

~~~text
https://abc123.execute-api.us-east-1.amazonaws.com/test
~~~

Luego agrega `/api/eventos` para listar. No repitas el stage.

**Cada modificación de recursos, authorizer, integraciones o scopes requiere redeploy al stage test.** Si cambias el nombre del stage, actualiza también context path, EVENTOS_BASE_URL, health checks y reglas ALB. Mantener `test` simplifica la práctica.

## 14. Probar el flujo completo

1. Importa `postman/RiffPass.postman_collection.json`.
2. Completa variables locales:
   - `base_url`: Invoke URL con `/test`.
   - `organizador_token`: access token del organizador.
   - `asistente_token`: access token del asistente.
3. Ejecuta las solicitudes en orden.

| Paso | Qué debe pasar |
|---|---|
| 01 | Sin token, rechazo 401 |
| 02 | Organizador crea concierto: 201; guarda evento_id |
| 03-04 | Asistente lista y consulta: 200 |
| 05 | Organizador habilita 50 cupos: 201 |
| 06 | Consulta inventario: 200 |
| 07 | Asistente obtiene entrada: 201; guarda entrada_codigo |
| 08 | Asistente consulta sus entradas: 200 |
| 09 | Asistente intenta validar: 403 |
| 10 | Organizador valida: 200, estado UTILIZADA |
| 11 | Segunda validación: 409 |
| 12 | Asistente intenta crear evento: 403 |
| 13 | Organizador edita información propia: 200 |

La colección guarda el UUID del evento y el código de entrada de las respuestas; no debes inventarlos. Para repetir toda la demostración, empieza creando otro evento.

Obtén un token nuevo sin `eventos-read` y llama GET /api/eventos: debe ser rechazado por Gateway. Al modificar scopes/grupos, genera tokens nuevos.

Para probar firma alterada, cambia un carácter de la firma de una copia del token: debe rechazarlo. Para expiración, usa un token realmente vencido; editar el claim exp sin volver a firmar prueba firma alterada, no expiración.

Crea otro organizador y trata de editar/validar un evento ajeno: debe responder 403.

### Confirmar persistencia en RDS

Reinicia los dos procesos:

~~~bash
sudo systemctl restart riffpass@eventos riffpass@entradas
~~~

Vuelve a consultar el evento y Mis entradas. Los datos deben permanecer.

Conecta a cada base con su usuario usando `psql` y TLS, y comprueba:

~~~sql
-- En riffpass_eventos:
SELECT id, nombre, fecha FROM eventos;

-- En riffpass_entradas:
SELECT evento_id, capacidad, emitidas FROM inventarios;
SELECT id, evento_id, estado FROM entradas;
~~~

Estas comprobaciones con RDS completan lo que no puede garantizar una prueba local con H2.

## 15. Evidencia para mostrar al profesor

- Diagrama de los dos microservicios, Gateway, Cognito y RDS.
- Cada servicio activo en EC2, con su puerto y base.
- Token con claims visibles de forma segura, sin publicar el JWT completo.
- Scope configurado en Cognito, App Client y método de Gateway.
- Petición exitosa con token y rechazos sin token/sin permisos.
- Usuario común impedido de hacer una operación de organizador.
- Organizador impedido de operar sobre un evento ajeno.
- Entrada emitida, utilizada y rechazada al validarla de nuevo.
- Persistencia después de reiniciar.
- `mvnw verify` finalizado correctamente.
- Código fuente en GitHub, sin claves, contraseñas ni tokens.

Esta es la parte backend. La evaluación completa todavía requiere React + Amplify Authenticator, rutas protegidas, cierre de sesión y `apiFetch`, que representan el indicador del 60%.

## 16. Diagnóstico rápido

| Problema | Qué revisar |
|---|---|
| No puedo entrar por SSH | IP pública, key pair, regla 22 desde tu IP y ruta Internet Gateway |
| Backend no inicia | Variables de entorno, JDK, journalctl, conexión RDS |
| RDS timeout | Misma VPC, SG 5432 desde EC2, endpoint y puerto |
| Error SSL RDS | global-bundle.pem, ruta y permisos, endpoint exacto y sslmode |
| Target unhealthy | Puerto, SG ALB -> EC2, health /test/actuator/health, proceso activo |
| Gateway 404 | Stage /test, context path, prioridad de reglas ALB y mapping de id |
| Gateway 500/502/503 | VPC Link Available, ARN ALB, integración, targets y logs |
| 401 con token | Access token, issuer/client, expiración, scopes y redeploy |
| 403 en Entradas | Grupo correcto, eventos-read + entradas-write y propietario |
| Scope no aparece | Habilitar en App Client, solicitar por OAuth y generar token nuevo |
| Organizador se reconoce como asistente | cognito:groups y token generado después del cambio |
| Se crea el evento pero no hay entradas | Falta habilitar inventario |
| Postman funciona pero navegador falla | CORS, OPTIONS sin authorizer y origen exacto |
| Memoria insuficiente | Tamaño EC2, procesos duplicados, límites -Xmx de systemd |

No resuelvas 401 desactivando Spring Security. El backend debe verificar el JWT aunque Gateway también lo haga.

## 17. Actualizar el código más adelante

1. Modifica y ejecuta `mvnw clean verify` en tu PC.
2. Copia los dos nuevos JAR a los directorios staging de `/home/ec2-user/riffpass` del paso 7.
3. Dentro de EC2:

~~~bash
cd ~/riffpass
sudo systemctl stop riffpass@eventos riffpass@entradas
sudo bash deploy/install.sh
sudo systemctl start riffpass@eventos riffpass@entradas
~~~

El instalador conserva los archivos de entorno existentes. Después revisa health y Postman.
Para cambios de esquema, agrega una nueva migración Flyway, no edites una migración ya aplicada.

## 18. Cierre del laboratorio

Cuando ya no necesites los recursos, revisa con el docente cuánto tiempo deben permanecer disponibles. Detener EC2 no detiene los cargos de ALB, almacenamiento o RDS.

Guarda código y evidencias. Antes de eliminar bases, decide si necesitas un snapshot. Revisa en tu cuenta los recursos creados con prefijo riffpass, sus dependencias y costes. Este proyecto no ejecuta eliminaciones automáticas.

## Fuentes y límites

La configuración de Cognito, Postman, scopes y REST sigue las dos guías del profesor registradas en `../../CONTEXTO_PROYECTO.md`. La topología EC2/RDS/ALB/VPC Link y los scripts son adaptación de RiffPass. Los enlaces oficiales anteriores sustentan los detalles que no estaban en esas guías.

Los archivos se verifican localmente; la importación y las pantallas AWS dependen de la región, versión de consola y permisos de tu cuenta. No se afirma un despliegue exitoso hasta completar las comprobaciones de esta guía.
