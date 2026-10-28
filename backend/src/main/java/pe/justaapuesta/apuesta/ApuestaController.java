package pe.justaapuesta.apuesta;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** "Analizar mi apuesta": la probabilidad de un boleto armado en una casa de apuestas. */
@RestController
@RequiredArgsConstructor
public class ApuestaController {

	private final ApuestaService apuestas;

	@PostMapping("/api/apuesta/analizar")
	public ApuestaService.AnalisisApuestaDto analizar(@RequestBody ApuestaService.PedidoDto pedido) {
		return apuestas.analizar(pedido.texto());
	}
}
