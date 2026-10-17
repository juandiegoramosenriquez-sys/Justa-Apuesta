package pe.justaapuesta.comun;

/** 401: credenciales incorrectas. */
public class NoAutorizadoException extends RuntimeException {

	public NoAutorizadoException(String mensaje) {
		super(mensaje);
	}
}
