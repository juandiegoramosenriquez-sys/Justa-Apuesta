package pe.justaapuesta.apuesta;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pe.justaapuesta.comun.Normalizador;
import pe.justaapuesta.partido.Equipo;
import pe.justaapuesta.partido.Partido;

/** Lectura del texto de un boleto: qué partidos tiene y qué se apostó en cada uno. */
class ApuestaServiceTest {

	private final Partido liverpool = partido(1L, "Liverpool FC", "Brentford FC");
	private final Partido united = partido(2L, "Manchester United FC", "Chelsea FC");
	private final Partido nacional = partido(3L, "Atletico Nacional", "Deportivo Pasto");
	private final Partido real = partido(4L, "Real Madrid CF", "Real Sociedad");
	private final List<Partido> candidatos = List.of(liverpool, united, nacional, real);

	@Test
	void leeUnBoletoComoElDeBetano() {
		String texto = """
				Combinada (3)
				Más de 2.5
				Total de goles
				Liverpool - Brentford
				1.85
				Ambos equipos marcan: Sí
				Man. United - Chelsea
				1.70
				Atl. Nacional
				Resultado final
				Atlético Nacional - Deportivo Pasto
				1.55
				Cuota total 4.87  Apuesta S/ 10.00
				""";
		Map<Long, List<String>> r = leer(texto);

		assertThat(r).containsOnlyKeys(1L, 2L, 3L);
		assertThat(r.get(1L)).containsExactly("goles.o25");
		assertThat(r.get(2L)).containsExactly("goles.btts");
		assertThat(r.get(3L)).containsExactly("resultado.local");
	}

	@Test
	void enBoletosConLoApostadoEncimaCadaSeleccionVaConElPartidoDeAbajo() {
		Map<Long, List<String>> r = leer("""
				Más de 2.5
				Total de goles
				Liverpool - Brentford
				Más de 9.5
				Total de córners
				Man. United - Chelsea
				Real Madrid
				Resultado final
				Real Madrid - Real Sociedad
				""");

		assertThat(r.get(1L)).containsExactly("goles.o25");
		assertThat(r.get(2L)).containsExactly("corners.o95");
		assertThat(r.get(4L)).containsExactly("resultado.local");
	}

	@Test
	void leeUnaApuestaEscritaAMano() {
		Map<Long, List<String>> r = leer("""
				Liverpool vs Brentford: menos de 9.5 corners
				Real Madrid vs Real Sociedad: Real Madrid o empate
				""");

		assertThat(r.get(1L)).containsExactly("corners.u95");
		assertThat(r.get(4L)).containsExactly("resultado.local_empate");
	}

	@Test
	void distingueTarjetasRematesYGolesDeUnEquipo() {
		Map<Long, List<String>> r = leer("""
				Liverpool - Brentford
				Más de 4.5 tarjetas
				Liverpool más de 1.5 goles
				Over 8.5 remates a puerta
				""");

		assertThat(r.get(1L)).containsExactly("tarjetas.o45", "equipos.local15", "remates.o85");
	}

	@Test
	void noConfundeDosEquiposQueSoloCompartenUnaPalabraComun() {
		// "Real" no basta para decir que es el Real Madrid
		List<ApuestaService.Mencion> m = ApuestaService.partidosEnTexto(ApuestaService.lineas("Real Betis - Real Valladolid"),
				candidatos);
		assertThat(m).isEmpty();
	}

	@Test
	void lasCuotasYElMontoNoSeLeenComoApuestas() {
		Map<Long, List<String>> r = leer("""
				Liverpool - Brentford
				1.85
				Ganancia posible S/ 18.50
				""");
		assertThat(r).isEmpty();
	}

	private Map<Long, List<String>> leer(String texto) {
		List<String> lineas = ApuestaService.lineas(texto);
		List<ApuestaService.Mencion> menciones = ApuestaService.partidosEnTexto(lineas, candidatos);
		Map<ApuestaService.Mencion, List<ApuestaService.Seleccion>> sel = ApuestaService.asignar(lineas, menciones);
		return sel.entrySet().stream().collect(java.util.stream.Collectors.toMap(e -> e.getKey().partido().getId(),
				e -> e.getValue().stream().map(s -> s.clave() != null ? s.clave() : s.tipo() + "?" + s.linea()).toList()));
	}

	private static Partido partido(Long id, String local, String visita) {
		Partido p = new Partido();
		p.setId(id);
		p.setLocal(equipo(local));
		p.setVisita(equipo(visita));
		p.setFechaHoraUtc(Instant.now().plusSeconds(3600));
		return p;
	}

	private static Equipo equipo(String nombre) {
		Equipo e = new Equipo();
		e.setNombre(nombre);
		e.setNombreNormalizado(Normalizador.normalizar(nombre));
		return e;
	}
}
