package pe.justaapuesta.analisis;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import pe.justaapuesta.analisis.AnalisisDtos.AnalisisDto;
import pe.justaapuesta.analisis.AnalisisDtos.CategoriaDto;
import pe.justaapuesta.analisis.AnalisisDtos.ComparaDto;
import pe.justaapuesta.analisis.AnalisisDtos.ConsejoDto;
import pe.justaapuesta.analisis.AnalisisDtos.EquipoDto;
import pe.justaapuesta.analisis.AnalisisDtos.FinalDto;
import pe.justaapuesta.analisis.AnalisisDtos.GanadorDto;
import pe.justaapuesta.analisis.AnalisisDtos.LigaDto;
import pe.justaapuesta.analisis.AnalisisDtos.MercadoDto;
import pe.justaapuesta.analisis.AnalisisDtos.PartidoResumenDto;
import pe.justaapuesta.analisis.AnalisisDtos.PickDto;
import pe.justaapuesta.comun.NoEncontradoException;
import pe.justaapuesta.config.JustaProperties;
import pe.justaapuesta.partido.CatalogoService;
import pe.justaapuesta.partido.Equipo;
import pe.justaapuesta.partido.EstadisticaPartido;
import pe.justaapuesta.partido.EstadisticaPartidoRepository;
import pe.justaapuesta.partido.EstadoPartido;
import pe.justaapuesta.partido.Partido;
import pe.justaapuesta.partido.PartidoRepository;
import pe.justaapuesta.picks.FavoritoRepository;
import pe.justaapuesta.picks.PickSeguidoRepository;
import pe.justaapuesta.proveedor.FootballDataProvider;
import pe.justaapuesta.proveedor.ProveedorException;
import pe.justaapuesta.sync.BusquedaCache;
import pe.justaapuesta.sync.BusquedaCacheRepository;
import pe.justaapuesta.sync.SincronizacionService;
import pe.justaapuesta.usuario.Modo;
import pe.justaapuesta.usuario.Usuario;
import pe.justaapuesta.usuario.UsuarioRepository;

@Service
@RequiredArgsConstructor
public class AnalisisService {

	private static final Logger log = LoggerFactory.getLogger(AnalisisService.class);
	/** Un análisis de un partido por jugar se recalcula si tiene más de esto. */
	private static final Duration VIGENCIA_ANALISIS = Duration.ofHours(6);
	private static final Duration VIGENCIA_H2H = Duration.ofDays(7);

	private final PartidoRepository partidoRepository;
	private final EstadisticaPartidoRepository estadisticaRepository;
	private final AnalisisCacheRepository cacheRepository;
	private final EntradasService entradasService;
	private final ObjectMapper json;
	private final FavoritoRepository favoritoRepository;
	private final PickSeguidoRepository pickRepository;
	private final UsuarioRepository usuarioRepository;
	private final SincronizacionService sync;
	private final FootballDataProvider proveedor;
	private final CatalogoService catalogo;
	private final BusquedaCacheRepository busquedaCache;
	private final JustaProperties props;

	// ---------- Entradas con caché ----------

	/** Entradas de varios partidos (usa la caché; calcula y guarda las que falten o estén viejas). */
	public Map<Long, Entradas> entradas(Collection<Partido> partidos) {
		Map<Long, AnalisisCache> caches = partidos.isEmpty() ? Map.of()
				: cacheRepository.base(partidos.stream().map(Partido::getId).toList()).stream()
						.collect(Collectors.toMap(c -> c.getClave().getPartidoId(), Function.identity()));
		Map<Long, Entradas> out = new HashMap<>();
		for (Partido p : partidos) {
			out.put(p.getId(), entradas(p, caches.get(p.getId()), false));
		}
		return out;
	}

	private Entradas entradas(Partido p, AnalisisCache cache, boolean forzar) {
		Instant ahora = Instant.now();
		boolean empezado = !p.getFechaHoraUtc().isAfter(ahora);
		if (cache != null && !forzar) {
			try {
				Entradas guardadas = json.readValue(cache.getJson(), Entradas.class);
				// Los de antes de las pruebas del pick blindado se recalculan si el partido aún no empieza
				if (empezado || guardadas.historiales() != null) {
					// Lo guardado se muestra al instante; si está viejo se recalcula aparte, sin hacer esperar a nadie
					if (!empezado && cache.getCalculadoEn().isBefore(ahora.minus(VIGENCIA_ANALISIS))) {
						recalcularAparte(p.getId());
					}
					return guardadas;
				}
			} catch (RuntimeException e) {
				log.warn("Caché de análisis ilegible para el partido {}; se recalcula", p.getId());
			}
		}
		Entradas e = entradasService.calcular(p);
		// Un partido ya empezado conserva su análisis previo; solo se guarda si no había
		if (!empezado || cache == null) {
			AnalisisCache c = cache != null ? cache : new AnalisisCache();
			c.setClave(new AnalisisCache.Clave(p.getId(), AnalisisCache.BASE));
			c.setJson(json.writeValueAsString(e));
			c.setCalculadoEn(ahora);
			cacheRepository.save(c);
		}
		return e;
	}

	/** Partidos que se están recalculando aparte (para no repetirlos). */
	private final Set<Long> enRecalculo = ConcurrentHashMap.newKeySet();
	/** Un solo hilo: el plan gratis tiene poca CPU y la base está en otro servidor. */
	private final ExecutorService aparte = Executors.newSingleThreadExecutor(
			Thread.ofVirtual().name("recalculo-", 0).factory());

	private void recalcularAparte(Long partidoId) {
		if (!enRecalculo.add(partidoId)) {
			return;
		}
		aparte.submit(() -> {
			try {
				partidoRepository.conEquipos(partidoId).ifPresent(this::precalcular);
			} catch (RuntimeException ex) {
				log.warn("No se pudo recalcular el partido {}: {}", partidoId, ex.getMessage());
			} finally {
				enRecalculo.remove(partidoId);
			}
		});
	}

	/**
	 * Deja listo el análisis de un partido para que la lista cargue al instante: los que no empiezan se recalculan
	 * con los datos más nuevos; los ya empezados solo se calculan si nunca se hizo (con datos de antes del partido).
	 */
	public void precalcular(Partido p) {
		boolean empezado = !p.getFechaHoraUtc().isAfter(Instant.now());
		AnalisisCache cache = cacheRepository.base(List.of(p.getId())).stream().findFirst().orElse(null);
		if (empezado && cache != null) {
			return;
		}
		entradas(p, cache, !empezado);
	}

	// ---------- Tarjetas de partido ----------

	public List<PartidoResumenDto> resumenes(List<Partido> partidos, Long usuarioId) {
		return resumenes(partidos, modoDe(usuarioId), favoritosDe(usuarioId));
	}

	public List<PartidoResumenDto> resumenes(List<Partido> partidos, Modo modo, Set<Long> favoritos) {
		Map<Long, Entradas> entradas = entradas(partidos);
		Map<Long, Motor.Final> finales = finales(partidos);
		return partidos.stream()
				.map(p -> resumen(p, entradas.get(p.getId()), modo, favoritos.contains(p.getId()), finales.get(p.getId())))
				.toList();
	}

	PartidoResumenDto resumen(Partido p, Entradas e, Modo modo, boolean favorito, Motor.Final f) {
		ConsejoDto consejo = null;
		if (e != null && e.suficientes()) {
			consejo = consejo(Motor.mejor(e, modo), f);
		}
		return new PartidoResumenDto(p.getId(), p.getFechaHoraUtc(),
				new LigaDto(p.getLiga().getNombre(), p.getLiga().getPais(), p.getLiga().getLogoUrl()),
				p.getEstadio(), estadoActual(p),
				new EquipoDto(p.getLocal().getId(), p.getLocal().getNombre(), p.getLocal().getEscudoUrl()),
				new EquipoDto(p.getVisita().getId(), p.getVisita().getNombre(), p.getVisita().getEscudoUrl()),
				p.getGolesLocal(), p.getGolesVisita(), favorito, consejo);
	}

	private static ConsejoDto consejo(Motor.Mercado m, Motor.Final f) {
		return new ConsejoDto(m.clave(), m.nombreCategoria(), m.texto(), m.pct(), Motor.conf(m.pct()),
				f == null ? null : m.check().apply(f), m.cuota(), m.blindado());
	}

	/**
	 * HOY / PROXIMO se recalculan con la fecha de hoy en Lima (el guardado puede ser de ayer). Uno que ya empezó
	 * y aún no tiene resultado se muestra EN_VIVO.
	 */
	public EstadoPartido estadoActual(Partido p) {
		if (p.getEstado() == EstadoPartido.HOY || p.getEstado() == EstadoPartido.PROXIMO) {
			if (!p.getFechaHoraUtc().isAfter(Instant.now())) {
				return EstadoPartido.EN_VIVO;
			}
			return EstadoPartido.porFecha(p.getFechaHoraUtc(), props.zona(), LocalDate.now(props.zona()));
		}
		return p.getEstado();
	}

	// ---------- Resultado final real ----------

	public Map<Long, Motor.Final> finales(Collection<Partido> partidos) {
		List<Partido> jugados = partidos.stream().filter(Partido::jugado).toList();
		if (jugados.isEmpty()) {
			return Map.of();
		}
		Map<EstadisticaPartido.Clave, EstadisticaPartido> stats = estadisticaRepository
				.dePartidos(jugados.stream().map(Partido::getId).toList()).stream()
				.collect(Collectors.toMap(EstadisticaPartido::getClave, Function.identity()));
		Map<Long, Motor.Final> out = new HashMap<>();
		for (Partido p : jugados) {
			EstadisticaPartido sh = stats.get(new EstadisticaPartido.Clave(p.getId(), p.getLocal().getId()));
			EstadisticaPartido sa = stats.get(new EstadisticaPartido.Clave(p.getId(), p.getVisita().getId()));
			Integer rojas = sh == null || sa == null || sh.getTarjetasRojas() == null || sa.getTarjetasRojas() == null
					? null : sh.getTarjetasRojas() + sa.getTarjetasRojas();
			out.put(p.getId(), new Motor.Final(p.getGolesLocal(), p.getGolesVisita(),
					sh == null ? null : sh.getCorners(), sa == null ? null : sa.getCorners(),
					sh == null ? null : sh.tarjetas(), sa == null ? null : sa.tarjetas(),
					sh == null ? null : sh.getRematesAPuerta(), sa == null ? null : sa.getRematesAPuerta(),
					sh == null ? null : sh.getRematesTotales(), sa == null ? null : sa.getRematesTotales(), rojas));
		}
		return out;
	}

	// ---------- Detalle ----------

	public AnalisisDto detalle(Long partidoId, Long usuarioId) {
		Partido p = partidoRepository.conEquipos(partidoId)
				.orElseThrow(() -> new NoEncontradoException("No encontramos ese partido."));
		Refresco r = refrescar(p);
		if (r.cambio()) {
			p = partidoRepository.conEquipos(partidoId).orElseThrow();
		}

		Modo modo = modoDe(usuarioId);
		AnalisisCache cache = cacheRepository.base(List.of(p.getId())).stream().findFirst().orElse(null);
		Entradas e = entradas(p, cache, r.cambio() && p.getFechaHoraUtc().isAfter(Instant.now()));
		Motor.Final f = finales(List.of(p)).get(p.getId());
		boolean favorito = favoritoRepository.existsById(new pe.justaapuesta.picks.Favorito.Clave(usuarioId, p.getId()));
		PartidoResumenDto resumen = resumen(p, e, modo, favorito, f);

		if (!e.suficientes()) {
			return new AnalisisDto(resumen, false,
					"Se necesitan al menos 3 partidos jugados de cada equipo.",
					e.hf(), e.af(), null, null, null, false, List.of(), List.of(), e.h2h(), List.of(), finalDto(f),
					List.of(), List.of(), r.aviso());
		}

		Motor.Ajuste a = Motor.adj(e);
		Motor.Ganador w = Motor.winProbs(e);
		List<Motor.Mercado> tp = Motor.picksFor(e, modo);
		Motor.Mercado top = Motor.mejor(e, modo);
		boolean siguiendo = pickRepository.existsByUsuarioIdAndPartidoIdAndMercadoClave(usuarioId, p.getId(), top.clave());

		List<CategoriaDto> categorias = Motor.markets(e).stream()
				.map(c -> new CategoriaDto(c.clave(), c.nombre(), c.promedio(), c.mercados().stream()
						.map(m -> new MercadoDto(m.clave(), m.texto(), m.pct(), Motor.tone(m.pct()),
								f == null ? null : m.check().apply(f), m.cuota(), m.modelo(), m.blindado(), m.pruebas()))
						.toList()))
				.toList();

		List<ComparaDto> cara = new ArrayList<>();
		cara.add(new ComparaDto("Goles esperados", a.xh(), a.xa()));
		if (e.corLocal() != null && e.corVisita() != null) {
			cara.add(new ComparaDto("Corners", e.corLocal(), e.corVisita()));
		}
		if (e.sotLocal() != null && e.sotVisita() != null) {
			cara.add(new ComparaDto("Remates a puerta", e.sotLocal(), e.sotVisita()));
		}
		if (e.remLocal() != null && e.remVisita() != null) {
			cara.add(new ComparaDto("Remates totales", e.remLocal(), e.remVisita()));
		}
		if (e.cardsLocal() != null && e.cardsVisita() != null) {
			cara.add(new ComparaDto("Tarjetas", e.cardsLocal(), e.cardsVisita()));
		}

		return new AnalisisDto(resumen, true, null, e.hf(), e.af(),
				new GanadorDto(w.h(), w.d(), w.a(), veredicto(w, p, f)),
				resumen.consejo(), Motor.reason(top.categoria(), e), siguiendo,
				tp.stream().filter(m -> !m.clave().equals(top.clave())).limit(2).map(AnalisisService::pick).toList(),
				Motor.lowPicks(e).stream().limit(2).map(AnalisisService::pick).toList(),
				e.h2h(), categorias, finalDto(f), cara, Motor.players(e), r.aviso());
	}

	private static PickDto pick(Motor.Mercado m) {
		return new PickDto(m.clave(), m.texto(), m.pct(), m.nombreCategoria(), m.cuota(), m.blindado());
	}

	private static String veredicto(Motor.Ganador w, Partido p, Motor.Final f) {
		int top = Math.max(w.h(), Math.max(w.d(), w.a()));
		String v = w.h() == top ? "Favorito: " + p.getLocal().getNombre() + " (" + w.h() + "%)"
				: w.a() == top ? "Favorito: " + p.getVisita().getNombre() + " (" + w.a() + "%)"
						: "Lo más probable es el empate (" + w.d() + "%)";
		if (f != null) {
			v += " · Final: " + (f.gh() > f.ga() ? "ganó " + p.getLocal().getNombre()
					: f.gh() < f.ga() ? "ganó " + p.getVisita().getNombre() : "empate");
		}
		return v;
	}

	private static FinalDto finalDto(Motor.Final f) {
		if (f == null) {
			return null;
		}
		StringBuilder r = new StringBuilder("Final: " + f.gh() + "-" + f.ga());
		if (f.cor() != null) {
			r.append(", ").append(f.cor()).append(" corners");
		}
		if (f.cards() != null) {
			r.append(", ").append(f.cards()).append(" tarjetas");
		}
		if (f.sot() != null) {
			r.append(", ").append(f.sot()).append(" a puerta");
		}
		return new FinalDto(f.gh(), f.ga(), f.cor(), f.cards(), f.sot(), r.toString());
	}

	public Set<Long> favoritosDe(Long usuarioId) {
		return Set.copyOf(favoritoRepository.partidosDe(usuarioId));
	}

	public Modo modoDe(Long usuarioId) {
		return usuarioRepository.findById(usuarioId).map(Usuario::getModo).orElse(Modo.SEGURO);
	}

	// ---------- Refresco bajo demanda (al abrir un partido) ----------

	private record Refresco(boolean cambio, String aviso) {
	}

	/**
	 * Antes de mostrar el detalle (plan gratis: pocas consultas y solo lo que falta):
	 * el resultado del día si el partido ya debería haber terminado, el H2H real si no lo tenemos (1 consulta,
	 * se reusa 7 días) y las estadísticas que falten del propio partido y del historial de ambos equipos
	 * (con tope). Si el proveedor falla o se acaba la cuota, se sigue con lo guardado.
	 */
	private Refresco refrescar(Partido p) {
		if (!proveedor.disponible()) {
			return new Refresco(false, null);
		}
		boolean cambio = false;
		Instant ahora = Instant.now();
		try {
			// Ya debería haber terminado: bajar de nuevo ese día para tener el marcador final
			boolean deberiaTerminar = p.getEstado() != EstadoPartido.JUGADO && p.getEstado() != EstadoPartido.SUSPENDIDO
					&& p.getFechaHoraUtc().isBefore(ahora.minus(Duration.ofMinutes(150)));
			// (solo los de API-Football: los de football-data.org se actualizan en la sincronización diaria)
			if (deberiaTerminar && p.getProveedorId() > 0) {
				sync.descargarDia(p.getFechaHoraUtc().atZone(props.zona()).toLocalDate());
				p = partidoRepository.conEquipos(p.getId()).orElseThrow();
				cambio = true;
			}

			String claveH2h = "h2h:" + Math.min(p.getLocal().getProveedorId(), p.getVisita().getProveedorId()) + "-"
					+ Math.max(p.getLocal().getProveedorId(), p.getVisita().getProveedorId());
			boolean sinH2h = partidoRepository.enfrentamientos(p.getLocal().getId(), p.getVisita().getId(),
					p.getFechaHoraUtc(), PageRequest.of(0, 1)).isEmpty();
			boolean h2hReciente = busquedaCache.findById(claveH2h)
					.filter(b -> b.getBuscadoEn().isAfter(ahora.minus(VIGENCIA_H2H))).isPresent();
			boolean ambosEnApiFootball = p.getLocal().getProveedorId() > 0 && p.getVisita().getProveedorId() > 0;
			if (sinH2h && !h2hReciente && ambosEnApiFootball) {
				// Solo los de los últimos 2 años (los más viejos se borrarían en la limpieza diaria)
				Instant limite = ahora.minus(Duration.ofDays(props.sync().diasRetencion()));
				catalogo.guardarPartidos(proveedor.enfrentamientos(p.getLocal().getProveedorId(),
						p.getVisita().getProveedorId()).stream().filter(x -> x.fecha().isAfter(limite)).toList());
				busquedaCache.save(new BusquedaCache(claveH2h, ahora));
				cambio = true;
			}

			// Estadísticas que falten: primero el propio partido, luego los últimos de cada equipo (con tope)
			List<Partido> sinStats = new ArrayList<>();
			if (p.jugado() && !p.isDetalleSync()) {
				sinStats.add(p);
			}
			int tope = props.sync().estadisticasPorDetalle();
			for (Equipo eq : List.of(p.getLocal(), p.getVisita())) {
				partidoRepository.jugadosDeEquipo(eq.getId(), p.getFechaHoraUtc(), PageRequest.of(0, EntradasService.ULTIMOS))
						.stream().filter(x -> !x.isDetalleSync()).limit(tope / 2).forEach(sinStats::add);
			}
			if (sync.completarEstadisticas(sinStats) > 0) {
				cambio = true;
			}
			return new Refresco(cambio, null);
		} catch (ProveedorException ex) {
			log.warn("No se pudo refrescar el partido {}: {}", p.getId(), ex.getMessage());
			return new Refresco(cambio, "No pudimos actualizar todos los datos ahora; mostramos los guardados.");
		}
	}
}
