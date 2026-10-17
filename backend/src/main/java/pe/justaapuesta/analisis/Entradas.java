package pe.justaapuesta.analisis;

import java.util.List;

/**
 * Entradas del modelo para un partido, calculadas SOLO con datos reales anteriores al inicio del partido.
 * Es lo que se guarda en analisis_cache (así la probabilidad de un partido jugado es la de antes del partido).
 *
 * @param xh       goles esperados del local (sin ajustar)
 * @param xa       goles esperados del visitante (sin ajustar)
 * @param corLocal corners promedio del local por partido (null si no hay datos)
 * @param remLocal remates totales (a puerta o no) promedio del local por partido (null si no hay datos)
 * @param hf       racha del local en sus últimos 5, del más antiguo al más reciente (G/E/P)
 * @param h2h      enfrentamientos anteriores (null si no hay)
 */
public record Entradas(
		String local,
		String visita,
		int partidosLocal,
		int partidosVisita,
		double xh,
		double xa,
		Double corLocal,
		Double corVisita,
		Double cardsLocal,
		Double cardsVisita,
		Double sotLocal,
		Double sotVisita,
		Double remLocal,
		Double remVisita,
		String hf,
		String af,
		H2h h2h,
		List<Jugador> jugadoresLocal,
		List<Jugador> jugadoresVisita,
		Historiales historiales) {

	/** Mínimo de partidos de cada equipo para analizar. */
	public static final int MINIMO_PARTIDOS = 3;

	/** Sin historiales (no se pueden hacer las pruebas del pick blindado). */
	public Entradas(String local, String visita, int partidosLocal, int partidosVisita, double xh, double xa,
			Double corLocal, Double corVisita, Double cardsLocal, Double cardsVisita, Double sotLocal, Double sotVisita,
			Double remLocal, Double remVisita, String hf, String af, H2h h2h, List<Jugador> jugadoresLocal,
			List<Jugador> jugadoresVisita) {
		this(local, visita, partidosLocal, partidosVisita, xh, xa, corLocal, corVisita, cardsLocal, cardsVisita,
				sotLocal, sotVisita, remLocal, remVisita, hf, af, h2h, jugadoresLocal, jugadoresVisita, null);
	}

	/**
	 * Resultados reales anteriores al partido, para comprobar cada mercado contra lo que de verdad pasó.
	 * Van orientados: en "local" el equipo local de hoy está siempre del lado H (aunque ese día jugara de visita),
	 * y en "visita" el visitante de hoy está siempre del lado A. Así "Gana Barça" se comprueba igual en todos.
	 *
	 * @param local últimos partidos del local de hoy
	 * @param visita últimos partidos del visitante de hoy
	 * @param h2h enfrentamientos directos (local de hoy del lado H)
	 * @param liga últimos partidos de la liga, tal cual se jugaron
	 */
	public record Historiales(List<Motor.Final> local, List<Motor.Final> visita, List<Motor.Final> h2h,
			List<Motor.Final> liga) {
	}

	public boolean suficientes() {
		return partidosLocal >= MINIMO_PARTIDOS && partidosVisita >= MINIMO_PARTIDOS;
	}

	/** cor = corners promedio del local + del visitante. */
	public Double cor() {
		return corLocal == null || corVisita == null ? null : corLocal + corVisita;
	}

	/** hs = proporción del local (corners del local / cor). Se usa también para repartir remates. */
	public Double hs() {
		Double cor = cor();
		return cor == null || cor <= 0 ? null : corLocal / cor;
	}

	public Double cards() {
		return cardsLocal == null || cardsVisita == null ? null : cardsLocal + cardsVisita;
	}

	public Double sot() {
		return sotLocal == null || sotVisita == null ? null : sotLocal + sotVisita;
	}

	/** Remates totales esperados entre los dos. */
	public Double rem() {
		return remLocal == null || remVisita == null ? null : remLocal + remVisita;
	}

	/** Resumen de enfrentamientos reales: hw = victorias del local de hoy, aw = del visitante de hoy. */
	public record H2h(int hw, int d, int aw, int n, double goles, int btts, int over25, List<Duelo> lista) {
	}

	public record Duelo(String fecha, String estadio, String local, String visita, int golesLocal, int golesVisita) {
	}

	/** Promedios por partido de un jugador en los últimos partidos de su equipo. */
	public record Jugador(String nombre, Integer dorsal, int partidos, double goles, double rematesAPuerta,
			double tarjetas) {
	}
}
