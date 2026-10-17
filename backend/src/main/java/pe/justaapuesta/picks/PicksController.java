package pe.justaapuesta.picks;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import pe.justaapuesta.analisis.AnalisisDtos.ListaPartidosDto;
import pe.justaapuesta.partido.PartidosService;
import pe.justaapuesta.picks.PicksDtos.BoletoDto;
import pe.justaapuesta.picks.PicksDtos.PickSeguidoDto;
import pe.justaapuesta.picks.PicksDtos.PicksDelDiaDto;
import pe.justaapuesta.picks.PicksDtos.SeguirPickRequest;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PicksController {

	private final PicksService picks;
	private final BoletoService boletos;

	/** Combinada sugerida + top 8 picks de hoy o de los próximos días. */
	@GetMapping("/picks")
	public PicksDelDiaDto picks(@RequestParam(defaultValue = "HOY") PartidosService.Pestana estado,
			@AuthenticationPrincipal Long usuarioId) {
		return picks.delDia(estado, usuarioId);
	}

	@GetMapping("/favoritos")
	public ListaPartidosDto favoritos(@AuthenticationPrincipal Long usuarioId) {
		return picks.favoritos(usuarioId);
	}

	@PostMapping("/favoritos/{partidoId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void marcar(@PathVariable Long partidoId, @AuthenticationPrincipal Long usuarioId) {
		picks.marcarFavorito(usuarioId, partidoId);
	}

	@DeleteMapping("/favoritos/{partidoId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void quitar(@PathVariable Long partidoId, @AuthenticationPrincipal Long usuarioId) {
		picks.quitarFavorito(usuarioId, partidoId);
	}

	@GetMapping("/picks-seguidos")
	public List<PickSeguidoDto> seguidos(@AuthenticationPrincipal Long usuarioId) {
		return picks.seguidos(usuarioId);
	}

	@PostMapping("/picks-seguidos")
	@ResponseStatus(HttpStatus.CREATED)
	public PickSeguidoDto seguir(@Valid @RequestBody SeguirPickRequest req, @AuthenticationPrincipal Long usuarioId) {
		return picks.seguir(usuarioId, req.partidoId(), req.mercadoClave());
	}

	@DeleteMapping("/picks-seguidos")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void dejar(@RequestParam Long partidoId, @RequestParam String mercadoClave,
			@AuthenticationPrincipal Long usuarioId) {
		picks.dejarDeSeguir(usuarioId, partidoId, mercadoClave);
	}

	/** Quita del boleto todos los picks pendientes. */
	@DeleteMapping("/picks-seguidos/pendientes")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void vaciar(@AuthenticationPrincipal Long usuarioId) {
		picks.vaciarBoleto(usuarioId);
	}

	/** Comparte el boleto: devuelve el código para el link público. */
	@PostMapping("/boletos")
	@ResponseStatus(HttpStatus.CREATED)
	public BoletoDto compartir(@AuthenticationPrincipal Long usuarioId) {
		return boletos.compartir(usuarioId);
	}

	/** Boleto compartido: se ve sin iniciar sesión. */
	@GetMapping("/publico/boletos/{codigo}")
	public BoletoDto boleto(@PathVariable String codigo) {
		return boletos.ver(codigo);
	}
}
