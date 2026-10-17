package pe.justaapuesta.partido;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import pe.justaapuesta.config.JustaProperties;

@RestController
@RequiredArgsConstructor
public class LigasController {

	private final JustaProperties props;

	/** Ligas configuradas (para los chips de "Ligas favoritas"). */
	@GetMapping("/api/ligas")
	public List<String> ligas() {
		return props.sync().ligas().stream().map(JustaProperties.Liga::nombre).toList();
	}
}
