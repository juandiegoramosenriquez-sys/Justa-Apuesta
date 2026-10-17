package pe.justaapuesta.comun;

/** 409: la acción choca con datos que ya existen (por ejemplo, un correo ya registrado). */
public class ConflictoException extends RuntimeException {

	public ConflictoException(String mensaje) {
		super(mensaje);
	}
}
