package pe.justaapuesta;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import pe.justaapuesta.salud.SaludController;

/**
 * Arranca la app con H2 en modo MySQL y aplica las mismas migraciones de Flyway que en MySQL.
 */
@SpringBootTest
@ActiveProfiles("test")
class JustaApuestaApplicationTests {

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private SaludController salud;

	@Test
	void migracionesCreanTodasLasTablas() {
		List<String> tablas = jdbc.queryForList(
				"select table_name from information_schema.tables where table_schema = 'public'", String.class);

		assertThat(tablas).contains("usuarios", "ligas", "equipos", "partidos", "estadisticas_partido",
				"jugadores_clave", "favoritos", "ligas_favoritas", "picks_seguidos", "analisis_cache");
	}

	@Test
	void saludRespondeOkConBaseDeDatos() {
		assertThat(salud.salud().estado()).isEqualTo("ok");
	}
}
