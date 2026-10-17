package pe.justaapuesta.historial;

import java.time.Instant;

/** Datos de football-data.org traducidos a nuestros nombres. */
public final class HistorialDtos {

	private HistorialDtos() {
	}

	/**
	 * @param nombre       nombre corto para mostrar ("Barça")
	 * @param otrosNombres nombre completo y sigla ("FC Barcelona", "FCB"), para buscar y vincular
	 */
	public record FdEquipo(long id, String nombre, String escudo, java.util.List<String> otrosNombres) {

		public FdEquipo(long id, String nombre, String escudo) {
			this(id, nombre, escudo, java.util.List.of());
		}
	}

	/**
	 * @param estado SCHEDULED, TIMED, IN_PLAY, PAUSED, FINISHED, SUSPENDED, POSTPONED, CANCELLED, AWARDED
	 */
	public record FdPartido(long id, Instant fecha, String estado, String estadio, FdEquipo local, FdEquipo visita,
			Integer golesLocal, Integer golesVisita, Integer temporada, String competicion, String pais) {
	}

	/** Equipo de TheSportsDB. @param liga id de su liga local en TheSportsDB (null si no tiene) */
	public record TsdbEquipo(long id, String nombre, java.util.List<String> otrosNombres, Long liga, String escudo) {
	}

	/** @param temporada como la escribe TheSportsDB: "2026-2027" o "2026" */
	public record TsdbJornada(int ronda, String temporada) {
	}

	/** Partido de TheSportsDB (solo goles: la versión gratis no trae corners ni tarjetas). */
	public record TsdbPartido(long id, Instant fecha, long localId, String local, long visitaId, String visita,
			Integer golesLocal, Integer golesVisita, String estadio, long liga, String nombreLiga, String pais,
			String temporada) {
	}
}
