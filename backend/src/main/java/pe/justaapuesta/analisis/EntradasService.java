package pe.justaapuesta.analisis;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import pe.justaapuesta.config.JustaProperties;
import pe.justaapuesta.partido.EstadisticaPartido;
import pe.justaapuesta.partido.EstadisticaPartidoRepository;
import pe.justaapuesta.partido.JugadorPartido;
import pe.justaapuesta.partido.JugadorPartidoRepository;
import pe.justaapuesta.partido.Partido;
import pe.justaapuesta.partido.PartidoRepository;

/**
 * Calcula las entradas del modelo (sección 5.1) con los últimos 10 partidos REALES de cada equipo
 * jugados antes del inicio del partido analizado.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EntradasService {

	public static final int ULTIMOS = 10;
	private static final int RACHA = 5;
	private static final int H2H = 6;
	/** Partidos de la liga para su promedio y para la prueba de la liga. */
	private static final int LIGA = 60;
	private static final int LIGA_MINIMO = 20;
	/** Cuántos partidos vale el promedio de la liga frente a los del equipo. */
	private static final double PRIOR_LIGA = 5;

	private final PartidoRepository partidoRepository;
	private final EstadisticaPartidoRepository estadisticaRepository;
	private final JugadorPartidoRepository jugadorRepository;
	private final JustaProperties props;

	public Entradas calcular(Partido p) {
		Long local = p.getLocal().getId();
		Long visita = p.getVisita().getId();
		List<Partido> histL = partidoRepository.jugadosDeEquipo(local, p.getFechaHoraUtc(), PageRequest.of(0, ULTIMOS));
		List<Partido> histV = partidoRepository.jugadosDeEquipo(visita, p.getFechaHoraUtc(), PageRequest.of(0, ULTIMOS));

		List<Partido> enf = partidoRepository.enfrentamientos(local, visita, p.getFechaHoraUtc(), PageRequest.of(0, H2H));
		List<Partido> liga = partidoRepository.jugadosDeLiga(p.getLiga().getId(), p.getFechaHoraUtc(),
				PageRequest.of(0, LIGA));

		List<Long> ids = Stream.of(histL, histV, enf, liga).flatMap(List::stream).map(Partido::getId).distinct().toList();
		Map<EstadisticaPartido.Clave, EstadisticaPartido> stats = ids.isEmpty() ? Map.of()
				: estadisticaRepository.dePartidos(ids).stream()
						.collect(Collectors.toMap(EstadisticaPartido::getClave, Function.identity()));

		// xh = promedio(goles a favor del local en casa, goles en contra del visitante fuera)
		double gfLocalCasa = promedio(histL, x -> x.getLocal().getId().equals(local), x -> x.getGolesLocal(),
				x -> gf(x, local));
		double gcVisitaFuera = promedio(histV, x -> x.getVisita().getId().equals(visita), x -> x.getGolesLocal(),
				x -> gc(x, visita));
		// xa = promedio(goles a favor del visitante fuera, goles en contra del local en casa)
		double gfVisitaFuera = promedio(histV, x -> x.getVisita().getId().equals(visita), x -> x.getGolesVisita(),
				x -> gf(x, visita));
		double gcLocalCasa = promedio(histL, x -> x.getLocal().getId().equals(local), x -> x.getGolesVisita(),
				x -> gc(x, local));
		double xh = (gfLocalCasa + gcVisitaFuera) / 2;
		double xa = (gfVisitaFuera + gcLocalCasa) / 2;

		// Con pocos partidos un equipo sale exagerado (una goleada pesa mucho): se acerca al promedio real de su
		// liga, más cuanto menos partidos tenga. Con 10 partidos la liga pesa 1/3; con 5, la mitad.
		List<Partido> ligaJugados = liga.stream().filter(Partido::jugado).toList();
		if (ligaJugados.size() >= LIGA_MINIMO) {
			double golesCasa = ligaJugados.stream().mapToInt(Partido::getGolesLocal).average().orElse(xh);
			double golesFuera = ligaJugados.stream().mapToInt(Partido::getGolesVisita).average().orElse(xa);
			int n = Math.min(histL.size(), histV.size());
			double pesoLiga = PRIOR_LIGA / (n + PRIOR_LIGA);
			xh = (1 - pesoLiga) * xh + pesoLiga * golesCasa;
			xa = (1 - pesoLiga) * xa + pesoLiga * golesFuera;
		}

		Entradas.H2h h2h = h2h(p, enf);
		Entradas.Historiales historiales = new Entradas.Historiales(
				finales(histL, stats, x -> !x.getLocal().getId().equals(local)),
				finales(histV, stats, x -> !x.getVisita().getId().equals(visita)),
				finales(enf, stats, x -> !x.getLocal().getId().equals(local)),
				finales(liga, stats, x -> false));

		return new Entradas(
				p.getLocal().getNombre(),
				p.getVisita().getNombre(),
				histL.size(),
				histV.size(),
				xh,
				xa,
				promedioStat(histL, local, stats, EstadisticaPartido::getCorners, x -> x.getLocal().getId().equals(local)),
				promedioStat(histV, visita, stats, EstadisticaPartido::getCorners, x -> x.getVisita().getId().equals(visita)),
				promedioStat(histL, local, stats, EstadisticaPartido::tarjetas, x -> x.getLocal().getId().equals(local)),
				promedioStat(histV, visita, stats, EstadisticaPartido::tarjetas, x -> x.getVisita().getId().equals(visita)),
				promedioStat(histL, local, stats, EstadisticaPartido::getRematesAPuerta, x -> x.getLocal().getId().equals(local)),
				promedioStat(histV, visita, stats, EstadisticaPartido::getRematesAPuerta, x -> x.getVisita().getId().equals(visita)),
				promedioStat(histL, local, stats, EstadisticaPartido::getRematesTotales, x -> x.getLocal().getId().equals(local)),
				promedioStat(histV, visita, stats, EstadisticaPartido::getRematesTotales, x -> x.getVisita().getId().equals(visita)),
				racha(histL, local),
				racha(histV, visita),
				h2h,
				jugadores(local, histL),
				jugadores(visita, histV),
				historiales);
	}

	/**
	 * Resultados reales de esos partidos para comprobar mercados. Con "girar" el partido se da vuelta
	 * (H pasa a A), para que el equipo de hoy quede siempre del mismo lado.
	 */
	private static List<Motor.Final> finales(List<Partido> partidos,
			Map<EstadisticaPartido.Clave, EstadisticaPartido> stats, java.util.function.Predicate<Partido> girar) {
		List<Motor.Final> out = new ArrayList<>();
		for (Partido x : partidos) {
			if (!x.jugado()) {
				continue;
			}
			EstadisticaPartido sh = stats.get(new EstadisticaPartido.Clave(x.getId(), x.getLocal().getId()));
			EstadisticaPartido sa = stats.get(new EstadisticaPartido.Clave(x.getId(), x.getVisita().getId()));
			if (girar.test(x)) {
				EstadisticaPartido t = sh;
				sh = sa;
				sa = t;
			}
			int gh = girar.test(x) ? x.getGolesVisita() : x.getGolesLocal();
			int ga = girar.test(x) ? x.getGolesLocal() : x.getGolesVisita();
			Integer rojas = sh == null || sa == null || sh.getTarjetasRojas() == null || sa.getTarjetasRojas() == null
					? null : sh.getTarjetasRojas() + sa.getTarjetasRojas();
			out.add(new Motor.Final(gh, ga,
					sh == null ? null : sh.getCorners(), sa == null ? null : sa.getCorners(),
					sh == null ? null : sh.tarjetas(), sa == null ? null : sa.tarjetas(),
					sh == null ? null : sh.getRematesAPuerta(), sa == null ? null : sa.getRematesAPuerta(),
					sh == null ? null : sh.getRematesTotales(), sa == null ? null : sa.getRematesTotales(), rojas));
		}
		return out;
	}

	/** Peso del partido según lo reciente: el último vale 1, el anterior 0.92, luego 0.85... */
	private static final double DECAIMIENTO = 0.92;

	/** Promedio que da más peso a los partidos más recientes (la lista viene del más reciente al más antiguo). */
	private static double ponderado(List<Partido> hist, Function<Partido, Integer> valor) {
		double suma = 0, pesos = 0, w = 1;
		for (Partido x : hist) {
			suma += w * valor.apply(x);
			pesos += w;
			w *= DECAIMIENTO;
		}
		return pesos == 0 ? 0 : suma / pesos;
	}

	/**
	 * Promedio de goles en los partidos de la condición (en casa / fuera), con más peso a los recientes.
	 * Si el equipo no jugó ninguno en esa condición, se usa el promedio de todos sus partidos.
	 */
	private static double promedio(List<Partido> hist, java.util.function.Predicate<Partido> condicion,
			Function<Partido, Integer> valorCondicion, Function<Partido, Integer> valorGeneral) {
		List<Partido> enCondicion = hist.stream().filter(condicion).toList();
		if (!enCondicion.isEmpty()) {
			return ponderado(enCondicion, valorCondicion);
		}
		return ponderado(hist, valorGeneral);
	}

	private static int gf(Partido x, Long equipo) {
		return x.getLocal().getId().equals(equipo) ? x.getGolesLocal() : x.getGolesVisita();
	}

	private static int gc(Partido x, Long equipo) {
		return x.getLocal().getId().equals(equipo) ? x.getGolesVisita() : x.getGolesLocal();
	}

	/**
	 * Promedio por partido de una estadística del equipo, mirando cómo juega en esa condición (el local en casa,
	 * el visitante fuera) si tiene al menos 3 partidos así con el dato; si no, todos sus partidos.
	 * null si hay menos de 3 partidos con el dato.
	 */
	private static Double promedioStat(List<Partido> hist, Long equipo,
			Map<EstadisticaPartido.Clave, EstadisticaPartido> stats, Function<EstadisticaPartido, Integer> valor,
			java.util.function.Predicate<Partido> condicion) {
		List<Integer> enCondicion = valores(hist.stream().filter(condicion).toList(), equipo, stats, valor);
		if (enCondicion.size() >= Entradas.MINIMO_PARTIDOS) {
			return enCondicion.stream().mapToInt(Integer::intValue).average().orElse(0);
		}
		List<Integer> todos = valores(hist, equipo, stats, valor);
		return todos.size() < Entradas.MINIMO_PARTIDOS ? null
				: todos.stream().mapToInt(Integer::intValue).average().orElse(0);
	}

	private static List<Integer> valores(List<Partido> hist, Long equipo,
			Map<EstadisticaPartido.Clave, EstadisticaPartido> stats, Function<EstadisticaPartido, Integer> valor) {
		List<Integer> out = new ArrayList<>();
		for (Partido x : hist) {
			EstadisticaPartido s = stats.get(new EstadisticaPartido.Clave(x.getId(), equipo));
			if (s != null && valor.apply(s) != null) {
				out.add(valor.apply(s));
			}
		}
		return out;
	}

	/** Últimos 5 como G/E/P, del más antiguo al más reciente. */
	static String racha(List<Partido> histMasRecientePrimero, Long equipo) {
		List<Partido> ultimos = new ArrayList<>(histMasRecientePrimero.subList(0, Math.min(RACHA, histMasRecientePrimero.size())));
		Collections.reverse(ultimos);
		StringBuilder sb = new StringBuilder();
		for (Partido x : ultimos) {
			int a = gf(x, equipo), b = gc(x, equipo);
			sb.append(a > b ? 'G' : a == b ? 'E' : 'P');
		}
		return sb.toString();
	}

	private Entradas.H2h h2h(Partido p, List<Partido> lista) {
		if (lista.isEmpty()) {
			return null;
		}
		ZoneId zona = props.zona();
		int hw = 0, d = 0, aw = 0, goles = 0, btts = 0, over = 0;
		List<Entradas.Duelo> duelos = new ArrayList<>();
		for (Partido x : lista) {
			// Goles del local de HOY y del visitante de HOY en ese duelo
			int gA = gf(x, p.getLocal().getId());
			int gB = gf(x, p.getVisita().getId());
			if (gA > gB) {
				hw++;
			} else if (gA == gB) {
				d++;
			} else {
				aw++;
			}
			goles += gA + gB;
			if (gA > 0 && gB > 0) {
				btts++;
			}
			if (gA + gB > 2) {
				over++;
			}
			duelos.add(new Entradas.Duelo(fecha(x.getFechaHoraUtc().atZone(zona).toLocalDate()),
					x.getEstadio(), x.getLocal().getNombre(), x.getVisita().getNombre(), x.getGolesLocal(),
					x.getGolesVisita()));
		}
		int n = lista.size();
		return new Entradas.H2h(hw, d, aw, n, goles / (double) n, Math.round(btts * 100f / n),
				Math.round(over * 100f / n), duelos);
	}

	/** Mismos meses que el prototipo: "14 sep 2025" (no "set."). */
	private static final String[] MESES = { "ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic" };

	static String fecha(java.time.LocalDate d) {
		return d.getDayOfMonth() + " " + MESES[d.getMonthValue() - 1] + " " + d.getYear();
	}

	/** Promedios por partido de cada jugador en los últimos partidos de su equipo. */
	private List<Entradas.Jugador> jugadores(Long equipo, List<Partido> hist) {
		if (hist.isEmpty()) {
			return List.of();
		}
		List<JugadorPartido> filas = jugadorRepository.deEquipoEnPartidos(equipo,
				hist.stream().map(Partido::getId).toList());
		Map<Long, List<JugadorPartido>> porJugador = new HashMap<>();
		filas.forEach(j -> porJugador.computeIfAbsent(j.getClave().getJugadorId(), k -> new ArrayList<>()).add(j));
		List<Entradas.Jugador> out = new ArrayList<>();
		for (List<JugadorPartido> l : porJugador.values()) {
			int n = l.size();
			JugadorPartido ref = l.get(0);
			out.add(new Entradas.Jugador(ref.getNombre(), ref.getDorsal(), n,
					l.stream().mapToInt(JugadorPartido::getGoles).sum() / (double) n,
					l.stream().mapToInt(JugadorPartido::getRematesAPuerta).sum() / (double) n,
					l.stream().mapToInt(j -> j.getAmarillas() + j.getRojas()).sum() / (double) n));
		}
		return out;
	}
}
