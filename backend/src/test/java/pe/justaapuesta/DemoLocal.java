package pe.justaapuesta;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * SOLO PARA DESARROLLO: arranca la app con el proveedor falso de los tests (equipos de prueba de LaLiga)
 * para revisar las pantallas sin gastar consultas de API-Football. No forma parte del build de producción.
 *
 * Uso: mvnw spring-boot:test-run -Dspring-boot.run.main-class=pe.justaapuesta.DemoLocal
 * Busca "atletico" en la app para cargar los datos de prueba.
 */
public class DemoLocal {

	public static void main(String[] args) {
		SpringApplication.from(JustaApuestaApplication::main).with(Config.class).run(args);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class Config {
		@Bean
		@Primary
		FakeProvider proveedorDemo() {
			return new FakeProvider();
		}
	}
}
