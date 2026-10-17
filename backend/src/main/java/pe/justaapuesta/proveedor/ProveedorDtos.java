package pe.justaapuesta.proveedor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Datos tal como llegan del proveedor, ya traducidos a nuestros nombres. No dependen de API-Football.
 */
public final class ProveedorDtos {

	private ProveedorDtos() {
	}

	public record LigaProv(long id, String nombre, String pais, String logo, Integer temporada) {
	}

	public record EquipoProv(long id, String nombre, String escudo, String estadio) {
	}

	public record EstadisticaProv(long equipoId, Integer corners, Integer amarillas, Integer rojas,
			Integer rematesAPuerta, Integer rematesTotales, BigDecimal posesion) {
	}

	/** Lo que hizo un jugador en un partido. */
	public record JugadorPartidoProv(long equipoId, long jugadorId, String nombre, String posicion, Integer dorsal,
			int minutos, int goles, int rematesAPuerta, int amarillas, int rojas) {
	}

	/**
	 * @param estadoCorto código del proveedor: NS, FT, 1H, PST...
	 * @param estadisticas null si la consulta no las incluye (solo vienen al pedir partidos por id)
	 * @param jugadores null si la consulta no los incluye
	 */
	public record PartidoProv(long id, LigaProv liga, Instant fecha, String estadio, String estadoCorto,
			EquipoProv local, EquipoProv visita, Integer golesLocal, Integer golesVisita,
			List<EstadisticaProv> estadisticas, List<JugadorPartidoProv> jugadores) {
	}
}
