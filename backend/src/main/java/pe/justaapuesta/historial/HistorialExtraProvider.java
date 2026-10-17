package pe.justaapuesta.historial;

import java.util.List;
import java.util.Optional;

import pe.justaapuesta.historial.HistorialDtos.TsdbEquipo;
import pe.justaapuesta.historial.HistorialDtos.TsdbJornada;
import pe.justaapuesta.historial.HistorialDtos.TsdbPartido;

/** Fuente 3 (TheSportsDB): resultados de cualquier liga del mundo, para equipos sin historial. */
public interface HistorialExtraProvider {

	boolean disponible();

	List<TsdbEquipo> buscarEquipos(String nombre);

	/** Última jornada jugada de una liga (número y temporada). */
	Optional<TsdbJornada> ultimaJornada(long liga);

	List<TsdbPartido> jornada(long liga, int ronda, String temporada);
}
