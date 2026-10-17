package pe.justaapuesta.historial;

import java.util.List;

import pe.justaapuesta.historial.HistorialDtos.FdPartido;

/**
 * Segunda fuente gratis: historial de la temporada (resultados y calendario) de las grandes ligas.
 * Hoy la implementa football-data.org. Puede lanzar ProveedorException.
 */
public interface HistorialProvider {

	boolean disponible();

	/** Todos los partidos de la temporada actual de una competición (jugados y por jugar). */
	List<FdPartido> partidosDeTemporada(String codigoCompeticion);

	/** Los partidos de una temporada pasada (año en que empezó: 2025 = 2025-26). Vacío si la fuente no los da. */
	default List<FdPartido> partidosDeTemporada(String codigoCompeticion, int temporada) {
		return List.of();
	}
}
