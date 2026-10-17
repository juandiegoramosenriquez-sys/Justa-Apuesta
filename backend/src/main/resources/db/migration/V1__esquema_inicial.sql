-- Esquema inicial de Justa Apuesta.
-- Compatible con MySQL 8 (Aiven / Docker), MariaDB 10.4 (XAMPP) y H2 en modo MySQL (tests).
-- Todas las fechas se guardan en UTC.

CREATE TABLE usuarios (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    nombre        VARCHAR(80)  NOT NULL,
    email         VARCHAR(120) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    modo          VARCHAR(15)  NOT NULL DEFAULT 'SEGURO',
    tip_visto     BOOLEAN      NOT NULL DEFAULT FALSE,
    creado_en     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_usuarios_email UNIQUE (email),
    CONSTRAINT ck_usuarios_modo CHECK (modo IN ('SEGURO', 'EQUILIBRADO', 'ARRIESGADO'))
);

CREATE TABLE ligas (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    proveedor_id BIGINT       NOT NULL,
    nombre       VARCHAR(100) NOT NULL,
    pais         VARCHAR(80),
    PRIMARY KEY (id),
    CONSTRAINT uk_ligas_proveedor UNIQUE (proveedor_id)
);

CREATE TABLE equipos (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    proveedor_id       BIGINT       NOT NULL,
    nombre             VARCHAR(100) NOT NULL,
    -- Sin tildes y en minúsculas, para buscar "atletico" y encontrar "Atlético"
    nombre_normalizado VARCHAR(100) NOT NULL,
    liga_id            BIGINT,
    estadio            VARCHAR(120),
    escudo_url         VARCHAR(255),
    actualizado_en     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_equipos_proveedor UNIQUE (proveedor_id),
    CONSTRAINT fk_equipos_liga FOREIGN KEY (liga_id) REFERENCES ligas (id)
);
CREATE INDEX idx_equipos_nombre ON equipos (nombre_normalizado);

CREATE TABLE partidos (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    proveedor_id   BIGINT       NOT NULL,
    liga_id        BIGINT       NOT NULL,
    local_id       BIGINT       NOT NULL,
    visita_id      BIGINT       NOT NULL,
    fecha_hora_utc DATETIME(6)  NOT NULL,
    estadio        VARCHAR(120),
    estado         VARCHAR(12)  NOT NULL,
    goles_local    INT,
    goles_visita   INT,
    actualizado_en DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_partidos_proveedor UNIQUE (proveedor_id),
    CONSTRAINT fk_partidos_liga FOREIGN KEY (liga_id) REFERENCES ligas (id),
    CONSTRAINT fk_partidos_local FOREIGN KEY (local_id) REFERENCES equipos (id),
    CONSTRAINT fk_partidos_visita FOREIGN KEY (visita_id) REFERENCES equipos (id),
    CONSTRAINT ck_partidos_estado CHECK (estado IN ('JUGADO', 'HOY', 'PROXIMO', 'EN_VIVO', 'SUSPENDIDO'))
);
CREATE INDEX idx_partidos_fecha ON partidos (fecha_hora_utc);
CREATE INDEX idx_partidos_estado_fecha ON partidos (estado, fecha_hora_utc);
CREATE INDEX idx_partidos_local_fecha ON partidos (local_id, fecha_hora_utc);
CREATE INDEX idx_partidos_visita_fecha ON partidos (visita_id, fecha_hora_utc);

CREATE TABLE estadisticas_partido (
    partido_id         BIGINT NOT NULL,
    equipo_id          BIGINT NOT NULL,
    corners            INT,
    tarjetas_amarillas INT,
    tarjetas_rojas     INT,
    remates_a_puerta   INT,
    remates_totales    INT,
    posesion           DECIMAL(5, 2),
    PRIMARY KEY (partido_id, equipo_id),
    CONSTRAINT fk_estadisticas_partido FOREIGN KEY (partido_id) REFERENCES partidos (id) ON DELETE CASCADE,
    CONSTRAINT fk_estadisticas_equipo FOREIGN KEY (equipo_id) REFERENCES equipos (id)
);

CREATE TABLE jugadores_clave (
    id                         BIGINT       NOT NULL AUTO_INCREMENT,
    partido_id                 BIGINT       NOT NULL,
    equipo_id                  BIGINT       NOT NULL,
    nombre                     VARCHAR(100) NOT NULL,
    posicion                   VARCHAR(40),
    dorsal                     INT,
    goles_temporada            INT,
    partidos_jugados           INT,
    remates_a_puerta_temporada INT,
    tarjetas_temporada         INT,
    PRIMARY KEY (id),
    CONSTRAINT fk_jugadores_partido FOREIGN KEY (partido_id) REFERENCES partidos (id) ON DELETE CASCADE,
    CONSTRAINT fk_jugadores_equipo FOREIGN KEY (equipo_id) REFERENCES equipos (id)
);
CREATE INDEX idx_jugadores_partido ON jugadores_clave (partido_id);

CREATE TABLE favoritos (
    usuario_id BIGINT      NOT NULL,
    partido_id BIGINT      NOT NULL,
    creado_en  DATETIME(6) NOT NULL,
    PRIMARY KEY (usuario_id, partido_id),
    CONSTRAINT fk_favoritos_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE,
    CONSTRAINT fk_favoritos_partido FOREIGN KEY (partido_id) REFERENCES partidos (id) ON DELETE CASCADE
);

CREATE TABLE ligas_favoritas (
    usuario_id  BIGINT       NOT NULL,
    nombre_liga VARCHAR(100) NOT NULL,
    PRIMARY KEY (usuario_id, nombre_liga),
    CONSTRAINT fk_ligas_fav_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE
);

CREATE TABLE picks_seguidos (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    usuario_id    BIGINT       NOT NULL,
    partido_id    BIGINT       NOT NULL,
    mercado_clave VARCHAR(60)  NOT NULL,
    texto         VARCHAR(150) NOT NULL,
    probabilidad  INT          NOT NULL,
    estado        VARCHAR(12)  NOT NULL DEFAULT 'PENDIENTE',
    creado_en     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_picks_usuario_partido_mercado UNIQUE (usuario_id, partido_id, mercado_clave),
    CONSTRAINT fk_picks_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE,
    CONSTRAINT fk_picks_partido FOREIGN KEY (partido_id) REFERENCES partidos (id) ON DELETE CASCADE,
    CONSTRAINT ck_picks_estado CHECK (estado IN ('PENDIENTE', 'ACERTADO', 'FALLADO'))
);
CREATE INDEX idx_picks_estado ON picks_seguidos (estado);

-- Análisis calculado ANTES del partido: así la probabilidad mostrada en jugados es honesta
CREATE TABLE analisis_cache (
    partido_id     BIGINT      NOT NULL,
    modo           VARCHAR(15) NOT NULL,
    json_resultado LONGTEXT    NOT NULL,
    calculado_en   DATETIME(6) NOT NULL,
    PRIMARY KEY (partido_id, modo),
    CONSTRAINT fk_analisis_partido FOREIGN KEY (partido_id) REFERENCES partidos (id) ON DELETE CASCADE
);
