package pe.justaapuesta.historial;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
import pe.justaapuesta.historial.HistorialDtos.TsdbEquipo;
import pe.justaapuesta.historial.HistorialDtos.TsdbJornada;
import pe.justaapuesta.historial.HistorialDtos.TsdbPartido;
import pe.justaapuesta.partido.CatalogoService;
import pe.justaapuesta.partido.Equipo;
import pe.justaapuesta.partido.EquipoRepository;
import pe.justaapuesta.partido.PartidoRepository;
import pe.justaapuesta.proveedor.ProveedorDtos.EquipoProv;
import pe.justaapuesta.proveedor.ProveedorDtos.PartidoProv;

/**
 * TheSportsDB completa el historial de un equipo que va a jugar y no tiene partidos guardados: busca su liga,
 * baja sus últimas jornadas y no repite partidos que ya trajo otra fuente.
 */
@SpringBootTest(properties = { "justa.sync.programado=false", "justa.the-sports-db.api-key=prueba" })
@ActiveProfiles("test")
class TheSportsDbServiceTest {

	static final long AF_CELTIC = 881_001, AF_RIVAL = 881_002, TSDB_CELTIC = 133_647, TSDB_RANGERS = 133_642,
			TSDB_HIBS = 133_643, LIGA = 4330;

	static class TsdbFalso implements HistorialExtraProvider {
		@Override
		public boolean disponible() {
			return true;
		}

		@Override
		public List<TsdbEquipo> buscarEquipos(String nombre) {
			return nombre.equals("Kilmarnock")
					? List.of(new TsdbEquipo(TSDB_CELTIC, "Kilmarnock", List.of("Kilmarnock FC"), (long) LIGA, null))
					: List.of();
		}

		@Override
		public Optional<TsdbJornada> ultimaJornada(long liga) {
			return Optional.of(new TsdbJornada(6, "2026-2027"));
		}

		/** Cada jornada: Celtic contra Rangers o Hibernian, una semana antes que la anterior. */
		@Override
		public List<TsdbPartido> jornada(long liga, int ronda, String temporada) {
			Instant fecha = Instant.now().minus(Duration.ofDays(7L * (7 - ronda)));
			boolean rangers = ronda % 2 == 0;
			return List.of(new TsdbPartido(9_000_000 + ronda, fecha, TSDB_CELTIC, "Kilmarnock",
					rangers ? TSDB_RANGERS : TSDB_HIBS, rangers ? "Rangers" : "Hibernian", 2, 1, null, liga,
					"Scottish Premiership", "Scotland", temporada));
		}
	}

	@TestConfiguration
	static class Config {
		@Bean
		@Primary
		TsdbFalso tsdbFalso() {
			return new TsdbFalso();
		}
	}

	@Autowired
	private TheSportsDbService servicio;
	@Autowired
	private CatalogoService catalogo;
	@Autowired
	private EquipoRepository equipoRepository;
	@Autowired
	private PartidoRepository partidoRepository;
	@Autowired
	private TransactionTemplate tx;

	@Test
	void completaElHistorialSinRepetir() {
		// API-Football: Kilmarnock juega en 2 días y no tiene ningún partido guardado
		catalogo.guardarPartidos(List.of(new PartidoProv(778_001, FakeProvider.LALIGA,
				Instant.now().plus(Duration.ofDays(2)), null, "NS", new EquipoProv(AF_CELTIC, "Kilmarnock", null, null),
				new EquipoProv(AF_RIVAL, "Equipo Rival Lejano", null, null), null, null, null, null)));

		String r = servicio.completar();
		assertThat(r).contains("1 ligas bajadas");

		tx.executeWithoutResult(s -> {
			Equipo kilmarnock = equipoRepository.findByProveedorIdIn(List.of(AF_CELTIC)).get(0);
			// 6 jornadas -> 6 jugados del Kilmarnock de API-Football (no de uno repetido)
			assertThat(partidoRepository.jugadosDeEquipo(kilmarnock.getId(), Instant.now(), PageRequest.of(0, 10)))
					.hasSize(6);
			assertThat(equipoRepository.findByProveedorIdIn(List.of(-(TheSportsDbService.DESPLAZAMIENTO + TSDB_CELTIC))))
					.isEmpty();
			// Los rivales se crean como equipos de TheSportsDB
			assertThat(equipoRepository.findByProveedorIdIn(List.of(-(TheSportsDbService.DESPLAZAMIENTO + TSDB_RANGERS))))
					.hasSize(1);
		});

		// Volver a correrlo no duplica partidos (y ya no figura como "sin historial")
		long antes = partidoRepository.count();
		servicio.completar();
		assertThat(partidoRepository.count()).isEqualTo(antes);
	}

	@Test
	void eligeSoloSiEsClaramenteElMismoEquipo() {
		Equipo nuestro = new Equipo();
		nuestro.setNombreNormalizado("celtic");
		List<TsdbEquipo> resultados = new ArrayList<>(List.of(
				new TsdbEquipo(1, "Celtic", List.of("Celtic FC"), 4330L, null),
				new TsdbEquipo(2, "Cardiff Celtic", List.of(), 9999L, null)));
		assertThat(TheSportsDbService.elegir(resultados, nuestro)).map(TsdbEquipo::id).contains(1L);
		resultados.add(new TsdbEquipo(3, "Celtic", List.of(), 8888L, null));
		assertThat(TheSportsDbService.elegir(resultados, nuestro)).isEmpty(); // dos "Celtic": mejor no adivinar
	}
}
