package pe.justaapuesta.usuario;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import pe.justaapuesta.usuario.UsuarioDtos.ActualizarMeRequest;
import pe.justaapuesta.usuario.UsuarioDtos.MeDto;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

	private final UsuarioService usuarioService;

	@GetMapping
	public MeDto me(@AuthenticationPrincipal Long usuarioId) {
		return usuarioService.me(usuarioId);
	}

	@PatchMapping
	public MeDto actualizar(@AuthenticationPrincipal Long usuarioId, @Valid @RequestBody ActualizarMeRequest req) {
		return usuarioService.actualizar(usuarioId, req);
	}
}
