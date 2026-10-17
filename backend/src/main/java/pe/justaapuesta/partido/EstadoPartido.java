package pe.justaapuesta.partido;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public enum EstadoPartido {
	JUGADO,
	HOY,
	PROXIMO,
	EN_VIVO,
	SUSPENDIDO;

	/**
	 * Traduce el estado de API-Football. Los partidos por jugar son HOY o PROXIMO según la fecha en Lima.
	 * https://www.api-football.com/documentation-v3#tag/Fixtures (status.short)
	 */
	public static EstadoPartido desdeProveedor(String corto, Instant fecha, ZoneId zona, LocalDate hoy) {
		return switch (corto == null ? "" : corto) {
			case "FT", "AET", "PEN" -> JUGADO;
			case "1H", "HT", "2H", "ET", "BT", "P", "LIVE", "INT" -> EN_VIVO;
			case "PST", "CANC", "ABD", "SUSP", "AWD", "WO" -> SUSPENDIDO;
			default -> porFecha(fecha, zona, hoy);
		};
	}

	/** Traduce el estado de football-data.org (https://docs.football-data.org/general/v4/match.html). */
	public static EstadoPartido desdeFootballData(String estado, Instant fecha, ZoneId zona, LocalDate hoy) {
		return switch (estado == null ? "" : estado) {
			case "FINISHED", "AWARDED" -> JUGADO;
			case "IN_PLAY", "PAUSED" -> EN_VIVO;
			case "POSTPONED", "SUSPENDED", "CANCELLED" -> SUSPENDIDO;
			default -> porFecha(fecha, zona, hoy);
		};
	}

	/** Para partidos sin jugar: HOY si es hoy en Lima, PROXIMO si no. */
	public static EstadoPartido porFecha(Instant fecha, ZoneId zona, LocalDate hoy) {
		return fecha.atZone(zona).toLocalDate().equals(hoy) ? HOY : PROXIMO;
	}
}
