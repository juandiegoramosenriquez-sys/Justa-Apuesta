package pe.justaapuesta.auth;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Test
	void registroDevuelveTokenYUsuario() throws Exception {
		registrar("Diego Ramos", "Diego@Correo.com", "secreta1", "EQUILIBRADO")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.token").value(not(emptyString())))
				.andExpect(jsonPath("$.usuario.nombre").value("Diego Ramos"))
				.andExpect(jsonPath("$.usuario.email").value("diego@correo.com"))
				.andExpect(jsonPath("$.usuario.modo").value("EQUILIBRADO"))
				.andExpect(jsonPath("$.usuario.tipVisto").value(false))
				.andExpect(jsonPath("$.usuario.estadisticas.seguidos").value(0));
	}

	@Test
	void registroSinModoUsaSeguro() throws Exception {
		mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Ana\",\"email\":\"ana@correo.com\",\"password\":\"secreta1\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.usuario.modo").value("SEGURO"));
	}

	@Test
	void validacionesConMensajesDelPrototipo() throws Exception {
		registrar("", "malcorreo", "123", "SEGURO")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("Escribe tu nombre."));
		registrar("Ana", "malcorreo", "123", "SEGURO")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("Escribe un correo válido."));
		registrar("Ana", "ana@correo.com", "123", "SEGURO")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("La contraseña debe tener al menos 6 caracteres."));
	}

	@Test
	void correoRepetidoDa409() throws Exception {
		registrar("Ana", "ana@correo.com", "secreta1", "SEGURO").andExpect(status().isCreated());
		registrar("Otra Ana", "ANA@correo.com", "secreta2", "SEGURO")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.mensaje").value("Ya existe una cuenta con ese correo. Inicia sesión."));
	}

	@Test
	void loginCorrectoEIncorrecto() throws Exception {
		registrar("Ana", "ana@correo.com", "secreta1", "SEGURO").andExpect(status().isCreated());

		login("ana@correo.com", "secreta1")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").value(not(emptyString())));
		login("ana@correo.com", "otraclave")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.mensaje").value("Correo o contraseña incorrectos."));
		login("nadie@correo.com", "secreta1")
				.andExpect(status().isUnauthorized());
	}

	@Test
	void meRequiereTokenValido() throws Exception {
		mvc.perform(get("/api/me")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.mensaje").value("Tu sesión expiró. Vuelve a iniciar sesión."));
		mvc.perform(get("/api/me").header("Authorization", "Bearer token-falso"))
				.andExpect(status().isUnauthorized());

		String token = tokenDe(registrar("Ana", "ana@correo.com", "secreta1", "SEGURO").andReturn());
		mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("ana@correo.com"));
	}

	@Test
	void actualizarModoTipYLigasFavoritas() throws Exception {
		String token = tokenDe(registrar("Ana", "ana@correo.com", "secreta1", "SEGURO").andReturn());

		mvc.perform(patch("/api/me").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"modo\":\"ARRIESGADO\",\"tipVisto\":true,\"ligasFavoritas\":[\"Liga 1 Perú\",\"LaLiga\"]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.modo").value("ARRIESGADO"))
				.andExpect(jsonPath("$.tipVisto").value(true))
				.andExpect(jsonPath("$.ligasFavoritas[0]").value("Liga 1 Perú"))
				.andExpect(jsonPath("$.ligasFavoritas[1]").value("LaLiga"));
	}

	@Test
	void tokenDeCuentaBorradaDa401() throws Exception {
		String token = tokenDe(registrar("Ana", "ana@correo.com", "secreta1", "SEGURO").andReturn());
		jdbc.update("delete from usuarios where email = 'ana@correo.com'");
		entityManager.clear(); // el test corre en una sola transacción: olvidar la cuenta ya cargada
		mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.mensaje").value("Tu sesión expiró. Vuelve a iniciar sesión."));
	}

	@Autowired
	private org.springframework.jdbc.core.JdbcTemplate jdbc;

	@jakarta.persistence.PersistenceContext
	private jakarta.persistence.EntityManager entityManager;

	@Test
	void healthEsPublico() throws Exception {
		mvc.perform(get("/api/health")).andExpect(status().isOk());
	}

	private org.springframework.test.web.servlet.ResultActions registrar(String nombre, String email, String password,
			String modo) throws Exception {
		return mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nombre":"%s","email":"%s","password":"%s","modo":"%s"}""".formatted(nombre, email, password, modo)));
	}

	private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
		return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s"}""".formatted(email, password)));
	}

	private static String tokenDe(MvcResult r) throws Exception {
		return JsonPath.read(r.getResponse().getContentAsString(), "$.token");
	}
}
