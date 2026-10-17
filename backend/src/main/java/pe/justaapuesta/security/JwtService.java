package pe.justaapuesta.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import pe.justaapuesta.config.JustaProperties;

/**
 * Crea y verifica los tokens de sesión (HS256). El token solo lleva el id del usuario.
 */
@Service
public class JwtService {

	private final SecretKey clave;
	private final int expiracionDias;

	public JwtService(JustaProperties props) {
		this.clave = Keys.hmacShaKeyFor(props.jwt().secreto().getBytes(StandardCharsets.UTF_8));
		this.expiracionDias = props.jwt().expiracionDias();
	}

	public String crear(Long usuarioId) {
		Instant ahora = Instant.now();
		return Jwts.builder()
				.subject(usuarioId.toString())
				.issuedAt(Date.from(ahora))
				.expiration(Date.from(ahora.plus(expiracionDias, ChronoUnit.DAYS)))
				.signWith(clave)
				.compact();
	}

	/** El id del usuario si el token es válido y no expiró. */
	public Optional<Long> usuarioId(String token) {
		try {
			String subject = Jwts.parser().verifyWith(clave).build()
					.parseSignedClaims(token)
					.getPayload()
					.getSubject();
			return Optional.of(Long.valueOf(subject));
		} catch (JwtException | IllegalArgumentException e) {
			return Optional.empty();
		}
	}
}
