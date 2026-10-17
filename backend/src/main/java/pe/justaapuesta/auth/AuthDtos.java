package pe.justaapuesta.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.justaapuesta.usuario.Modo;
import pe.justaapuesta.usuario.UsuarioDtos.MeDto;

public final class AuthDtos {

	private AuthDtos() {
	}

	private static final String NOMBRE = "Escribe tu nombre.";
	private static final String CORREO = "Escribe un correo válido.";
	private static final String CLAVE = "La contraseña debe tener al menos 6 caracteres.";

	public record RegistroRequest(
			@NotBlank(message = NOMBRE) @Size(max = 80, message = NOMBRE) String nombre,
			@NotBlank(message = CORREO) @Email(message = CORREO) @Size(max = 120, message = CORREO) String email,
			@NotNull(message = CLAVE) @Size(min = 6, message = CLAVE) String password,
			/** Opcional: si no viene, SEGURO. */
			Modo modo) {
	}

	public record LoginRequest(
			@NotBlank(message = CORREO) @Email(message = CORREO) String email,
			@NotNull(message = CLAVE) @Size(min = 6, message = CLAVE) String password) {
	}

	public record AuthRespuesta(String token, MeDto usuario) {
	}
}
