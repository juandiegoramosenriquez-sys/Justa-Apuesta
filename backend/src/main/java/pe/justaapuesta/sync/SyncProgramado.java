package pe.justaapuesta.sync;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import lombok.RequiredArgsConstructor;

/**
 * Tareas programadas (hora de Lima). No hace falta llamar a POST /api/admin/sync a mano:
 * si la PC estaba apagada a la hora del cron, los partidos del día se bajan solos al arrancar
 * o en la siguiente revisión (cada 3 horas).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "justa.sync.programado", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class SyncProgramado {

	private final SincronizacionService sync;

	@Scheduled(cron = "${justa.sync.cron}", zone = "America/Lima")
	public void diaria() {
		sync.diaria();
	}

	@Scheduled(cron = "0 0 */3 * * *", zone = "America/Lima")
	public void hoy() {
		if (sync.diariaHechaHoy()) {
			sync.hoy();
		} else {
			sync.diaria();
		}
		sync.historialExtra();
		sync.espn();
		sync.estadisticasCsv();
	}

	/** Al arrancar: si hoy todavía no se bajaron los partidos, se bajan ya (en segundo plano). */
	@EventListener(ApplicationReadyEvent.class)
	public void alArrancar() {
		Thread.ofVirtual().name("sync-al-arrancar").start(() -> {
			if (!sync.diariaHechaHoy() && !sync.intentadaHacePoco(java.time.Duration.ofHours(2))) {
				sync.diaria();
				sync.historialExtra();
			}
			sync.espn();
			sync.estadisticasCsv();
		});
	}
}
