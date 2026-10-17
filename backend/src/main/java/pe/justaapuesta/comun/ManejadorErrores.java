package pe.justaapuesta.comun;

import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Todos los errores salen como JSON en español: {"mensaje": "..."}.
 */
@RestControllerAdvice
public class ManejadorErrores {

	/** Si hay varios campos mal, se avisa en el mismo orden que el formulario. */
	private static final List<String> ORDEN_CAMPOS = List.of("nombre", "email", "password");

	public record ErrorRespuesta(String mensaje) {
	}

	@ExceptionHandler(NoEncontradoException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ErrorRespuesta noEncontrado(NoEncontradoException ex) {
		return new ErrorRespuesta(ex.getMessage());
	}

	@ExceptionHandler(ConflictoException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ErrorRespuesta conflicto(ConflictoException ex) {
		return new ErrorRespuesta(ex.getMessage());
	}

	@ExceptionHandler(NoAutorizadoException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public ErrorRespuesta noAutorizado(NoAutorizadoException ex) {
		return new ErrorRespuesta(ex.getMessage());
	}

	@ExceptionHandler(SolicitudInvalidaException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorRespuesta solicitudInvalida(SolicitudInvalidaException ex) {
		return new ErrorRespuesta(ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorRespuesta validacion(MethodArgumentNotValidException ex) {
		String mensaje = ex.getBindingResult().getFieldErrors().stream()
				.min(Comparator.comparingInt(ManejadorErrores::orden))
				.map(FieldError::getDefaultMessage)
				.orElse("Datos inválidos.");
		return new ErrorRespuesta(mensaje);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorRespuesta jsonInvalido(HttpMessageNotReadableException ex) {
		return new ErrorRespuesta("Los datos enviados no son válidos.");
	}

	private static int orden(FieldError e) {
		int i = ORDEN_CAMPOS.indexOf(e.getField());
		return i < 0 ? ORDEN_CAMPOS.size() : i;
	}
}
