package pe.justaapuesta.partido;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Component;

import pe.justaapuesta.config.JustaProperties;

/**
 * Equipos famosos, sin lista a mano: los que en el último año jugaron la Champions, la Libertadores o una de las
 * ligas top, según los partidos guardados de las dos fuentes. Las pestañas y los Picks solo muestran sus partidos de
 * ligas y copas principales; cualquier otro equipo del mundo aparece al buscarlo.
 */
@Component
public class EquiposFamosos {

	/**
	 * Quien juega estas es famoso (ids de API-Football): Champions, Libertadores, Premier League, LaLiga, Serie A,
	 * Bundesliga, Ligue 1, Liga Portugal, Eredivisie y Brasileirão.
	 */
	static final Set<Long> LIGAS_DE_FAMOSOS = Set.of(2L, 13L, 39L, 140L, 135L, 78L, 61L, 94L, 88L, 71L);
	/** Otras copas internacionales que también se muestran: Europa League, Conference, Sudamericana y Mundial de Clubes. */
	private static final Set<Long> OTRAS_COPAS = Set.of(3L, 848L, 11L, 15L);
	private static final Duration VENTANA = Duration.ofDays(400);

	private final Set<Long> ligasPrincipales;
	private final PartidoRepository partidoRepository;

	public EquiposFamosos(JustaProperties props, PartidoRepository partidoRepository) {
		this.partidoRepository = partidoRepository;
		Set<Long> ligas = new HashSet<>(LIGAS_DE_FAMOSOS);
		ligas.addAll(OTRAS_COPAS);
		if (props.sync() != null) {
			ligas.addAll(props.sync().idsLigas());
		}
		if (props.historial() != null && props.historial().competiciones() != null) {
			props.historial().competiciones().forEach(c -> ligas.add(c.ligaApiFootball()));
		}
		this.ligasPrincipales = Set.copyOf(ligas);
	}

	/** Ids de los equipos que jugaron alguna de esas competiciones en el último año. */
	public Set<Long> equipos() {
		Set<Long> ids = new HashSet<>();
		for (Object[] fila : partidoRepository.equiposDeLigas(LIGAS_DE_FAMOSOS, Instant.now().minus(VENTANA))) {
			ids.add((Long) fila[0]);
			ids.add((Long) fila[1]);
		}
		return ids;
	}

	public boolean esLigaPrincipal(long proveedorIdLiga) {
		return ligasPrincipales.contains(proveedorIdLiga);
	}

	/** Partido de una liga o copa principal (no amistosos ni juveniles) con al menos un equipo famoso. */
	public boolean esPartidoFamoso(Partido p, Set<Long> famosos) {
		return ligasPrincipales.contains(p.getLiga().getProveedorId())
				&& (famosos.contains(p.getLocal().getId()) || famosos.contains(p.getVisita().getId()));
	}
}
