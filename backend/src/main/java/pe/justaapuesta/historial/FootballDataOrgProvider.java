package pe.justaapuesta.historial;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;
import pe.justaapuesta.config.JustaProperties;
import pe.justaapuesta.historial.HistorialDtos.FdEquipo;
import pe.justaapuesta.historial.HistorialDtos.FdPartido;
import pe.justaapuesta.proveedor.LimitadorConsultas;
import pe.justaapuesta.proveedor.ProveedorException;

/**
 * Cliente de football-data.org v4 (https://docs.football-data.org). Plan gratis: 12 competiciones, 10 consultas
 * por minuto. Autenticación con la cabecera X-Auth-Token. Nunca registra la clave en los logs.
 */
@Component
public class FootballDataOrgProvider implements HistorialProvider {

	private static final Logger log = LoggerFactory.getLogger(FootballDataOrgProvider.class);

	private final RestClient http;
	private final boolean configurado;
	private final LimitadorConsultas limitador;

	public FootballDataOrgProvider(JustaProperties props) {
		JustaProperties.Historial h = props.historial();
		this.configurado = h.configurado();
		this.limitador = new LimitadorConsultas(h.maxPorMinuto(), 0);
		SimpleClientHttpRequestFactory tiempos = new SimpleClientHttpRequestFactory();
		tiempos.setConnectTimeout(Duration.ofSeconds(10));
		tiempos.setReadTimeout(Duration.ofSeconds(30));
		this.http = RestClient.builder().requestFactory(tiempos).baseUrl(h.url())
				.defaultHeader("X-Auth-Token", configurado ? h.apiKey().trim() : "")
				.build();
	}

	@Override
	public boolean disponible() {
		return configurado;
	}

	@Override
	public List<FdPartido> partidosDeTemporada(String codigo) {
		return consultar(codigo, null);
	}

	@Override
	public List<FdPartido> partidosDeTemporada(String codigo, int temporada) {
		return consultar(codigo, temporada);
	}

	private List<FdPartido> consultar(String codigo, Integer temporada) {
		if (!configurado) {
			throw new ProveedorException("Falta configurar FOOTBALL_DATA_ORG_KEY.");
		}
		limitador.antesDeConsultar();
		JsonNode raiz;
		try {
			raiz = http.get().uri(u -> {
				u.path("/competitions/{codigo}/matches");
				if (temporada != null) {
					u.queryParam("season", temporada);
				}
				return u.build(codigo);
			}).exchange((req, res) -> {
				if (res.getStatusCode().isError()) {
					JsonNode cuerpo = res.bodyTo(JsonNode.class);
					String msg = cuerpo == null ? "" : cuerpo.path("message").asString("");
					throw new ProveedorException("football-data.org respondió " + res.getStatusCode().value() + " " + msg);
				}
				return res.bodyTo(JsonNode.class);
			});
		} catch (RestClientException e) {
			throw new ProveedorException("No se pudo conectar con football-data.org.", e);
		}
		List<FdPartido> lista = partidos(raiz);
		log.info("football-data.org {}{} -> {} partidos", codigo, temporada == null ? "" : " " + temporada, lista.size());
		return lista;
	}

	/** Traduce la respuesta de /competitions/{codigo}/matches. Tolera campos faltantes. */
	static List<FdPartido> partidos(JsonNode raiz) {
		List<FdPartido> out = new ArrayList<>();
		if (raiz == null) {
			return out;
		}
		String competicion = raiz.path("competition").path("name").asString(null);
		for (JsonNode m : raiz.path("matches")) {
			JsonNode ft = m.path("score").path("fullTime");
			String inicio = m.path("season").path("startDate").asString("");
			out.add(new FdPartido(
					m.path("id").asLong(),
					Instant.parse(m.path("utcDate").asString()),
					m.path("status").asString(""),
					texto(m.path("venue")),
					equipo(m.path("homeTeam")),
					equipo(m.path("awayTeam")),
					entero(ft.path("home")),
					entero(ft.path("away")),
					inicio.length() >= 4 ? Integer.valueOf(inicio.substring(0, 4)) : null,
					competicion,
					texto(m.path("area").path("name"))));
		}
		return out;
	}

	private static FdEquipo equipo(JsonNode t) {
		String corto = texto(t.path("shortName"));
		String completo = t.path("name").asString("");
		java.util.List<String> otros = java.util.stream.Stream.of(completo, texto(t.path("tla")))
				.filter(x -> x != null && !x.isBlank()).toList();
		return new FdEquipo(t.path("id").asLong(), corto != null ? corto : completo, texto(t.path("crest")), otros);
	}

	private static String texto(JsonNode n) {
		return n.isMissingNode() || n.isNull() ? null : n.asString(null);
	}

	private static Integer entero(JsonNode n) {
		return n.isNumber() ? n.asInt() : null;
	}
}
