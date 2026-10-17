package pe.justaapuesta.proveedor;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Respeta los límites del plan: espera si ya se hicieron N consultas en el último minuto,
 * y deja de consultar cuando quedan pocas en el día.
 */
public class LimitadorConsultas {

	private static final long MINUTO_MS = 60_000;

	private final int maxPorMinuto;
	private final int reservaDiaria;
	private final Deque<Long> ultimas = new ArrayDeque<>();
	/** Lo que el proveedor dijo que quedaba hoy; null hasta la primera respuesta. */
	private Integer restantesHoy;

	public LimitadorConsultas(int maxPorMinuto, int reservaDiaria) {
		this.maxPorMinuto = maxPorMinuto;
		this.reservaDiaria = reservaDiaria;
	}

	public synchronized void antesDeConsultar() {
		if (restantesHoy != null && restantesHoy <= reservaDiaria) {
			throw new ProveedorException("Se acabó la cuota diaria del proveedor de datos.");
		}
		long ahora = System.currentTimeMillis();
		while (!ultimas.isEmpty() && ahora - ultimas.peekFirst() >= MINUTO_MS) {
			ultimas.pollFirst();
		}
		if (ultimas.size() >= maxPorMinuto) {
			long espera = MINUTO_MS - (ahora - ultimas.peekFirst()) + 200;
			try {
				Thread.sleep(espera);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new ProveedorException("Consulta interrumpida.");
			}
			ultimas.pollFirst();
		}
		ultimas.addLast(System.currentTimeMillis());
	}

	synchronized void registrarRestantes(Integer restantes) {
		if (restantes != null) {
			restantesHoy = restantes;
		}
	}

	synchronized Integer restantesHoy() {
		return restantesHoy;
	}
}
