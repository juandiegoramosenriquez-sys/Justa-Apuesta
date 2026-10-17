package pe.justaapuesta.analisis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import pe.justaapuesta.usuario.Modo;

/**
 * Motor de probabilidades. Las fórmulas son EXACTAMENTE las del prototipo aprobado
 * (pois, over, formScore, adj, winProbs, markets, picksFor, lowPicks, conf, tone, reason, players).
 * Solo cambia el origen de los datos: aquí todo viene de partidos reales.
 */
public final class Motor {

	private Motor() {
	}

	// ---------- Modos (MODES del prototipo) ----------

	public record RangoModo(int min, int max) {
	}

	public static RangoModo rango(Modo modo) {
		return switch (modo) {
			case SEGURO -> new RangoModo(78, 97);
			case EQUILIBRADO -> new RangoModo(62, 88);
			case ARRIESGADO -> new RangoModo(50, 75);
		};
	}

	// ---------- Utilidades (idénticas al prototipo) ----------

	/** Poisson: probabilidad de exactamente k eventos con media l. */
	public static double pois(double l, int k) {
		double p = Math.exp(-l);
		for (int i = 1; i <= k; i++) {
			p = p * l / i;
		}
		return p;
	}

	/** Probabilidad (en %) de más de "linea" eventos. */
	public static int over(double l, double linea) {
		double s = 0;
		for (int i = 0; i <= Math.floor(linea); i++) {
			s += pois(l, i);
		}
		return redondear((1 - s) * 100);
	}

	/** (G×3 + E×1) / 15 */
	public static double formScore(String racha) {
		double t = 0;
		for (char r : racha.toCharArray()) {
			t += r == 'G' ? 3 : r == 'E' ? 1 : 0;
		}
		return t / 15;
	}

	public static int contar(String racha, char c) {
		return (int) racha.chars().filter(x -> x == c).count();
	}

	/** Igual que Math.round de JavaScript. */
	static int redondear(double x) {
		return (int) Math.floor(x + 0.5);
	}

	private static String f1(double x) {
		return String.format(Locale.US, "%.1f", x);
	}

	private static String f2(double x) {
		return String.format(Locale.US, "%.2f", x);
	}

	// ---------- Modelo ----------

	/** Goles esperados ajustados por forma (+/-10%) y localía (+5% / -5%). */
	public record Ajuste(double xh, double xa) {
	}

	public static Ajuste adj(Entradas e) {
		return new Ajuste(e.xh() * (0.9 + 0.2 * formScore(e.hf())) * 1.05,
				e.xa() * (0.9 + 0.2 * formScore(e.af())) * 0.95);
	}

	public record Ganador(int h, int d, int a) {
	}

	/**
	 * Corrección de Dixon-Coles: el Poisson simple se equivoca en los marcadores bajos (0-0, 1-0, 0-1, 1-1),
	 * que en el fútbol real salen más o menos de lo que dice. RHO es el valor típico de las grandes ligas.
	 */
	static final double RHO = -0.1;

	/** Probabilidad de cada marcador i-j (0 a 9 goles), con la corrección de Dixon-Coles y normalizada a 1. */
	public static double[][] marcadores(Ajuste m) {
		double[][] p = new double[10][10];
		double suma = 0;
		for (int i = 0; i <= 9; i++) {
			for (int j = 0; j <= 9; j++) {
				double tau = 1;
				if (i == 0 && j == 0) {
					tau = 1 - m.xh() * m.xa() * RHO;
				} else if (i == 0 && j == 1) {
					tau = 1 + m.xh() * RHO;
				} else if (i == 1 && j == 0) {
					tau = 1 + m.xa() * RHO;
				} else if (i == 1 && j == 1) {
					tau = 1 - RHO;
				}
				p[i][j] = pois(m.xh(), i) * pois(m.xa(), j) * Math.max(tau, 0);
				suma += p[i][j];
			}
		}
		for (double[] fila : p) {
			for (int j = 0; j < fila.length; j++) {
				fila[j] /= suma;
			}
		}
		return p;
	}

	/** Suma (en %) de los marcadores que cumplen la condición. */
	private static int suma(double[][] p, java.util.function.BiPredicate<Integer, Integer> cumple) {
		double s = 0;
		for (int i = 0; i < p.length; i++) {
			for (int j = 0; j < p[i].length; j++) {
				if (cumple.test(i, j)) {
					s += p[i][j];
				}
			}
		}
		return redondear(s * 100);
	}

	/** ¿Quién gana? 85% modelo (Poisson con Dixon-Coles) + 15% historial real de enfrentamientos (100% modelo si no hay). */
	public static Ganador winProbs(Entradas e) {
		double[][] marcadores = marcadores(adj(e));
		double h = 0, d = 0, a = 0;
		for (int i = 0; i <= 9; i++) {
			for (int j = 0; j <= 9; j++) {
				double p = marcadores[i][j];
				if (i > j) {
					h += p;
				} else if (i == j) {
					d += p;
				} else {
					a += p;
				}
			}
		}
		Entradas.H2h hh = e.h2h();
		int hFinal, aFinal;
		if (hh != null && hh.n() > 0) {
			hFinal = redondear((0.85 * h + 0.15 * hh.hw() / (double) hh.n()) * 100);
			aFinal = redondear((0.85 * a + 0.15 * hh.aw() / (double) hh.n()) * 100);
		} else {
			hFinal = redondear(h * 100);
			aFinal = redondear(a * 100);
		}
		return new Ganador(hFinal, 100 - hFinal - aFinal, aFinal);
	}

	// ---------- Mercados ----------

	/** Resultado final real de un partido, para comprobar ✓/✗. Cada estadística es null si no hay dato. */
	public record Final(int gh, int ga, Integer corH, Integer corA, Integer cardsH, Integer cardsA, Integer sotH,
			Integer sotA, Integer remH, Integer remA, Integer rojas) {

		int tot() {
			return gh + ga;
		}

		Integer cor() {
			return corH == null || corA == null ? null : corH + corA;
		}

		Integer cards() {
			return cardsH == null || cardsA == null ? null : cardsH + cardsA;
		}

		Integer sot() {
			return sotH == null || sotA == null ? null : sotH + sotA;
		}

		Integer rem() {
			return remH == null || remA == null ? null : remH + remA;
		}
	}

	/**
	 * @param pct      certeza final: modelo afinado con lo que de verdad pasó en los partidos de ambos y de la liga
	 * @param check    devuelve true/false si salió, o null si falta el dato para saberlo
	 * @param modelo   lo que dice solo el modelo de goles esperados
	 * @param pruebas  las 5 pruebas del pick blindado
	 * @param blindado pasó todas las pruebas que tienen datos
	 */
	public record Mercado(String clave, String categoria, String nombreCategoria, String texto, int pct,
			Function<Final, Boolean> check, int modelo, List<Prueba> pruebas, boolean blindado) {

		public double cuota() {
			return Motor.cuota(pct);
		}
	}

	/** @param paso true/false, o null si no hay datos para hacerla (no cuenta) */
	public record Prueba(String nombre, String detalle, Boolean paso) {
	}

	/** Margen típico de una casa de apuestas (~6%): la cuota que pagaría por esa probabilidad. */
	private static final double MARGEN = 1.06;

	/** Cuota aproximada para una probabilidad (en %), con el margen de una casa. Mínimo 1.01. */
	public static double cuota(int pct) {
		double c = 1 / (Math.max(pct, 1) / 100.0 * MARGEN);
		return Math.max(1.01, Math.round(c * 100) / 100.0);
	}

	/** Cuota de varios picks juntos (producto). */
	public static double cuotaTotal(List<Integer> pcts) {
		double c = pcts.stream().mapToDouble(Motor::cuota).reduce(1, (a, b) -> a * b);
		return Math.round(c * 100) / 100.0;
	}

	public record Categoria(String clave, String nombre, String promedio, List<Mercado> mercados) {
	}

	public static final List<String[]> CATS = List.of(
			new String[] { "goles", "Goles" },
			new String[] { "corners", "Corners" },
			new String[] { "tarjetas", "Tarjetas" },
			new String[] { "remates", "Remates" },
			new String[] { "resultado", "Ganador" });

	/**
	 * Las categorías con sus 6 mercados. Solo líneas que ofrecen las casas (como Betano): nada de "más de 0.5 goles".
	 * Una categoría sin datos reales suficientes no se incluye.
	 */
	public static List<Categoria> markets(Entradas e) {
		Ajuste m = adj(e);
		double tot = m.xh() + m.xa();
		double[][] mx = marcadores(m);
		Ganador w = winProbs(e);
		String L = e.local(), V = e.visita();
		List<Categoria> cats = new ArrayList<>();

		cats.add(cat("goles", "Goles", f1(tot) + " goles esperados", List.of(
				mk("goles.o15", "Más de 1.5 goles", suma(mx, (i, j) -> i + j > 1), f -> f.tot() > 1.5),
				mk("goles.o25", "Más de 2.5 goles", suma(mx, (i, j) -> i + j > 2), f -> f.tot() > 2.5),
				mk("goles.o35", "Más de 3.5 goles", suma(mx, (i, j) -> i + j > 3), f -> f.tot() > 3.5),
				mk("goles.btts", "Ambos marcan", suma(mx, (i, j) -> i > 0 && j > 0), f -> f.gh() > 0 && f.ga() > 0),
				mk("goles.local", L + " marca", suma(mx, (i, j) -> i > 0), f -> f.gh() > 0),
				mk("goles.visita", V + " marca", suma(mx, (i, j) -> j > 0), f -> f.ga() > 0))));

		Double cor = e.cor(), hs = e.hs();
		if (cor != null && hs != null) {
			cats.add(cat("corners", "Corners", f1(cor) + " corners esperados", List.of(
					mk("corners.o75", "Más de 7.5 corners", over(cor, 7.5), f -> mayor(f.cor(), 7.5)),
					mk("corners.o85", "Más de 8.5 corners", over(cor, 8.5), f -> mayor(f.cor(), 8.5)),
					mk("corners.o95", "Más de 9.5 corners", over(cor, 9.5), f -> mayor(f.cor(), 9.5)),
					mk("corners.o105", "Más de 10.5 corners", over(cor, 10.5), f -> mayor(f.cor(), 10.5)),
					mk("corners.local35", L + " más de 3.5 corners", over(cor * hs, 3.5), f -> mayor(f.corH(), 3.5)),
					mk("corners.visita35", V + " más de 3.5 corners", over(cor * (1 - hs), 3.5),
							f -> mayor(f.corA(), 3.5)))));
		}

		Double cards = e.cards();
		if (cards != null) {
			cats.add(cat("tarjetas", "Tarjetas", f1(cards) + " tarjetas esperadas", List.of(
					mk("tarjetas.o25", "Más de 2.5 tarjetas", over(cards, 2.5), f -> mayor(f.cards(), 2.5)),
					mk("tarjetas.o35", "Más de 3.5 tarjetas", over(cards, 3.5), f -> mayor(f.cards(), 3.5)),
					mk("tarjetas.o45", "Más de 4.5 tarjetas", over(cards, 4.5), f -> mayor(f.cards(), 4.5)),
					mk("tarjetas.o55", "Más de 5.5 tarjetas", over(cards, 5.5), f -> mayor(f.cards(), 5.5)),
					mk("tarjetas.visita", V + " recibe tarjeta", over(cards * 0.55, 0.5), f -> mayor(f.cardsA(), 0)),
					mk("tarjetas.roja", "Habrá tarjeta roja", redondear((1 - Math.exp(-cards * 0.04)) * 100),
							f -> mayor(f.rojas(), 0)))));
		}

		Double sot = e.sot();
		if (sot != null && hs != null) {
			cats.add(cat("remates", "Remates", f1(sot) + " remates a puerta esperados", List.of(
					mk("remates.o55", "Más de 5.5 a puerta", over(sot, 5.5), f -> mayor(f.sot(), 5.5)),
					mk("remates.o65", "Más de 6.5 a puerta", over(sot, 6.5), f -> mayor(f.sot(), 6.5)),
					mk("remates.o75", "Más de 7.5 a puerta", over(sot, 7.5), f -> mayor(f.sot(), 7.5)),
					mk("remates.o95", "Más de 9.5 a puerta", over(sot, 9.5), f -> mayor(f.sot(), 9.5)),
					mk("remates.local3", L + " 3+ a puerta", over(sot * hs, 2.5), f -> mayor(f.sotH(), 2.5)),
					mk("remates.visita2", V + " 2+ a puerta", over(sot * (1 - hs), 1.5), f -> mayor(f.sotA(), 1.5)))));
		}

		// Remates totales (a puerta o no), con el promedio real de cada equipo en sus partidos anteriores
		Double rem = e.rem();
		if (rem != null) {
			cats.add(cat("tiros", "Tiros", f1(rem) + " remates totales esperados", List.of(
					mk("tiros.o185", "Más de 18.5 remates", over(rem, 18.5), f -> mayor(f.rem(), 18.5)),
					mk("tiros.o215", "Más de 21.5 remates", over(rem, 21.5), f -> mayor(f.rem(), 21.5)),
					mk("tiros.o245", "Más de 24.5 remates", over(rem, 24.5), f -> mayor(f.rem(), 24.5)),
					mk("tiros.o275", "Más de 27.5 remates", over(rem, 27.5), f -> mayor(f.rem(), 27.5)),
					mk("tiros.local", L + " más de 9.5 remates", over(e.remLocal(), 9.5), f -> mayor(f.remH(), 9.5)),
					mk("tiros.visita", V + " más de 7.5 remates", over(e.remVisita(), 7.5), f -> mayor(f.remA(), 7.5)))));
		}

		cats.add(cat("resultado", "Ganador", "Modelo de goles + enfrentamientos directos", List.of(
				mk("resultado.local", "Gana " + L, w.h(), f -> f.gh() > f.ga()),
				mk("resultado.empate", "Empate", w.d(), f -> f.gh() == f.ga()),
				mk("resultado.visita", "Gana " + V, w.a(), f -> f.ga() > f.gh()),
				mk("resultado.local_empate", L + " o empate", w.h() + w.d(), f -> f.gh() >= f.ga()),
				mk("resultado.visita_empate", V + " o empate", w.a() + w.d(), f -> f.ga() >= f.gh()),
				mk("resultado.sin_empate", "No hay empate", w.h() + w.a(), f -> f.gh() != f.ga()))));

		// Completa la categoría y pasa cada mercado por las pruebas con datos reales
		return cats.stream().map(c -> new Categoria(c.clave(), c.nombre(), c.promedio(),
				c.mercados().stream().map(x -> evaluar(e, x.clave(), c.clave(), c.nombre(), x.texto(), x.pct(), x.check()))
						.toList()))
				.toList();
	}

	private static Categoria cat(String clave, String nombre, String promedio, List<Mercado> mercados) {
		return new Categoria(clave, nombre, promedio, mercados);
	}

	private static Mercado mk(String clave, String texto, int pct, Function<Final, Boolean> check) {
		return new Mercado(clave, null, null, texto, pct, check, pct, List.of(), false);
	}

	// ---------- Pick blindado ----------

	/** Mínimos para que un pick sea blindado. */
	static final int MODELO_MIN = 75;
	static final double EQUIPO_MIN = 0.8;
	static final double H2H_MIN = 0.67;
	static final double LIGA_MIN = 0.7;
	private static final int DATOS_EQUIPO = 5;
	private static final int DATOS_H2H = 2;
	private static final int DATOS_LIGA = 10;

	/** Cuántas veces se cumplió el mercado en esos partidos (los que no tienen el dato no cuentan). */
	record Tasa(int si, int n) {
		double valor() {
			return n == 0 ? 0 : si / (double) n;
		}
	}

	static Tasa tasa(List<Final> partidos, Function<Final, Boolean> check) {
		int si = 0, n = 0;
		if (partidos != null) {
			for (Final f : partidos) {
				Boolean r = check.apply(f);
				if (r != null) {
					n++;
					if (r) {
						si++;
					}
				}
			}
		}
		return new Tasa(si, n);
	}

	/**
	 * Las 5 pruebas con datos reales y la certeza final. La certeza mezcla el modelo con lo que de verdad pasó:
	 * 55% modelo, 30% los últimos partidos de ambos equipos y 15% la liga (si falta algo, pesa más el modelo).
	 */
	static Mercado evaluar(Entradas e, String clave, String categoria, String nombreCategoria, String texto,
			int modelo, Function<Final, Boolean> check) {
		Entradas.Historiales h = e.historiales();
		List<Prueba> pruebas = new ArrayList<>();
		pruebas.add(new Prueba("Modelo de goles esperados", modelo + "%", modelo >= MODELO_MIN));
		if (h == null) {
			return new Mercado(clave, categoria, nombreCategoria, texto, modelo, check, modelo, pruebas, false);
		}
		Tasa tl = tasa(h.local(), check), tv = tasa(h.visita(), check);
		Tasa th = tasa(h.h2h(), check), tg = tasa(h.liga(), check);

		Boolean pl = tl.n() >= DATOS_EQUIPO ? tl.valor() >= EQUIPO_MIN : null;
		Boolean pv = tv.n() >= DATOS_EQUIPO ? tv.valor() >= EQUIPO_MIN : null;
		Boolean ph = th.n() >= DATOS_H2H ? th.valor() >= H2H_MIN : null;
		Boolean pg = tg.n() >= DATOS_LIGA ? tg.valor() >= LIGA_MIN : null;
		pruebas.add(new Prueba(e.local() + " · últimos " + tl.n(), veces(tl, DATOS_EQUIPO), pl));
		pruebas.add(new Prueba(e.visita() + " · últimos " + tv.n(), veces(tv, DATOS_EQUIPO), pv));
		pruebas.add(new Prueba("Enfrentamientos directos", veces(th, DATOS_H2H), ph));
		pruebas.add(new Prueba("Liga · últimos " + tg.n(), tg.n() >= DATOS_LIGA
				? redondear(tg.valor() * 100) + "% de los partidos" : "Sin datos suficientes", pg));

		double pct = modelo;
		if (pl != null && pv != null) {
			double equipos = (tl.valor() + tv.valor()) / 2 * 100;
			pct = pg != null ? 0.55 * modelo + 0.30 * equipos + 0.15 * tg.valor() * 100 : 0.6 * modelo + 0.4 * equipos;
		} else if (pl != null || pv != null) {
			pct = 0.75 * modelo + 0.25 * (pl != null ? tl : tv).valor() * 100;
		}
		int fin = Math.max(1, Math.min(99, redondear(pct)));
		boolean blindado = modelo >= MODELO_MIN && fin >= MODELO_MIN && Boolean.TRUE.equals(pl)
				&& Boolean.TRUE.equals(pv) && !Boolean.FALSE.equals(ph) && !Boolean.FALSE.equals(pg);
		return new Mercado(clave, categoria, nombreCategoria, texto, fin, check, modelo, pruebas, blindado);
	}

	private static String veces(Tasa t, int minimo) {
		return t.n() >= minimo ? "Se cumplió " + t.si() + " de " + t.n() : "Sin datos suficientes";
	}

	private static Boolean mayor(Integer valor, double linea) {
		return valor == null ? null : valor > linea;
	}

	public static List<Mercado> allPicks(Entradas e) {
		return markets(e).stream().flatMap(c -> c.mercados().stream()).toList();
	}

	/** Mercados de mayor a menor dentro del rango del modo; si no hay ninguno, todos los <= máximo. */
	public static List<Mercado> picksFor(Entradas e, Modo modo) {
		RangoModo r = rango(modo);
		List<Mercado> todos = allPicks(e).stream().sorted(Comparator.comparingInt(Mercado::pct).reversed()).toList();
		List<Mercado> enRango = todos.stream().filter(a -> a.pct() >= r.min() && a.pct() <= r.max()).toList();
		return enRango.isEmpty() ? todos.stream().filter(a -> a.pct() <= r.max()).toList() : enRango;
	}

	/**
	 * Cuota mínima para recomendar un pick: por debajo (más de ~86%) las casas casi no lo ofrecen o no paga nada.
	 */
	public static final double CUOTA_MINIMA = 1.10;

	public static boolean jugable(Mercado m) {
		return m.cuota() >= CUOTA_MINIMA;
	}

	/**
	 * El consejo del partido: el primer pick blindado del modo con cuota jugable; si no hay, el más alto del modo
	 * con cuota jugable; si tampoco, el más alto del modo.
	 */
	public static Mercado mejor(Entradas e, Modo modo) {
		List<Mercado> tp = picksFor(e, modo);
		return tp.stream().filter(m -> m.blindado() && jugable(m)).findFirst()
				.or(() -> tp.stream().filter(Motor::jugable).findFirst())
				.orElse(tp.get(0));
	}

	/** "Evita · poco probable": los de menor probabilidad (desde 3%). */
	public static List<Mercado> lowPicks(Entradas e) {
		return allPicks(e).stream().filter(a -> a.pct() >= 3).sorted(Comparator.comparingInt(Mercado::pct)).toList();
	}

	public static String conf(int p) {
		return p >= 85 ? "Confianza muy alta" : p >= 75 ? "Confianza alta" : p >= 65 ? "Confianza buena" : "Arriesgado";
	}

	/** Tono de color: VERDE (>=65), ROJO (<=35) o MEDIO (lavanda). */
	public static String tone(int p) {
		return p >= 65 ? "VERDE" : p <= 35 ? "ROJO" : "MEDIO";
	}

	/** El dato clave detrás del consejo, en una línea. */
	public static String reason(String categoria, Entradas e) {
		Ajuste m = adj(e);
		return switch (categoria) {
			case "goles" -> "Goles esperados: " + e.local() + " " + f1(m.xh()) + " · " + e.visita() + " " + f1(m.xa());
			case "corners" -> f1(e.cor()) + " corners por partido entre ambos · " + e.local() + " "
					+ redondear(e.hs() * 100) + "%";
			case "tarjetas" -> f1(e.cards()) + " tarjetas por partido entre ambos";
			case "remates" -> f1(e.sot()) + " remates a puerta por partido entre ambos";
			case "tiros" -> f1(e.rem()) + " remates por partido entre ambos";
			default -> "Últimos 5: " + e.local() + " " + contar(e.hf(), 'G') + " victorias · " + e.visita() + " "
					+ contar(e.af(), 'G');
		};
	}

	// ---------- Jugadores clave ----------

	public record JugadorClave(String num, String nombre, String equipo, String stat, int pct, String que, String tono) {
	}

	/**
	 * Mismas 4 tarjetas que el prototype players(): goleador del local, rematador del visitante,
	 * el más amonestado del local y goleador del visitante, con sus promedios reales por partido.
	 */
	public static List<JugadorClave> players(Entradas e) {
		List<JugadorClave> out = new ArrayList<>();
		mejor(e.jugadoresLocal(), Entradas.Jugador::goles).ifPresent(j -> out.add(clave(j, e.local(),
				f2(j.goles()) + " goles/partido", redondear((1 - Math.exp(-j.goles())) * 100), "Marca gol")));
		mejor(e.jugadoresVisita(), Entradas.Jugador::rematesAPuerta).ifPresent(j -> out.add(clave(j, e.visita(),
				f1(j.rematesAPuerta()) + " a puerta/partido", over(j.rematesAPuerta(), 0.5), "1+ remate a puerta")));
		mejor(e.jugadoresLocal(), Entradas.Jugador::tarjetas).ifPresent(j -> out.add(clave(j, e.local(),
				f1(j.tarjetas()) + " tarjetas/partido", redondear((1 - Math.exp(-j.tarjetas())) * 100), "Recibe tarjeta")));
		mejor(e.jugadoresVisita(), Entradas.Jugador::goles).ifPresent(j -> out.add(clave(j, e.visita(),
				f2(j.goles()) + " goles/partido", redondear((1 - Math.exp(-j.goles())) * 100), "Marca gol")));
		return out;
	}

	private static java.util.Optional<Entradas.Jugador> mejor(List<Entradas.Jugador> lista,
			java.util.function.ToDoubleFunction<Entradas.Jugador> valor) {
		if (lista == null) {
			return java.util.Optional.empty();
		}
		return lista.stream().filter(j -> j.partidos() >= Entradas.MINIMO_PARTIDOS && valor.applyAsDouble(j) > 0)
				.max(Comparator.comparingDouble(valor));
	}

	private static JugadorClave clave(Entradas.Jugador j, String equipo, String stat, int pct, String que) {
		String num = j.dorsal() != null ? String.valueOf(j.dorsal()) : j.nombre().substring(0, 1).toUpperCase(Locale.ROOT);
		return new JugadorClave(num, j.nombre(), equipo, stat, pct, que, tone(pct));
	}
}
