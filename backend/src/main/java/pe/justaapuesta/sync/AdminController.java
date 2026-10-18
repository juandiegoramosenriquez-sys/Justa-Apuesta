package pe.justaapuesta.sync;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import pe.justaapuesta.analisis.PrecisionService;
import pe.justaapuesta.comun.ManejadorErrores.ErrorRespuesta;
import pe.justaapuesta.config.JustaProperties;

/**
 * POST /api/admin/sync con la cabecera X-Admin-Key: lanza la sincronización diaria en segundo plano.
 * Pensado para un cron externo gratuito (Render dormido no corre los @Scheduled).
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

	private final SincronizacionService sync;
	private final JustaProperties props;
	private final PrecisionService precision;

	@PostMapping("/sync")
	public ResponseEntity<?> sincronizar(@RequestHeader(value = "X-Admin-Key", required = false) String clave) {
		ResponseEntity<?> sinPermiso = sinPermiso(clave);
		if (sinPermiso != null) {
			return sinPermiso;
		}
		if (sync.estaEnCurso()) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorRespuesta("Ya hay una sincronización en curso."));
		}
		CompletableFuture.runAsync(sync::diaria);
		return ResponseEntity.accepted().body(new ErrorRespuesta("Sincronización iniciada."));
	}

	/** Mide ya el acierto real del modelo en los jugados de los últimos días (tarda; responde el detalle). */
	@PostMapping("/precision")
	public ResponseEntity<?> precision(@RequestHeader(value = "X-Admin-Key", required = false) String clave,
			@RequestParam(defaultValue = "30") int dias, @RequestParam(defaultValue = "400") int maximo,
			@RequestParam(defaultValue = "false") boolean muestras) {
		ResponseEntity<?> sinPermiso = sinPermiso(clave);
		if (sinPermiso != null) {
			return sinPermiso;
		}
		return ResponseEntity.ok(precision.medir(Math.min(dias, 120), Math.min(maximo, 2000), muestras));
	}

	private ResponseEntity<?> sinPermiso(String clave) {
		String esperada = props.admin().clave();
		if (esperada == null || esperada.isBlank()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(new ErrorRespuesta("La sincronización manual está desactivada (falta ADMIN_SYNC_KEY)."));
		}
		if (clave == null || !MessageDigest.isEqual(clave.getBytes(StandardCharsets.UTF_8),
				esperada.getBytes(StandardCharsets.UTF_8))) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorRespuesta("Clave incorrecta."));
		}
		return null;
	}
}
