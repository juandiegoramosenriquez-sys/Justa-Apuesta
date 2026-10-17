package pe.justaapuesta;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import pe.justaapuesta.proveedor.FootballDataProvider;
import pe.justaapuesta.proveedor.ProveedorDtos.EquipoProv;
import pe.justaapuesta.proveedor.ProveedorDtos.EstadisticaProv;
import pe.justaapuesta.proveedor.ProveedorDtos.LigaProv;
import pe.justaapuesta.proveedor.ProveedorDtos.PartidoProv;
import pe.justaapuesta.proveedor.ProveedorException;

/**
 * Proveedor falso para tests (imita el plan gratis: partidos por fecha, estadísticas por partido y H2H).
 * LaLiga con 4 equipos: 10 jugados por equipo con estadísticas, próximos partidos y 3 enfrentamientos
 * Atlético - Real Madrid. Cuenta las consultas para comprobar la caché.
 */
public class FakeProvider implements FootballDataProvider {

	public static final long ATLETICO = 530, REAL_MADRID = 541, SEVILLA = 536, SOCIEDAD = 548;
	public static final LigaProv LALIGA = new LigaProv(140, "La Liga", "Spain", null, 2026);
	private static final ZoneId LIMA = ZoneId.of("America/Lima");
	private static final Map<Long, String> NOMBRES = Map.of(ATLETICO, "Atlético Madrid", REAL_MADRID, "Real Madrid",
			SEVILLA, "Sevilla", SOCIEDAD, "Real Sociedad");

	public final AtomicInteger consultas = new AtomicInteger();
	public volatile boolean fallar;
	private final Map<Long, PartidoProv> porId = new HashMap<>();
	private final Instant ahora = Instant.now();

	public FakeProvider() {
		for (long equipo : NOMBRES.keySet()) {
			historial(equipo).forEach(p -> porId.putIfAbsent(p.id(), p));
			proximos(equipo).forEach(p -> porId.putIfAbsent(p.id(), p));
		}
	}

	public static EquipoProv equipo(long id) {
		return new EquipoProv(id, NOMBRES.get(id), null, null);
	}

	private static long rival(long equipo, int i) {
		List<Long> otros = NOMBRES.keySet().stream().filter(x -> x != equipo).sorted().toList();
		return otros.get(i % otros.size());
	}

	/** 10 jugados: uno cada 4 días hacia atrás (desde hace 10 días), alternando local/visita. */
	private List<PartidoProv> historial(long equipo) {
		List<PartidoProv> out = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			boolean casa = i % 2 == 0;
			long r = rival(equipo, i);
			long id = equipo * 1000 + i;
			int gf = (int) ((equipo + i) % 3) + (casa ? 1 : 0);
			int gc = (int) ((equipo * 7 + i) % 2);
			out.add(partido(id, ahora.minus(Duration.ofDays(4L * i + 10)), "FT",
					casa ? equipo : r, casa ? r : equipo, casa ? gf : gc, casa ? gc : gf));
		}
		return out;
	}

	/** Próximos: el primero del Atlético es en casa contra el Real Madrid dentro de 2 días. */
	private List<PartidoProv> proximos(long equipo) {
		List<PartidoProv> out = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			long r = equipo == ATLETICO && i == 0 ? REAL_MADRID : rival(equipo, i + 1);
			long id = equipo * 1000 + 100 + i;
			out.add(partido(id, ahora.plus(Duration.ofDays(2L + 2L * i)), "NS", equipo, r, null, null));
		}
		return out;
	}

	private List<PartidoProv> h2h() {
		List<PartidoProv> out = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			boolean casa = i % 2 == 0;
			out.add(partido(900_000 + i, ahora.minus(Duration.ofDays(200L + 90L * i)), "FT",
					casa ? ATLETICO : REAL_MADRID, casa ? REAL_MADRID : ATLETICO, i, 1));
		}
		// Uno muy antiguo (más de 2 años): no se debe guardar
		out.add(partido(999_999, ahora.minus(Duration.ofDays(1500)), "FT", ATLETICO, REAL_MADRID, 4, 0));
		return out;
	}

	public static PartidoProv partido(long id, Instant fecha, String estado, long local, long visita, Integer gl, Integer gv) {
		return new PartidoProv(id, LALIGA, fecha, "Estadio " + NOMBRES.get(local), estado, equipo(local), equipo(visita),
				gl, gv, null, null);
	}

	/** Estadísticas finales de un partido terminado. */
	public static List<EstadisticaProv> stats(long id, long local, long visita) {
		return List.of(
				new EstadisticaProv(local, 6, 2, 0, 5, 12, new BigDecimal("55")),
				new EstadisticaProv(visita, 4, 3, (int) (id % 5 == 0 ? 1 : 0), 3, 9, new BigDecimal("45")));
	}

	private void consulta() {
		consultas.incrementAndGet();
		if (fallar) {
			throw new ProveedorException("Se acabó la cuota diaria del proveedor de datos.");
		}
	}

	@Override
	public boolean disponible() {
		return true;
	}

	@Override
	public List<PartidoProv> partidosDelDia(LocalDate fecha) {
		consulta();
		return porId.values().stream().filter(p -> p.fecha().atZone(LIMA).toLocalDate().equals(fecha)).toList();
	}

	@Override
	public List<EstadisticaProv> estadisticas(long partidoId) {
		consulta();
		PartidoProv p = porId.get(partidoId);
		return p == null || !"FT".equals(p.estadoCorto()) ? List.of() : stats(p.id(), p.local().id(), p.visita().id());
	}

	@Override
	public List<PartidoProv> enfrentamientos(long equipoA, long equipoB) {
		consulta();
		return h2h();
	}
}
