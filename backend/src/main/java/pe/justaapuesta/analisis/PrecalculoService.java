package pe.justaapuesta.analisis;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import pe.justaapuesta.partido.Partido;
import pe.justaapuesta.partido.PartidosService;
import pe.justaapuesta.picks.PicksService;
import pe.justaapuesta.sync.DatosActualizadosEvent;

/**
 * Calcula de antemano el análisis de los partidos de las pestañas (al arrancar y cada vez que llegan datos nuevos),
 * así quien abre la web los ve al instante en vez de esperar a que se calculen todos.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "justa.precalculo.activo", havingValue = "true", matchIfMissing = true)
public class PrecalculoService {

	private static final Logger log = LoggerFactory.getLogger(PrecalculoService.class);
	private static final PartidosService.Pestana[] ORDEN = { PartidosService.Pestana.HOY,
			PartidosService.Pestana.PROXIMO, PartidosService.Pestana.JUGADO };

	private final PartidosService partidos;
	private final AnalisisService analisis;
	private final PrecisionService precision;
	private final PicksService picks;
	private final AtomicBoolean enCurso = new AtomicBoolean(false);

	/** Al arrancar se espera un poco: primero el servidor tiene que poder responder (el plan gratis tiene poca CPU). */
	private static final long ESPERA_AL_ARRANCAR_MS = 45_000;
	/** Pausa entre partido y partido, para dejar CPU libre a quien está usando la web. */
	private static final long PAUSA_MS = 25;

	@EventListener(ApplicationReadyEvent.class)
	public void alArrancar() {
		Thread.ofVirtual().name("precalculo-espera").start(() -> {
			dormir(ESPERA_AL_ARRANCAR_MS);
			lanzar();
		});
	}

	static void dormir(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	@EventListener(DatosActualizadosEvent.class)
	public void alActualizar() {
		lanzar();
	}

	/** En segundo plano; si ya hay uno corriendo, no se lanza otro. Primero lo que más se mira: hoy. */
	public void lanzar() {
		if (!enCurso.compareAndSet(false, true)) {
			return;
		}
		Thread.ofVirtual().name("precalculo").start(() -> {
			long inicio = System.currentTimeMillis();
			int n = 0;
			try {
				for (PartidosService.Pestana pestana : ORDEN) {
					for (Partido p : partidos.partidosDe(pestana)) {
						try {
							analisis.precalcular(p);
							n++;
							dormir(PAUSA_MS);
						} catch (RuntimeException e) {
							log.warn("Precálculo del partido {} falló: {}", p.getId(), e.getMessage());
						}
					}
				}
				// Con los análisis listos, las listas de cada pestaña quedan armadas en memoria
				partidos.rehacerListas();
				picks.rehacerPicks();
				log.info("Análisis precalculados: {} partidos en {} s", n, (System.currentTimeMillis() - inicio) / 1000);
			} finally {
				enCurso.set(false);
			}
			// La medición del acierto real se rehace como mucho 2 veces al día, después del precálculo (no compite)
			PrecisionService.Precision ultima = precision.ultima();
			if (ultima == null || ultima.calculadoEn().isBefore(java.time.Instant.now().minus(java.time.Duration.ofHours(12)))) {
				precision.medirAparte();
			}
		});
	}
}
