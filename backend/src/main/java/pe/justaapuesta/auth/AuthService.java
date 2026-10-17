package pe.justaapuesta.auth;

import java.time.Instant;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import pe.justaapuesta.auth.AuthDtos.AuthRespuesta;
import pe.justaapuesta.auth.AuthDtos.LoginRequest;
import pe.justaapuesta.auth.AuthDtos.RegistroRequest;
import pe.justaapuesta.comun.ConflictoException;
import pe.justaapuesta.comun.NoAutorizadoException;
import pe.justaapuesta.comun.SolicitudInvalidaException;
import pe.justaapuesta.security.JwtService;
import pe.justaapuesta.usuario.Modo;
import pe.justaapuesta.usuario.Usuario;
import pe.justaapuesta.usuario.UsuarioRepository;
import pe.justaapuesta.usuario.UsuarioService;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

	/** BCrypt solo usa los primeros 72 bytes de la contraseña. */
	private static final int MAX_BYTES_CLAVE = 72;

	private final UsuarioRepository usuarioRepository;
	private final UsuarioService usuarioService;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public AuthRespuesta registrar(RegistroRequest req) {
		String email = normalizar(req.email());
		if (req.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES_CLAVE) {
			throw new SolicitudInvalidaException("La contraseña es demasiado larga (máximo 72 caracteres).");
		}
		if (usuarioRepository.existsByEmail(email)) {
			throw new ConflictoException("Ya existe una cuenta con ese correo. Inicia sesión.");
		}

		Usuario u = new Usuario();
		u.setNombre(req.nombre().trim());
		u.setEmail(email);
		u.setPasswordHash(passwordEncoder.encode(req.password()));
		u.setModo(req.modo() == null ? Modo.SEGURO : req.modo());
		u.setCreadoEn(Instant.now());
		usuarioRepository.save(u);

		return new AuthRespuesta(jwtService.crear(u.getId()), usuarioService.me(u.getId()));
	}

	@Transactional(readOnly = true)
	public AuthRespuesta login(LoginRequest req) {
		Usuario u = usuarioRepository.findByEmail(normalizar(req.email()))
				.filter(x -> passwordEncoder.matches(req.password(), x.getPasswordHash()))
				.orElseThrow(() -> new NoAutorizadoException("Correo o contraseña incorrectos."));
		return new AuthRespuesta(jwtService.crear(u.getId()), usuarioService.me(u.getId()));
	}

	private static String normalizar(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
