package pe.justaapuesta.partido;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import pe.justaapuesta.comun.Normalizador;
import pe.justaapuesta.config.JustaProperties;
import pe.justaapuesta.proveedor.ProveedorDtos.EquipoProv;
import pe.justaapuesta.proveedor.ProveedorDtos.EstadisticaProv;
import pe.justaapuesta.proveedor.ProveedorDtos.JugadorPartidoProv;
import pe.justaapuesta.proveedor.ProveedorDtos.LigaProv;
import pe.justaapuesta.proveedor.ProveedorDtos.PartidoProv;

/**
 * Guarda en MySQL lo que llega del proveedor, sin duplicar (se identifica todo por el id del proveedor).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CatalogoService {

	private final LigaRepository ligaRepository;
	private final EquipoRepository equipoRepository;
	private final PartidoRepository partidoRepository;
	private final EstadisticaPartidoRepository estadisticaRepository;
	private final JugadorPartidoRepository jugadorRepository;
	private final JustaProperties props;

	/** Crea o actualiza equipos sueltos (resultado de una búsqueda). */
	public List<Equipo> guardarEquipos(List<EquipoProv> lista) {
		Map<Long, Equipo> equipos = equiposPorProveedor(lista);
		return lista.stream().map(e -> equipos.get(e.id())).toList();
	}

	/** Crea o actualiza partidos con sus ligas, equipos y, si vienen, estadísticas y jugadores. */
	public List<Partido> guardarPartidos(List<PartidoProv> lista) {
		if (lista.isEmpty()) {
			return List.of();
		}
		Instant ahora = Instant.now();
		LocalDate hoy = LocalDate.now(props.zona());

		Map<Long, Liga> ligas = ligasPorProveedor(lista.stream().map(PartidoProv::liga).toList());
		List<EquipoProv> equiposProv = new ArrayList<>();
		lista.forEach(p -> {
			equiposProv.add(p.local());
			equiposProv.add(p.visita());
		});
		Map<Long, Equipo> equipos = equiposPorProveedor(equiposProv);
		Map<Long, Partido> existentes = partidoRepository
				.findByProveedorIdIn(lista.stream().map(PartidoProv::id).toList()).stream()
				.collect(Collectors.toMap(Partido::getProveedorId, Function.identity()));

		List<Partido> guardados = new ArrayList<>();
		for (PartidoProv pp : lista) {
			Partido p = existentes.getOrDefault(pp.id(), new Partido());
			p.setProveedorId(pp.id());
			p.setLiga(ligas.get(pp.liga().id()));
			p.setLocal(equipos.get(pp.local().id()));
			p.setVisita(equipos.get(pp.visita().id()));
			p.setFechaHoraUtc(pp.fecha());
			p.setEstadio(pp.estadio());
			p.setEstado(EstadoPartido.desdeProveedor(pp.estadoCorto(), pp.fecha(), props.zona(), hoy));
			p.setEstadoProveedor(pp.estadoCorto());
			p.setGolesLocal(pp.golesLocal());
			p.setGolesVisita(pp.golesVisita());
			p.setTemporada(pp.liga().temporada());
			p.setActualizadoEn(ahora);
			// El equipo queda asociado a la liga en la que juega (la primera que veamos)
			if (p.getLocal().getLiga() == null) {
				p.getLocal().setLiga(p.getLiga());
			}
			if (p.getVisita().getLiga() == null) {
				p.getVisita().setLiga(p.getLiga());
			}
			partidoRepository.save(p);

			if (pp.estadisticas() != null) {
				guardarEstadisticas(p, pp.estadisticas(), equipos);
				// Solo cuenta como completo si el partido ya terminó (si no, habrá que volver a pedirlo)
				p.setDetalleSync(p.getEstado() == EstadoPartido.JUGADO);
			}
			if (pp.jugadores() != null) {
				guardarJugadores(p, pp.jugadores(), equipos);
			}
			guardados.add(p);
		}
		return guardados;
	}

	/**
	 * Guarda las estadísticas de un partido pedidas aparte (/fixtures/statistics) y lo marca como completo,
	 * aunque el proveedor no tenga datos (así no se vuelve a pedir).
	 */
	public void guardarEstadisticas(Long partidoId, List<EstadisticaProv> stats) {
		Partido p = partidoRepository.conEquipos(partidoId).orElseThrow();
		Map<Long, Equipo> equipos = Map.of(p.getLocal().getProveedorId(), p.getLocal(),
				p.getVisita().getProveedorId(), p.getVisita());
		guardarEstadisticas(p, stats, equipos);
		p.setDetalleSync(true);
		partidoRepository.save(p);
	}

	private void guardarEstadisticas(Partido p, List<EstadisticaProv> stats, Map<Long, Equipo> equipos) {
		estadisticaRepository.borrarDePartido(p.getId());
		estadisticaRepository.flush();
		for (EstadisticaProv s : stats) {
			Equipo e = equipos.get(s.equipoId());
			if (e == null) {
				continue;
			}
			EstadisticaPartido ep = new EstadisticaPartido();
			ep.setClave(new EstadisticaPartido.Clave(p.getId(), e.getId()));
			ep.setCorners(s.corners());
			ep.setTarjetasAmarillas(s.amarillas());
			ep.setTarjetasRojas(s.rojas());
			ep.setRematesAPuerta(s.rematesAPuerta());
			ep.setRematesTotales(s.rematesTotales());
			ep.setPosesion(s.posesion());
			estadisticaRepository.save(ep);
		}
	}

	private void guardarJugadores(Partido p, List<JugadorPartidoProv> jugadores, Map<Long, Equipo> equipos) {
		jugadorRepository.borrarDePartido(p.getId());
		jugadorRepository.flush();
		Set<Long> vistos = new LinkedHashSet<>();
		for (JugadorPartidoProv j : jugadores) {
			Equipo e = equipos.get(j.equipoId());
			if (e == null || !vistos.add(j.jugadorId())) {
				continue;
			}
			JugadorPartido jp = new JugadorPartido();
			jp.setClave(new JugadorPartido.Clave(p.getId(), j.jugadorId()));
			jp.setEquipoId(e.getId());
			jp.setNombre(recortar(j.nombre(), 100));
			jp.setPosicion(recortar(j.posicion(), 10));
			jp.setDorsal(j.dorsal());
			jp.setMinutos(j.minutos());
			jp.setGoles(j.goles());
			jp.setRematesAPuerta(j.rematesAPuerta());
			jp.setAmarillas(j.amarillas());
			jp.setRojas(j.rojas());
			jugadorRepository.save(jp);
		}
	}

	private Map<Long, Liga> ligasPorProveedor(List<LigaProv> lista) {
		Map<Long, LigaProv> unicas = new HashMap<>();
		lista.forEach(l -> unicas.putIfAbsent(l.id(), l));
		Map<Long, Liga> existentes = ligaRepository.findByProveedorIdIn(unicas.keySet()).stream()
				.collect(Collectors.toMap(Liga::getProveedorId, Function.identity()));
		for (LigaProv lp : unicas.values()) {
			Liga l = existentes.computeIfAbsent(lp.id(), id -> new Liga());
			l.setProveedorId(lp.id());
			String nombre = props.nombreLiga(lp.id()).orElse(lp.nombre());
			l.setNombre(recortar(nombre, 100));
			l.setNombreNormalizado(recortar(Normalizador.normalizar(nombre), 100));
			l.setPais(recortar(lp.pais(), 80));
			if (lp.logo() != null) {
				l.setLogoUrl(recortar(lp.logo(), 255));
			}
			ligaRepository.save(l);
		}
		return existentes;
	}

	private Map<Long, Equipo> equiposPorProveedor(List<EquipoProv> lista) {
		Map<Long, EquipoProv> unicos = new HashMap<>();
		lista.forEach(e -> unicos.putIfAbsent(e.id(), e));
		Map<Long, Equipo> existentes = equipoRepository.findByProveedorIdIn(unicos.keySet()).stream()
				.collect(Collectors.toMap(Equipo::getProveedorId, Function.identity()));
		Instant ahora = Instant.now();
		for (EquipoProv ep : unicos.values()) {
			Equipo e = existentes.computeIfAbsent(ep.id(), id -> new Equipo());
			e.setProveedorId(ep.id());
			e.setNombre(recortar(ep.nombre(), 100));
			// Si ya está vinculado con football-data.org, conserva también sus otros nombres ("psg", "barca"...)
			e.setNombreNormalizado(recortar(e.getFdId() == null ? Normalizador.normalizar(ep.nombre())
					: pe.justaapuesta.historial.NombresEquipos.variantes(ep.nombre(),
							pe.justaapuesta.historial.NombresEquipos.variantesDe(e.getNombreNormalizado())), 100));
			if (ep.escudo() != null) {
				e.setEscudoUrl(recortar(ep.escudo(), 255));
			}
			if (ep.estadio() != null) {
				e.setEstadio(recortar(ep.estadio(), 120));
			}
			e.setActualizadoEn(ahora);
			equipoRepository.save(e);
		}
		return existentes;
	}

	private static String recortar(String s, int max) {
		return s == null ? null : s.length() <= max ? s : s.substring(0, max);
	}
}
