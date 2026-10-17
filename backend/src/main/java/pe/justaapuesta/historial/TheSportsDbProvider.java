package pe.justaapuesta.historial;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;
import pe.justaapuesta.config.JustaProperties;
import pe.justaapuesta.historial.HistorialDtos.TsdbEquipo;
import pe.justaapuesta.historial.HistorialDtos.TsdbJornada;
import pe.justaapuesta.historial.HistorialDtos.TsdbPartido;
import pe.justaapuesta.proveedor.LimitadorConsultas;
import pe.justaapuesta.proveedor.ProveedorException;

/**
 * Cliente de TheSportsDB v1 (https://www.thesportsdb.com/free_sports_api). Gratis y sin registro (clave pública
 * "123"), unas 30 consultas por minuto. Trae jornadas completas con goles de casi cualquier liga del mundo.
 */
@Component
public class TheSportsDbProvider implements HistorialExtraProvider {

	private final RestClient http;
	private final boolean activo;
	private final LimitadorConsultas limitador;

	public TheSportsDbProvider(JustaProperties props) {
		JustaProperties.TheSportsDb t = props.theSportsDb();
		this.activo = t != null && t.apiKey() != null && !t.apiKey().isBlank();
		this.limitador = new LimitadorConsultas(t == null ? 1 : t.maxPorMinuto(), 0);
		SimpleClientHttpRequestFactory tiempos = new SimpleClientHttpRequestFactory();
		tiempos.setConnectTimeout(Duration.ofSeconds(10));
		tiempos.setReadTimeout(Duration.ofSeconds(30));
		this.http = RestClient.builder().requestFactory(tiempos)
				.baseUrl(activo ? t.url() + "/" + t.apiKey().trim() : "http://localhost")
				.build();
	}

	@Override
	public boolean disponible() {
		return activo;
	}

	@Override
	public List<TsdbEquipo> buscarEquipos(String nombre) {
		JsonNode raiz = consultar("/searchteams.php?t={t}", nombre);
		List<TsdbEquipo> out = new ArrayList<>();
		for (JsonNode t : raiz.path("teams")) {
			if (!"Soccer".equalsIgnoreCase(t.path("strSport").asString(""))
					|| "Female".equalsIgnoreCase(t.path("strGender").asString(""))) {
				continue;
			}
			List<String> otros = Arrays.stream(t.path("strTeamAlternate").asString("").split(","))
					.map(String::trim).filter(s -> !s.isEmpty()).toList();
			out.add(new TsdbEquipo(t.path("idTeam").asLong(), t.path("strTeam").asString(""), otros,
					largo(t.path("idLeague")), texto(t.path("strBadge"))));
		}
		return out;
	}

	@Override
	public Optional<TsdbJornada> ultimaJornada(long liga) {
		JsonNode raiz = consultar("/eventspastleague.php?id={id}", liga);
		for (JsonNode e : raiz.path("events")) {
			Integer ronda = entero(e.path("intRound"));
			String temporada = texto(e.path("strSeason"));
			if (ronda != null && ronda > 0 && temporada != null) {
				return Optional.of(new TsdbJornada(ronda, temporada));
			}
		}
		return Optional.empty();
	}

	@Override
	public List<TsdbPartido> jornada(long liga, int ronda, String temporada) {
		JsonNode raiz = consultar("/eventsround.php?id={id}&r={r}&s={s}", liga, ronda, temporada);
		List<TsdbPartido> out = new ArrayList<>();
		for (JsonNode e : raiz.path("events")) {
			Instant fecha = fecha(e);
			Long local = largo(e.path("idHomeTeam")), visita = largo(e.path("idAwayTeam"));
			if (fecha == null || local == null || visita == null) {
				continue;
			}
			out.add(new TsdbPartido(e.path("idEvent").asLong(), fecha, local, e.path("strHomeTeam").asString(""),
					visita, e.path("strAwayTeam").asString(""), entero(e.path("intHomeScore")),
					entero(e.path("intAwayScore")), texto(e.path("strVenue")), liga, texto(e.path("strLeague")),
					texto(e.path("strCountry")), temporada));
		}
		return out;
	}

	private JsonNode consultar(String uri, Object... variables) {
		if (!activo) {
			throw new ProveedorException("TheSportsDB está desactivado.");
		}
		for (int intento = 1;; intento++) {
			limitador.antesDeConsultar();
			JsonNode raiz = pedir(uri, variables);
			if (raiz != null || intento == 2) {
				if (raiz == null) {
					throw new ProveedorException("TheSportsDB pidió esperar (429).");
				}
				return raiz;
			}
			esperar(); // 429: la versión gratis pide esperar un minuto
		}
	}

	/** @return la respuesta, o null si TheSportsDB pidió esperar (429) */
	private JsonNode pedir(String uri, Object... variables) {
		try {
			JsonNode raiz = http.get().uri(uri, variables).exchange((req, res) -> {
				if (res.getStatusCode().value() == 429) {
					return null;
				}
				if (res.getStatusCode().isError()) {
					throw new ProveedorException("TheSportsDB respondió " + res.getStatusCode().value());
				}
				JsonNode cuerpo = res.bodyTo(JsonNode.class);
				if (cuerpo == null) {
					throw new ProveedorException("TheSportsDB respondió vacío.");
				}
				return cuerpo;
			});
			return raiz;
		} catch (RestClientException e) {
			throw new ProveedorException("No se pudo conectar con TheSportsDB.", e);
		}
	}

	private static void esperar() {
		try {
			Thread.sleep(Duration.ofSeconds(61));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ProveedorException("Consulta interrumpida.");
		}
	}

	/** strTimestamp viene en UTC ("2026-09-20T11:00:00" o con zona); si falta, la fecha a mediodía. */
	static Instant fecha(JsonNode e) {
		String ts = texto(e.path("strTimestamp"));
		try {
			if (ts != null) {
				return ts.endsWith("Z") || ts.matches(".*[+-]\\d\\d:\\d\\d$") ? OffsetDateTime.parse(ts).toInstant()
						: LocalDateTime.parse(ts).toInstant(ZoneOffset.UTC);
			}
			String dia = texto(e.path("dateEvent"));
			return dia == null ? null : LocalDate.parse(dia).atTime(12, 0).toInstant(ZoneOffset.UTC);
		} catch (RuntimeException ex) {
			return null;
		}
	}

	private static String texto(JsonNode n) {
		String s = n == null || n.isNull() || n.isMissingNode() ? null : n.asString("").trim();
		return s == null || s.isEmpty() ? null : s;
	}

	private static Integer entero(JsonNode n) {
		String s = texto(n);
		try {
			return s == null ? null : Integer.valueOf(s);
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private static Long largo(JsonNode n) {
		String s = texto(n);
		try {
			return s == null ? null : Long.valueOf(s);
		} catch (NumberFormatException ex) {
			return null;
		}
	}
}
