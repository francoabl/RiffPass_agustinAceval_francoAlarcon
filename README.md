# RiffPass

Plataforma de entradas para conciertos desarrollada para las evaluaciones EP1 y EP2 de DSY1107.

## Estructura

```text
RiffPass/
├── backend/   Microservicios Spring Boot, scripts y despliegue AWS
├── frontend/  Aplicación React desplegada en Vercel
├── presentacion/ Presentación HTML y evidencias visuales
└── docs/      Seguimiento general de la evaluación
```

La carpeta definitiva del proyecto es `C:\Users\franc\Proyectos\RiffPass`; el nombre histórico `Trueke` ya no representa el caso evaluado.

## Estado actual

- Backend implementado con dos microservicios, seguridad JWT de Cognito y PostgreSQL RDS.
- Infraestructura desplegada y verificada en AWS: API Gateway, Cognito, VPC Link V2, ALB interno, EC2 y RDS.
- Frontend React desplegado en Vercel y probado con usuarios reales `ORGANIZADOR` y `ASISTENTE`.
- Colección Postman aprobada con 13/13 casos sobre la infraestructura real.
- EP2: mensajería asíncrona con un tópico SNS y tres colas SQS con DLQ (fanout y filtros), consumidor con dos réplicas y microservicio administrador. Desplegada y verificada en AWS el 19/10/2026.

## Continuar con el backend

Abre PowerShell en esta carpeta y ejecuta:

```powershell
cd backend
.\mvnw.cmd clean verify
```

Documentos principales:

- [Guía de estudio del proyecto completo](docs/GUIA_ESTUDIO_RIFFPASS.md)
- [Presentación HTML de la EP1](presentacion/riffpass-ep1.html)
- [Presentación de la EP2 publicada](https://riffpassev2.netlify.app/#1) ([archivo](presentacion/riffpass-ep2.html))
- [Inicio y pruebas del backend](backend/README.md)
- [Guía de AWS paso a paso](backend/docs/AWS_PASO_A_PASO.md)
- [Contrato de la API](backend/docs/API.md)
- [Mensajería SNS/SQS de la EP2](backend/docs/MENSAJERIA.md)
- [Cumplimiento de la pauta EP1](docs/CUMPLIMIENTO_EP1.md)
- [Contexto y decisiones del proyecto](CONTEXTO_PROYECTO.md)
