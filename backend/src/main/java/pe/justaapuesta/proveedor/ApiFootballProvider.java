package pe.justaapuesta.proveedor;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import tools.jackson.databind.JsonNode;
import pe.justaapuesta.config.JustaProperties;
import pe.justaapuesta.proveedor.ProveedorDtos.EstadisticaProv;
import pe.justaapuesta.proveedor.ProveedorDtos.PartidoProv;

/**
 * Cliente de API-Football v3 (https://www.api-football.com/documentation-v3).
 * Autenticación con la cabecera x-apisports-key. Nunca registra la clave en los logs.
 */
@Component
public class ApiFootballProvider implements FootballDataProvider {

	private static final Logger log = LoggerFactory.getLogger(ApiFootballProvider.class);
	private static final String CABECERA_RESTANTES = "x-ratelimit-requests-remaining";

	private final RestClient http;
	private final boolean configurado;
	private final String zona;
	private final LimitadorConsultas limitador;

	public ApiFootballProvider(JustaProperties props) {
		JustaProperties.Proveedor p = props.proveedor();
		this.configurado = p.configurado();
		this.zona = props.zonaHoraria();
		this.limitador = new LimitadorConsultas(p.maxPorMinuto(), p.reservaDiaria());
		SimpleClientHttpRequestFactory tiempos = new SimpleClientHttpRequestFactory();
		tiempos.setConnectTimeout(Duration.ofSeconds(10));
		tiempos.setReadTimeout(Duration.ofSeconds(30));
		this.http = RestClient.builder().requestFactory(tiempos).baseUrl(p.url())
				.defaultHeader("x-apisports-key", configurado ? p.apiKey().trim() : "")
				.build();
	}

	@Override
	public boolean disponible() {
		return configurado;
	}

	// Solo consultas permitidas en el plan gratis (sin "last", "next" ni "ids", y sin filtrar por temporada).

	@Override
	public List<PartidoProv> partidosDelDia(LocalDate fecha) {
		return ApiFootballMapper.partidos(get("/fixtures?date", u -> u.path("/fixtures")
				.queryParam("date", fecha.toString()).queryParam("timezone", zona).build()));
	}

	@Override
	public List<EstadisticaProv> estadisticas(long partidoId) {
		return ApiFootballMapper.estadisticasDePartido(get("/fixtures/statistics", u -> u.path("/fixtures/statistics")
				.queryParam("fixture", partidoId).build()));
	}

	@Override
	public List<PartidoProv> enfrentamientos(long equipoA, long equipoB) {
		return ApiFootballMapper.partidos(get("/fixtures/headtohead", u -> u.path("/fixtures/headtohead")
				.queryParam("h2h", equipoA + "-" + equipoB).queryParam("timezone", zona).build()));
	}

	/** Restantes de hoy según el proveedor (para mostrar en /api/admin). */
	public Integer consultasRestantesHoy() {
		return limitador.restantesHoy();
	}

	private JsonNode get(String nombre, Function<UriBuilder, java.net.URI> uri) {
		if (!configurado) {
			throw new ProveedorException("Falta configurar FOOTBALL_API_KEY.");
		}
		limitador.antesDeConsultar();
		JsonNode raiz;
		try {
			raiz = http.get().uri(uri).exchange((req, res) -> {
				limitador.registrarRestantes(numero(res.getHeaders().getFirst(CABECERA_RESTANTES)));
				if (res.getStatusCode().isError()) {
					throw new ProveedorException("El proveedor respondió " + res.getStatusCode().value());
				}
				return res.bodyTo(JsonNode.class);
			});
		} catch (RestClientException e) {
			throw new ProveedorException("No se pudo conectar con el proveedor de datos.", e);
		}
		if (raiz == null) {
			throw new ProveedorException("El proveedor respondió vacío.");
		}
		String error = ApiFootballMapper.error(raiz);
		log.info("API-Football {} -> {} resultados (quedan hoy: {}){}", nombre, raiz.path("results").asInt(0),
				limitador.restantesHoy(), error == null ? "" : " ERROR: " + error);
		if (error != null) {
			throw new ProveedorException("El proveedor rechazó la consulta: " + error);
		}
		return raiz;
	}

	private static Integer numero(String s) {
		try {
			return s == null ? null : Integer.valueOf(s.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
