package pe.justaapuesta.partido;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import pe.justaapuesta.analisis.AnalisisDtos.ListaPartidosDto;
import pe.justaapuesta.analisis.AnalisisDtos.PartidoResumenDto;
import pe.justaapuesta.analisis.AnalisisDtos.UltimoPartidoDto;
import pe.justaapuesta.analisis.AnalisisDtos.UltimosDto;
import pe.justaapuesta.analisis.AnalisisService;
import pe.justaapuesta.comun.NoEncontradoException;
import pe.justaapuesta.comun.Normalizador;
import pe.justaapuesta.comun.SolicitudInvalidaException;
import pe.justaapuesta.config.JustaProperties;
import pe.justaapuesta.historial.NombresEquipos;
import pe.justaapuesta.historial.TheSportsDbService;
import pe.justaapuesta.proveedor.ProveedorException;
import pe.justaapuesta.sync.SincronizacionService;
import pe.justaapuesta.sync.SyncEstado;
import pe.justaapuesta.usuario.Modo;
import pe.justaapuesta.usuario.UsuarioRepository;

/**
 * Listas de partidos (Jugados / Hoy / Próximos), búsqueda total y últimos partidos de un equipo.
 */
@Service
@RequiredArgsConstructor
public class PartidosService {

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PartidosService.class);
	private static final int MAX_RESULTADOS = 40;
	/** En la búsqueda: próximos y últimos partidos de cada equipo encontrado. */
	private static final int POR_EQUIPO = 5;
	private static final int MAX_EQUIPOS = 4;
	/** Lo que muestran las pestañas (independiente de lo que deja bajar cada proveedor). */
	private static final int DIAS_JUGADOS = 3;
	/** Próximos = mañana y pasado mañana. */
	private static final int DIAS_PROXIMOS = 2;

	public enum Pestana { JUGADO, HOY, PROXIMO }

	private final PartidoRepository partidoRepository;
	private final EquipoRepository equipoRepository;
	private final LigaRepository ligaRepository;
	private final AnalisisService analisis;
	private final SincronizacionService sync;
	private final UsuarioRepository usuarioRepository;
	private final JustaProperties props;
	private final EquiposFamosos famosos;
	private final TheSportsDbService theSportsDb;

	/**
	 * Partidos de una pestaña, con las ligas favoritas primero. Solo los que ya tienen consejo: los de equipos
	 * con poco historial siguen apareciendo al buscarlos.
	 */
	public ListaPartidosDto listar(Pestana pestana, Long usuarioId) {
		Set<Long> favoritos = analisis.favoritosDe(usuarioId);
		Instant ahora = Instant.now();
		List<PartidoResumenDto> lista = listaBase(pestana, analisis.modoDe(usuarioId)).stream()
				// La lista guardada puede tener unos minutos: los que ya empezaron no se muestran en Hoy
				.filter(r -> pestana != Pestana.HOY || r.fecha().isAfter(ahora))
				.map(r -> r.conFavorito(favoritos.contains(r.id())))
				.toList();
		return new ListaPartidosDto(ordenarPorLigasFavoritas(lista, usuarioId), actualizadoEn(),
				avisoSiDesactualizado());
	}

	// ---------- Listas listas en memoria (para que la web cargue en 1-2 segundos) ----------

	/** Una lista se rehace como mucho cada 3 minutos; mientras tanto se sirve la guardada. */
	private static final Duration VIGENCIA_LISTA = Duration.ofMinutes(3);

	private record ClaveLista(Pestana pestana, Modo modo) {
	}

	private record ListaGuardada(Instant hecha, List<PartidoResumenDto> lista) {
	}

	private final Map<ClaveLista, ListaGuardada> listas = new ConcurrentHashMap<>();
	private final Set<ClaveLista> rehaciendo = ConcurrentHashMap.newKeySet();

	/**
	 * Partidos de la pestaña con su consejo, ya calculados (sin favoritos, que son de cada usuario). Si la lista
	 * guardada está vieja se rehace aparte y se responde con la guardada: nadie espera el cálculo.
	 */
	private List<PartidoResumenDto> listaBase(Pestana pestana, Modo modo) {
		ClaveLista clave = new ClaveLista(pestana, modo);
		ListaGuardada g = listas.get(clave);
		if (g == null) {
			return rehacer(clave);
		}
		if (g.hecha().isBefore(Instant.now().minus(VIGENCIA_LISTA)) && rehaciendo.add(clave)) {
			Thread.ofVirtual().name("lista-" + pestana).start(() -> {
				try {
					rehacer(clave);
				} catch (RuntimeException e) {
					log.warn("No se pudo rehacer la lista {}: {}", clave, e.getMessage());
				} finally {
					rehaciendo.remove(clave);
				}
			});
		}
		return g.lista();
	}

	private List<PartidoResumenDto> rehacer(ClaveLista clave) {
		List<PartidoResumenDto> lista = analisis.resumenes(partidosDe(clave.pestana()), clave.modo(), Set.of()).stream()
				.filter(r -> r.consejo() != null)
				.toList();
		listas.put(clave, new ListaGuardada(Instant.now(), lista));
		return lista;
	}

	/** Rehace todas las listas (después de precalcular los análisis), así la próxima visita no espera. */
	public void rehacerListas() {
		for (Pestana p : Pestana.values()) {
			for (Modo m : Modo.values()) {
				rehacer(new ClaveLista(p, m));
			}
		}
	}

	/** Partidos de una pestaña, solo de las ligas configuradas (también para Picks). */
	public List<Partido> partidosDe(Pestana pestana) {
		// Solo los partidos de equipos famosos (se detectan solos). El resto aparece al buscarlo.
		Set<Long> equiposFamosos = famosos.equipos();
		if (!equiposFamosos.isEmpty()) {
			// ...y todos los de las ligas configuradas (Liga 1 Perú, Argentina, MLS...), vengan de la fuente que vengan
			Set<Long> ligasConfig = props.sync().idsLigas();
			Set<String> nombresConfig = props.sync().ligas().stream().map(JustaProperties.Liga::nombre)
					.collect(java.util.stream.Collectors.toSet());
			return partidosDeTodas(pestana).stream()
					.filter(p -> famosos.esPartidoFamoso(p, equiposFamosos)
							|| ligasConfig.contains(p.getLiga().getProveedorId())
							|| nombresConfig.contains(p.getLiga().getNombre()))
					.toList();
		}
		// Base recién creada (sin copas internacionales todavía): las ligas configuradas
		Set<Long> ligas = props.sync().idsLigas();
		return partidosDeTodas(pestana).stream().filter(p -> ligas.contains(p.getLiga().getProveedorId())).toList();
	}

	private List<Partido> partidosDeTodas(Pestana pestana) {
		ZoneId zona = props.zona();
		LocalDate hoy = LocalDate.now(zona);
		Instant inicioHoy = hoy.atStartOfDay(zona).toInstant();
		Instant inicioManana = hoy.plusDays(1).atStartOfDay(zona).toInstant();
		Instant ahora = Instant.now();
		return switch (pestana) {
			// Hoy: solo los que todavía no empiezan. En cuanto empieza, pasa a Jugados.
			case HOY -> partidoRepository.entre(inicioHoy, inicioManana).stream()
					.filter(p -> p.getFechaHoraUtc().isAfter(ahora) && sinTerminar(p))
					.toList();
			case PROXIMO -> partidoRepository
					.entre(inicioManana, hoy.plusDays(DIAS_PROXIMOS + 1L).atStartOfDay(zona).toInstant())
					.stream()
					.filter(this::sinTerminar)
					.toList();
			// Jugados: los terminados y los que ya empezaron (en juego o esperando el resultado), el más reciente primero
			case JUGADO -> partidoRepository
					.entre(hoy.minusDays(DIAS_JUGADOS).atStartOfDay(zona).toInstant(), ahora).stream()
					.filter(p -> p.jugado() || (sinTerminar(p) && !p.getFechaHoraUtc().isAfter(ahora)))
					.sorted(Comparator.comparing(Partido::getFechaHoraUtc).reversed())
					.toList();
		};
	}

	private boolean sinTerminar(Partido p) {
		return p.getEstado() != EstadoPartido.JUGADO && p.getEstado() != EstadoPartido.SUSPENDIDO;
	}

	/**
	 * Búsqueda total en lo guardado: la sincronización baja cada día los partidos de TODAS las ligas,
	 * así que aparece cualquier equipo que haya jugado o vaya a jugar en las fechas descargadas.
	 * Ignora mayúsculas y tildes. No gasta consultas del proveedor.
	 */
	public ListaPartidosDto buscar(String texto, Long usuarioId) {
		String q = Normalizador.normalizar(texto);
		if (q.length() < 3) {
			throw new SolicitudInvalidaException("Escribe al menos 3 letras.");
		}
		String aviso = null;
		List<Equipo> equipos = equiposRelevantes(q);
		// Si no hay un equipo que se llame exactamente así y con historial, se busca en TheSportsDB (una vez cada
		// 7 días por búsqueda) y se baja su liga: así aparece cualquier equipo del mundo
		if (!hayExactoConHistorial(q, equipos) && ligaRepository.buscar(q).isEmpty()) {
			try {
				if (theSportsDb.buscarYBajar(texto)) {
					equipos = equiposRelevantes(q);
				}
			} catch (ProveedorException e) {
				aviso = "No pudimos buscar ese equipo en este momento. Inténtalo en un rato.";
			}
		}

		Instant ahora = Instant.now();
		Map<Long, Partido> porJugar = new LinkedHashMap<>();
		Map<Long, Partido> jugados = new LinkedHashMap<>();
		for (Equipo e : equipos) {
			partidoRepository.proximosDeEquipo(e.getId(), ahora.minus(Duration.ofHours(3)), PageRequest.of(0, POR_EQUIPO))
					.forEach(p -> porJugar.putIfAbsent(p.getId(), p));
			partidoRepository.jugadosDeEquipo(e.getId(), ahora, PageRequest.of(0, POR_EQUIPO))
					.forEach(p -> jugados.putIfAbsent(p.getId(), p));
		}
		// Búsqueda por liga ("premier", "liga 1"): sus partidos de estos días
		List<Liga> ligas = ligaRepository.buscar(q);
		if (!ligas.isEmpty()) {
			LocalDate hoy = LocalDate.now(props.zona());
			partidoRepository.deLigas(ligas.stream().map(Liga::getId).toList(),
					hoy.minusDays(DIAS_JUGADOS).atStartOfDay(props.zona()).toInstant(),
					hoy.plusDays(DIAS_PROXIMOS + 1L).atStartOfDay(props.zona()).toInstant())
					.forEach(p -> (p.jugado() ? jugados : porJugar).putIfAbsent(p.getId(), p));
		}
		List<Partido> lista = new ArrayList<>(porJugar.values().stream()
				.sorted(Comparator.comparing(Partido::getFechaHoraUtc)).toList());
		jugados.values().stream().filter(p -> !porJugar.containsKey(p.getId()))
				.sorted(Comparator.comparing(Partido::getFechaHoraUtc).reversed()).forEach(lista::add);
		lista = lista.subList(0, Math.min(MAX_RESULTADOS, lista.size()));
		return new ListaPartidosDto(analisis.resumenes(lista, usuarioId), actualizadoEn(),
				aviso != null ? aviso : avisoSiDesactualizado());
	}

	/**
	 * Los equipos que mejor responden a la búsqueda: si alguno se llama exactamente así o empieza así, solo esos
	 * ("milan" -> AC Milan, no el Inter de Milano); si no, los más parecidos. Sin juveniles ni femeninos, salvo que
	 * se busquen. Máximo 4.
	 */
	private List<Equipo> equiposRelevantes(String q) {
		boolean pideJuveniles = NombresEquipos.esJuvenilOFemenino(q);
		List<Equipo> candidatos = equipoRepository.buscar(q, PageRequest.of(0, 40)).stream()
				.filter(e -> pideJuveniles || !NombresEquipos.esJuvenilOFemenino(e.getNombre()))
				.toList();
		Map<Long, Integer> puntaje = new LinkedHashMap<>();
		candidatos.forEach(e -> puntaje.put(e.getId(), NombresEquipos.relevancia(q, e.getNombreNormalizado())));
		int mejor = puntaje.values().stream().max(Integer::compare).orElse(0);
		List<Equipo> elegidos = candidatos.stream().filter(e -> mejor < 3 || puntaje.get(e.getId()) >= 3).toList();
		Set<Long> conocidos = elegidos.size() > 1 ? famosos.equipos() : Set.of();
		// Con el mismo parecido: primero los famosos y luego los que tienen más partidos guardados
		Map<Long, Long> partidos = new LinkedHashMap<>();
		if (elegidos.size() > 1) {
			elegidos.forEach(e -> partidos.put(e.getId(), partidoRepository.contarDeEquipo(e.getId())));
		}
		return elegidos.stream()
				.sorted(Comparator.comparingInt((Equipo e) -> -puntaje.get(e.getId()))
						.thenComparingInt(e -> conocidos.contains(e.getId()) ? 0 : 1)
						.thenComparingLong(e -> -partidos.getOrDefault(e.getId(), 0L))
						.thenComparingInt(e -> e.getNombre().length()))
				.limit(MAX_EQUIPOS)
				.toList();
	}

	/** Ya tenemos un equipo que se llama exactamente así, con historial y con algún partido por jugar. */
	private boolean hayExactoConHistorial(String q, List<Equipo> equipos) {
		Instant ahora = Instant.now();
		return equipos.stream().anyMatch(e -> NombresEquipos.relevancia(q, e.getNombreNormalizado()) >= 4
				&& partidoRepository.jugadosDeEquipo(e.getId(), ahora, PageRequest.of(0, 3)).size() >= 3
				&& !partidoRepository.proximosDeEquipo(e.getId(), ahora, PageRequest.of(0, 1)).isEmpty());
	}


	private List<PartidoResumenDto> ordenarPorLigasFavoritas(List<PartidoResumenDto> lista, Long usuarioId) {
		Set<String> favoritas = Set.copyOf(usuarioRepository.ligasFavoritas(usuarioId));
		if (favoritas.isEmpty()) {
			return lista;
		}
		return lista.stream().sorted(Comparator.comparingInt(p -> favoritas.contains(p.liga().nombre()) ? 0 : 1)).toList();
	}

	/** Últimos N partidos jugados de un equipo, del más reciente al más antiguo. */
	public UltimosDto ultimos(Long equipoId, int n) {
		Equipo eq = equipoRepository.findById(equipoId)
				.orElseThrow(() -> new NoEncontradoException("No encontramos ese equipo."));
		List<Partido> lista = partidoRepository.jugadosDeEquipo(equipoId, Instant.now(), PageRequest.of(0, Math.min(n, 10)));
		int g = 0, e = 0, p = 0;
		List<UltimoPartidoDto> out = new ArrayList<>();
		for (Partido x : lista) {
			boolean casa = x.getLocal().getId().equals(equipoId);
			int gf = casa ? x.getGolesLocal() : x.getGolesVisita();
			int gc = casa ? x.getGolesVisita() : x.getGolesLocal();
			String r = gf > gc ? "G" : gf == gc ? "E" : "P";
			switch (r) {
				case "G" -> g++;
				case "E" -> e++;
				default -> p++;
			}
			out.add(new UltimoPartidoDto(x.getId(), x.getFechaHoraUtc(),
					casa ? x.getVisita().getNombre() : x.getLocal().getNombre(), casa, gf, gc, r));
		}
		return new UltimosDto(eq.getNombre(), g + "G " + e + "E " + p + "P", out);
	}

	/** Hora de la última sincronización correcta (para "Datos actualizados hace X horas"). */
	public Instant actualizadoEn() {
		return List.of(SincronizacionService.HOY, SincronizacionService.DIARIA, SincronizacionService.HISTORIAL,
				SincronizacionService.HISTORIAL_EXTRA).stream()
				.map(sync::ultima)
				.flatMap(Optional::stream)
				// Las fuentes de historial cuentan aunque alguna competición haya fallado, si trajeron partidos
				.filter(e -> e.isOk() || (SincronizacionService.HISTORIAL.equals(e.getClave())
						|| SincronizacionService.HISTORIAL_EXTRA.equals(e.getClave()))
						&& e.getDetalle() != null && !e.getDetalle().startsWith("0 "))
				.map(SyncEstado::getEjecutadoEn)
				.max(Comparator.naturalOrder())
				.orElse(null);
	}

	/** Aviso suave si la última sincronización correcta tiene más de 6 horas (o nunca hubo). */
	private String avisoSiDesactualizado() {
		Instant t = actualizadoEn();
		if (t == null) {
			return "Todavía no se descargaron partidos. Se cargan solos cada día (o con la sincronización manual).";
		}
		long horas = Duration.between(t, Instant.now()).toHours();
		if (horas < 6) {
			return null;
		}
		return "Datos actualizados hace " + horas + " horas.";
	}
}
