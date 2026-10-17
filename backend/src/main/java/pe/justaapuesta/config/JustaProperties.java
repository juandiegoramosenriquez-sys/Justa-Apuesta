package pe.justaapuesta.config;

import java.time.ZoneId;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración propia de la app (prefijo "justa" en application.yml).
 */
@ConfigurationProperties(prefix = "justa")
public record JustaProperties(String frontendUrl, String zonaHoraria, Jwt jwt, Proveedor proveedor,
		Historial historial, TheSportsDb theSportsDb, Admin admin, Sync sync) {

	/** football-data.org: historial de la temporada (gratis, sin estadísticas). */
	public record Historial(String url, String apiKey, int maxPorMinuto, List<Competicion> competiciones) {

		public boolean configurado() {
			return apiKey != null && !apiKey.isBlank();
		}
	}

	public record Competicion(String codigo, long ligaApiFootball) {
	}

	/** TheSportsDB: historial de goles de cualquier liga del mundo (gratis, sin registro). Clave vacía = desactivado. */
	public record TheSportsDb(String url, String apiKey, int maxPorMinuto, int equiposPorEjecucion,
			int ligasPorEjecucion, int rondas) {
	}

	public ZoneId zona() {
		return ZoneId.of(zonaHoraria);
	}

	/** Nombre a mostrar de una liga configurada ("Liga 1 Perú" en vez de "Primera División"). */
	public java.util.Optional<String> nombreLiga(long proveedorId) {
		return sync.ligas().stream().filter(l -> l.proveedorId() == proveedorId).map(Liga::nombre).findFirst();
	}

	public record Jwt(String secreto, int expiracionDias) {

		public Jwt {
			if (secreto == null || secreto.length() < 32) {
				throw new IllegalArgumentException("JWT_SECRET debe tener al menos 32 caracteres.");
			}
		}
	}

	public record Proveedor(String url, String apiKey, int maxPorMinuto, int reservaDiaria) {

		public boolean configurado() {
			return apiKey != null && !apiKey.isBlank();
		}
	}

	public record Admin(String clave) {
	}

	public record Sync(String cron, int diasAtras, int diasAdelante, int diasRetencion, int diasHistorial,
			int diasHistorialPorEjecucion, int estadisticasPorEjecucion, int estadisticasPorRefresco,
			int estadisticasPorDetalle, List<Liga> ligas) {

		public java.util.Set<Long> idsLigas() {
			return ligas.stream().map(Liga::proveedorId).collect(java.util.stream.Collectors.toSet());
		}
	}

	public record Liga(String nombre, String pais, long proveedorId) {
	}
}
