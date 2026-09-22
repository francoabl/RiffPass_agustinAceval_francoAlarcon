# Guia para colaborar en el frontend de RiffPass

Esta guia permite actualizar el diseno visual del frontend sin necesitar acceso a AWS Academy, a la consola AWS ni a credenciales del backend.

## 1. Alcance del trabajo

Puedes modificar:

- Colores, tipografias, espaciados y estilos responsive.
- Layout, barra de navegacion, tarjetas, formularios y mensajes.
- Componentes visuales y organizacion de las pantallas.
- Accesibilidad visual: contraste, foco, labels y estados de carga/error.

Evita cambiar sin coordinacion previa:

- Rutas definidas en `src/App.jsx`.
- Logica de `src/auth/`.
- `src/amplifyConfig.js`.
- `src/api/apiFetch.js` y contratos dentro de `src/api/`.
- Nombres de campos enviados al backend.
- Reglas que muestran acciones segun `ORGANIZADOR` o `ASISTENTE`.

La autenticacion real usa Amplify Auth con Cognito Managed Login mediante Authorization Code + PKCE. Las peticiones usan un access token con scopes personalizados. No reemplaces este flujo por un login simulado ni por usuario/clave directo.

## 2. Requisitos locales

- Git.
- Node.js y npm.
- Acceso como colaborador al repositorio privado de GitHub.

No necesitas:

- Usuario de AWS Academy.
- Credenciales temporales de AWS.
- Passwords de RDS.
- Llave `.pem` de EC2.
- Tokens JWT.
- El archivo `.env` de otro integrante.

## 3. Descargar y preparar el proyecto

Clona el repositorio si aun no lo tienes:

```powershell
Set-Location "C:\Users\TU_USUARIO\Proyectos"
git clone https://github.com/francoabl/RiffPass_agustinAceval_francoAlarcon.git
Set-Location "RiffPass_agustinAceval_francoAlarcon"
```

Si ya lo tienes, sincroniza primero:

```powershell
git switch main
git pull origin main
```

Crea una rama propia. No trabajes directamente en `main`:

```powershell
git switch -c frontend/redisenio-visual
```

Instala las dependencias e inicia Vite:

```powershell
Set-Location frontend
npm install
npm run dev
```

Abre:

```text
http://localhost:5173/
```

## 4. Trabajar sin AWS: modo vista previa

En desarrollo, la pantalla de login muestra:

- **Ver como Asistente**
- **Ver como Organizador**

Estos botones cargan datos de ejemplo solo en memoria. No usan Cognito, API Gateway, EC2 ni RDS.

Prueba ambos perfiles porque la navegacion cambia segun el rol:

### ASISTENTE

- Ve Conciertos y Mis entradas.
- Puede abrir el detalle de un concierto.
- Puede observar la accion para obtener una entrada.
- No debe ver Crear concierto ni Validar entrada.

### ORGANIZADOR

- Ve Conciertos, Mis entradas, Crear concierto y Validar entrada.
- Puede revisar formularios de creacion, edicion, cupos y validacion.

El banner amarillo indica que estas usando datos simulados. Este modo no aparece en el build de produccion.

## 5. Archivos principales para el rediseño

```text
frontend/src/styles.css
frontend/src/components/Layout.jsx
frontend/src/pages/Login.jsx
frontend/src/pages/Eventos.jsx
frontend/src/pages/EventoDetalle.jsx
frontend/src/pages/CrearEvento.jsx
frontend/src/pages/MisEntradas.jsx
frontend/src/pages/ValidarEntrada.jsx
```

Puedes extraer componentes reutilizables dentro de `frontend/src/components/` cuando ayude a evitar duplicacion.

Conserva los estados funcionales:

- Cargando.
- Lista vacia.
- Error de API.
- Accion exitosa.
- Boton deshabilitado mientras se envia una solicitud.
- Navegacion diferenciada por rol.
- Vista utilizable en escritorio y movil.

No ocultes errores unicamente con CSS ni elimines mensajes necesarios para entender el resultado de una operacion.

## 6. Configuracion AWS opcional

Para un rediseño visual no necesitas `.env`.

Si se acuerda una prueba real, crea tu propio archivo local a partir del ejemplo:

```powershell
Copy-Item .env.example .env
```

Completa solo los valores publicos indicados por el equipo. `.env` esta ignorado por Git y nunca debe subirse.

Para que la prueba real funcione:

- El Learner Lab debe estar activo porque EC2 se detiene entre sesiones.
- `http://localhost:5173/` debe permanecer autorizado en Cognito.
- Debes utilizar un usuario de prueba de Cognito.

No compartas capturas que muestren JWT completos, passwords o credenciales temporales.

## 7. Verificacion antes de entregar cambios

Ejecuta siempre:

```powershell
npm run build
```

Luego revisa manualmente:

- [ ] Login y modo vista previa se ven correctamente.
- [ ] Catalogo de conciertos funciona en escritorio y movil.
- [ ] Detalle de concierto conserva todas sus acciones.
- [ ] Mis entradas tiene estados con datos y sin datos.
- [ ] Crear concierto conserva todos los campos y labels.
- [ ] Validar entrada conserva su formulario y respuestas.
- [ ] ASISTENTE no ve acciones exclusivas del ORGANIZADOR.
- [ ] ORGANIZADOR conserva sus opciones de navegacion.
- [ ] No hay texto cortado, solapamientos ni scroll horizontal accidental.
- [ ] Los controles tienen foco visible y contraste legible.
- [ ] La consola del navegador no muestra errores nuevos.

Comprueba tambien que no agregaste archivos sensibles:

```powershell
git status --short
git diff --check
```

No deben aparecer `.env`, `node_modules/`, tokens, passwords ni llaves privadas.

## 8. Publicar la rama

Desde la raiz del repositorio:

```powershell
git status --short
git add frontend
git commit -m "feat: actualizar diseno visual del frontend"
git push -u origin frontend/redisenio-visual
```

Envia al equipo el nombre de la rama o crea un Pull Request hacia `main`.

No uses `git push --force` y no mezcles en este commit cambios del backend o de infraestructura.

## 9. Si `main` cambia mientras trabajas

Guarda primero tus cambios en un commit y luego integra lo nuevo:

```powershell
git fetch origin
git rebase origin/main
```

Si aparece un conflicto, no borres automaticamente el trabajo de otro integrante. Resuelve solo los archivos que entiendas y coordina cualquier conflicto en autenticacion, API o rutas.

Despues del rebase, vuelve a ejecutar:

```powershell
Set-Location frontend
npm run build
```

## 10. Regla de seguridad principal

Para modificar la apariencia del frontend solo necesitas el repositorio y el modo vista previa. Nunca solicites ni compartas credenciales de AWS Academy, RDS, EC2 o tokens de usuarios para realizar cambios visuales.
