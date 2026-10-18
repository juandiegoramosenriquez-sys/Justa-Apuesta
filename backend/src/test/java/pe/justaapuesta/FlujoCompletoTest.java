package pe.justaapuesta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.jayway.jsonpath.JsonPath;

import pe.justaapuesta.partido.CatalogoService;
import pe.justaapuesta.partido.Partido;
import pe.justaapuesta.partido.PartidoRepository;
import pe.justaapuesta.picks.ResolucionPicks;
import pe.justaapuesta.proveedor.FootballDataProvider;
import pe.justaapuesta.sync.SincronizacionService;

/**
 * Flujo completo con un proveedor falso que imita el plan gratis: sincronización por fechas y estadísticas,
 * búsqueda sin gastar consultas, análisis, favoritos, seguir un pick y resolverlo cuando termina el partido.
 */
@SpringBootTest(properties = {
		"justa.sync.programado=false",
		// En el test se baja todo el historial y todas las estadísticas en una sola ejecución
		"justa.sync.dias-atras=3",
		"justa.sync.dias-adelante=7",
		"justa.sync.dias-historial=75",
		"justa.sync.dias-historial-por-ejecucion=80",
		"justa.sync.estadisticas-por-ejecucion=200" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FlujoCompletoTest {

	@TestConfiguration
	static class Config {
		@Bean
		@Primary
		FakeProvider fakeProvider() {
			return new FakeProvider();
		}
	}

	@Autowired
	private MockMvc mvc;
	@Autowired
	private FakeProvider fake;
	@Autowired
	private PartidoRepository partidoRepository;
	@Autowired
	private CatalogoService catalogo;
	@Autowired
	private ResolucionPicks resolucion;
	@Autowired
	private SincronizacionService sync;
	@Autowired
	private FootballDataProvider proveedor;

	private String token;

	@BeforeEach
	void usuario() throws Exception {
		fake.fallar = false;
		String email = "flujo" + System.nanoTime() + "@test.pe";
		String r = mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Flujo\",\"email\":\"%s\",\"password\":\"secreta1\",\"modo\":\"EQUILIBRADO\"}"
						.formatted(email)))
				.andReturn().getResponse().getContentAsString();
		token = JsonPath.read(r, "$.token");
	}

	private ResultActions get(String url) throws Exception {
		return mvc.perform(MockMvcRequestBuilders.get(url).header("Authorization", "Bearer " + token));
	}

	@Test
	void flujoCompleto() throws Exception {
		assertThat(proveedor).isSameAs(fake);

		// 1. La sincronización baja los partidos día por día y las estadísticas de cada jugado
		assertThat(sync.diaria()).isTrue();
		assertThat(partidoRepository.count()).isGreaterThanOrEqualTo(30);

		// 2. Buscar "atletico" (sin tilde) no gasta consultas y devuelve próximos y jugados
		int consultas = fake.consultas.get();
		String lista = get("/api/buscar?q=atletico").andExpect(status().isOk())
				.andExpect(jsonPath("$.partidos[*].local.nombre", hasItem("Atlético Madrid")))
				.andReturn().getResponse().getContentAsString();
		assertThat(fake.consultas.get()).isEqualTo(consultas);
		List<Map<String, Object>> partidos = JsonPath.read(lista, "$.partidos");
		assertThat(partidos).hasSizeGreaterThanOrEqualTo(10);
		assertThat(partidos.get(0).get("estado")).isEqualTo("PROXIMO");
		assertThat(partidos.get(partidos.size() - 1).get("estado")).isEqualTo("JUGADO");

		// 3. Detalle del Atlético - Real Madrid: baja el H2H real (sin los de hace más de 2 años)
		Long idPartido = partidoRepository.findByProveedorIdIn(List.of(FakeProvider.ATLETICO * 1000 + 100)).get(0).getId();
		String det = get("/api/partidos/" + idPartido + "/analisis").andExpect(status().isOk())
				.andExpect(jsonPath("$.datosSuficientes").value(true))
				.andExpect(jsonPath("$.partido.local.nombre").value("Atlético Madrid"))
				.andExpect(jsonPath("$.partido.visita.nombre").value("Real Madrid"))
				.andExpect(jsonPath("$.h2h.n").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)))
				.andExpect(jsonPath("$.categorias.length()").value(8))
				.andExpect(jsonPath("$.consejo.clave").exists())
				.andExpect(jsonPath("$.consejo.cuota").isNumber())
				.andExpect(jsonPath("$.categorias[0].mercados[0].pruebas.length()").value(5))
				.andExpect(jsonPath("$.tambien.length()").value(2))
				.andExpect(jsonPath("$.evita.length()").value(2))
				// El plan gratis no trae jugadores: la sección se oculta (no se inventa)
				.andExpect(jsonPath("$.jugadores.length()").value(0))
				.andReturn().getResponse().getContentAsString();
		assertThat(partidoRepository.findByProveedorIdIn(List.of(999_999L))).isEmpty();
		int h = JsonPath.read(det, "$.ganador.h"), d = JsonPath.read(det, "$.ganador.d"), a = JsonPath.read(det, "$.ganador.a");
		assertThat(h + d + a).isEqualTo(100);
		assertThat((String) JsonPath.read(det, "$.rachaLocal")).hasSize(5);
		int pctConsejo = JsonPath.read(det, "$.consejo.pct");
		assertThat(pctConsejo).isBetween(62, 88); // modo Equilibrado
		String clave = JsonPath.read(det, "$.consejo.clave");

		// La lista "Próximos" (ligas configuradas) incluye el partido
		get("/api/partidos?estado=PROXIMO").andExpect(status().isOk())
				.andExpect(jsonPath("$.partidos[*].id", hasItem(idPartido.intValue())));

		// 4. Últimos 5 del equipo (rachas tocables)
		Long idAtletico = ((Number) JsonPath.read(det, "$.partido.local.id")).longValue();
		get("/api/equipos/" + idAtletico + "/ultimos?n=5").andExpect(status().isOk())
				.andExpect(jsonPath("$.partidos.length()").value(5));

		// 5. Favorito
		mvc.perform(post("/api/favoritos/" + idPartido).header("Authorization", "Bearer " + token))
				.andExpect(status().isNoContent());
		get("/api/favoritos").andExpect(jsonPath("$.partidos[0].id").value(idPartido))
				.andExpect(jsonPath("$.partidos[0].favorito").value(true));

		// 6. Seguir el consejo
		mvc.perform(post("/api/picks-seguidos").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"partidoId\":%d,\"mercadoClave\":\"%s\"}".formatted(idPartido, clave)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.estado").value("PENDIENTE"));
		get("/api/partidos/" + idPartido + "/analisis").andExpect(jsonPath("$.siguiendo").value(true));
		get("/api/me").andExpect(jsonPath("$.estadisticas.seguidos").value(1));

		// 6b. Compartir el boleto: el link se ve sin sesión
		String boleto = mvc.perform(post("/api/boletos").header("Authorization", "Bearer " + token))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.picks.length()").value(1))
				.andReturn().getResponse().getContentAsString();
		String codigo = JsonPath.read(boleto, "$.codigo");
		mvc.perform(MockMvcRequestBuilders.get("/api/publico/boletos/" + codigo)).andExpect(status().isOk())
				.andExpect(jsonPath("$.picks[0].pct").value(pctConsejo))
				.andExpect(jsonPath("$.cuota").isNumber());
		mvc.perform(MockMvcRequestBuilders.get("/api/publico/boletos/noexiste")).andExpect(status().isNotFound());

		// 7. Picks de los próximos días
		get("/api/picks?estado=PROXIMO").andExpect(status().isOk())
				.andExpect(jsonPath("$.combinadas").isArray())
				.andExpect(jsonPath("$.mejores").isNotEmpty());

		// 8. El partido termina 3-1 con estadísticas: el pick se resuelve y el análisis marca ✓/✗
		Partido p = partidoRepository.findById(idPartido).orElseThrow();
		catalogo.guardarPartidos(List.of(FakeProvider.partido(p.getProveedorId(), Instant.now().minusSeconds(3 * 3600),
				"FT", FakeProvider.ATLETICO, FakeProvider.REAL_MADRID, 3, 1)));
		catalogo.guardarEstadisticas(idPartido, FakeProvider.stats(p.getProveedorId(), FakeProvider.ATLETICO,
				FakeProvider.REAL_MADRID));
		assertThat(resolucion.resolver()).isEqualTo(1);
		get("/api/picks-seguidos").andExpect(jsonPath("$[0].estado").value(org.hamcrest.Matchers.oneOf("ACERTADO", "FALLADO")));
		get("/api/partidos/" + idPartido + "/analisis")
				.andExpect(jsonPath("$.resultadoFinal.golesLocal").value(3))
				.andExpect(jsonPath("$.consejo.acerto").isBoolean())
				.andExpect(jsonPath("$.categorias[0].mercados[0].salio").value(true));

		// 9. Si el proveedor falla (cuota agotada), el detalle se muestra con lo guardado y un aviso suave.
		//    (Un partido jugado al que le faltan estadísticas: hay que pedirlas y el proveedor falla.)
		Partido sinStats = partidoRepository.findByProveedorIdIn(List.of(FakeProvider.SOCIEDAD * 1000)).get(0);
		sinStats.setDetalleSync(false);
		partidoRepository.save(sinStats);
		fake.fallar = true;
		get("/api/partidos/" + sinStats.getId() + "/analisis").andExpect(status().isOk())
				.andExpect(jsonPath("$.aviso").isNotEmpty());
		get("/api/buscar?q=sociedad").andExpect(status().isOk()).andExpect(jsonPath("$.partidos").isNotEmpty());
	}

	@Test
	void busquedaCortaYSinSesion() throws Exception {
		get("/api/buscar?q=ab").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("Escribe al menos 3 letras."));
		mvc.perform(MockMvcRequestBuilders.get("/api/partidos?estado=HOY")).andExpect(status().isUnauthorized());
		get("/api/partidos?estado=HOY").andExpect(status().isOk()).andExpect(jsonPath("$.partidos").isArray());
	}
}
