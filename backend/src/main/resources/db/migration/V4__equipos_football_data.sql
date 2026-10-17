-- Segunda fuente gratis (football-data.org): id del equipo en esa fuente.
-- Un equipo de API-Football queda "vinculado" cuando se detecta el mismo partido en las dos fuentes.
ALTER TABLE equipos ADD COLUMN fd_id BIGINT;
CREATE UNIQUE INDEX uk_equipos_fd ON equipos (fd_id);
