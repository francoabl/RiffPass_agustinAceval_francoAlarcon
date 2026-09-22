-- Ejecutar como administrador RDS desde psql. No contiene passwords.
-- Usar después: \password riffpass_eventos y \password riffpass_entradas
\set ON_ERROR_STOP on
CREATE ROLE riffpass_eventos LOGIN;
CREATE ROLE riffpass_entradas LOGIN;
CREATE DATABASE riffpass_eventos OWNER riffpass_eventos;
CREATE DATABASE riffpass_entradas OWNER riffpass_entradas;
REVOKE CONNECT ON DATABASE riffpass_eventos FROM PUBLIC;
REVOKE CONNECT ON DATABASE riffpass_entradas FROM PUBLIC;
GRANT CONNECT ON DATABASE riffpass_eventos TO riffpass_eventos;
GRANT CONNECT ON DATABASE riffpass_entradas TO riffpass_entradas;
