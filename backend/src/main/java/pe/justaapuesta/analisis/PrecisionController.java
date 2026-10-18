package pe.justaapuesta.analisis;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** Acierto real del modelo en los partidos jugados de los últimos 30 días (para mostrarlo en la app). */
@RestController
@RequiredArgsConstructor
public class PrecisionController {

	private final PrecisionService precision;

	/** 204 si todavía no se midió (se mide en segundo plano al arrancar y 2 veces al día). */
	@GetMapping("/api/precision")
	public ResponseEntity<PrecisionService.Precision> precision() {
		PrecisionService.Precision p = precision.ultima();
		return p == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(p);
	}
}
