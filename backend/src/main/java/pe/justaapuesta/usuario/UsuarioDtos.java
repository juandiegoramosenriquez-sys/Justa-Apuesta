package pe.justaapuesta.usuario;

import java.util.List;

import jakarta.validation.constraints.Size;

public final class UsuarioDtos {

	private UsuarioDtos() {
	}

	public record EstadisticasPicks(long seguidos, long acertados, int porcentajeAcierto) {
	}

	public record MeDto(Long id, String nombre, String email, Modo modo, boolean tipVisto,
			List<String> ligasFavoritas, EstadisticasPicks estadisticas, Tema tema, Acento acento) {
	}

	/** Todos los campos son opcionales: solo se cambia lo que venga. */
	public record ActualizarMeRequest(
			Modo modo,
			Boolean tipVisto,
			Tema tema,
			Acento acento,
			@Size(max = 30, message = "Demasiadas ligas favoritas.") List<@Size(max = 100) String> ligasFavoritas) {
	}
}
