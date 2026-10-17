package pe.justaapuesta.sync;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
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

	@PostMapping("/sync")
	public ResponseEntity<?> sincronizar(@RequestHeader(value = "X-Admin-Key", required = false) String clave) {
		String esperada = props.admin().clave();
		if (esperada == null || esperada.isBlank()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(new ErrorRespuesta("La sincronización manual está desactivada (falta ADMIN_SYNC_KEY)."));
		}
		if (clave == null || !MessageDigest.isEqual(clave.getBytes(StandardCharsets.UTF_8),
				esperada.getBytes(StandardCharsets.UTF_8))) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorRespuesta("Clave incorrecta."));
		}
		if (sync.estaEnCurso()) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorRespuesta("Ya hay una sincronización en curso."));
		}
		CompletableFuture.runAsync(sync::diaria);
		return ResponseEntity.accepted().body(new ErrorRespuesta("Sincronización iniciada."));
	}
}
