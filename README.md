# RiffPass

Plataforma de entradas para conciertos desarrollada para la evaluación EP1 de DSY1107.

## Estructura

```text
RiffPass/
├── backend/   Microservicios Spring Boot, scripts y despliegue AWS
├── frontend/  Aplicación React (siguiente etapa)
└── docs/      Seguimiento general de la evaluación
```

La carpeta definitiva del proyecto es `C:\Users\franc\Proyectos\RiffPass`; el nombre histórico `Trueke` ya no representa el caso evaluado.

## Estado actual

- Backend implementado con dos microservicios, seguridad JWT de Cognito y PostgreSQL.
- Pruebas locales del backend disponibles.
- Despliegue en AWS Academy Learner Lab en curso.
- Frontend: primera version implementada (React + Amplify Hosted UI); pendiente probar con Cognito real.

## Continuar con el backend

Abre PowerShell en esta carpeta y ejecuta:

```powershell
cd backend
.\mvnw.cmd clean verify
```

Documentos principales:

- [Inicio y pruebas del backend](backend/README.md)
- [Guía de AWS paso a paso](backend/docs/AWS_PASO_A_PASO.md)
- [Contrato de la API](backend/docs/API.md)
- [Cumplimiento de la pauta EP1](docs/CUMPLIMIENTO_EP1.md)
- [Contexto y decisiones del proyecto](CONTEXTO_PROYECTO.md)
