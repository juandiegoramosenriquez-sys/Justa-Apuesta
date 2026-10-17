package pe.justaapuesta.proveedor;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import pe.justaapuesta.proveedor.ProveedorDtos.PartidoProv;

/** Formato de respuesta de API-Football v3 (fixtures con estadísticas y jugadores). */
class ApiFootballMapperTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final String PARTIDO = """
			{"get":"fixtures","results":1,"errors":[],"response":[{
			  "fixture":{"id":1035000,"date":"2026-10-12T15:00:00-05:00","venue":{"name":"Estadio Monumental"},
			             "status":{"long":"Match Finished","short":"FT"}},
			  "league":{"id":281,"name":"Primera División","country":"Peru","logo":"https://x/l.png","season":2026},
			  "teams":{"home":{"id":2539,"name":"Universitario","logo":"https://x/u.png"},
			           "away":{"id":2550,"name":"Alianza Lima","logo":"https://x/a.png"}},
			  "goals":{"home":2,"away":1},
			  "statistics":[
			    {"team":{"id":2539},"statistics":[
			      {"type":"Shots on Goal","value":6},{"type":"Total Shots","value":14},
			      {"type":"Corner Kicks","value":7},{"type":"Ball Possession","value":"58%"},
			      {"type":"Yellow Cards","value":2},{"type":"Red Cards","value":null}]},
			    {"team":{"id":2550},"statistics":[
			      {"type":"Shots on Goal","value":"3"},{"type":"Total Shots","value":9},
			      {"type":"Corner Kicks","value":null},{"type":"Ball Possession","value":"42%"},
			      {"type":"Yellow Cards","value":3},{"type":"Red Cards","value":1}]}],
			  "players":[{"team":{"id":2539},"players":[
			      {"player":{"id":1,"name":"A. Valera"},"statistics":[{"games":{"minutes":90,"number":9,"position":"F"},
			        "goals":{"total":2},"shots":{"on":3},"cards":{"yellow":0,"red":0}}]},
			      {"player":{"id":2,"name":"Suplente"},"statistics":[{"games":{"minutes":null,"number":20,"position":"M"},
			        "goals":{"total":null},"shots":{"on":null},"cards":{"yellow":0,"red":0}}]}]}]
			}]}""";

	@Test
	void partidoConEstadisticasYJugadores() {
		JsonNode raiz = JSON.readTree(PARTIDO);
		assertThat(ApiFootballMapper.error(raiz)).isNull();

		PartidoProv p = ApiFootballMapper.partidos(raiz).get(0);
		assertThat(p.id()).isEqualTo(1035000);
		assertThat(p.fecha()).isEqualTo(Instant.parse("2026-10-12T20:00:00Z"));
		assertThat(p.estadoCorto()).isEqualTo("FT");
		assertThat(p.liga().id()).isEqualTo(281);
		assertThat(p.liga().temporada()).isEqualTo(2026);
		assertThat(p.local().nombre()).isEqualTo("Universitario");
		assertThat(p.golesLocal()).isEqualTo(2);
		assertThat(p.estadio()).isEqualTo("Estadio Monumental");

		var local = p.estadisticas().get(0);
		assertThat(local.corners()).isEqualTo(7);
		assertThat(local.rematesAPuerta()).isEqualTo(6);
		assertThat(local.rojas()).isZero(); // null con cobertura = 0
		assertThat(local.posesion()).isEqualByComparingTo(new BigDecimal("58"));
		var visita = p.estadisticas().get(1);
		assertThat(visita.rematesAPuerta()).isEqualTo(3); // "3" como texto
		assertThat(visita.corners()).isZero();
		assertThat(visita.rojas()).isEqualTo(1);

		// El suplente que no jugó no cuenta
		assertThat(p.jugadores()).hasSize(1);
		assertThat(p.jugadores().get(0).goles()).isEqualTo(2);
		assertThat(p.jugadores().get(0).dorsal()).isEqualTo(9);
	}

	@Test
	void sinEstadisticasEsNullYSinCoberturaSeOmite() {
		JsonNode sinBloque = JSON.readTree(PARTIDO.replaceAll(",\\s*\"statistics\":\\[[\\s\\S]*\\],\\s*\"players\"", ",\"players\""));
		assertThat(ApiFootballMapper.partidos(sinBloque).get(0).estadisticas()).isNull();

		JsonNode vacio = JSON.readTree("""
				{"errors":[],"response":[{"fixture":{"id":1,"date":"2026-10-12T15:00:00+00:00","status":{"short":"FT"}},
				"league":{"id":1},"teams":{"home":{"id":1},"away":{"id":2}},"goals":{"home":0,"away":0},
				"statistics":[{"team":{"id":1},"statistics":[]}]}]}""");
		assertThat(ApiFootballMapper.partidos(vacio).get(0).estadisticas()).isEmpty();
	}

	@Test
	void erroresDelProveedor() {
		assertThat(ApiFootballMapper.error(JSON.readTree("""
				{"errors":{"plan":"Free plans do not have access to this season."},"response":[]}""")))
				.contains("Free plans");
		assertThat(ApiFootballMapper.error(JSON.readTree("""
				{"errors":{"token":"Error/Missing application key."},"response":[]}""")))
				.contains("application key");
	}

	@Test
	void estadisticasDeUnPartido() {
		var stats = ApiFootballMapper.estadisticasDePartido(JSON.readTree("""
				{"errors":[],"response":[
				  {"team":{"id":1},"statistics":[{"type":"Corner Kicks","value":5},{"type":"Yellow Cards","value":2},
				    {"type":"Red Cards","value":null},{"type":"Shots on Goal","value":5}]},
				  {"team":{"id":2},"statistics":[{"type":"Corner Kicks","value":1},{"type":"Yellow Cards","value":3},
				    {"type":"Red Cards","value":null},{"type":"Shots on Goal","value":5}]}]}"""));
		assertThat(stats).hasSize(2);
		assertThat(stats.get(0).corners()).isEqualTo(5);
		assertThat(stats.get(0).rojas()).isZero();
		assertThat(stats.get(1).amarillas()).isEqualTo(3);
	}
}
