package pe.justaapuesta.historial;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.RequiredArgsConstructor;
import pe.justaapuesta.comun.Normalizador;
import pe.justaapuesta.config.JustaProperties;
import pe.justaapuesta.historial.HistorialDtos.TsdbEquipo;
import pe.justaapuesta.historial.HistorialDtos.TsdbJornada;
import pe.justaapuesta.historial.HistorialDtos.TsdbPartido;
import pe.justaapuesta.partido.Equipo;
import pe.justaapuesta.partido.EquipoRepository;
import pe.justaapuesta.partido.EquiposFamosos;
import pe.justaapuesta.partido.EstadoPartido;
import pe.justaapuesta.partido.Liga;
import pe.justaapuesta.partido.LigaRepository;
import pe.justaapuesta.partido.Partido;
import pe.justaapuesta.partido.PartidoRepository;
import pe.justaapuesta.proveedor.ProveedorException;
import pe.justaapuesta.sync.BusquedaCache;
import pe.justaapuesta.sync.BusquedaCacheRepository;

/**
 * Fuente 3 (TheSportsDB): completa el historial de goles de los equipos que van a jugar y casi no tienen partidos
 * guardados (Celtic, Sturm Graz, Pafos...). Busca la liga local de cada uno y baja sus últimas jornadas completas,
 * que de paso sirven para todos los equipos de esa liga.
 * <ul>
 * <li>Ligas, equipos y partidos de TheSportsDB se guardan con id negativo (-(1.000.000.000 + id)).</li>
 * <li>Cada equipo de TheSportsDB se une con uno nuestro si es claramente el mismo nombre; si no, se crea aparte
 * y se une por nombre más adelante.</li>
 * <li>Un partido que ya está guardado desde otra fuente (mismos equipos, ±36 h) no se repite.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class TheSportsDbService {

	private static final Logger log = LoggerFactory.getLogger(TheSportsDbService.class);
	static final long DESPLAZAMIENTO = 1_000_000_000L;
	/** Partidos jugados que se buscan para cada equipo (el análisis pide al menos 3). */
	static final int DESEADOS = 5;
	private static final Duration MISMO_PARTIDO = Duration.ofHours(36);
	private static final Duration PROXIMOS = Duration.ofDays(8);

	private final HistorialExtraProvider fuente;
	private final EquipoRepository equipoRepository;
	private final LigaRepository ligaRepository;
	private final PartidoRepository partidoRepository;
	private final EquiposFamosos famosos;
	private final TransactionTemplate tx;
	private final JustaProperties props;
	private final BusquedaCacheRepository busquedas;

	public boolean disponible() {
		return fuente.disponible();
	}

	/** @return resumen para el log */
	public String completar() {
		JustaProperties.TheSportsDb c = props.theSportsDb();
		List<Equipo> faltan = equiposSinHistorial(c.equiposPorEjecucion());
		if (faltan.isEmpty()) {
			return "todos los equipos de los próximos partidos ya tienen historial";
		}
		// 1. Cada equipo con su id y su liga en TheSportsDB
		Map<Long, Long> conocidos = new HashMap<>(); // id TheSportsDB -> id nuestro
		Set<Long> ambiguos = new HashSet<>(); // dos equipos nuestros con el mismo de TheSportsDB: no se adivina
		Set<Long> ligas = new LinkedHashSet<>();
		int errores = 0;
		for (Equipo e : faltan) {
			try {
				elegir(fuente.buscarEquipos(e.getNombre()), e).ifPresent(t -> {
					Long antes = conocidos.putIfAbsent(t.id(), e.getId());
					if (antes != null && !antes.equals(e.getId())) {
						ambiguos.add(t.id());
					}
					if (t.liga() != null) {
						ligas.add(t.liga());
					}
				});
			} catch (ProveedorException ex) {
				errores++;
				log.debug("TheSportsDB no encontró {}: {}", e.getNombre(), ex.getMessage());
			}
		}
		ambiguos.forEach(conocidos::remove);
		// 2. Últimas jornadas de cada liga
		int partidos = 0, ligasBajadas = 0;
		for (Long liga : ligas.stream().limit(c.ligasPorEjecucion()).toList()) {
			try {
				Optional<TsdbJornada> ultima = fuente.ultimaJornada(liga);
				if (ultima.isEmpty()) {
					continue;
				}
				List<TsdbPartido> eventos = new ArrayList<>();
				for (int r = ultima.get().ronda(); r > 0 && r > ultima.get().ronda() - c.rondas(); r--) {
					eventos.addAll(fuente.jornada(liga, r, ultima.get().temporada()));
				}
				partidos += tx.execute(s -> guardar(eventos, conocidos));
				ligasBajadas++;
			} catch (ProveedorException ex) {
				errores++;
				log.warn("TheSportsDB liga {}: {}", liga, ex.getMessage());
			}
		}
		return "%d equipos sin historial, %d ligas bajadas, %d partidos nuevos%s".formatted(faltan.size(), ligasBajadas,
				partidos, errores == 0 ? "" : ", " + errores + " consultas con error");
	}

	/**
	 * Búsqueda bajo demanda: cuando el usuario busca un equipo que no tenemos (Boca, Universitario...), se busca en
	 * TheSportsDB y se bajan las últimas jornadas y las 2 siguientes de su liga. Se recuerda 7 días por búsqueda.
	 *
	 * @return true si se guardó algo nuevo
	 */
	public boolean buscarYBajar(String texto) {
		String clave = "tsdb:" + Normalizador.normalizar(texto);
		Instant ahora = Instant.now();
		boolean reciente = busquedas.findById(clave)
				.filter(b -> b.getBuscadoEn().isAfter(ahora.minus(Duration.ofDays(7)))).isPresent();
		if (reciente || !fuente.disponible()) {
			return false;
		}
		busquedas.save(new BusquedaCache(clave, ahora));

		// Los equipos que claramente son lo que se buscó (sin juveniles ni femeninos), máximo 2
		List<TsdbEquipo> equipos = fuente.buscarEquipos(texto).stream()
				.filter(t -> !NombresEquipos.esJuvenilOFemenino(t.nombre()) && t.liga() != null)
				.filter(t -> relevancia(texto, t) >= 2)
				.sorted(Comparator.comparingInt((TsdbEquipo t) -> -relevancia(texto, t)))
				.limit(2)
				.toList();
		JustaProperties.TheSportsDb c = props.theSportsDb();
		int nuevos = 0;
		for (Long liga : equipos.stream().map(TsdbEquipo::liga).distinct().toList()) {
			Optional<TsdbJornada> ultima = fuente.ultimaJornada(liga);
			if (ultima.isEmpty()) {
				continue;
			}
			List<TsdbPartido> eventos = new ArrayList<>();
			int r0 = ultima.get().ronda();
			for (int r = r0 + 2; r > 0 && r > r0 - c.rondas(); r--) {
				eventos.addAll(fuente.jornada(liga, r, ultima.get().temporada()));
			}
			nuevos += tx.execute(s -> guardar(eventos, new HashMap<>(), true));
		}
		log.info("TheSportsDB búsqueda '{}': {} equipos, {} partidos nuevos", texto, equipos.size(), nuevos);
		return nuevos > 0;
	}

	private static int relevancia(String texto, TsdbEquipo t) {
		List<String> nombres = new ArrayList<>(t.otrosNombres());
		nombres.add(t.nombre());
		return NombresEquipos.relevancia(texto, String.join(NombresEquipos.SEPARADOR,
				nombres.stream().map(Normalizador::normalizar).toList()));
	}

	/**
	 * Equipos de los próximos 8 días con menos de 5 jugados guardados: primero los que juegan hoy, y dentro de cada
	 * día los de ligas y copas principales.
	 */
	List<Equipo> equiposSinHistorial(int maximo) {
		Instant ahora = Instant.now();
		return tx.execute(s -> {
			Map<Long, Equipo> equipos = new LinkedHashMap<>();
			partidoRepository.entre(ahora, ahora.plus(PROXIMOS)).stream()
					.sorted(Comparator.comparing((Partido p) -> LocalDate.ofInstant(p.getFechaHoraUtc(), props.zona()))
							.thenComparingInt(p -> famosos.esLigaPrincipal(p.getLiga().getProveedorId()) ? 0 : 1))
					.forEach(p -> {
						equipos.putIfAbsent(p.getLocal().getId(), p.getLocal());
						equipos.putIfAbsent(p.getVisita().getId(), p.getVisita());
					});
			return equipos.values().stream()
					.filter(e -> !NombresEquipos.esJuvenilOFemenino(e.getNombre()))
					.filter(e -> partidoRepository.jugadosDeEquipo(e.getId(), ahora, PageRequest.of(0, DESEADOS)).size()
							< DESEADOS)
					.limit(maximo)
					.toList();
		});
	}

	/** El resultado de la búsqueda que es claramente nuestro equipo (y uno solo). */
	static Optional<TsdbEquipo> elegir(List<TsdbEquipo> resultados, Equipo nuestro) {
		List<TsdbEquipo> iguales = resultados.stream().filter(t -> {
			List<String> nombres = new ArrayList<>(t.otrosNombres());
			nombres.add(t.nombre());
			return nombres.stream().anyMatch(n -> NombresEquipos.similitudConVariantes(n,
					nuestro.getNombreNormalizado()) >= NombresEquipos.IGUAL);
		}).toList();
		return iguales.size() == 1 ? Optional.of(iguales.get(0)) : Optional.empty();
	}

	int guardar(List<TsdbPartido> eventos, Map<Long, Long> conocidos) {
		return guardar(eventos, conocidos, false);
	}

	/** @param conProximos también guarda los partidos por jugar (búsqueda bajo demanda), no solo el historial */
	int guardar(List<TsdbPartido> eventos, Map<Long, Long> conocidos, boolean conProximos) {
		if (eventos.isEmpty()) {
			return 0;
		}
		Instant ahora = Instant.now();
		Liga liga = liga(eventos.get(0));
		List<Equipo> todos = equipoRepository.findAll();
		Map<Long, Equipo> porId = new HashMap<>();
		Map<Long, Equipo> porProveedor = new HashMap<>();
		todos.forEach(e -> {
			porId.put(e.getId(), e);
			porProveedor.put(e.getProveedorId(), e);
		});
		Map<Long, Partido> existentes = new HashMap<>();
		partidoRepository.findByProveedorIdIn(eventos.stream().map(ev -> -(DESPLAZAMIENTO + ev.id())).toList())
				.forEach(p -> existentes.put(p.getProveedorId(), p));

		int n = 0;
		LocalDate hoy = LocalDate.now(props.zona());
		for (TsdbPartido ev : eventos) {
			boolean jugado = ev.golesLocal() != null && ev.golesVisita() != null && ev.fecha().isBefore(ahora);
			if (!jugado && (!conProximos || ev.fecha().isBefore(ahora))) {
				continue; // sin marcador: solo interesa si es un partido por jugar y se pidieron
			}
			Partido p = existentes.get(-(DESPLAZAMIENTO + ev.id()));
			if (p != null) {
				p.setFechaHoraUtc(ev.fecha());
				if (jugado) {
					p.setGolesLocal(ev.golesLocal());
					p.setGolesVisita(ev.golesVisita());
					p.setEstado(EstadoPartido.JUGADO);
					p.setEstadoProveedor("FT");
				}
				p.setActualizadoEn(ahora);
				continue;
			}
			Equipo local = equipo(ev.localId(), ev.local(), liga, conocidos, todos, porId, porProveedor);
			Equipo visita = equipo(ev.visitaId(), ev.visita(), liga, conocidos, todos, porId, porProveedor);
			if (local.getId().equals(visita.getId()) || partidoRepository.existsByLocal_IdAndVisita_IdAndFechaHoraUtcBetween(
					local.getId(), visita.getId(), ev.fecha().minus(MISMO_PARTIDO), ev.fecha().plus(MISMO_PARTIDO))) {
				continue; // ya lo trajo otra fuente
			}
			p = new Partido();
			p.setProveedorId(-(DESPLAZAMIENTO + ev.id()));
			p.setLiga(liga);
			p.setLocal(local);
			p.setVisita(visita);
			p.setFechaHoraUtc(ev.fecha());
			p.setEstadio(recortar(ev.estadio(), 120));
			p.setEstado(jugado ? EstadoPartido.JUGADO : EstadoPartido.porFecha(ev.fecha(), props.zona(), hoy));
			p.setEstadoProveedor(jugado ? "FT" : "NS");
			p.setGolesLocal(jugado ? ev.golesLocal() : null);
			p.setGolesVisita(jugado ? ev.golesVisita() : null);
			p.setTemporada(temporada(ev.temporada()));
			p.setActualizadoEn(ahora);
			p.setDetalleSync(true); // TheSportsDB gratis no trae corners, tarjetas ni remates
			partidoRepository.save(p);
			n++;
		}
		return n;
	}

	/** Nuestro equipo para un id de TheSportsDB: ya conocido, ya guardado, el mismo nombre sin dudas, o uno nuevo. */
	private Equipo equipo(long idTsdb, String nombre, Liga liga, Map<Long, Long> conocidos, List<Equipo> todos,
			Map<Long, Equipo> porId, Map<Long, Equipo> porProveedor) {
		Long id = conocidos.get(idTsdb);
		if (id != null && porId.containsKey(id)) {
			return porId.get(id);
		}
		Equipo e = porProveedor.get(-(DESPLAZAMIENTO + idTsdb));
		if (e == null) {
			List<Equipo> iguales = todos.stream()
					.filter(x -> !NombresEquipos.esJuvenilOFemenino(x.getNombre()))
					.filter(x -> NombresEquipos.similitudConVariantes(nombre, x.getNombreNormalizado()) >= NombresEquipos.IGUAL)
					.filter(x -> NombresEquipos.mismoPais(x.pais(), liga.getPais()))
					.toList();
			List<Equipo> deApiFootball = iguales.stream().filter(x -> x.getProveedorId() > 0).toList();
			e = deApiFootball.size() == 1 ? deApiFootball.get(0) : iguales.size() == 1 ? iguales.get(0) : null;
		}
		if (e == null) {
			e = new Equipo();
			e.setProveedorId(-(DESPLAZAMIENTO + idTsdb));
			e.setNombre(recortar(nombre, 100));
			e.setNombreNormalizado(recortar(Normalizador.normalizar(nombre), 100));
			e.setLiga(liga);
			e.setActualizadoEn(Instant.now());
			e = equipoRepository.save(e);
			todos.add(e);
			porId.put(e.getId(), e);
			porProveedor.put(e.getProveedorId(), e);
		}
		conocidos.put(idTsdb, e.getId());
		return e;
	}

	/** Nombres de TheSportsDB -> los nombres de la app (los mismos que en application.yml). */
	static final Map<String, String> NOMBRES_LIGAS = Map.ofEntries(
			Map.entry("peruvian primera division", "Liga 1 Perú"),
			Map.entry("argentinian primera division", "Liga Profesional Argentina"),
			Map.entry("american major league soccer", "MLS"),
			Map.entry("english premier league", "Premier League"),
			Map.entry("spanish la liga", "LaLiga"),
			Map.entry("italian serie a", "Serie A"),
			Map.entry("german bundesliga", "Bundesliga"),
			Map.entry("french ligue 1", "Ligue 1"),
			Map.entry("uefa champions league", "Champions League"),
			Map.entry("copa libertadores", "Copa Libertadores"),
			Map.entry("portuguese primeira liga", "Primeira Liga"),
			Map.entry("dutch eredivisie", "Eredivisie"),
			Map.entry("brazilian serie a", "Campeonato Brasileiro Série A"),
			Map.entry("chilean primera division", "Primera División de Chile"),
			Map.entry("colombian primera a", "Liga BetPlay"),
			Map.entry("uruguayan primera division", "Primera División de Uruguay"),
			Map.entry("mexican primera league", "Liga MX"));

	static String nombreLiga(String deTheSportsDb) {
		String n = deTheSportsDb == null ? "Liga" : deTheSportsDb;
		return NOMBRES_LIGAS.getOrDefault(Normalizador.normalizar(n), n);
	}

	private Liga liga(TsdbPartido ejemplo) {
		long proveedorId = -(DESPLAZAMIENTO + ejemplo.liga());
		String nombreApp = nombreLiga(ejemplo.nombreLiga());
		return ligaRepository.findByProveedorIdIn(List.of(proveedorId)).stream().findFirst().map(l -> {
			// Ligas guardadas antes con el nombre en inglés de TheSportsDB
			if (!l.getNombre().equals(nombreApp)) {
				l.setNombre(recortar(nombreApp, 100));
				l.setNombreNormalizado(recortar(Normalizador.normalizar(nombreApp), 100));
			}
			return l;
		}).orElseGet(() -> {
			String nombre = nombreApp;
			Liga l = new Liga();
			l.setProveedorId(proveedorId);
			l.setNombre(recortar(nombre, 100));
			l.setNombreNormalizado(recortar(Normalizador.normalizar(nombre), 100));
			l.setPais(recortar(ejemplo.pais(), 80));
			return ligaRepository.save(l);
		});
	}

	/** "2026-2027" -> 2026 */
	private static Integer temporada(String s) {
		try {
			return s == null || s.length() < 4 ? null : Integer.valueOf(s.substring(0, 4));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String recortar(String s, int max) {
		return s == null ? null : s.length() <= max ? s : s.substring(0, max);
	}
}
