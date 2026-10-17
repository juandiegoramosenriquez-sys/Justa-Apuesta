package pe.justaapuesta.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import pe.justaapuesta.auth.AuthDtos.AuthRespuesta;
import pe.justaapuesta.auth.AuthDtos.LoginRequest;
import pe.justaapuesta.auth.AuthDtos.RegistroRequest;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	@PostMapping("/registro")
	@ResponseStatus(HttpStatus.CREATED)
	public AuthRespuesta registro(@Valid @RequestBody RegistroRequest req) {
		return authService.registrar(req);
	}

	@PostMapping("/login")
	public AuthRespuesta login(@Valid @RequestBody LoginRequest req) {
		return authService.login(req);
	}
}
