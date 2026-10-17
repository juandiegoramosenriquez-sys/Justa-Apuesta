package pe.justaapuesta.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Lee "Authorization: Bearer <token>" y, si es válido, deja al usuario autenticado.
 * El principal es el id del usuario (Long).
 */
@Component
@RequiredArgsConstructor
public class JwtFiltro extends OncePerRequestFilter {

	private static final String PREFIJO = "Bearer ";

	private final JwtService jwtService;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String cabecera = request.getHeader("Authorization");
		if (cabecera != null && cabecera.startsWith(PREFIJO)) {
			jwtService.usuarioId(cabecera.substring(PREFIJO.length())).ifPresent(id -> {
				var auth = new UsernamePasswordAuthenticationToken(id, null, List.of());
				SecurityContextHolder.getContext().setAuthentication(auth);
			});
		}
		chain.doFilter(request, response);
	}
}
