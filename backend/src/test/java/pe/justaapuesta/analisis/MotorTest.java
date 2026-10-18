package pe.justaapuesta.analisis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;

import pe.justaapuesta.usuario.Modo;

class MotorTest {

	private static Entradas entradas(Entradas.H2h h2h, Double cor) {
		return new Entradas("Local FC", "Visita FC", 10, 10, 1.6, 1.0,
				cor == null ? null : cor * 0.6, cor == null ? null : cor * 0.4,
				2.5, 2.3, 4.8, 3.6, 13.0, 10.0, "GGEGP", "PEGEP", h2h, List.of(), List.of());
	}

	@Test
	void poissonYOver() {
		assertThat(Motor.pois(2.0, 0)).isCloseTo(Math.exp(-2), within(1e-12));
		assertThat(Motor.pois(2.0, 2)).isCloseTo(2 * Math.exp(-2), within(1e-12));
		// P(más de 1.5) con media 2 = 1 - 3e^-2 = 59.4% -> 59
		assertThat(Motor.over(2.0, 1.5)).isEqualTo(59);
		// P(más de 0.5) con media 1 = 1 - e^-1 = 63.2% -> 63
		assertThat(Motor.over(1.0, 0.5)).isEqualTo(63);
	}

	@Test
	void formScoreYAjuste() {
		assertThat(Motor.formScore("GGGGG")).isEqualTo(1.0);
		assertThat(Motor.formScore("PPPPP")).isEqualTo(0.0);
		assertThat(Motor.formScore("GEPGE")).isCloseTo(8 / 15.0, within(1e-12));

		Entradas e = entradas(null, 9.0);
		Motor.Ajuste a = Motor.adj(e);
		// xh' = 1.6 × (0.9 + 0.2 × 10/15) × 1.05 ; xa' = 1.0 × (0.9 + 0.2 × 5/15) × 0.95  (PEGEP = 0+1+3+1+0)
		assertThat(a.xh()).isCloseTo(1.6 * (0.9 + 0.2 * 10 / 15.0) * 1.05, within(1e-12));
		assertThat(a.xa()).isCloseTo(1.0 * (0.9 + 0.2 * 5 / 15.0) * 0.95, within(1e-12));
	}

	@Test
	void ganadorSuma100YMezclaH2h() {
		Motor.Ganador sinH2h = Motor.winProbs(entradas(null, 9.0));
		assertThat(sinH2h.h() + sinH2h.d() + sinH2h.a()).isEqualTo(100);
		assertThat(sinH2h.h()).isGreaterThan(sinH2h.a());

		// Con 6 enfrentamientos todos ganados por el visitante, su probabilidad sube (15% de peso)
		Entradas.H2h h2h = new Entradas.H2h(0, 0, 6, 6, 2.0, 50, 33, List.of());
		Motor.Ganador conH2h = Motor.winProbs(entradas(h2h, 9.0));
		assertThat(conH2h.h() + conH2h.d() + conH2h.a()).isEqualTo(100);
		assertThat(conH2h.a()).isGreaterThan(sinH2h.a());
		assertThat(conH2h.h()).isLessThan(sinH2h.h());
	}

	@Test
	void categoriasConSusMercados() {
		List<Motor.Categoria> cats = Motor.markets(entradas(null, 9.0));
		assertThat(cats).extracting(Motor.Categoria::clave)
				.containsExactly("goles", "equipos", "combinados", "corners", "tarjetas", "remates", "tiros", "resultado");
		assertThat(cats.get(0).mercados()).hasSize(7);
		assertThat(cats.subList(1, cats.size())).allSatisfy(c -> assertThat(c.mercados()).hasSize(6));
		assertThat(cats.get(1).mercados().get(0).texto()).isEqualTo("Local FC marca");
		assertThat(cats.get(3).mercados().get(5).texto()).isEqualTo("Visita FC más de 3.5 corners");
		assertThat(cats.get(6).mercados().get(4).texto()).isEqualTo("Local FC más de 9.5 remates");
		assertThat(cats.get(7).mercados().get(0).texto()).isEqualTo("Gana Local FC");
		// Solo líneas que existen en las casas: no hay "más de 0.5 goles"
		assertThat(Motor.allPicks(entradas(null, 9.0))).extracting(Motor.Mercado::clave).doesNotContain("goles.o05");
		// Más y menos de la misma línea suman 100%
		java.util.Map<String, Integer> pct = new java.util.HashMap<>();
		Motor.allPicks(entradas(null, 9.0)).forEach(m -> pct.put(m.clave(), m.modelo()));
		assertThat(pct.get("goles.o25") + pct.get("goles.u25")).isBetween(99, 101);
		assertThat(pct.get("goles.btts") + pct.get("goles.btts_no")).isBetween(99, 101);
	}

	@Test
	void sinDatosDeCornersNoSeInventaLaCategoria() {
		List<Motor.Categoria> cats = Motor.markets(entradas(null, null));
		assertThat(cats).extracting(Motor.Categoria::clave).doesNotContain("corners", "remates");
	}

	@Test
	void picksSegunModo() {
		Entradas e = entradas(null, 9.0);
		for (Modo modo : Modo.values()) {
			Motor.RangoModo r = Motor.rango(modo);
			List<Motor.Mercado> picks = Motor.picksFor(e, modo);
			assertThat(picks).isNotEmpty();
			// Ordenados de mayor a menor y dentro del rango
			assertThat(picks).isSortedAccordingTo((a, b) -> Integer.compare(b.pct(), a.pct()));
			assertThat(picks).allSatisfy(p -> assertThat(p.pct()).isBetween(r.min(), r.max()));
		}
		assertThat(Motor.picksFor(e, Modo.SEGURO).get(0).pct())
				.isGreaterThanOrEqualTo(Motor.picksFor(e, Modo.ARRIESGADO).get(0).pct());

		List<Motor.Mercado> bajos = Motor.lowPicks(e);
		assertThat(bajos).allSatisfy(p -> assertThat(p.pct()).isGreaterThanOrEqualTo(3));
		assertThat(bajos).isSortedAccordingTo((a, b) -> Integer.compare(a.pct(), b.pct()));
	}

	@Test
	void confianzaYTono() {
		assertThat(Motor.conf(85)).isEqualTo("Confianza muy alta");
		assertThat(Motor.conf(75)).isEqualTo("Confianza alta");
		assertThat(Motor.conf(65)).isEqualTo("Confianza buena");
		assertThat(Motor.conf(64)).isEqualTo("Arriesgado");
		assertThat(Motor.tone(65)).isEqualTo("VERDE");
		assertThat(Motor.tone(35)).isEqualTo("ROJO");
		assertThat(Motor.tone(50)).isEqualTo("MEDIO");
	}

	@Test
	void comprobacionesConResultadoReal() {
		Entradas e = entradas(null, 9.0);
		// 2-1, corners 6+4, tarjetas 2+3, a puerta 5+2, 1 roja
		Motor.Final f = new Motor.Final(2, 1, 6, 4, 2, 3, 5, 2, 14, 9, 1);
		java.util.Map<String, Boolean> salio = new java.util.HashMap<>();
		Motor.allPicks(e).forEach(m -> salio.put(m.clave(), m.check().apply(f)));

		assertThat(salio.get("goles.o25")).isTrue();
		assertThat(salio.get("goles.o35")).isFalse();
		assertThat(salio.get("goles.btts")).isTrue();
		assertThat(salio.get("corners.o85")).isTrue();
		assertThat(salio.get("corners.o105")).isFalse();
		assertThat(salio.get("corners.visita35")).isTrue();
		assertThat(salio.get("tarjetas.o55")).isFalse();
		assertThat(salio.get("tarjetas.roja")).isTrue();
		assertThat(salio.get("remates.o65")).isTrue();
		assertThat(salio.get("remates.visita2")).isTrue();
		// remates totales 14+9 = 23
		assertThat(salio.get("tiros.o215")).isTrue();
		assertThat(salio.get("tiros.o245")).isFalse();
		assertThat(salio.get("tiros.local")).isTrue();
		assertThat(salio.get("resultado.local")).isTrue();
		assertThat(salio.get("resultado.empate")).isFalse();
		assertThat(salio.get("resultado.sin_empate")).isTrue();
		// Mercados nuevos con el 2-1
		assertThat(salio.get("goles.u35")).isTrue();
		assertThat(salio.get("goles.u25")).isFalse();
		assertThat(salio.get("goles.btts_no")).isFalse();
		assertThat(salio.get("equipos.local15")).isTrue();
		assertThat(salio.get("equipos.local_cero")).isFalse();
		assertThat(salio.get("combo.local_o15")).isTrue();
		assertThat(salio.get("combo.btts_o25")).isTrue();
		assertThat(salio.get("combo.local_cero")).isFalse();

		// Sin estadísticas finales, los mercados de corners no se pueden comprobar (null), los de goles sí
		Motor.Final soloGoles = new Motor.Final(0, 0, null, null, null, null, null, null, null, null, null);
		Motor.Mercado corners = Motor.allPicks(e).stream().filter(m -> m.clave().equals("corners.o75")).findFirst().orElseThrow();
		Motor.Mercado empate = Motor.allPicks(e).stream().filter(m -> m.clave().equals("resultado.empate")).findFirst().orElseThrow();
		assertThat(corners.check().apply(soloGoles)).isNull();
		assertThat(empate.check().apply(soloGoles)).isTrue();
	}

	@Test
	void porQueConPlantillasDelPrototipo() {
		Entradas e = entradas(null, 9.0);
		assertThat(Motor.reason("corners", e)).isEqualTo("9.0 corners por partido entre ambos · Local FC 60%");
		assertThat(Motor.reason("resultado", e))
				.isEqualTo("Últimos 5: Local FC 3 victorias · Visita FC 1");
	}

	@Test
	void jugadoresClaveConDatosReales() {
		Entradas e = new Entradas("L", "V", 10, 10, 1.5, 1.0, 5.0, 4.0, 2.0, 2.0, 5.0, 3.0, null, null, "GGGGG", "PPPPP", null,
				List.of(new Entradas.Jugador("Goleador", 9, 8, 0.5, 1.5, 0.1),
						new Entradas.Jugador("Duro", 5, 9, 0.0, 0.2, 0.4)),
				List.of(new Entradas.Jugador("Rematador", 7, 7, 0.3, 1.2, 0.1)));
		List<Motor.JugadorClave> js = Motor.players(e);
		assertThat(js).extracting(Motor.JugadorClave::nombre).containsExactly("Goleador", "Rematador", "Duro", "Rematador");
		// 1 - e^-0.5 = 39.3% -> 39
		assertThat(js.get(0).pct()).isEqualTo(39);
		assertThat(js.get(0).num()).isEqualTo("9");

		// Sin datos de jugadores, no hay sección (no se inventa)
		assertThat(Motor.players(entradas(null, 9.0))).isEmpty();
	}

	@Test
	void cuotaAproximadaConMargenDeCasa() {
		// 1 / (0.86 × 1.06) = 1.097 -> 1.10
		assertThat(Motor.cuota(86)).isEqualTo(1.10);
		assertThat(Motor.cuota(50)).isEqualTo(1.89);
		assertThat(Motor.cuota(99)).isEqualTo(1.01);
		assertThat(Motor.cuotaTotal(List.of(86, 50))).isEqualTo(2.08);
	}

	@Test
	void marcadoresDixonColesSuman1() {
		double[][] p = Motor.marcadores(new Motor.Ajuste(1.4, 1.1));
		double suma = 0;
		for (double[] fila : p) {
			for (double x : fila) {
				suma += x;
			}
		}
		assertThat(suma).isCloseTo(1.0, within(1e-9));
		// Con rho negativo el 1-1 sale más que en el Poisson simple
		assertThat(p[1][1]).isGreaterThan(Motor.pois(1.4, 1) * Motor.pois(1.1, 1));
	}

	private static Motor.Final goles(int gh, int ga) {
		return new Motor.Final(gh, ga, null, null, null, null, null, null, null, null, null);
	}

	private static Entradas conHistorial(List<Motor.Final> local, List<Motor.Final> visita) {
		Entradas e = entradas(null, 9.0);
		return new Entradas(e.local(), e.visita(), 10, 10, 2.2, 1.6, e.corLocal(), e.corVisita(), e.cardsLocal(),
				e.cardsVisita(), e.sotLocal(), e.sotVisita(), e.remLocal(), e.remVisita(), "GGGGG", "GGEGG", null,
				List.of(), List.of(), new Entradas.Historiales(local, visita, List.of(goles(2, 1), goles(3, 1)),
						java.util.Collections.nCopies(20, goles(2, 1))));
	}

	@Test
	void pickBlindadoSoloSiPasaLasPruebas() {
		// Los dos equipos con 2+ goles en 9 de 10: "más de 1.5 goles" es blindado
		List<Motor.Final> muchos = new java.util.ArrayList<>(java.util.Collections.nCopies(9, goles(2, 1)));
		muchos.add(goles(0, 0));
		Motor.Mercado o15 = Motor.allPicks(conHistorial(muchos, muchos)).stream()
				.filter(m -> m.clave().equals("goles.o15")).findFirst().orElseThrow();
		assertThat(o15.pruebas()).hasSize(5);
		assertThat(o15.pruebas().get(1).detalle()).isEqualTo("Se cumplió 9 de 10");
		assertThat(o15.blindado()).isTrue();

		// Si el visitante casi nunca llega a 2 goles en sus partidos, no es blindado aunque el modelo diga alto
		List<Motor.Final> pocos = java.util.Collections.nCopies(10, goles(1, 0));
		Motor.Mercado otro = Motor.allPicks(conHistorial(muchos, pocos)).stream()
				.filter(m -> m.clave().equals("goles.o15")).findFirst().orElseThrow();
		assertThat(otro.pruebas().get(2).paso()).isFalse();
		assertThat(otro.blindado()).isFalse();
		assertThat(otro.pct()).isLessThan(o15.pct());

		// Sin historiales no hay blindados
		assertThat(Motor.allPicks(entradas(null, 9.0))).noneMatch(Motor.Mercado::blindado);
	}
}
