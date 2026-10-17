package pe.justaapuesta.proveedor;

/** El proveedor no respondió, rechazó la consulta o se acabó la cuota. La app sigue con la caché. */
public class ProveedorException extends RuntimeException {

	public ProveedorException(String mensaje) {
		super(mensaje);
	}

	public ProveedorException(String mensaje, Throwable causa) {
		super(mensaje, causa);
	}
}
