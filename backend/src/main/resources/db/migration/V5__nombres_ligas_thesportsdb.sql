-- Ligas de TheSportsDB guardadas con su nombre en inglés: se les pone el nombre de la app.
-- (Solo las de TheSportsDB: su proveedor_id es menor a -1000000000.)
UPDATE ligas SET nombre = 'Liga 1 Perú', nombre_normalizado = 'liga 1 peru' WHERE proveedor_id < -1000000000 AND nombre = 'Peruvian Primera Division';
UPDATE ligas SET nombre = 'Liga Profesional Argentina', nombre_normalizado = 'liga profesional argentina' WHERE proveedor_id < -1000000000 AND nombre = 'Argentinian Primera Division';
UPDATE ligas SET nombre = 'MLS', nombre_normalizado = 'mls' WHERE proveedor_id < -1000000000 AND nombre = 'American Major League Soccer';
UPDATE ligas SET nombre = 'Premier League', nombre_normalizado = 'premier league' WHERE proveedor_id < -1000000000 AND nombre = 'English Premier League';
UPDATE ligas SET nombre = 'LaLiga', nombre_normalizado = 'laliga' WHERE proveedor_id < -1000000000 AND nombre = 'Spanish La Liga';
UPDATE ligas SET nombre = 'Serie A', nombre_normalizado = 'serie a' WHERE proveedor_id < -1000000000 AND nombre = 'Italian Serie A';
UPDATE ligas SET nombre = 'Bundesliga', nombre_normalizado = 'bundesliga' WHERE proveedor_id < -1000000000 AND nombre = 'German Bundesliga';
UPDATE ligas SET nombre = 'Ligue 1', nombre_normalizado = 'ligue 1' WHERE proveedor_id < -1000000000 AND nombre = 'French Ligue 1';
UPDATE ligas SET nombre = 'Champions League', nombre_normalizado = 'champions league' WHERE proveedor_id < -1000000000 AND nombre = 'UEFA Champions League';
UPDATE ligas SET nombre = 'Primeira Liga', nombre_normalizado = 'primeira liga' WHERE proveedor_id < -1000000000 AND nombre = 'Portuguese Primeira Liga';
UPDATE ligas SET nombre = 'Eredivisie', nombre_normalizado = 'eredivisie' WHERE proveedor_id < -1000000000 AND nombre = 'Dutch Eredivisie';
UPDATE ligas SET nombre = 'Campeonato Brasileiro Série A', nombre_normalizado = 'campeonato brasileiro serie a' WHERE proveedor_id < -1000000000 AND nombre = 'Brazilian Serie A';
UPDATE ligas SET nombre = 'Primera División de Chile', nombre_normalizado = 'primera division de chile' WHERE proveedor_id < -1000000000 AND nombre = 'Chilean Primera Division';
UPDATE ligas SET nombre = 'Liga BetPlay', nombre_normalizado = 'liga betplay' WHERE proveedor_id < -1000000000 AND nombre = 'Colombian Primera A';
UPDATE ligas SET nombre = 'Primera División de Uruguay', nombre_normalizado = 'primera division de uruguay' WHERE proveedor_id < -1000000000 AND nombre = 'Uruguayan Primera Division';
UPDATE ligas SET nombre = 'Liga MX', nombre_normalizado = 'liga mx' WHERE proveedor_id < -1000000000 AND nombre = 'Mexican Primera League';
