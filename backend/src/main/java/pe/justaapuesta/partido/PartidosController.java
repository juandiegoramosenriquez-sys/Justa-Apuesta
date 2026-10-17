package pe.justaapuesta.partido;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import pe.justaapuesta.analisis.AnalisisDtos.AnalisisDto;
import pe.justaapuesta.analisis.AnalisisDtos.ListaPartidosDto;
import pe.justaapuesta.analisis.AnalisisDtos.UltimosDto;
import pe.justaapuesta.analisis.AnalisisService;
import pe.justaapuesta.analisis.Entradas;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PartidosController {

	private final PartidosService partidos;
	private final AnalisisService analisis;

	/** Pestañas Jugados / Hoy / Próximos. Cada partido trae el consejo principal según el modo del usuario. */
	@GetMapping("/partidos")
	public ListaPartidosDto listar(@RequestParam(defaultValue = "HOY") PartidosService.Pestana estado,
			@AuthenticationPrincipal Long usuarioId) {
		return partidos.listar(estado, usuarioId);
	}

	/** Búsqueda total de cualquier equipo o liga: jugados, hoy y próximos. */
	@GetMapping("/buscar")
	public ListaPartidosDto buscar(@RequestParam String q, @AuthenticationPrincipal Long usuarioId) {
		return partidos.buscar(q, usuarioId);
	}

	/** Detalle completo del partido. */
	@GetMapping("/partidos/{id}/analisis")
	public AnalisisDto analisis(@PathVariable Long id, @AuthenticationPrincipal Long usuarioId) {
		return analisis.detalle(id, usuarioId);
	}

	/** Últimos 6 enfrentamientos reales entre ambos equipos y su resumen (null si no hay). */
	@GetMapping("/partidos/{id}/h2h")
	public Entradas.H2h h2h(@PathVariable Long id, @AuthenticationPrincipal Long usuarioId) {
		return analisis.detalle(id, usuarioId).h2h();
	}

	@GetMapping("/equipos/{id}/ultimos")
	public UltimosDto ultimos(@PathVariable Long id, @RequestParam(defaultValue = "5") int n) {
		return partidos.ultimos(id, n);
	}
}
