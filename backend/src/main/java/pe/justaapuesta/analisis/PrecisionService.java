package pe.justaapuesta.analisis;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import pe.justaapuesta.partido.Partido;
import pe.justaapuesta.partido.PartidoRepository;
import pe.justaapuesta.usuario.Modo;

/**
 * Mide el acierto real del modelo: toma partidos ya jugados, calcula su análisis SOLO con los datos de antes del
 * partido (lo mismo que se habría mostrado ese día) y lo compara con el resultado. Sirve para ajustar el pick
 * blindado con evidencia y para mostrar en la app cuánto acierta de verdad.
 */
@Service
@RequiredArgsConstructor
public class PrecisionService {

	private static final Logger log = LoggerFactory.getLogger(PrecisionService.class);
	public static final int DIAS = 30;
	public static final int MAXIMO = 400;

	private final PartidoRepository partidoRepository;
	private final EntradasService entradasService;
	private final AnalisisService analisis;
	private final AtomicReference<Precision> ultima = new AtomicReference<>();
	private final AtomicBoolean enCurso = new AtomicBoolean(false);

	/** Aciertos de un grupo de picks. */
	public record Conteo(int picks, int acertados, int pct) {
		static Conteo de(int[] c) {
			return new Conteo(c[0], c[1], c[0] == 0 ? 0 : Math.round(c[1] * 100f / c[0]));
		}
	}

	/**
	 * @param blindados   el pick blindado de cada partido (el más probable con cuota jugable)
	 * @param consejo     el consejo del modo Seguro de cada partido
	 * @param porMercado  todos los picks blindados, por tipo de mercado (goles.o15, resultado.local_empate...)
	 * @param porCerteza  todos los mercados, por rango de certeza: ¿el 80% sale el 80% de las veces?
	 */
	public record Precision(Instant calculadoEn, int partidos, Conteo blindados, Conteo consejo,
			Map<String, Conteo> porMercado, Map<String, Conteo> porCerteza, List<Muestra> muestras) {
	}

	/** Un mercado de un partido jugado (solo para el ajuste del modelo, no se guarda). */
	public record Muestra(long partido, String clave, int pct, int modelo, boolean blindado, double cuota,
			boolean salio, String pruebas) {
	}

	/** El último resultado guardado (null si aún no se calculó). */
	public Precision ultima() {
		return ultima.get();
	}


	/** Mide en segundo plano (tarda: recalcula cientos de partidos). */
	public void medirAparte() {
		if (!enCurso.compareAndSet(false, true)) {
			return;
		}
		Thread.ofVirtual().name("precision").start(() -> {
			try {
				medir(DIAS, MAXIMO);
			} catch (RuntimeException e) {
				log.warn("No se pudo medir el acierto: {}", e.getMessage());
			} finally {
				enCurso.set(false);
			}
		});
	}

	public Precision medir(int dias, int maximo) {
		return medir(dias, maximo, false);
	}

	public Precision medir(int dias, int maximo, boolean conMuestras) {
		List<Muestra> muestras = new ArrayList<>();
		Instant ahora = Instant.now();
		List<Partido> jugados = partidoRepository.entre(ahora.minus(Duration.ofDays(dias)), ahora).stream()
				.filter(Partido::jugado)
				.sorted(Comparator.comparing(Partido::getFechaHoraUtc).reversed())
				.limit(maximo)
				.toList();
		Map<Long, Motor.Final> finales = analisis.finales(jugados);

		int[] blindados = new int[2], consejo = new int[2];
		Map<String, int[]> porMercado = new TreeMap<>();
		Map<String, int[]> porCerteza = new LinkedHashMap<>();
		for (String r : List.of("50-64%", "65-74%", "75-79%", "80-84%", "85-89%", "90%+")) {
			porCerteza.put(r, new int[2]);
		}
		int analizados = 0;
		for (Partido p : jugados) {
			Motor.Final f = finales.get(p.getId());
			if (f == null) {
				continue;
			}
			Entradas e = entradasService.calcular(p);
			if (!e.suficientes()) {
				continue;
			}
			analizados++;
			List<Motor.Mercado> todos = Motor.allPicks(e);
			for (Motor.Mercado m : todos) {
				Boolean salio = m.check().apply(f);
				if (salio == null) {
					continue;
				}
				if (conMuestras) {
					muestras.add(new Muestra(p.getId(), m.clave(), m.pct(), m.modelo(), m.blindado(), m.cuota(), salio,
							m.pruebas().stream().map(x -> x.paso() == null ? "-" : x.paso() ? "1" : "0")
									.reduce("", String::concat)));
				}
				String rango = rango(m.pct());
				if (rango != null) {
					sumar(porCerteza.get(rango), salio);
				}
				if (m.blindado() && Motor.jugable(m)) {
					sumar(porMercado.computeIfAbsent(m.clave(), k -> new int[2]), salio);
				}
			}
			todos.stream().filter(m -> m.blindado() && Motor.jugable(m)).max(Comparator.comparingInt(Motor.Mercado::pct))
					.ifPresent(m -> {
						Boolean salio = m.check().apply(f);
						if (salio != null) {
							sumar(blindados, salio);
						}
					});
			Boolean salioConsejo = Motor.mejor(e, Modo.SEGURO).check().apply(f);
			if (salioConsejo != null) {
				sumar(consejo, salioConsejo);
			}
		}
		Map<String, Conteo> mercados = new LinkedHashMap<>();
		porMercado.forEach((k, v) -> mercados.put(k, Conteo.de(v)));
		Map<String, Conteo> certeza = new LinkedHashMap<>();
		porCerteza.forEach((k, v) -> certeza.put(k, Conteo.de(v)));
		Precision r = new Precision(ahora, analizados, Conteo.de(blindados), Conteo.de(consejo), mercados, certeza,
				conMuestras ? muestras : List.of());
		// Lo que se guarda para la app va sin las muestras (son miles)
		ultima.set(new Precision(r.calculadoEn(), r.partidos(), r.blindados(), r.consejo(), r.porMercado(),
				r.porCerteza(), List.of()));
		log.info("Acierto real en {} partidos: blindados {}% ({} picks), consejo {}%", analizados,
				r.blindados().pct(), r.blindados().picks(), r.consejo().pct());
		return r;
	}

	private static void sumar(int[] c, boolean salio) {
		c[0]++;
		if (salio) {
			c[1]++;
		}
	}

	private static String rango(int pct) {
		if (pct >= 90) {
			return "90%+";
		}
		if (pct >= 85) {
			return "85-89%";
		}
		if (pct >= 80) {
			return "80-84%";
		}
		if (pct >= 75) {
			return "75-79%";
		}
		if (pct >= 65) {
			return "65-74%";
		}
		return pct >= 50 ? "50-64%" : null;
	}

	/** Para listas: los mercados ordenados del que más falla al que menos (solo los que tienen 5+ picks). */
	public static List<Map.Entry<String, Conteo>> peores(Precision p) {
		List<Map.Entry<String, Conteo>> l = new ArrayList<>(p.porMercado().entrySet());
		l.removeIf(x -> x.getValue().picks() < 5);
		l.sort(Comparator.comparingInt(x -> x.getValue().pct()));
		return l;
	}
}
