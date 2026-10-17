package pe.justaapuesta.proveedor;

import java.time.LocalDate;
import java.util.List;

import pe.justaapuesta.proveedor.ProveedorDtos.EstadisticaProv;
import pe.justaapuesta.proveedor.ProveedorDtos.PartidoProv;

/**
 * Fuente de datos de fútbol. Hoy la implementa API-Football (plan gratis); se puede cambiar sin tocar el resto.
 * Solo usa consultas que el plan gratis permite: partidos por fecha, estadísticas de un partido y H2H.
 * Todas las llamadas pueden lanzar {@link ProveedorException} (sin clave, cuota agotada, error del proveedor).
 */
public interface FootballDataProvider {

	/** false si no hay API key configurada. */
	boolean disponible();

	/** Todos los partidos de un día (hora de Lima), de todas las ligas. */
	List<PartidoProv> partidosDelDia(LocalDate fecha);

	/** Estadísticas finales de un partido (una por equipo; vacía si el proveedor no tiene). */
	List<EstadisticaProv> estadisticas(long partidoId);

	/** Enfrentamientos entre dos equipos. */
	List<PartidoProv> enfrentamientos(long equipoA, long equipoB);
}
