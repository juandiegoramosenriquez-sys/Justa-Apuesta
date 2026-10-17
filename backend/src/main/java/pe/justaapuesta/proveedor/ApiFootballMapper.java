package pe.justaapuesta.proveedor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import tools.jackson.databind.JsonNode;
import pe.justaapuesta.proveedor.ProveedorDtos.EquipoProv;
import pe.justaapuesta.proveedor.ProveedorDtos.EstadisticaProv;
import pe.justaapuesta.proveedor.ProveedorDtos.JugadorPartidoProv;
import pe.justaapuesta.proveedor.ProveedorDtos.LigaProv;
import pe.justaapuesta.proveedor.ProveedorDtos.PartidoProv;

/**
 * Traduce las respuestas JSON de API-Football (v3) a nuestros DTOs. Tolera campos faltantes o nulos.
 */
final class ApiFootballMapper {

	private ApiFootballMapper() {
	}

	/** API-Football responde 200 aunque haya error: el detalle viene en "errors" (objeto o lista). */
	static String error(JsonNode raiz) {
		JsonNode errores = raiz.path("errors");
		if (errores.isMissingNode() || errores.isNull() || errores.isEmpty()) {
			return null;
		}
		StringJoiner j = new StringJoiner(" ");
		for (JsonNode e : errores.values()) {
			j.add(e.asString(""));
		}
		return j.toString().isBlank() ? errores.toString() : j.toString();
	}

	/** Respuesta de /fixtures/statistics?fixture=ID: una entrada por equipo. */
	static List<EstadisticaProv> estadisticasDePartido(JsonNode raiz) {
		return estadisticas(raiz.path("response"));
	}

	static List<PartidoProv> partidos(JsonNode raiz) {
		List<PartidoProv> lista = new ArrayList<>();
		for (JsonNode n : raiz.path("response")) {
			lista.add(partido(n));
		}
		return lista;
	}

	static PartidoProv partido(JsonNode n) {
		JsonNode f = n.path("fixture");
		JsonNode l = n.path("league");
		JsonNode equipos = n.path("teams");
		JsonNode goles = n.path("goals");

		LigaProv liga = new LigaProv(l.path("id").asLong(), l.path("name").asString(""), texto(l.path("country")),
				texto(l.path("logo")), entero(l.path("season")));
		EquipoProv local = equipo(equipos.path("home"));
		EquipoProv visita = equipo(equipos.path("away"));

		return new PartidoProv(
				f.path("id").asLong(),
				liga,
				OffsetDateTime.parse(f.path("date").asString()).toInstant(),
				texto(f.path("venue").path("name")),
				f.path("status").path("short").asString(""),
				local,
				visita,
				entero(goles.path("home")),
				entero(goles.path("away")),
				n.has("statistics") ? estadisticas(n.path("statistics")) : null,
				n.has("players") ? jugadores(n.path("players")) : null);
	}

	private static EquipoProv equipo(JsonNode t) {
		return new EquipoProv(t.path("id").asLong(), t.path("name").asString(""), texto(t.path("logo")), null);
	}

	private static List<EstadisticaProv> estadisticas(JsonNode lista) {
		List<EstadisticaProv> out = new ArrayList<>();
		for (JsonNode porEquipo : lista) {
			JsonNode items = porEquipo.path("statistics");
			if (items.isEmpty()) {
				continue; // el proveedor no tiene estadísticas de este partido
			}
			// Con cobertura, API-Football pone null cuando el valor es 0 (por ejemplo, "Red Cards": null)
			Integer corners = 0, amarillas = 0, rojas = 0, aPuerta = 0, totales = 0;
			BigDecimal posesion = null;
			for (JsonNode s : items) {
				JsonNode v = s.path("value");
				switch (s.path("type").asString("")) {
					case "Corner Kicks" -> corners = ceroSiNulo(v);
					case "Yellow Cards" -> amarillas = ceroSiNulo(v);
					case "Red Cards" -> rojas = ceroSiNulo(v);
					case "Shots on Goal" -> aPuerta = ceroSiNulo(v);
					case "Total Shots" -> totales = ceroSiNulo(v);
					case "Ball Possession" -> posesion = porcentaje(v);
					default -> {
						// otras estadísticas no se usan
					}
				}
			}
			out.add(new EstadisticaProv(porEquipo.path("team").path("id").asLong(), corners, amarillas, rojas, aPuerta,
					totales, posesion));
		}
		return out;
	}

	private static List<JugadorPartidoProv> jugadores(JsonNode lista) {
		List<JugadorPartidoProv> out = new ArrayList<>();
		for (JsonNode porEquipo : lista) {
			long equipoId = porEquipo.path("team").path("id").asLong();
			for (JsonNode p : porEquipo.path("players")) {
				JsonNode st = p.path("statistics").path(0);
				JsonNode juego = st.path("games");
				int minutos = cero(juego.path("minutes"));
				if (minutos == 0) {
					continue; // no jugó
				}
				out.add(new JugadorPartidoProv(equipoId, p.path("player").path("id").asLong(),
						p.path("player").path("name").asString(""), texto(juego.path("position")),
						entero(juego.path("number")), minutos, cero(st.path("goals").path("total")),
						cero(st.path("shots").path("on")), cero(st.path("cards").path("yellow")),
						cero(st.path("cards").path("red"))));
			}
		}
		return out;
	}

	private static String texto(JsonNode n) {
		return n.isMissingNode() || n.isNull() ? null : n.asString(null);
	}

	/** Número o texto numérico ("5"); null si no hay dato. */
	static Integer entero(JsonNode n) {
		if (n.isNumber()) {
			return n.asInt();
		}
		if (n.isString()) {
			try {
				return Integer.valueOf(n.asString().trim());
			} catch (NumberFormatException e) {
				return null;
			}
		}
		return null;
	}

	private static Integer ceroSiNulo(JsonNode n) {
		return n.isNull() ? Integer.valueOf(0) : entero(n);
	}

	private static int cero(JsonNode n) {
		Integer v = entero(n);
		return v == null ? 0 : v;
	}

	/** "55%" -> 55.00 */
	private static BigDecimal porcentaje(JsonNode n) {
		if (n.isNumber()) {
			return BigDecimal.valueOf(n.asDouble());
		}
		if (n.isString()) {
			try {
				return new BigDecimal(n.asString().replace("%", "").trim());
			} catch (NumberFormatException e) {
				return null;
			}
		}
		return null;
	}
}
