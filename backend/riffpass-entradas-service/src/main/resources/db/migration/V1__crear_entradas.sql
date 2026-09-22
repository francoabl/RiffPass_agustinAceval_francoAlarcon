CREATE TABLE inventarios (
    evento_id UUID PRIMARY KEY,
    capacidad INTEGER NOT NULL CHECK (capacidad BETWEEN 1 AND 100000),
    emitidas INTEGER NOT NULL DEFAULT 0 CHECK (emitidas >= 0 AND emitidas <= capacidad)
);
CREATE TABLE entradas (
    id UUID PRIMARY KEY,
    evento_id UUID NOT NULL REFERENCES inventarios(evento_id),
    titular_sub VARCHAR(128) NOT NULL,
    codigo UUID NOT NULL UNIQUE,
    estado VARCHAR(20) NOT NULL CHECK (estado IN ('EMITIDA', 'UTILIZADA')),
    emitida_en TIMESTAMP WITH TIME ZONE NOT NULL,
    utilizada_en TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_entrada_titular_evento UNIQUE(evento_id, titular_sub)
);
CREATE INDEX idx_entradas_titular ON entradas(titular_sub, emitida_en);
