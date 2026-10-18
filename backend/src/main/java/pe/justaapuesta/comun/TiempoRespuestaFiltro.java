package pe.justaapuesta.comun;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Anota en el log las respuestas que tardan más de 1 segundo, para encontrar lo que hace lenta la web. */
@Component
public class TiempoRespuestaFiltro extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(TiempoRespuestaFiltro.class);
	private static final long LENTO_MS = 1000;

	@Override
	protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
			throws ServletException, IOException {
		long inicio = System.currentTimeMillis();
		try {
			chain.doFilter(req, res);
		} finally {
			long ms = System.currentTimeMillis() - inicio;
			if (ms > LENTO_MS) {
				String q = req.getQueryString();
				log.info("Respuesta lenta: {} {}{} tardó {} ms", req.getMethod(), req.getRequestURI(),
						q == null ? "" : "?" + q, ms);
			}
		}
	}
}
