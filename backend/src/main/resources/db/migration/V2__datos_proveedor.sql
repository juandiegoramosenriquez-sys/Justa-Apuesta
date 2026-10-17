-- Fase 3: datos del proveedor y cachés.

-- Para buscar por liga sin tildes ("ligue" encuentra "Ligue 1")
ALTER TABLE ligas ADD COLUMN nombre_normalizado VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE ligas ADD COLUMN logo_url VARCHAR(255);
CREATE INDEX idx_ligas_nombre ON ligas (nombre_normalizado);

-- Cuándo se descargaron por última vez los últimos/próximos partidos de un equipo
ALTER TABLE equipos ADD COLUMN partidos_sync_en DATETIME(6);

ALTER TABLE partidos ADD COLUMN temporada INT;
-- Código original del proveedor (NS, FT, PST...) para depurar
ALTER TABLE partidos ADD COLUMN estado_proveedor VARCHAR(10);
-- true cuando ya se pidieron las estadísticas finales (aunque el proveedor no tuviera)
ALTER TABLE partidos ADD COLUMN detalle_sync BOOLEAN NOT NULL DEFAULT FALSE;

-- Lo que hizo cada jugador en cada partido (para "Jugadores clave" con datos reales)
CREATE TABLE jugadores_partido (
    partido_id       BIGINT       NOT NULL,
    jugador_id       BIGINT       NOT NULL,
    equipo_id        BIGINT       NOT NULL,
    nombre           VARCHAR(100) NOT NULL,
    posicion         VARCHAR(10),
    dorsal           INT,
    minutos          INT          NOT NULL,
    goles            INT          NOT NULL,
    remates_a_puerta INT          NOT NULL,
    amarillas        INT          NOT NULL,
    rojas            INT          NOT NULL,
    PRIMARY KEY (partido_id, jugador_id),
    CONSTRAINT fk_jp_partido FOREIGN KEY (partido_id) REFERENCES partidos (id) ON DELETE CASCADE,
    CONSTRAINT fk_jp_equipo FOREIGN KEY (equipo_id) REFERENCES equipos (id)
);
CREATE INDEX idx_jp_equipo ON jugadores_partido (equipo_id);

-- Búsquedas ya consultadas al proveedor (se reusan 7 días)
CREATE TABLE busquedas_cache (
    termino    VARCHAR(100) NOT NULL,
    buscado_en DATETIME(6)  NOT NULL,
    PRIMARY KEY (termino)
);

-- Última sincronización (para "Datos actualizados hace X horas")
CREATE TABLE sync_estado (
    clave         VARCHAR(40)  NOT NULL,
    ejecutado_en  DATETIME(6)  NOT NULL,
    ok            BOOLEAN      NOT NULL,
    detalle       VARCHAR(500),
    PRIMARY KEY (clave)
);
