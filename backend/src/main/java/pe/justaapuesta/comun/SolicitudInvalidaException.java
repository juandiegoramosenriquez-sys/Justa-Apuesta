package pe.justaapuesta.comun;

/** 400: datos que pasan la validación básica pero no son aceptables. */
public class SolicitudInvalidaException extends RuntimeException {

	public SolicitudInvalidaException(String mensaje) {
		super(mensaje);
	}
}
