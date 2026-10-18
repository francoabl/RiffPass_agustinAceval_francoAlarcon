-- EP2: base del consumidor (idempotencia, tickets, notificaciones y auditoría).
-- Ejecutar como administrador RDS desde psql. No contiene passwords.
-- Usar después: \password riffpass_procesamiento
\set ON_ERROR_STOP on
CREATE ROLE riffpass_procesamiento LOGIN;
GRANT riffpass_procesamiento TO riffpass_admin;
CREATE DATABASE riffpass_procesamiento OWNER riffpass_procesamiento;
REVOKE riffpass_procesamiento FROM riffpass_admin;
REVOKE CONNECT ON DATABASE riffpass_procesamiento FROM PUBLIC;
GRANT CONNECT ON DATABASE riffpass_procesamiento TO riffpass_procesamiento;
