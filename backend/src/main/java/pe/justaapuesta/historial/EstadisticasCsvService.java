package pe.justaapuesta.historial;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.RequiredArgsConstructor;
import pe.justaapuesta.config.JustaProperties;
import pe.justaapuesta.partido.Equipo;
import pe.justaapuesta.partido.EstadisticaPartido;
import pe.justaapuesta.partido.EstadisticaPartidoRepository;
import pe.justaapuesta.partido.Partido;
import pe.justaapuesta.partido.PartidoRepository;

/**
 * Fuente 4 (football-data.co.uk, gratis y sin clave): corners, tarjetas, remates totales y remates a puerta de
 * cada partido de las ligas principales de Europa. Son archivos CSV que se actualizan un par de veces por semana.
 * Cada fila se une con el partido ya guardado del mismo día, con el mismo marcador y equipos de nombre parecido;
 * así funcionan los mercados de corners, tarjetas y remates aunque API-Football no esté disponible.
 */
@Service
@RequiredArgsConstructor
public class EstadisticasCsvService {

	private static final Logger log = LoggerFactory.getLogger(EstadisticasCsvService.class);
	private static final String URL = "https://www.football-data.co.uk/mmz4281/%s/%s.csv";
	/** Inglaterra (4), Escocia, Alemania (2), Italia (2), España (2), Francia (2), Países Bajos, Bélgica, Portugal, Turquía, Grecia. */
	static final List<String> DIVISIONES = List.of("E0", "E1", "E2", "E3", "SC0", "D1", "D2", "I1", "I2", "SP1",
			"SP2", "F1", "F2", "N1", "B1", "P1", "T1", "G1");
	private static final ZoneId HORA_UK = ZoneId.of("Europe/London");
	/** Diferencia de hora aceptada entre fuentes (algunas guardan la hora aproximada). */
	private static final Duration MARGEN = Duration.ofHours(6);

	/** Abreviaturas del CSV que no se parecen al nombre completo. */
	private static final Map<String, String> ALIAS = Map.ofEntries(
			Map.entry("Man United", "Manchester United"), Map.entry("Man City", "Manchester City"),
			Map.entry("Nott'm Forest", "Nottingham Forest"), Map.entry("Wolves", "Wolverhampton Wanderers"),
			Map.entry("Sheffield Weds", "Sheffield Wednesday"), Map.entry("QPR", "Queens Park Rangers"),
			Map.entry("West Brom", "West Bromwich Albion"), Map.entry("Ath Madrid", "Atletico Madrid"),
			Map.entry("Ath Bilbao", "Athletic Club"), Map.entry("Sociedad", "Real Sociedad"),
			Map.entry("Espanol", "Espanyol"), Map.entry("Vallecano", "Rayo Vallecano"),
			Map.entry("Betis", "Real Betis"), Map.entry("Celta", "Celta Vigo"),
			Map.entry("M'gladbach", "Borussia Monchengladbach"), Map.entry("Ein Frankfurt", "Eintracht Frankfurt"),
			Map.entry("FC Koln", "Koln"), Map.entry("Paris SG", "Paris Saint Germain"),
			Map.entry("St Etienne", "Saint Etienne"), Map.entry("Sp Lisbon", "Sporting CP"),
			Map.entry("Sp Braga", "Braga"), Map.entry("For Sittard", "Fortuna Sittard"),
			Map.entry("Inter", "Internazionale"), Map.entry("PSV Eindhoven", "PSV"));

	private final PartidoRepository partidoRepository;
	private final EstadisticaPartidoRepository estadisticaRepository;
	private final TransactionTemplate tx;
	private final JustaProperties props;
	private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(15)).build();

	/** Una fila del CSV: el partido y las estadísticas de cada equipo. */
	record Fila(Instant inicio, boolean conHora, String local, String visita, int golesLocal, int golesVisita,
			Stats statsLocal, Stats statsVisita) {
	}

	record Stats(Integer corners, Integer amarillas, Integer rojas, Integer rematesAPuerta, Integer rematesTotales) {
	}

	/** Baja la temporada actual y la anterior y completa los partidos que aún no tienen estadísticas. */
	public String importar() {
		LocalDate hoy = LocalDate.now(props.zona());
		List<String> temporadas = List.of(temporada(hoy), temporada(hoy.minusYears(1)));
		List<Fila> filas = new ArrayList<>();
		int errores = 0;
		for (String t : temporadas) {
			for (String div : DIVISIONES) {
				try {
					filas.addAll(leer(bajar(URL.formatted(t, div))));
				} catch (Exception e) {
					errores++;
					log.debug("Sin CSV {}/{}: {}", t, div, e.getMessage());
				}
			}
		}
		int unidas = filas.isEmpty() ? 0 : unir(filas);
		String r = "%d filas leídas, %d partidos con estadísticas nuevas%s".formatted(filas.size(), unidas,
				errores > 0 ? ", " + errores + " archivos sin datos" : "");
		log.info("Estadísticas football-data.co.uk: {}", r);
		return r;
	}

	/** "2627" para la temporada 2026-27 (empieza en julio). */
	static String temporada(LocalDate dia) {
		int inicio = dia.getMonthValue() >= 7 ? dia.getYear() : dia.getYear() - 1;
		return "%02d%02d".formatted(inicio % 100, (inicio + 1) % 100);
	}

	private String bajar(String url) throws Exception {
		HttpResponse<byte[]> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(40))
				.header("User-Agent", "JustaApuesta/1.0").build(), HttpResponse.BodyHandlers.ofByteArray());
		if (r.statusCode() != 200) {
			throw new IllegalStateException("HTTP " + r.statusCode());
		}
		// Los archivos vienen en latin-1 o UTF-8 según la temporada; los nombres usados no llevan tildes
		return new String(r.body(), StandardCharsets.ISO_8859_1);
	}

	/** Lee el CSV; las filas sin marcador o sin ninguna estadística se saltan. */
	static List<Fila> leer(String csv) {
		String[] lineas = csv.replace("﻿", "").replace("ï»¿", "").split("\r?\n");
		if (lineas.length < 2) {
			return List.of();
		}
		Map<String, Integer> col = new HashMap<>();
		String[] cab = lineas[0].split(",", -1);
		for (int i = 0; i < cab.length; i++) {
			col.putIfAbsent(cab[i].trim(), i);
		}
		List<Fila> out = new ArrayList<>();
		for (int i = 1; i < lineas.length; i++) {
			String[] c = lineas[i].split(",", -1);
			String fecha = valor(c, col, "Date"), local = valor(c, col, "HomeTeam"), visita = valor(c, col, "AwayTeam");
			Integer gl = numero(c, col, "FTHG"), gv = numero(c, col, "FTAG");
			if (fecha == null || local == null || visita == null || gl == null || gv == null) {
				continue;
			}
			Stats sl = new Stats(numero(c, col, "HC"), numero(c, col, "HY"), numero(c, col, "HR"), numero(c, col, "HST"),
					numero(c, col, "HS"));
			Stats sv = new Stats(numero(c, col, "AC"), numero(c, col, "AY"), numero(c, col, "AR"), numero(c, col, "AST"),
					numero(c, col, "AS"));
			if (sl.corners() == null && sl.amarillas() == null && sl.rematesTotales() == null) {
				continue;
			}
			LocalDate dia;
			try {
				dia = LocalDate.parse(fecha, DateTimeFormatter.ofPattern(fecha.length() == 8 ? "dd/MM/yy" : "dd/MM/yyyy"));
			} catch (RuntimeException e) {
				continue;
			}
			String hora = valor(c, col, "Time");
			LocalTime t = LocalTime.NOON;
			boolean conHora = false;
			if (hora != null) {
				try {
					t = LocalTime.parse(hora);
					conHora = true;
				} catch (RuntimeException e) {
					// sin hora: se busca en todo el día
				}
			}
			out.add(new Fila(LocalDateTime.of(dia, t).atZone(HORA_UK).toInstant(), conHora, local, visita, gl, gv, sl, sv));
		}
		return out;
	}

	private static String valor(String[] c, Map<String, Integer> col, String nombre) {
		Integer i = col.get(nombre);
		if (i == null || i >= c.length) {
			return null;
		}
		String v = c[i].trim();
		return v.isEmpty() ? null : v;
	}

	private static Integer numero(String[] c, Map<String, Integer> col, String nombre) {
		String v = valor(c, col, nombre);
		if (v == null) {
			return null;
		}
		try {
			return (int) Math.round(Double.parseDouble(v));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private int unir(List<Fila> filas) {
		Instant desde = filas.stream().map(Fila::inicio).min(Comparator.naturalOrder()).orElseThrow().minus(Duration.ofDays(1));
		Instant hasta = filas.stream().map(Fila::inicio).max(Comparator.naturalOrder()).orElseThrow().plus(Duration.ofDays(1));
		List<Partido> guardados = partidoRepository.entre(desde, hasta).stream().filter(Partido::jugado).toList();
		Set<Long> conStats = new HashSet<>(estadisticaRepository.partidosConCorners());
		Map<LocalDate, List<Partido>> porDia = new HashMap<>();
		for (Partido p : guardados) {
			if (!conStats.contains(p.getId())) {
				porDia.computeIfAbsent(p.getFechaHoraUtc().atZone(ZoneId.of("UTC")).toLocalDate(), d -> new ArrayList<>()).add(p);
			}
		}
		List<EstadisticaPartido> nuevas = new ArrayList<>();
		Set<Long> usados = new HashSet<>();
		for (Fila f : filas) {
			Partido p = buscar(f, porDia);
			if (p == null || !usados.add(p.getId())) {
				continue;
			}
			nuevas.add(estadistica(p.getId(), p.getLocal().getId(), f.statsLocal()));
			nuevas.add(estadistica(p.getId(), p.getVisita().getId(), f.statsVisita()));
		}
		if (!nuevas.isEmpty()) {
			tx.executeWithoutResult(s -> {
				usados.forEach(estadisticaRepository::borrarDePartido);
				estadisticaRepository.flush();
				estadisticaRepository.saveAll(nuevas);
			});
		}
		return usados.size();
	}

	/** El partido guardado de la fila: mismo día, mismo marcador y los dos equipos con nombre parecido. */
	static Partido buscar(Fila f, Map<LocalDate, List<Partido>> porDia) {
		LocalDate dia = f.inicio().atZone(ZoneId.of("UTC")).toLocalDate();
		Duration margen = f.conHora() ? MARGEN : Duration.ofHours(14);
		Partido mejor = null;
		int mejorPuntos = 0;
		boolean empate = false;
		for (int d = -1; d <= 1; d++) {
			for (Partido p : porDia.getOrDefault(dia.plusDays(d), List.of())) {
				if (!Integer.valueOf(f.golesLocal()).equals(p.getGolesLocal())
						|| !Integer.valueOf(f.golesVisita()).equals(p.getGolesVisita())
						|| Duration.between(p.getFechaHoraUtc(), f.inicio()).abs().compareTo(margen) > 0) {
					continue;
				}
				int a = parecido(f.local(), p.getLocal()), b = parecido(f.visita(), p.getVisita());
				if (a < 2 || b < 2) {
					continue;
				}
				if (a + b > mejorPuntos) {
					mejor = p;
					mejorPuntos = a + b;
					empate = false;
				} else if (a + b == mejorPuntos) {
					empate = true;
				}
			}
		}
		return empate ? null : mejor;
	}

	private static int parecido(String nombreCsv, Equipo e) {
		String n = ALIAS.getOrDefault(nombreCsv, nombreCsv);
		int s = Math.max(NombresEquipos.similitud(n, e.getNombre()),
				NombresEquipos.similitudConVariantes(n, e.getNombreNormalizado()));
		if (!n.equals(nombreCsv)) {
			s = Math.max(s, NombresEquipos.similitudConVariantes(nombreCsv, e.getNombreNormalizado()));
		}
		return s;
	}

	private static EstadisticaPartido estadistica(Long partidoId, Long equipoId, Stats s) {
		EstadisticaPartido e = new EstadisticaPartido();
		e.setClave(new EstadisticaPartido.Clave(partidoId, equipoId));
		e.setCorners(s.corners());
		e.setTarjetasAmarillas(s.amarillas());
		e.setTarjetasRojas(s.rojas());
		e.setRematesAPuerta(s.rematesAPuerta());
		e.setRematesTotales(s.rematesTotales());
		return e;
	}
}
