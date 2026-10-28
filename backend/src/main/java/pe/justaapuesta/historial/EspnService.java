package pe.justaapuesta.historial;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.RequiredArgsConstructor;
import pe.justaapuesta.comun.Normalizador;
import pe.justaapuesta.config.JustaProperties;
import pe.justaapuesta.partido.Equipo;
import pe.justaapuesta.partido.EquipoRepository;
import pe.justaapuesta.partido.EstadisticaPartido;
import pe.justaapuesta.partido.EstadisticaPartidoRepository;
import pe.justaapuesta.partido.EstadoPartido;
import pe.justaapuesta.partido.Liga;
import pe.justaapuesta.partido.LigaRepository;
import pe.justaapuesta.partido.Partido;
import pe.justaapuesta.partido.PartidoRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fuente 5 (ESPN, gratis y sin clave): los partidos de hoy y de los próximos días de muchas ligas y copas (Europa
 * League, Libertadores, Liga MX, Colombia...), con su historial de los últimos meses. De cada partido jugado trae
 * también corners, remates, remates a puerta y tarjetas, así salen esos mercados en todas las ligas.
 * <ul>
 * <li>Ligas, equipos y partidos de ESPN se guardan con id negativo (-(2.000.000.000 + id)).</li>
 * <li>Un equipo se une con uno nuestro si es claramente el mismo nombre (y del mismo país); si no, se crea aparte.</li>
 * <li>Un partido que ya trajo otra fuente (mismos equipos, ±36 h) no se repite: solo se le agregan las
 * estadísticas si no las tenía.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class EspnService {

	private static final Logger log = LoggerFactory.getLogger(EspnService.class);
	static final long DESPLAZAMIENTO = 2_000_000_000L;
	private static final String URL = "https://site.api.espn.com/apis/site/v2/sports/soccer/%s/scoreboard?dates=%s&limit=1000";
	private static final Duration MISMO_PARTIDO = Duration.ofHours(36);
	/** Meses de historial que se bajan una vez al día (el análisis pide al menos 3 partidos por equipo). */
	private static final int MESES_HISTORIAL = 3;
	private static final ObjectMapper JSON = JsonMapper.builder().build();

	/** Liga de ESPN: código, nombre en la app y país (en inglés, como el resto de fuentes; vacío en copas). */
	record LigaEspn(String codigo, String nombre, String pais) {
	}

	static final List<LigaEspn> LIGAS = List.of(
			new LigaEspn("uefa.champions", "Champions League", ""),
			new LigaEspn("uefa.europa", "Europa League", ""),
			new LigaEspn("uefa.europa.conf", "Conference League", ""),
			new LigaEspn("conmebol.libertadores", "Copa Libertadores", ""),
			new LigaEspn("conmebol.sudamericana", "Copa Sudamericana", ""),
			new LigaEspn("concacaf.champions", "Concacaf Champions Cup", ""),
			new LigaEspn("eng.1", "Premier League", "England"),
			new LigaEspn("eng.2", "Championship", "England"),
			new LigaEspn("eng.3", "League One", "England"),
			new LigaEspn("esp.1", "LaLiga", "Spain"),
			new LigaEspn("esp.2", "LaLiga 2", "Spain"),
			new LigaEspn("ger.1", "Bundesliga", "Germany"),
			new LigaEspn("ger.2", "2. Bundesliga", "Germany"),
			new LigaEspn("ita.1", "Serie A", "Italy"),
			new LigaEspn("ita.2", "Serie B", "Italy"),
			new LigaEspn("fra.1", "Ligue 1", "France"),
			new LigaEspn("fra.2", "Ligue 2", "France"),
			new LigaEspn("por.1", "Primeira Liga", "Portugal"),
			new LigaEspn("ned.1", "Eredivisie", "Netherlands"),
			new LigaEspn("bel.1", "Pro League Bélgica", "Belgium"),
			new LigaEspn("tur.1", "Süper Lig", "Turkey"),
			new LigaEspn("sco.1", "Premiership Escocia", "Scotland"),
			new LigaEspn("gre.1", "Superliga Grecia", "Greece"),
			new LigaEspn("aut.1", "Bundesliga Austria", "Austria"),
			new LigaEspn("sui.1", "Superliga Suiza", "Switzerland"),
			new LigaEspn("den.1", "Superliga Dinamarca", "Denmark"),
			new LigaEspn("ksa.1", "Liga Saudí", "Saudi Arabia"),
			new LigaEspn("usa.1", "MLS", "USA"),
			new LigaEspn("mex.1", "Liga MX", "Mexico"),
			new LigaEspn("arg.1", "Liga Profesional Argentina", "Argentina"),
			new LigaEspn("bra.1", "Campeonato Brasileiro Série A", "Brazil"),
			new LigaEspn("bra.2", "Brasileirão Série B", "Brazil"),
			new LigaEspn("col.1", "Liga BetPlay", "Colombia"),
			new LigaEspn("chi.1", "Primera División de Chile", "Chile"),
			new LigaEspn("per.1", "Liga 1 Perú", "Peru"),
			new LigaEspn("uru.1", "Primera División de Uruguay", "Uruguay"),
			new LigaEspn("ecu.1", "LigaPro Ecuador", "Ecuador"),
			new LigaEspn("par.1", "Primera División de Paraguay", "Paraguay"),
			new LigaEspn("bol.1", "División Profesional Bolivia", "Bolivia"));

	/** Estadísticas de un equipo en un partido (null si ESPN no las trae). */
	record StatsEspn(Integer corners, Integer amarillas, Integer rojas, Integer rematesAPuerta, Integer rematesTotales,
			BigDecimal posesion) {
		boolean vacias() {
			return corners == null && rematesTotales == null;
		}
	}

	record EventoEspn(long id, long ligaId, String logoLiga, Instant fecha, String estado, String estadio,
			long localId, String local, String logoLocal, long visitaId, String visita, String logoVisita,
			Integer golesLocal, Integer golesVisita, StatsEspn statsLocal, StatsEspn statsVisita) {
	}

	private final EquipoRepository equipoRepository;
	private final LigaRepository ligaRepository;
	private final PartidoRepository partidoRepository;
	private final EstadisticaPartidoRepository estadisticaRepository;
	private final TransactionTemplate tx;
	private final JustaProperties props;
	private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(15)).build();

	/**
	 * @param conHistorial además del mes actual, baja los meses anteriores (una vez al día)
	 * @return resumen para el log
	 */
	public String sincronizar(boolean conHistorial) {
		LocalDate hoy = LocalDate.now(props.zona());
		Set<YearMonth> meses = new LinkedHashSet<>();
		meses.add(YearMonth.from(hoy));
		// El mes siguiente también: a fin de mes, los partidos de la semana que viene ya son del otro mes
		meses.add(YearMonth.from(hoy).plusMonths(1));
		if (conHistorial) {
			for (int i = 1; i <= MESES_HISTORIAL; i++) {
				meses.add(YearMonth.from(hoy).minusMonths(i));
			}
		}
		// Partidos de otras fuentes en esas fechas (se cargan una vez: un mismo equipo puede estar guardado dos veces,
		// una por fuente, así que se comparan por nombre y hora, no por id)
		YearMonth primero = meses.stream().min(YearMonth::compareTo).orElseThrow();
		YearMonth ultimo = meses.stream().max(YearMonth::compareTo).orElseThrow();
		Map<LocalDate, List<Partido>> deOtras = tx.execute(s -> porDia(partidoRepository.entre(
				primero.atDay(1).minusDays(2).atStartOfDay(props.zona()).toInstant(),
				ultimo.atEndOfMonth().plusDays(2).atStartOfDay(props.zona()).toInstant()).stream()
				.filter(p -> p.getProveedorId() == null || p.getProveedorId() > -DESPLAZAMIENTO)
				.toList()));
		int nuevos = 0, conStats = 0, errores = 0;
		for (LigaEspn liga : LIGAS) {
			List<EventoEspn> eventos = new ArrayList<>();
			for (YearMonth m : meses) {
				try {
					eventos.addAll(leer(bajar(URL.formatted(liga.codigo(), "%04d%02d".formatted(m.getYear(), m.getMonthValue())))));
				} catch (Exception e) {
					errores++;
					log.debug("ESPN {} {}: {}", liga.codigo(), m, e.getMessage());
				}
			}
			if (eventos.isEmpty()) {
				continue;
			}
			try {
				int[] r = tx.execute(s -> guardar(liga, eventos, deOtras));
				nuevos += r[0];
				conStats += r[1];
			} catch (RuntimeException e) {
				errores++;
				log.warn("ESPN {}: no se pudo guardar: {}", liga.codigo(), e.getMessage());
			}
		}
		return "%d partidos nuevos, %d con estadísticas nuevas%s".formatted(nuevos, conStats,
				errores == 0 ? "" : ", " + errores + " consultas con error");
	}

	private String bajar(String url) throws Exception {
		HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(40))
				.header("User-Agent", "JustaApuesta/1.0").build(), HttpResponse.BodyHandlers.ofString());
		if (r.statusCode() != 200) {
			throw new IllegalStateException("HTTP " + r.statusCode());
		}
		return r.body();
	}

	/** Lee la respuesta del scoreboard de una liga. */
	static List<EventoEspn> leer(String cuerpo) {
		JsonNode raiz = JSON.readTree(cuerpo);
		JsonNode l = raiz.path("leagues").path(0);
		long ligaId = l.path("id").asLong(0);
		String logoLiga = l.path("logos").path(0).path("href").asString(null);
		List<EventoEspn> out = new ArrayList<>();
		for (JsonNode ev : raiz.path("events")) {
			JsonNode c = ev.path("competitions").path(0);
			JsonNode local = null, visita = null;
			for (JsonNode t : c.path("competitors")) {
				if ("home".equals(t.path("homeAway").asString(""))) {
					local = t;
				} else {
					visita = t;
				}
			}
			if (local == null || visita == null) {
				continue;
			}
			Instant fecha;
			try {
				// "2026-10-22T16:45Z" (sin segundos)
				fecha = java.time.OffsetDateTime.parse(ev.path("date").asString(""),
						java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
			} catch (RuntimeException e) {
				continue;
			}
			JsonNode tipo = ev.path("status").path("type");
			String estado = tipo.path("completed").asBoolean(false) ? "post"
					: tipo.path("name").asString("").matches("STATUS_(POSTPONED|CANCELED|ABANDONED|SUSPENDED|FORFEIT)") ? "susp"
							: tipo.path("state").asString("pre");
			boolean jugado = "post".equals(estado);
			long idLocal = local.path("team").path("id").asLong(0), idVisita = visita.path("team").path("id").asLong(0);
			out.add(new EventoEspn(ev.path("id").asLong(0), ligaId, logoLiga, fecha, estado,
					c.path("venue").path("fullName").asString(ev.path("venue").path("displayName").asString(null)),
					idLocal, local.path("team").path("displayName").asString(""), local.path("team").path("logo").asString(null),
					idVisita, visita.path("team").path("displayName").asString(""), visita.path("team").path("logo").asString(null),
					jugado ? entero(local.path("score")) : null, jugado ? entero(visita.path("score")) : null,
					jugado ? stats(local, c, idLocal) : null, jugado ? stats(visita, c, idVisita) : null));
		}
		return out;
	}

	private static Integer entero(JsonNode n) {
		try {
			String s = n.isObject() ? n.path("displayValue").asString("") : n.asString("");
			return s.isBlank() ? null : (int) Math.round(Double.parseDouble(s));
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static StatsEspn stats(JsonNode equipo, JsonNode competicion, long equipoId) {
		Map<String, String> s = new HashMap<>();
		for (JsonNode x : equipo.path("statistics")) {
			s.put(x.path("name").asString(""), x.path("displayValue").asString(""));
		}
		if (s.isEmpty()) {
			return null;
		}
		int amarillas = 0, rojas = 0;
		for (JsonNode d : competicion.path("details")) {
			if (d.path("team").path("id").asLong(-1) != equipoId) {
				continue;
			}
			String t = d.path("type").path("text").asString("");
			if (t.contains("Yellow Card")) {
				amarillas++;
			} else if (t.contains("Red Card")) {
				rojas++;
			}
		}
		BigDecimal posesion = null;
		try {
			posesion = s.containsKey("possessionPct") ? new BigDecimal(s.get("possessionPct")) : null;
		} catch (NumberFormatException e) {
			// sin posesión
		}
		return new StatsEspn(numero(s.get("wonCorners")), amarillas, rojas, numero(s.get("shotsOnTarget")),
				numero(s.get("totalShots")), posesion);
	}

	private static Integer numero(String v) {
		try {
			return v == null || v.isBlank() ? null : (int) Math.round(Double.parseDouble(v));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** @return {partidos nuevos, partidos con estadísticas nuevas} */
	int[] guardar(LigaEspn ligaEspn, List<EventoEspn> eventos, Map<LocalDate, List<Partido>> deOtras) {
		Instant ahora = Instant.now();
		LocalDate hoy = LocalDate.now(props.zona());
		Liga liga = liga(ligaEspn, eventos.get(0));
		List<Equipo> todos = new ArrayList<>(equipoRepository.findAll());
		Map<Long, Equipo> porProveedor = new HashMap<>();
		todos.forEach(e -> porProveedor.put(e.getProveedorId(), e));
		Map<Long, Partido> existentes = new HashMap<>();
		partidoRepository.findByProveedorIdIn(eventos.stream().map(ev -> -(DESPLAZAMIENTO + ev.id())).toList())
				.forEach(p -> existentes.put(p.getProveedorId(), p));
		Set<Long> conStats = new HashSet<>(estadisticaRepository.partidosConCorners());

		int nuevos = 0, stats = 0;
		Set<Long> vistos = new HashSet<>();
		for (EventoEspn ev : eventos) {
			if (!vistos.add(ev.id()) || ev.localId() == 0 || ev.visitaId() == 0
					|| NombresEquipos.esJuvenilOFemenino(ev.local()) || NombresEquipos.esJuvenilOFemenino(ev.visita())) {
				continue;
			}
			boolean jugado = "post".equals(ev.estado()) && ev.golesLocal() != null && ev.golesVisita() != null;
			Partido p = existentes.get(-(DESPLAZAMIENTO + ev.id()));
			if (p == null) {
				Partido otro = mismoPartido(ev, deOtras);
				if (otro != null) {
					// Ya lo trajo otra fuente: se completan el resultado (si le faltaba) y las estadísticas
					if (jugado && !otro.jugado()) {
						otro.setGolesLocal(ev.golesLocal());
						otro.setGolesVisita(ev.golesVisita());
						otro.setEstado(EstadoPartido.JUGADO);
						otro.setEstadoProveedor("FT");
						otro.setActualizadoEn(ahora);
						partidoRepository.save(otro);
					}
					if (jugado && !conStats.contains(otro.getId()) && guardarStats(otro, ev)) {
						conStats.add(otro.getId());
						stats++;
					}
					continue;
				}
				Equipo local = equipo(ev.localId(), ev.local(), ev.logoLocal(), liga, todos, porProveedor);
				Equipo visita = equipo(ev.visitaId(), ev.visita(), ev.logoVisita(), liga, todos, porProveedor);
				if (local.getId().equals(visita.getId())) {
					continue;
				}
				p = new Partido();
				p.setProveedorId(-(DESPLAZAMIENTO + ev.id()));
				p.setLiga(liga);
				p.setLocal(local);
				p.setVisita(visita);
				p.setTemporada(ev.fecha().atZone(props.zona()).getYear());
				p.setDetalleSync(true);
				nuevos++;
			}
			p.setFechaHoraUtc(ev.fecha());
			if (ev.estadio() != null) {
				p.setEstadio(recortar(ev.estadio(), 120));
			}
			p.setEstado(switch (ev.estado()) {
				case "post" -> jugado ? EstadoPartido.JUGADO : EstadoPartido.SUSPENDIDO;
				case "in" -> EstadoPartido.EN_VIVO;
				case "susp" -> EstadoPartido.SUSPENDIDO;
				default -> EstadoPartido.porFecha(ev.fecha(), props.zona(), hoy);
			});
			p.setEstadoProveedor(jugado ? "FT" : "in".equals(ev.estado()) ? "LIVE" : "NS");
			p.setGolesLocal(jugado ? ev.golesLocal() : null);
			p.setGolesVisita(jugado ? ev.golesVisita() : null);
			p.setActualizadoEn(ahora);
			p = partidoRepository.save(p);
			if (jugado && !conStats.contains(p.getId()) && guardarStats(p, ev)) {
				conStats.add(p.getId());
				stats++;
			}
		}
		return new int[] { nuevos, stats };
	}

	static Map<LocalDate, List<Partido>> porDia(List<Partido> partidos) {
		Map<LocalDate, List<Partido>> m = new HashMap<>();
		partidos.forEach(p -> m.computeIfAbsent(LocalDate.ofInstant(p.getFechaHoraUtc(), java.time.ZoneOffset.UTC),
				d -> new ArrayList<>()).add(p));
		return m;
	}

	/** El partido de otra fuente que es este: ±36 h y los dos equipos con nombre claramente igual. */
	static Partido mismoPartido(EventoEspn ev, Map<LocalDate, List<Partido>> deOtras) {
		Partido mejor = null;
		int mejorPuntos = 0;
		LocalDate dia = LocalDate.ofInstant(ev.fecha(), java.time.ZoneOffset.UTC);
		List<Partido> candidatos = new ArrayList<>();
		for (int d = -2; d <= 2; d++) {
			candidatos.addAll(deOtras.getOrDefault(dia.plusDays(d), List.of()));
		}
		for (Partido p : candidatos) {
			if (Duration.between(p.getFechaHoraUtc(), ev.fecha()).abs().compareTo(MISMO_PARTIDO) > 0) {
				continue;
			}
			int a = parecido(ev.local(), p.getLocal()), b = parecido(ev.visita(), p.getVisita());
			if (a >= 3 && b >= 3 && a + b > mejorPuntos) {
				mejor = p;
				mejorPuntos = a + b;
			}
		}
		return mejor;
	}

	private static int parecido(String nombre, Equipo e) {
		return Math.max(NombresEquipos.similitud(nombre, e.getNombre()),
				NombresEquipos.similitudConVariantes(nombre, e.getNombreNormalizado()));
	}

	private boolean guardarStats(Partido p, EventoEspn ev) {
		if (ev.statsLocal() == null || ev.statsVisita() == null || ev.statsLocal().vacias() || ev.statsVisita().vacias()) {
			return false;
		}
		estadisticaRepository.borrarDePartido(p.getId());
		estadisticaRepository.flush();
		estadisticaRepository.save(estadistica(p.getId(), p.getLocal().getId(), ev.statsLocal()));
		estadisticaRepository.save(estadistica(p.getId(), p.getVisita().getId(), ev.statsVisita()));
		return true;
	}

	private static EstadisticaPartido estadistica(Long partidoId, Long equipoId, StatsEspn s) {
		EstadisticaPartido e = new EstadisticaPartido();
		e.setClave(new EstadisticaPartido.Clave(partidoId, equipoId));
		e.setCorners(s.corners());
		e.setTarjetasAmarillas(s.amarillas());
		e.setTarjetasRojas(s.rojas());
		e.setRematesAPuerta(s.rematesAPuerta());
		e.setRematesTotales(s.rematesTotales());
		e.setPosesion(s.posesion());
		return e;
	}

	/** Nuestro equipo para un id de ESPN: ya guardado, el mismo nombre sin dudas, o uno nuevo. */
	private Equipo equipo(long idEspn, String nombre, String logo, Liga liga, List<Equipo> todos,
			Map<Long, Equipo> porProveedor) {
		Equipo e = porProveedor.get(-(DESPLAZAMIENTO + idEspn));
		if (e == null) {
			List<Equipo> iguales = todos.stream()
					.filter(x -> !NombresEquipos.esJuvenilOFemenino(x.getNombre()))
					.filter(x -> NombresEquipos.similitudConVariantes(nombre, x.getNombreNormalizado()) >= NombresEquipos.IGUAL)
					.filter(x -> NombresEquipos.mismoPais(x.pais(), liga.getPais()))
					.toList();
			List<Equipo> deApiFootball = iguales.stream().filter(x -> x.getProveedorId() > 0).toList();
			e = deApiFootball.size() == 1 ? deApiFootball.get(0) : iguales.size() == 1 ? iguales.get(0) : null;
			if (e != null) {
				porProveedor.put(-(DESPLAZAMIENTO + idEspn), e);
			}
		}
		if (e == null) {
			e = new Equipo();
			e.setProveedorId(-(DESPLAZAMIENTO + idEspn));
			e.setNombre(recortar(nombre, 100));
			e.setNombreNormalizado(recortar(Normalizador.normalizar(nombre), 100));
			e.setLiga(liga);
			e.setEscudoUrl(recortar(logo, 255));
			e.setActualizadoEn(Instant.now());
			e = equipoRepository.save(e);
			todos.add(e);
			porProveedor.put(e.getProveedorId(), e);
		} else if (e.getEscudoUrl() == null && logo != null) {
			e.setEscudoUrl(recortar(logo, 255));
		}
		return e;
	}

	private Liga liga(LigaEspn l, EventoEspn ejemplo) {
		long proveedorId = -(DESPLAZAMIENTO + (ejemplo.ligaId() != 0 ? ejemplo.ligaId() : Math.abs(l.codigo().hashCode())));
		return ligaRepository.findByProveedorIdIn(List.of(proveedorId)).stream().findFirst().orElseGet(() -> {
			Liga nueva = new Liga();
			nueva.setProveedorId(proveedorId);
			nueva.setNombre(recortar(l.nombre(), 100));
			nueva.setNombreNormalizado(recortar(Normalizador.normalizar(l.nombre()), 100));
			nueva.setPais(l.pais().isEmpty() ? "World" : l.pais());
			nueva.setLogoUrl(recortar(ejemplo.logoLiga(), 255));
			return ligaRepository.save(nueva);
		});
	}

	private static String recortar(String s, int max) {
		return s == null ? null : s.length() <= max ? s : s.substring(0, max);
	}
}
