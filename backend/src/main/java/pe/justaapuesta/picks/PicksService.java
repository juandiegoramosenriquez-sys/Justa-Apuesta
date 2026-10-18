package pe.justaapuesta.picks;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import pe.justaapuesta.analisis.AnalisisDtos.ListaPartidosDto;
import pe.justaapuesta.analisis.AnalisisService;
import pe.justaapuesta.analisis.Entradas;
import pe.justaapuesta.analisis.Motor;
import pe.justaapuesta.comun.ConflictoException;
import pe.justaapuesta.comun.NoEncontradoException;
import pe.justaapuesta.comun.SolicitudInvalidaException;
import pe.justaapuesta.partido.Partido;
import pe.justaapuesta.partido.PartidoRepository;
import pe.justaapuesta.partido.PartidosService;
import pe.justaapuesta.picks.PicksDtos.CombinadaDto;
import pe.justaapuesta.picks.PicksDtos.PickPartidoDto;
import pe.justaapuesta.picks.PicksDtos.PickSeguidoDto;
import pe.justaapuesta.picks.PicksDtos.PicksDelDiaDto;
import pe.justaapuesta.usuario.Modo;
import pe.justaapuesta.usuario.Usuario;
import pe.justaapuesta.usuario.UsuarioRepository;

/**
 * Picks del día, favoritos y picks seguidos.
 */
@Service
@RequiredArgsConstructor
public class PicksService {

	private final PartidosService partidos;
	private final AnalisisService analisis;
	private final PartidoRepository partidoRepository;
	private final UsuarioRepository usuarioRepository;
	private final FavoritoRepository favoritoRepository;
	private final PickSeguidoRepository pickRepository;

	// ---------- Picks del día ----------

	/** Combinadas de más a menos segura: cuántos picks lleva cada una. */
	private static final int[] TAMANOS = { 2, 3, 4 };
	private static final String[][] NIVELES = { { "MUY_SEGURA", "Muy segura" }, { "SEGURA", "Segura" },
			{ "RETO", "Para el reto" } };
	private static final int MEJORES = 10;

	/** Primero los blindados; entre iguales, el más probable. */
	private static final Comparator<PickPartidoDto> ORDEN = Comparator.comparing(PickPartidoDto::blindado).reversed()
			.thenComparing(Comparator.comparingInt(PickPartidoDto::pct).reversed());

	/**
	 * Combinadas seguras: solo con picks blindados de cuota jugable, uno por partido (los de mayor certeza primero).
	 * Mejores picks: los 2 mejores de cada partido según el modo con cuota jugable, blindados primero.
	 */
	public PicksDelDiaDto delDia(PartidosService.Pestana pestana, Long usuarioId) {
		if (pestana == PartidosService.Pestana.JUGADO) {
			throw new SolicitudInvalidaException("Los picks solo son de hoy o próximos.");
		}
		Candidatos c = candidatos(pestana, modo(usuarioId));
		// Lo guardado puede tener unos minutos: los partidos que ya empezaron salen de los picks
		Instant ahora = Instant.now();
		List<PickPartidoDto> blindados = c.blindados().stream().filter(x -> x.fecha().isAfter(ahora)).toList();
		List<PickPartidoDto> mejores = c.mejores().stream().filter(x -> x.fecha().isAfter(ahora)).limit(MEJORES).toList();

		List<CombinadaDto> combinadas = new ArrayList<>();
		if (blindados.size() == 1) {
			combinadas.add(combinada("MUY_SEGURA", "Pick blindado", blindados));
		}
		for (int i = 0; i < TAMANOS.length && blindados.size() >= TAMANOS[i]; i++) {
			combinadas.add(combinada(NIVELES[i][0], NIVELES[i][1], blindados.subList(0, TAMANOS[i])));
		}
		return new PicksDelDiaDto(combinadas, mejores);
	}

	/** Picks ya ordenados de una pestaña y un modo: el mejor blindado de cada partido y los mejores del modo. */
	private record Candidatos(Instant hechos, List<PickPartidoDto> blindados, List<PickPartidoDto> mejores) {
	}

	private record Clave(PartidosService.Pestana pestana, Modo modo) {
	}

	/** Se rehacen como mucho cada 3 minutos; mientras tanto se responde con lo guardado (carga al instante). */
	private static final java.time.Duration VIGENCIA = java.time.Duration.ofMinutes(3);
	private final Map<Clave, Candidatos> guardados = new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.Set<Clave> rehaciendo = java.util.concurrent.ConcurrentHashMap.newKeySet();

	private Candidatos candidatos(PartidosService.Pestana pestana, Modo modo) {
		Clave clave = new Clave(pestana, modo);
		Candidatos c = guardados.get(clave);
		if (c == null) {
			return calcular(clave);
		}
		if (c.hechos().isBefore(Instant.now().minus(VIGENCIA)) && rehaciendo.add(clave)) {
			Thread.ofVirtual().name("picks-" + pestana).start(() -> {
				try {
					calcular(clave);
				} finally {
					rehaciendo.remove(clave);
				}
			});
		}
		return c;
	}

	private Candidatos calcular(Clave clave) {
		List<Partido> lista = partidos.partidosDe(clave.pestana()).stream()
				.filter(p -> p.getFechaHoraUtc().isAfter(Instant.now()))
				.toList();
		Map<Long, Entradas> entradas = analisis.entradas(lista);
		List<PickPartidoDto> blindados = new ArrayList<>();
		List<PickPartidoDto> mejores = new ArrayList<>();
		for (Partido p : lista) {
			Entradas e = entradas.get(p.getId());
			if (e == null || !e.suficientes()) {
				continue;
			}
			List<Motor.Mercado> todos = analisis.mercados(p.getId(), e);
			todos.stream().filter(m -> m.blindado() && Motor.jugable(m))
					.max(Comparator.comparingInt(Motor.Mercado::pct))
					.ifPresent(m -> blindados.add(dto(p, m)));
			Motor.picksFor(todos, clave.modo()).stream().filter(Motor::jugable).map(m -> dto(p, m)).sorted(ORDEN).limit(2)
					.forEach(mejores::add);
		}
		blindados.sort(ORDEN);
		mejores.sort(ORDEN);
		Candidatos c = new Candidatos(Instant.now(), List.copyOf(blindados), List.copyOf(mejores));
		guardados.put(clave, c);
		return c;
	}

	/** Rehace los picks de hoy y próximos en todos los modos (después de precalcular los análisis). */
	public void rehacerPicks() {
		for (PartidosService.Pestana p : List.of(PartidosService.Pestana.HOY, PartidosService.Pestana.PROXIMO)) {
			for (Modo m : Modo.values()) {
				calcular(new Clave(p, m));
			}
		}
	}

	private static CombinadaDto combinada(String nivel, String nombre, List<PickPartidoDto> picks) {
		double producto = picks.stream().mapToDouble(x -> x.pct() / 100.0).reduce(1, (a, b) -> a * b);
		return new CombinadaDto(nivel, nombre, List.copyOf(picks), (int) Math.round(producto * 100),
				Motor.cuotaTotal(picks.stream().map(PickPartidoDto::pct).toList()));
	}

	private static PickPartidoDto dto(Partido p, Motor.Mercado m) {
		return new PickPartidoDto(p.getId(), p.getLocal().getNombre() + " vs " + p.getVisita().getNombre(),
				p.getFechaHoraUtc(), m.clave(), m.texto(), m.pct(), m.nombreCategoria(), m.cuota(), m.blindado());
	}

	// ---------- Favoritos ----------

	@Transactional
	public void marcarFavorito(Long usuarioId, Long partidoId) {
		if (!partidoRepository.existsById(partidoId)) {
			throw new NoEncontradoException("No encontramos ese partido.");
		}
		Favorito.Clave clave = new Favorito.Clave(usuarioId, partidoId);
		if (!favoritoRepository.existsById(clave)) {
			favoritoRepository.save(new Favorito(clave, Instant.now()));
		}
	}

	@Transactional
	public void quitarFavorito(Long usuarioId, Long partidoId) {
		favoritoRepository.deleteById(new Favorito.Clave(usuarioId, partidoId));
	}

	public ListaPartidosDto favoritos(Long usuarioId) {
		List<Partido> lista = partidoRepository.conEquipos(favoritoRepository.partidosDe(usuarioId)).stream()
				.sorted(Comparator.comparing(Partido::getFechaHoraUtc).reversed())
				.toList();
		return new ListaPartidosDto(analisis.resumenes(lista, usuarioId), partidos.actualizadoEn(), null);
	}

	// ---------- Picks seguidos ----------

	/** Sigue un mercado de un partido que aún no empieza. Texto y probabilidad son los calculados antes del partido. */
	@Transactional
	public PickSeguidoDto seguir(Long usuarioId, Long partidoId, String mercadoClave) {
		Partido p = partidoRepository.conEquipos(partidoId)
				.orElseThrow(() -> new NoEncontradoException("No encontramos ese partido."));
		if (!p.getFechaHoraUtc().isAfter(Instant.now())) {
			throw new ConflictoException("Este partido ya empezó; no se puede seguir el pick.");
		}
		Entradas e = analisis.entradas(List.of(p)).get(p.getId());
		if (e == null || !e.suficientes()) {
			throw new ConflictoException("Este partido no tiene datos suficientes.");
		}
		Motor.Mercado m = Motor.allPicks(e).stream().filter(x -> x.clave().equals(mercadoClave)).findFirst()
				.orElseThrow(() -> new SolicitudInvalidaException("Ese mercado no existe para este partido."));
		PickSeguido ps = pickRepository.findByUsuarioIdAndPartidoIdAndMercadoClave(usuarioId, partidoId, mercadoClave)
				.orElseGet(PickSeguido::new);
		if (ps.getId() == null) {
			ps.setUsuarioId(usuarioId);
			ps.setPartidoId(partidoId);
			ps.setMercadoClave(mercadoClave);
			ps.setTexto(m.texto().length() > 150 ? m.texto().substring(0, 150) : m.texto());
			ps.setProbabilidad(m.pct());
			ps.setEstado(EstadoPick.PENDIENTE);
			ps.setCreadoEn(Instant.now());
			pickRepository.save(ps);
		}
		return aDto(ps, p);
	}

	@Transactional
	public void dejarDeSeguir(Long usuarioId, Long partidoId, String mercadoClave) {
		pickRepository.findByUsuarioIdAndPartidoIdAndMercadoClave(usuarioId, partidoId, mercadoClave)
				.ifPresent(pickRepository::delete);
	}

	/** Vacía el boleto: quita los picks que aún no se resuelven. */
	@Transactional
	public void vaciarBoleto(Long usuarioId) {
		pickRepository.deleteAll(pickRepository.findByUsuarioIdOrderByCreadoEnDesc(usuarioId).stream()
				.filter(x -> x.getEstado() == EstadoPick.PENDIENTE).toList());
	}

	@Transactional(readOnly = true)
	public List<PickSeguidoDto> seguidos(Long usuarioId) {
		List<PickSeguido> lista = pickRepository.findByUsuarioIdOrderByCreadoEnDesc(usuarioId);
		Map<Long, Partido> ps = partidoRepository.conEquipos(lista.stream().map(PickSeguido::getPartidoId).toList())
				.stream().collect(Collectors.toMap(Partido::getId, Function.identity()));
		return lista.stream().map(x -> aDto(x, ps.get(x.getPartidoId()))).toList();
	}

	private static PickSeguidoDto aDto(PickSeguido x, Partido p) {
		String partido = p == null ? "" : p.getLocal().getNombre() + " vs " + p.getVisita().getNombre();
		return new PickSeguidoDto(x.getId(), x.getPartidoId(), partido, p == null ? null : p.getFechaHoraUtc(),
				x.getMercadoClave(), x.getTexto(), x.getProbabilidad(), Motor.cuota(x.getProbabilidad()), x.getEstado(),
				x.getCreadoEn());
	}

	private Modo modo(Long usuarioId) {
		return usuarioRepository.findById(usuarioId).map(Usuario::getModo).orElse(Modo.SEGURO);
	}
}
