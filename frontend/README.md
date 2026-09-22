# Frontend de RiffPass

Aplicacion React (Vite) que consume los microservicios de RiffPass a traves de Amazon Cognito y, en produccion, de API Gateway.

## Decision de autenticacion: Hosted UI, no el formulario directo

El login usa `signInWithRedirect()` de Amplify (Hosted UI de Cognito, Authorization Code + PKCE), no el formulario de usuario/clave via API directa. Motivo: el backend exige en cada endpoint de negocio un **access token con scopes personalizados** (`resource-server-riffpass/...`). Esos scopes solo se obtienen pasando por el flujo OAuth de Cognito; el login directo (`signIn()` con SRP) entrega un access token sin esos scopes y todas las llamadas a la API responderian 403. Ver `CONTEXTO_PROYECTO.md` seccion 6.

`apiFetch` (en `src/api/apiFetch.js`) usa el **access token**, nunca el ID token, porque el `scope` y los custom scopes solo viven ahi.

## Estructura

```text
src/
├── amplifyConfig.js   Configuracion de Cognito + OAuth (Hosted UI)
├── auth/              Sesion, claims (roles/scopes) y rutas protegidas
├── api/                apiFetch (interceptor JWT) y funciones por recurso (eventos, entradas)
├── pages/              Catalogo, detalle de concierto, crear concierto, mis entradas, validar entrada
└── components/         Layout con navegacion segun rol
```

Vistas:

- `/eventos`: catalogo de conciertos (cualquier usuario autenticado).
- `/eventos/:id`: detalle, cupos disponibles, obtener entrada; el organizador dueño puede editar y habilitar cupos.
- `/eventos/nuevo`: crear concierto (solo `ORGANIZADOR`).
- `/mis-entradas`: entradas propias.
- `/validar`: validar una entrada en la puerta (solo `ORGANIZADOR`).

## Configuracion

```bash
cp .env.example .env
```

Completa con los valores reales del User Pool `riffpass-pool` y el App Client publico `riffpass-web` (ver `../backend/docs/AWS_PASO_A_PASO.md`):

| Variable | De donde sale |
|---|---|
| `VITE_COGNITO_USER_POOL_ID` | Overview del User Pool |
| `VITE_COGNITO_APP_CLIENT_ID` | Detalle del App Client (sin secret) |
| `VITE_COGNITO_DOMAIN` | Branding > Domain del pool, sin `https://` |
| `VITE_OAUTH_SCOPES` | `openid email profile` + los 4 scopes de `resource-server-riffpass` habilitados en el App Client |
| `VITE_REDIRECT_SIGNIN` / `VITE_REDIRECT_SIGNOUT` | Deben estar en "Allowed callback/sign-out URLs" del App Client |
| `VITE_API_BASE_URL` | Invoke URL del API Gateway + stage (`.../test`); vacio = proxy local de Vite |

## Ejecutar

```bash
npm install
npm run dev
```

Sin `VITE_API_BASE_URL`, Vite redirige `/api/eventos*` a `localhost:8081` y `/api/entradas*` (y `/api/eventos/{id}/inventario|entradas`) a `localhost:8082`, siguiendo el contrato de `../backend/docs/API.md`. Con los dos microservicios corriendo localmente (ver `../backend/README.md`) y Cognito real configurado, el flujo funciona de punta a punta sin esperar el despliegue en AWS.

## Verificacion con Cognito real

- Verificado el 22/09/2026: Hosted UI con Authorization Code + PKCE vuelve a `http://localhost:5173/` y crea una sesion real.
- El usuario de prueba `ORGANIZADOR` recibe su rol, ve las rutas autorizadas y consume por API Gateway el catalogo, detalle y entradas sin errores CORS.
- Los endpoints protegidos responden usando el access token; el exito a traves de metodos con scopes en Gateway confirma que el token contiene los scopes solicitados.
- La coleccion Postman paso 13/13 casos contra AWS, incluidos 401 sin token, 403 por rol y 409 por validacion repetida.

Pendiente: repetir el recorrido visual con `ASISTENTE` y guardar evidencias sin mostrar JWT completos. Login y logout reales desde React ya fueron verificados. Codigo verificado: `npm run build` sin errores.

## Ver las pantallas sin Cognito (modo vista previa)

Antes de tener Cognito real, se puede revisar el diseño y navegar todas las vistas con datos de ejemplo:

1. En la pantalla de login aparece, solo en desarrollo (`npm run dev`, nunca en el build de produccion), una caja **"Vista previa (solo desarrollo)"** con dos botones: "Ver como Asistente" y "Ver como Organizador".
2. Al hacer clic, se simula una sesion en memoria (sin Amplify, sin red) y se navega con datos falsos: 2 conciertos de ejemplo, cupos, una entrada ya emitida, etc.
3. Un banner amarillo arriba de la pantalla recuerda que es vista previa.
4. "Cerrar sesion" vuelve al login sin dejar rastro (no usa localStorage ni cookies).

Este modo vive en `src/api/previewMode.js` y `src/api/mockBackend.js`. Nunca llama a `apiFetch` ni a Amplify, asi que funciona sin backend ni AWS Academy encendido. No reemplaza las pruebas reales descritas en la seccion anterior.

### Ubicacion local

El repositorio definitivo ya esta fuera de OneDrive. Ejecuta el frontend directamente desde:

```powershell
Set-Location "C:\Users\franc\Proyectos\RiffPass\frontend"
npm install
npm run dev
```

Abre exactamente `http://localhost:5173/`; ese origen debe coincidir con las callback y sign-out URLs configuradas en Cognito.

