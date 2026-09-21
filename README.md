# Pedidos360

Sistema de gestion de pedidos con arquitectura cloud native en AWS.

```
React (Amplify + Cognito) --JWT--> API Gateway --> Microservicios Spring Boot (EC2) --> RDS MySQL
```

| Componente | Tecnologia | Puerto local |
|---|---|---|
| `frontend/` | React + Vite + AWS Amplify (Authenticator) | 5173 |
| `backend/usuarios-service` | Spring Boot 3, JPA, MySQL | 8081 |
| `backend/productos-service` | Spring Boot 3, JPA, MySQL | 8082 |
| `backend/pedidos-service` | Spring Boot 3, JPA, MySQL | 8083 |

## Caso de negocio y reglas (detalle completo en `docs/CASO_DE_NEGOCIO.md`)

Pedidos360 permite a clientes ver un catalogo, armar pedidos y seguir su estado; y a administradores gestionar productos y despachar pedidos.

- Roles = grupos de Cognito: `ADMIN` y (sin grupo) `CLIENTE`.
- Solo ADMIN crea/edita/desactiva productos, lista usuarios, y cambia el estado de pedidos.
- El total del pedido lo calcula el servidor con el precio vigente; el cliente nunca envia precios.
- No se puede pedir mas unidades que el stock (respuesta 409).
- Estados: `CREADO -> CONFIRMADO -> ENVIADO`; `CANCELADO` desde CREADO o CONFIRMADO (el cliente solo cancela en CREADO).
- Un cliente solo ve sus propios pedidos.

## Seguridad (JWT de Cognito)

Cada microservicio es un *OAuth2 Resource Server*: el filtro `BearerTokenAuthenticationFilter` valida en cada request
1. **firma** con las llaves publicas (JWKS) del User Pool,
2. **vigencia** (`exp`/`nbf`),
3. **issuer** = `https://cognito-idp.<region>.amazonaws.com/<userPoolId>`,
4. **audience** = App Client ID (`aud` en ID token, `client_id` en access token) - `AudienceValidator`.

Roles se leen de `cognito:groups` y scopes de `scope` (`CognitoJwtAuthConverter`). Sin token o token invalido: **401** JSON; rol insuficiente: **403** JSON.

## Ejecucion local

### 1. Cognito (una vez)
Crear un User Pool (login por email) y un App Client **sin secret**. Crear el grupo `ADMIN` y asignarselo a un usuario.

### 2. Backend
Variables de entorno (las mismas para los 3 servicios):

```
COGNITO_REGION=us-east-1
COGNITO_USER_POOL_ID=us-east-1_xxxxxxxxx
COGNITO_APP_CLIENT_ID=xxxxxxxxxxxxxxxxxxxxxxxxxx
DB_URL=jdbc:mysql://<endpoint-rds>:3306/pedidos360_<servicio>?createDatabaseIfNotExist=true
DB_USER=admin
DB_PASSWORD=********
CORS_ALLOWED_ORIGINS=http://localhost:5173
PRODUCTOS_SERVICE_URL=http://localhost:8082   # solo pedidos-service
```

```
cd backend/productos-service && mvn spring-boot:run
cd backend/usuarios-service  && mvn spring-boot:run
cd backend/pedidos-service   && mvn spring-boot:run
mvn test   # pruebas de seguridad y reglas
```

Sin `DB_URL` usa MySQL local (`localhost:3306`, usuario `root`).

### 3. Frontend
```
cd frontend
cp .env.example .env   # completar Cognito
npm install
npm run dev
```
Sin `VITE_API_BASE_URL`, Vite redirige `/api/*` a los servicios locales. En AWS, poner la URL del API Gateway.

## Despliegue en EC2
1. `mvn clean package` en cada servicio y copiar `target/*.jar` a la instancia (Java 17).
2. Exportar las variables de entorno y ejecutar `java -jar <servicio>.jar` (idealmente como servicio `systemd`).
3. Security Group: puertos 8081-8083 abiertos solo al API Gateway/VPC; RDS solo accesible desde el SG de EC2.
4. API Gateway: rutas `/api/usuarios/{proxy+}`, `/api/productos/{proxy+}`, `/api/pedidos/{proxy+}` hacia cada servicio, con authorizer de Cognito.

## Endpoints
| Metodo | Ruta | Acceso |
|---|---|---|
| GET | `/api/usuarios/me`, `/api/usuarios/me/claims` | autenticado |
| GET | `/api/usuarios`, `/api/usuarios/{id}` | ADMIN |
| GET | `/api/productos`, `/api/productos/{id}` | autenticado |
| POST/PUT/DELETE | `/api/productos[/{id}]` | ADMIN |
| POST | `/api/productos/{id}/reservar`, `/api/productos/{id}/liberar` | autenticado (los usa pedidos-service) |
| POST | `/api/pedidos` | autenticado |
| GET | `/api/pedidos`, `/api/pedidos/{id}` | dueno o ADMIN |
| PATCH | `/api/pedidos/{id}/estado` | ADMIN |
| POST | `/api/pedidos/{id}/cancelar` | dueno o ADMIN |
| GET | `/actuator/health` | publico |
