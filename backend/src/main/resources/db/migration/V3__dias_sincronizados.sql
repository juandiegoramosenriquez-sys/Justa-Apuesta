-- Plan gratis de API-Football: los partidos se bajan por fecha.
-- Aquí se anota qué días ya se descargaron para armar el historial de a pocos.
CREATE TABLE dias_sincronizados (
    fecha          DATE        NOT NULL,
    sincronizado_en DATETIME(6) NOT NULL,
    partidos       INT         NOT NULL,
    PRIMARY KEY (fecha)
);
