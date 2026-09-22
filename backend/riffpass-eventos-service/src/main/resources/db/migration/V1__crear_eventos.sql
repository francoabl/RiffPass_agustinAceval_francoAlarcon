CREATE TABLE eventos (
    id UUID PRIMARY KEY,
    nombre VARCHAR(120) NOT NULL,
    artista VARCHAR(120) NOT NULL,
    recinto VARCHAR(160) NOT NULL,
    ciudad VARCHAR(100) NOT NULL,
    descripcion VARCHAR(2000) NOT NULL,
    fecha TIMESTAMP WITH TIME ZONE NOT NULL,
    organizador_sub VARCHAR(128) NOT NULL,
    creado_en TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_eventos_fecha ON eventos(fecha);
CREATE INDEX idx_eventos_organizador ON eventos(organizador_sub);
