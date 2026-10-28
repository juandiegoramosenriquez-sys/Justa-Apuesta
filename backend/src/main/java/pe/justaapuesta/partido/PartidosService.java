package pe.justaapuesta.partido;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
	/** En la búsqueda: últimos jugados de cada equipo (con si acertó o no). */
	private static final int JUGADOS_POR_EQUIPO = 10;
	/** Una búsqueda se recuerda 3 minutos: repetirla (o volver del detalle) es instantáneo. */
	private static final Duration VIGENCIA_BUSQUEDA = Duration.ofMinutes(3);
	private static final int MAX_BUSQUEDAS_GUARDADAS = 300;

	/** Apodos y abreviaturas que no se parecen al nombre guardado. */
	static final Map<String, String> APODOS = Map.ofEntries(
			Map.entry("psg", "paris saint germain"), Map.entry("barca", "barcelona"),
			Map.entry("atleti", "atletico madrid"), Map.entry("spurs", "tottenham"),
			Map.entry("man city", "manchester city"), Map.entry("man utd", "manchester united"),
			Map.entry("man u", "manchester united"), Map.entry("juve", "juventus"),
			Map.entry("gladbach", "monchengladbach"), Map.entry("wolves", "wolverhampton"),
			Map.entry("la u", "universitario"), Map.entry("cristal", "sporting cristal"),
			Map.entry("boca", "boca juniors"), Map.entry("river", "river plate"),
			Map.entry("chivas", "guadalajara"), Map.entry("verdao", "palmeiras"),
			Map.entry("mengao", "flamengo"), Map.entry("timao", "corinthians"),
			Map.entry("depor", "deportivo"), Map.entry("bvb", "dortmund"), Map.entry("leverkusen", "leverkusen"));
	private static final int MAX_EQUIPOS = 4;
	/** Lo que muestran las pestañas (independiente de lo que deja bajar cada proveedor). */
	private static final int DIAS_JUGADOS = 3;
	/** Próximos = mañana y pasado mañana. */
	private static final int DIAS_PROXIMOS = 2;
	/** Al buscar una liga: sus partidos de la próxima semana. */
	private static final int DIAS_LIGA_BUSCADA = 7;
	/** En Hoy: si quedan menos partidos importantes por jugar, se suman los demás partidos del día. */
	static final int MINIMO_HOY = 10;

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
		List<Partido> esenciales = esenciales(pestana);
		if (pestana != Pestana.HOY || esenciales.size() >= MINIMO_HOY) {
			return esenciales;
		}
		// Hoy: cuando ya se jugaron casi todos los importantes, se suman los demás partidos del día (otras ligas)
		Set<Long> ya = esenciales.stream().map(Partido::getId).collect(java.util.stream.Collectors.toSet());
		List<Partido> todos = new java.util.ArrayList<>(esenciales);
		partidosDeTodas(pestana).stream()
				.filter(p -> !ya.contains(p.getId()))
				.filter(p -> !NombresEquipos.esJuvenilOFemenino(p.getLocal().getNombre())
						&& !NombresEquipos.esJuvenilOFemenino(p.getVisita().getNombre()))
				.forEach(todos::add);
		return todos;
	}

	/** Los partidos que siempre se muestran: equipos famosos y ligas configuradas. El resto aparece al buscarlo. */
	private List<Partido> esenciales(Pestana pestana) {
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
	private record BusquedaGuardada(Instant hecha, ListaPartidosDto lista) {
	}

	private final Map<String, BusquedaGuardada> busquedas = new ConcurrentHashMap<>();
	private final Set<String> buscandoAfuera = ConcurrentHashMap.newKeySet();

	public ListaPartidosDto buscar(String texto, Long usuarioId) {
		String q = Normalizador.normalizar(texto);
		if (q.length() < 3) {
			throw new SolicitudInvalidaException("Escribe al menos 3 letras.");
		}
		q = APODOS.getOrDefault(q, q);
		Modo modo = analisis.modoDe(usuarioId);
		Set<Long> favoritos = analisis.favoritosDe(usuarioId);
		String clave = q + "|" + modo;
		BusquedaGuardada g = busquedas.get(clave);
		ListaPartidosDto r;
		if (g != null && g.hecha().isAfter(Instant.now().minus(VIGENCIA_BUSQUEDA))) {
			r = g.lista();
		} else {
			r = buscarSinMemoria(q, texto, modo);
			if (!r.buscandoMas()) {
				if (busquedas.size() > MAX_BUSQUEDAS_GUARDADAS) {
					busquedas.clear();
				}
				busquedas.put(clave, new BusquedaGuardada(Instant.now(), r));
			}
		}
		return new ListaPartidosDto(r.partidos().stream().map(x -> x.conFavorito(favoritos.contains(x.id()))).toList(),
				r.actualizadoEn(), r.aviso(), r.buscandoMas());
	}

	private ListaPartidosDto buscarSinMemoria(String q, String texto, Modo modo) {
		// "liverpool vs brentford": los partidos entre los dos
		String[] lados = q.split("\\s+(?:vs|v|x|contra)\\s+|\\s+-\\s+", 2);
		if (lados.length == 2 && lados[0].trim().length() >= 3 && lados[1].trim().length() >= 3) {
			ListaPartidosDto entre = buscarPartido(lados[0].trim(), lados[1].trim(), modo);
			if (!entre.partidos().isEmpty()) {
				return entre;
			}
		}
		List<Equipo> equipos = equiposRelevantes(q);
		// Si no hay un equipo que se llame exactamente así y con historial, se busca en TheSportsDB (una vez cada
		// 7 días por búsqueda) y se baja su liga: así aparece cualquier equipo del mundo. Va aparte: se responde
		// ya con lo que hay y la web vuelve a pedir en unos segundos
		boolean buscandoMas = false;
		if (!hayExactoConHistorial(q, equipos) && ligaRepository.buscar(q).isEmpty() && theSportsDb.pendiente(texto)
				&& buscandoAfuera.add(q)) {
			buscandoMas = true;
			String buscado = q;
			Thread.ofVirtual().name("buscar-afuera").start(() -> {
				try {
					theSportsDb.buscarYBajar(texto);
				} catch (RuntimeException e) {
					log.debug("TheSportsDB no pudo buscar '{}': {}", texto, e.getMessage());
				} finally {
					busquedas.keySet().removeIf(k -> k.startsWith(buscado + "|"));
					buscandoAfuera.remove(buscado);
				}
			});
		}

		Instant ahora = Instant.now();
		Map<Long, Partido> porJugar = new LinkedHashMap<>();
		Map<Long, Partido> jugados = new LinkedHashMap<>();
		// Un club puede estar guardado varias veces (una por fuente): se miran todas sus versiones
		for (Equipo e : conSusOtrasVersiones(equipos)) {
			partidoRepository.proximosDeEquipo(e.getId(), ahora.minus(Duration.ofHours(3)), PageRequest.of(0, POR_EQUIPO))
					.forEach(p -> porJugar.putIfAbsent(p.getId(), p));
			partidoRepository.jugadosDeEquipo(e.getId(), ahora, PageRequest.of(0, JUGADOS_POR_EQUIPO))
					.forEach(p -> jugados.putIfAbsent(p.getId(), p));
		}
		// Búsqueda por liga ("premier", "liga 1"): sus partidos de estos días y de la próxima semana
		List<Liga> ligas = ligaRepository.buscar(q);
		if (!ligas.isEmpty()) {
			LocalDate hoy = LocalDate.now(props.zona());
			partidoRepository.deLigas(ligas.stream().map(Liga::getId).toList(),
					hoy.minusDays(DIAS_JUGADOS).atStartOfDay(props.zona()).toInstant(),
					hoy.plusDays(DIAS_LIGA_BUSCADA + 1L).atStartOfDay(props.zona()).toInstant())
					.forEach(p -> (p.jugado() ? jugados : porJugar).putIfAbsent(p.getId(), p));
		}
		List<Partido> lista = new ArrayList<>(sinRepetidos(porJugar.values()).stream()
				.sorted(Comparator.comparing(Partido::getFechaHoraUtc)).toList());
		sinRepetidos(jugados.values()).stream().filter(p -> !porJugar.containsKey(p.getId()))
				.sorted(Comparator.comparing(Partido::getFechaHoraUtc).reversed()).forEach(lista::add);
		lista = lista.subList(0, Math.min(MAX_RESULTADOS, lista.size()));
		return new ListaPartidosDto(analisis.resumenes(lista, modo, Set.of()), actualizadoEn(), avisoSiDesactualizado(),
				buscandoMas);
	}

	/** Partidos entre dos equipos buscados juntos: los próximos y los últimos enfrentamientos. */
	private ListaPartidosDto buscarPartido(String a, String b, Modo modo) {
		Set<Long> idsA = conSusOtrasVersiones(equiposRelevantes(a)).stream().map(Equipo::getId)
				.collect(Collectors.toSet());
		Set<Long> idsB = conSusOtrasVersiones(equiposRelevantes(b)).stream().map(Equipo::getId)
				.collect(Collectors.toSet());
		if (idsA.isEmpty() || idsB.isEmpty()) {
			return new ListaPartidosDto(List.of(), actualizadoEn(), null);
		}
		Instant ahora = Instant.now();
		List<Partido> todos = sinRepetidos(partidoRepository.deEquipos(idsA).stream()
				.filter(p -> (idsA.contains(p.getLocal().getId()) && idsB.contains(p.getVisita().getId()))
						|| (idsB.contains(p.getLocal().getId()) && idsA.contains(p.getVisita().getId())))
				.filter(p -> p.jugado() || (sinTerminar(p) && p.getFechaHoraUtc().isAfter(ahora.minus(Duration.ofHours(3)))))
				.toList());
		List<Partido> lista = new ArrayList<>(todos.stream().filter(p -> !p.jugado())
				.sorted(Comparator.comparing(Partido::getFechaHoraUtc)).toList());
		todos.stream().filter(Partido::jugado).sorted(Comparator.comparing(Partido::getFechaHoraUtc).reversed())
				.limit(JUGADOS_POR_EQUIPO).forEach(lista::add);
		return new ListaPartidosDto(analisis.resumenes(lista, modo, Set.of()), actualizadoEn(), null);
	}

	/** El mismo club guardado por otras fuentes: mismo nombre (sin dudas) y mismo país. */
	private List<Equipo> conSusOtrasVersiones(List<Equipo> equipos) {
		Map<Long, Equipo> out = new LinkedHashMap<>();
		for (Equipo e : equipos) {
			out.putIfAbsent(e.getId(), e);
			String palabra = Arrays.stream(NombresEquipos.clave(e.getNombre()).split(" "))
					.max(Comparator.comparingInt(String::length)).orElse("");
			if (palabra.length() < 3) {
				continue;
			}
			equipoRepository.buscar(palabra, PageRequest.of(0, 40)).stream()
					.filter(x -> !NombresEquipos.esJuvenilOFemenino(x.getNombre()))
					.filter(x -> NombresEquipos.similitudConVariantes(e.getNombre(), x.getNombreNormalizado()) >= NombresEquipos.IGUAL
							|| NombresEquipos.similitudConVariantes(x.getNombre(), e.getNombreNormalizado()) >= NombresEquipos.IGUAL)
					.filter(x -> NombresEquipos.mismoPais(x.pais(), e.pais()))
					.forEach(x -> out.putIfAbsent(x.getId(), x));
		}
		return new ArrayList<>(out.values());
	}

	/** El mismo partido traído por dos fuentes (±36 h y mismos equipos por nombre) se muestra una vez. */
	public static List<Partido> sinRepetidos(Collection<Partido> partidos) {
		List<Partido> out = new ArrayList<>();
		// Se quedan primero los que tienen resultado y los más actualizados
		List<Partido> orden = partidos.stream()
				.sorted(Comparator.comparing((Partido p) -> p.jugado() ? 0 : 1)
						.thenComparing(p -> p.getActualizadoEn() == null ? Instant.EPOCH : p.getActualizadoEn(),
								Comparator.reverseOrder()))
				.toList();
		for (Partido p : orden) {
			boolean repetido = out.stream().anyMatch(x -> Duration.between(x.getFechaHoraUtc(), p.getFechaHoraUtc()).abs()
					.compareTo(Duration.ofHours(36)) <= 0
					&& NombresEquipos.similitud(x.getLocal().getNombre(), p.getLocal().getNombre()) >= 3
					&& NombresEquipos.similitud(x.getVisita().getNombre(), p.getVisita().getNombre()) >= 3);
			if (!repetido) {
				out.add(p);
			}
		}
		return out;
	}

	/**
	 * Los equipos que mejor responden a la búsqueda: si alguno se llama exactamente así o empieza así, solo esos
	 * ("milan" -> AC Milan, no el Inter de Milano); si no, los más parecidos. Sin juveniles ni femeninos, salvo que
	 * se busquen. Si el texto completo no aparece en ningún nombre ("man united"), se busca palabra por palabra.
	 * Máximo 4; con el mismo parecido, primero los que tienen partidos por jugar.
	 */
	private List<Equipo> equiposRelevantes(String q) {
		boolean pideJuveniles = NombresEquipos.esJuvenilOFemenino(q);
		List<Equipo> encontrados = equipoRepository.buscar(q, PageRequest.of(0, 40));
		if (encontrados.isEmpty()) {
			Map<Long, Equipo> porPalabra = new LinkedHashMap<>();
			Arrays.stream(q.split(" ")).filter(w -> w.length() >= 4)
					.forEach(w -> equipoRepository.buscar(w, PageRequest.of(0, 40)).forEach(e -> porPalabra.putIfAbsent(e.getId(), e)));
			encontrados = new ArrayList<>(porPalabra.values());
		}
		Set<Long> conError = new HashSet<>();
		if (encontrados.isEmpty()) {
			encontrados = conErrores(q); // "liverpol", "barcelno"
			encontrados.forEach(e -> conError.add(e.getId()));
		}
		List<Equipo> candidatos = encontrados.stream()
				.filter(e -> pideJuveniles || !NombresEquipos.esJuvenilOFemenino(e.getNombre()))
				.toList();
		Map<Long, Integer> puntaje = new LinkedHashMap<>();
		// Los encontrados con errores de escritura valen como "se parece" (2)
		candidatos.forEach(e -> puntaje.put(e.getId(), Math.max(conError.contains(e.getId()) ? 2
				: NombresEquipos.relevancia(q, e.getNombreNormalizado()),
				Math.min(3, NombresEquipos.similitudConVariantes(q, e.getNombreNormalizado())))));
		int mejor = puntaje.values().stream().max(Integer::compare).orElse(0);
		List<Equipo> elegidos = candidatos.stream()
				.filter(e -> puntaje.get(e.getId()) > 0 && (mejor < 3 || puntaje.get(e.getId()) >= 3))
				.toList();
		Set<Long> conocidos = elegidos.size() > 1 ? famosos.equipos() : Set.of();
		Map<Long, Long> partidos = new LinkedHashMap<>();
		Set<Long> conProximos = new HashSet<>();
		if (elegidos.size() > 1) {
			Instant ahora = Instant.now();
			elegidos.forEach(e -> {
				partidos.put(e.getId(), partidoRepository.contarDeEquipo(e.getId()));
				if (!partidoRepository.proximosDeEquipo(e.getId(), ahora, PageRequest.of(0, 1)).isEmpty()) {
					conProximos.add(e.getId());
				}
			});
		}
		return elegidos.stream()
				.sorted(Comparator.comparingInt((Equipo e) -> -puntaje.get(e.getId()))
						.thenComparingInt(e -> conProximos.contains(e.getId()) ? 0 : 1)
						.thenComparingInt(e -> conocidos.contains(e.getId()) ? 0 : 1)
						.thenComparingLong(e -> -partidos.getOrDefault(e.getId(), 0L))
						.thenComparingInt(e -> e.getNombre().length()))
				.limit(MAX_EQUIPOS)
				.toList();
	}

	// ---------- Errores de escritura ----------

	private volatile List<Object[]> nombres = List.of();
	private volatile Instant nombresEn = Instant.EPOCH;

	/** Equipos cuyo nombre se parece a lo escrito con 1 o 2 letras distintas (todas las palabras de 4+ letras). */
	private List<Equipo> conErrores(String q) {
		List<String> buscadas = Arrays.stream(q.split(" ")).filter(w -> w.length() >= 4).toList();
		if (buscadas.isEmpty()) {
			return List.of();
		}
		if (nombresEn.isBefore(Instant.now().minus(Duration.ofMinutes(30)))) {
			nombres = equipoRepository.nombres();
			nombresEn = Instant.now();
		}
		Map<Long, Integer> distancia = new LinkedHashMap<>();
		for (Object[] fila : nombres) {
			List<String> palabras = Arrays.stream(String.valueOf(fila[1]).split("[ |]")).filter(w -> !w.isEmpty()).toList();
			int total = 0;
			for (String b : buscadas) {
				int mejor = palabras.stream().mapToInt(w -> distancia(b, w)).min().orElse(99);
				if (mejor > (b.length() >= 7 ? 2 : 1)) {
					total = -1;
					break;
				}
				total += mejor;
			}
			if (total >= 0) {
				distancia.put((Long) fila[0], total);
			}
		}
		List<Long> ids = distancia.entrySet().stream().sorted(Map.Entry.comparingByValue()).limit(20)
				.map(Map.Entry::getKey).toList();
		return ids.isEmpty() ? List.of() : equipoRepository.conLiga(ids);
	}

	/** Cuántas letras hay que cambiar para pasar de una palabra a otra (Levenshtein). */
	static int distancia(String a, String b) {
		if (Math.abs(a.length() - b.length()) > 2) {
			return 99;
		}
		int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
		for (int j = 0; j <= b.length(); j++) {
			prev[j] = j;
		}
		for (int i = 1; i <= a.length(); i++) {
			cur[0] = i;
			for (int j = 1; j <= b.length(); j++) {
				cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
			}
			int[] x = prev;
			prev = cur;
			cur = x;
		}
		return prev[b.length()];
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
		// ESPN también cuenta: trae los partidos y resultados de casi todas las ligas
		return List.of(SincronizacionService.HOY, SincronizacionService.DIARIA, SincronizacionService.HISTORIAL,
				SincronizacionService.HISTORIAL_EXTRA, SincronizacionService.ESPN).stream()
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
