package pe.justaapuesta.salud;

import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * El frontend lo llama al abrir la app: si Render está dormido, muestra "Despertando el servidor…".
 */
@RestController
@RequiredArgsConstructor
public class SaludController {

	private final JdbcTemplate jdbc;

	public record Salud(String estado, boolean baseDeDatos, Instant hora) {
	}

	@GetMapping("/api/health")
	public Salud salud() {
		boolean bd;
		try {
			bd = Integer.valueOf(1).equals(jdbc.queryForObject("select 1", Integer.class));
		} catch (RuntimeException e) {
			bd = false;
		}
		return new Salud(bd ? "ok" : "sin_base_de_datos", bd, Instant.now());
	}
}
