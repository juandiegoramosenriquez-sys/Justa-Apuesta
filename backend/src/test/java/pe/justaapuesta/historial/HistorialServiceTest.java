package pe.justaapuesta.historial;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import pe.justaapuesta.FakeProvider;
import pe.justaapuesta.historial.HistorialDtos.FdEquipo;
import pe.justaapuesta.historial.HistorialDtos.FdPartido;
import pe.justaapuesta.partido.CatalogoService;
import pe.justaapuesta.partido.Equipo;
import pe.justaapuesta.partido.EquipoRepository;
import pe.justaapuesta.partido.PartidoRepository;
import pe.justaapuesta.proveedor.ProveedorDtos.EquipoProv;
import pe.justaapuesta.proveedor.ProveedorDtos.PartidoProv;

/**
 * Une el historial de football-data.org con los partidos de API-Football:
 * el mismo partido en las dos fuentes vincula a los equipos, el historial pasa al equipo de API-Football
 * y el partido repetido se borra.
 */
@SpringBootTest(properties = "justa.sync.programado=false")
@ActiveProfiles("test")
class HistorialServiceTest {

	/** Ids de API-Football y de football-data.org (inventados para el test, solo de este test). */
	static final long AF_GETAFE = 880_001, AF_OSASUNA = 880_002;
	static final long FD_GETAFE = 82_000, FD_OSASUNA = 79_000, FD_BETIS = 90_000;

	static class FdFalso implements HistorialProvider {
		List<FdPartido> partidos = new ArrayList<>();
		/** Premier League (solo la usa el test de la temporada pasada) */
		List<FdPartido> premier = new ArrayList<>();
		List<FdPartido> premierPasada = new ArrayList<>();
		int pedidasPasada;

		@Override
		public boolean disponible() {
			return true;
		}

		@Override
		public List<FdPartido> partidosDeTemporada(String codigo) {
			return "PD".equals(codigo) ? partidos : "PL".equals(codigo) ? premier : List.of();
		}

		@Override
		public List<FdPartido> partidosDeTemporada(String codigo, int temporada) {
			if (!"PL".equals(codigo)) {
				return List.of();
			}
			pedidasPasada++;
			return temporada == 2025 ? premierPasada : List.of();
		}
	}

	@TestConfiguration
	static class Config {
		@Bean
		@Primary
		FdFalso fdFalso() {
			return new FdFalso();
		}
	}

	@Autowired
	private FdFalso fd;
	@Autowired
	private HistorialService historial;
	@Autowired
	private CatalogoService catalogo;
	@Autowired
	private EquipoRepository equipoRepository;
	@Autowired
	private PartidoRepository partidoRepository;
	@Autowired
	private TransactionTemplate tx;

	private static FdPartido fd(long id, Instant fecha, String estado, long local, String nl, long visita, String nv,
			Integer gl, Integer gv) {
		return new FdPartido(id, fecha, estado, null, new FdEquipo(local, nl, null), new FdEquipo(visita, nv, null), gl, gv,
				2026, "Primera Division", "Spain");
	}

	@Test
	void vinculaEquiposPorElMismoPartidoYUneSuHistorial() {
		Instant manana = Instant.now().plus(Duration.ofHours(20));
		// API-Football: mañana Getafe - Osasuna
		catalogo.guardarPartidos(List.of(new PartidoProv(777_001, FakeProvider.LALIGA, manana, "Coliseum", "NS",
				new EquipoProv(AF_GETAFE, "Getafe", null, null), new EquipoProv(AF_OSASUNA, "Osasuna", null, null),
				null, null, null, null)));

		// football-data.org: el mismo partido (10 minutos de diferencia, otros nombres) + 4 jugados del Getafe
		fd.partidos = new ArrayList<>(List.of(
				fd(500, manana.plusSeconds(600), "TIMED", FD_GETAFE, "Getafe CF", FD_OSASUNA, "CA Osasuna", null, null)));
		for (int i = 1; i <= 4; i++) {
			fd.partidos.add(fd(500 + i, Instant.now().minus(Duration.ofDays(7L * i)), "FINISHED",
					FD_GETAFE, "Getafe CF", FD_BETIS, "Real Betis", 2, i % 2));
		}

		String r = historial.sincronizar();
		assertThat(r).contains("2 equipos vinculados");

		tx.executeWithoutResult(s -> {
			Equipo getafe = equipoRepository.findByProveedorIdIn(List.of(AF_GETAFE)).get(0);
			assertThat(getafe.getFdId()).isEqualTo(FD_GETAFE);
			assertThat(equipoRepository.findByProveedorIdIn(List.of(AF_OSASUNA)).get(0).getFdId()).isEqualTo(FD_OSASUNA);
			// El historial de football-data.org ahora es del Getafe de API-Football
			assertThat(partidoRepository.jugadosDeEquipo(getafe.getId(), Instant.now(), PageRequest.of(0, 10)))
					.hasSize(4);
			// No quedó el equipo duplicado ni el partido repetido
			assertThat(equipoRepository.findByProveedorIdIn(List.of(-FD_GETAFE, -FD_OSASUNA))).isEmpty();
			assertThat(partidoRepository.findByProveedorIdIn(List.of(-500L))).isEmpty();
			assertThat(partidoRepository.findByProveedorIdIn(List.of(777_001L))).hasSize(1);
			// El Betis no jugó en las dos fuentes: queda solo de football-data.org, con su historial
			assertThat(equipoRepository.findByProveedorIdIn(List.of(-FD_BETIS))).hasSize(1);
		});

		// Volver a sincronizar no duplica nada
		long antes = partidoRepository.count();
		historial.sincronizar();
		assertThat(partidoRepository.count()).isEqualTo(antes);
	}

	@Test
	void vinculaPorNombreAunqueNuncaCoincidanEnUnPartido() {
		long afJuve = 880_010, afJuveW = 880_011, afCeltic = 880_012, fdJuve = 109;
		// API-Football solo tiene a Juventus en otra competición (y a su equipo femenino)
		Instant enCuatroDias = Instant.now().plus(Duration.ofDays(4));
		catalogo.guardarPartidos(List.of(
				new PartidoProv(777_010, FakeProvider.LALIGA, enCuatroDias, null, "NS",
						new EquipoProv(afJuve, "Juventus", null, null), new EquipoProv(afCeltic, "Celtic", null, null),
						null, null, null, null),
				new PartidoProv(777_011, FakeProvider.LALIGA, enCuatroDias.plusSeconds(3600), null, "NS",
						new EquipoProv(afJuveW, "Juventus W", null, null), new EquipoProv(afCeltic, "Celtic", null, null),
						null, null, null, null)));
		// football-data.org: 3 jugados de "Juventus FC" hace semanas
		fd.partidos = new ArrayList<>();
		for (int i = 1; i <= 3; i++) {
			fd.partidos.add(fd(600 + i, Instant.now().minus(Duration.ofDays(10L * i)), "FINISHED",
					fdJuve, "Juventus FC", 880_099, "Equipo Sin Pareja", 1, 0));
		}

		historial.sincronizar();

		tx.executeWithoutResult(s -> {
			Equipo juve = equipoRepository.findByProveedorIdIn(List.of(afJuve)).get(0);
			assertThat(juve.getFdId()).isEqualTo(fdJuve);
			assertThat(partidoRepository.jugadosDeEquipo(juve.getId(), Instant.now(), PageRequest.of(0, 10))).hasSize(3);
			// El femenino no se toca
			assertThat(equipoRepository.findByProveedorIdIn(List.of(afJuveW)).get(0).getFdId()).isNull();
		});
	}

	@Test
	void bajaLaTemporadaPasadaUnaSolaVez() {
		long fdA = 95_001, fdB = 95_002;
		Instant pronto = Instant.now().plus(Duration.ofDays(2));
		fd.premier = new ArrayList<>(List.of(new FdPartido(700, pronto, "TIMED", null, new FdEquipo(fdA, "Equipo A", null),
				new FdEquipo(fdB, "Equipo B", null), null, null, 2026, "Premier League", "England")));
		fd.premierPasada = new ArrayList<>();
		for (int i = 1; i <= 5; i++) {
			fd.premierPasada.add(new FdPartido(700 + i, Instant.now().minus(Duration.ofDays(150 + 7L * i)), "FINISHED", null,
					new FdEquipo(fdA, "Equipo A", null), new FdEquipo(fdB, "Equipo B", null), 1, 1, 2025,
					"Premier League", "England"));
		}

		historial.sincronizar();
		historial.sincronizar();

		// Una sola consulta de la temporada pasada (ya terminó, no cambia) y sus 5 partidos quedan como historial
		assertThat(fd.pedidasPasada).isEqualTo(1);
		tx.executeWithoutResult(s -> {
			Equipo a = equipoRepository.findByProveedorIdIn(List.of(-fdA)).get(0);
			assertThat(partidoRepository.jugadosDeEquipo(a.getId(), Instant.now(), PageRequest.of(0, 10))).hasSize(5);
		});
	}

	@Test
	void nombresParecidos() {
		assertThat(NombresEquipos.parecidos("Paris Saint-Germain FC", "Paris Saint Germain")).isTrue();
		assertThat(NombresEquipos.parecidos("FC Barcelona", "Barcelona")).isTrue();
		assertThat(NombresEquipos.parecidos("Manchester City FC", "Manchester City")).isTrue();
		assertThat(NombresEquipos.parecidos("Atleti", "Atlético Madrid")).isTrue();
		// "Real" es una palabra común: compartirla no basta (antes confundía Real Madrid con Real Betis/Sociedad)
		assertThat(NombresEquipos.parecidos("Real Madrid CF", "Real Sociedad")).isFalse();
		assertThat(NombresEquipos.parecidos("Sevilla FC", "Real Betis")).isFalse();
		assertThat(NombresEquipos.parecidos("Olympique de Marseille", "Marseille")).isTrue();
		assertThat(NombresEquipos.parecidos("FC Bayern München", "Bayern Munich")).isTrue();
		assertThat(NombresEquipos.similitud("Benfica", "SL Benfica")).isEqualTo(NombresEquipos.IGUAL);
		assertThat(NombresEquipos.similitud("Lyon", "Olympique Lyon")).isEqualTo(NombresEquipos.IGUAL);
		assertThat(NombresEquipos.similitud("AZ Alkmaar", "AZ")).isEqualTo(NombresEquipos.IGUAL);
		assertThat(NombresEquipos.similitud("Rangers", "Queens Park Rangers")).isLessThan(NombresEquipos.IGUAL);
		assertThat(NombresEquipos.similitud("Manchester", "Manchester United")).isLessThan(NombresEquipos.IGUAL);
		assertThat(NombresEquipos.similitud("AC Milan", "AC Monza")).isLessThan(NombresEquipos.IGUAL);
		assertThat(NombresEquipos.esJuvenilOFemenino("SC Freiburg W")).isTrue();
		assertThat(NombresEquipos.esJuvenilOFemenino("SC Freiburg")).isFalse();
	}
}
