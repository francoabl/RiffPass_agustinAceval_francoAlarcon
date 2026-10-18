-- Idempotencia: un mismo evento se procesa como máximo una vez por cola, aunque llegue duplicado
-- o lo reciban dos réplicas del consumidor.
CREATE TABLE mensajes_procesados (
    cola VARCHAR(80) NOT NULL,
    evento_id UUID NOT NULL,
    procesado_en TIMESTAMP WITH TIME ZONE NOT NULL,
    instancia VARCHAR(80) NOT NULL,
    CONSTRAINT pk_mensajes_procesados PRIMARY KEY (cola, evento_id)
);
CREATE TABLE tickets (
    entrada_id UUID PRIMARY KEY,
    evento_id UUID NOT NULL,
    titular_sub VARCHAR(128) NOT NULL,
    codigo UUID NOT NULL UNIQUE,
    contenido VARCHAR(1000) NOT NULL,
    generado_en TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_tickets_titular ON tickets(titular_sub, generado_en);
CREATE TABLE notificaciones (
    id UUID PRIMARY KEY,
    evento_mensaje_id UUID NOT NULL,
    destinatario VARCHAR(128) NOT NULL,
    asunto VARCHAR(200) NOT NULL,
    cuerpo VARCHAR(1000) NOT NULL,
    creada_en TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE auditoria (
    evento_mensaje_id UUID PRIMARY KEY,
    tipo VARCHAR(40) NOT NULL,
    origen VARCHAR(80),
    concierto_id UUID,
    ocurrido_en TIMESTAMP WITH TIME ZONE NOT NULL,
    registrado_en TIMESTAMP WITH TIME ZONE NOT NULL,
    datos VARCHAR(4000) NOT NULL
);
CREATE INDEX idx_auditoria_concierto ON auditoria(concierto_id, tipo);
