package pe.justaapuesta.security;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import lombok.RequiredArgsConstructor;
import pe.justaapuesta.config.JustaProperties;

/**
 * API sin sesión de servidor: cada petición trae su JWT. Todo /api requiere login excepto auth y health.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtFiltro jwtFiltro;
	private final JustaProperties props;

	@Bean
	public SecurityFilterChain filtros(HttpSecurity http) throws Exception {
		return http
				.csrf(AbstractHttpConfigurer::disable)
				.cors(cors -> cors.configurationSource(cors()))
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(a -> a
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers("/api/auth/**", "/api/health", "/error").permitAll()
						// Boletos compartidos por link: los ve cualquiera
						.requestMatchers(HttpMethod.GET, "/api/publico/**").permitAll()
						// Protegido con X-Admin-Key (ver AdminController), no con JWT
						.requestMatchers(HttpMethod.POST, "/api/admin/sync").permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(e -> e.authenticationEntryPoint(noAutenticado()))
				.addFilterBefore(jwtFiltro, UsernamePasswordAuthenticationFilter.class)
				.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/**
	 * El login es propio (AuthService + JWT). Este bean evita que Spring cree un usuario
	 * por defecto e imprima su contraseña en los logs.
	 */
	@Bean
	public UserDetailsService sinUsuarioPorDefecto() {
		return username -> {
			throw new UsernameNotFoundException("No se usa");
		};
	}

	/** 401 con el mismo formato JSON que el resto de errores. */
	private AuthenticationEntryPoint noAutenticado() {
		return (request, response, ex) -> {
			response.setStatus(401);
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.setCharacterEncoding(StandardCharsets.UTF_8.name());
			response.getWriter().write("{\"mensaje\":\"Tu sesión expiró. Vuelve a iniciar sesión.\"}");
		};
	}

	/** Frontend web y APK de Capacitor. */
	private CorsConfigurationSource cors() {
		CorsConfiguration c = new CorsConfiguration();
		c.setAllowedOrigins(List.of(props.frontendUrl(), "capacitor://localhost", "http://localhost", "https://localhost"));
		c.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE"));
		c.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
		fuente.registerCorsConfiguration("/api/**", c);
		return fuente;
	}
}
