package pe.justaapuesta.sync;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.RequiredArgsConstructor;
import pe.justaapuesta.analisis.AnalisisCacheRepository;
import pe.justaapuesta.config.JustaProperties;
import pe.justaapuesta.historial.HistorialService;
import pe.justaapuesta.historial.TheSportsDbService;
import pe.justaapuesta.partido.CatalogoService;
import pe.justaapuesta.partido.Partido;
import pe.justaapuesta.partido.PartidoRepository;
import pe.justaapuesta.proveedor.FootballDataProvider;
import pe.justaapuesta.proveedor.ProveedorException;

/**
 * Sincronización con el proveedor, pensada para el plan gratis de API-Football (100 consultas al día):
 * <ul>
 * <li><b>Diaria:</b> los partidos de hace 3 días a dentro de 7 (1 consulta por día, todas las ligas), luego
 * algunos días anteriores para armar el historial de a pocos, luego las estadísticas (1 consulta por partido,
 * solo de las ligas configuradas) y por último borra partidos de más de 2 años.</li>
 * <li><b>Cada 3 horas:</b> los partidos de hoy y unas pocas estadísticas.</li>
 * </ul>
 * Si se acaba la cuota, se detiene y lo ya guardado queda guardado.
 */
@Service
@RequiredArgsConstructor
public class SincronizacionService {

	private static final Logger log = LoggerFactory.getLogger(SincronizacionService.class);
	public static final String DIARIA = "diaria";
	public static final String HOY = "hoy";
	public static final String HISTORIAL = "historial";
	public static final String HISTORIAL_EXTRA = "thesportsdb";

	private final FootballDataProvider proveedor;
	private final HistorialService historial;
	private final TheSportsDbService historialExtra;
	private final CatalogoService catalogo;
	private final PartidoRepository partidoRepository;
	private final DiaSincronizadoRepository diaRepository;
	private final SyncEstadoRepository syncEstadoRepository;
	private final AnalisisCacheRepository cacheRepository;
	private final TransactionTemplate tx;
	private final JustaProperties props;
	private final AtomicBoolean enCurso = new AtomicBoolean(false);
	private final AtomicBoolean extraEnCurso = new AtomicBoolean(false);

	/** @return false si ya había una sincronización corriendo */
	public boolean diaria() {
		if (!enCurso.compareAndSet(false, true)) {
			return false;
		}
		try {
			ejecutarDiaria();
		} finally {
			enCurso.set(false);
		}
		return true;
	}

	public boolean estaEnCurso() {
		return enCurso.get();
	}

	private void ejecutarDiaria() {
		// Fuente 2 (football-data.org): historial de la temporada de las grandes ligas. Va después de
		// API-Football para poder vincular los equipos con los partidos de hoy.
		try {
			ejecutarApiFootball();
		} finally {
			if (historial.disponible()) {
				String r = historial.sincronizar();
				log.info("Historial football-data.org: {}", r);
				registrar(HISTORIAL, !r.contains("con error"), r);
			}
			recalcularAnalisis();
			historialExtra();
		}
	}

	/** Con datos nuevos, los análisis guardados de partidos por jugar se recalculan al próximo pedido. */
	private void recalcularAnalisis() {
		int n = tx.execute(s -> cacheRepository.borrarRecalculables(Instant.now()));
		log.info("Análisis por recalcular: {}", n);
	}

	private void ejecutarApiFootball() {
		if (!proveedor.disponible()) {
			registrar(DIARIA, false, "Falta configurar FOOTBALL_API_KEY.");
			return;
		}
		JustaProperties.Sync c = props.sync();
		LocalDate hoy = LocalDate.now(props.zona());
		int partidos = 0, dias = 0, estadisticas = 0;
		try {
			// 1. Ventana actual (se vuelve a bajar cada día: cambian marcadores y estados)
			for (int d = -c.diasAtras(); d <= c.diasAdelante(); d++) {
				partidos += descargarDia(hoy.plusDays(d));
				dias++;
			}
			// 2. Historial: días anteriores que aún no se bajaron, del más reciente al más antiguo
			for (LocalDate dia : diasDeHistorialPendientes(hoy)) {
				partidos += descargarDia(dia);
				dias++;
			}
			// 3. Estadísticas de los jugados de las ligas configuradas
			estadisticas = completarEstadisticas(c.estadisticasPorEjecucion());
			int borrados = tx.execute(s -> partidoRepository.borrarAnterioresA(
					Instant.now().minus(Duration.ofDays(c.diasRetencion()))));
			String detalle = "%d días, %d partidos, %d con estadísticas nuevas, %d borrados"
					.formatted(dias, partidos, estadisticas, borrados);
			log.info("Sincronización diaria OK: {}", detalle);
			registrar(DIARIA, true, detalle);
		} catch (ProveedorException e) {
			log.warn("Sincronización diaria incompleta: {}", e.getMessage());
			registrar(DIARIA, false, "Incompleta (%d días, %d partidos, %d estadísticas): %s"
					.formatted(dias, partidos, estadisticas, e.getMessage()));
		}
	}

	/** Refresco ligero de hoy (marcadores y estados) y unas pocas estadísticas. */
	public void hoy() {
		if (!proveedor.disponible() || enCurso.get()) {
			return;
		}
		try {
			int n = descargarDia(LocalDate.now(props.zona()));
			completarEstadisticas(props.sync().estadisticasPorRefresco());
			recalcularAnalisis();
			registrar(HOY, true, n + " partidos de hoy");
		} catch (ProveedorException e) {
			log.warn("Refresco de hoy falló: {}", e.getMessage());
			registrar(HOY, false, e.getMessage());
		}
	}

	/** Baja todos los partidos de un día (todas las ligas: así la búsqueda encuentra cualquier equipo). */
	public int descargarDia(LocalDate dia) {
		int n = catalogo.guardarPartidos(proveedor.partidosDelDia(dia)).size();
		diaRepository.save(new DiaSincronizado(dia, Instant.now(), n));
		return n;
	}

	private List<LocalDate> diasDeHistorialPendientes(LocalDate hoy) {
		JustaProperties.Sync c = props.sync();
		List<LocalDate> candidatos = new ArrayList<>();
		for (int d = c.diasAtras() + 1; d <= c.diasHistorial(); d++) {
			candidatos.add(hoy.minusDays(d));
		}
		Set<LocalDate> hechos = new HashSet<>(diaRepository.yaDescargados(candidatos));
		return candidatos.stream().filter(f -> !hechos.contains(f)).limit(c.diasHistorialPorEjecucion()).toList();
	}

	/** Pide estadísticas de los jugados más recientes de las ligas configuradas (1 consulta por partido). */
	public int completarEstadisticas(int maximo) {
		if (maximo <= 0) {
			return 0;
		}
		List<Partido> pendientes = partidoRepository.jugadosSinDetalle(props.sync().idsLigas(), PageRequest.of(0, maximo));
		return completarEstadisticas(pendientes);
	}

	/** Pide las estadísticas de esos partidos (los que ya las tienen se saltan). */
	public int completarEstadisticas(List<Partido> partidos) {
		int n = 0;
		for (Partido p : partidos) {
			if (p.isDetalleSync()) {
				continue;
			}
			catalogo.guardarEstadisticas(p.getId(), proveedor.estadisticas(p.getProveedorId()));
			n++;
		}
		return n;
	}

	public Optional<SyncEstado> ultima(String clave) {
		return syncEstadoRepository.findById(clave);
	}

	/** true si hoy (hora de Lima) ya hubo una sincronización diaria correcta. */
	public boolean diariaHechaHoy() {
		return hechaHoy(DIARIA);
	}

	/**
	 * Hubo un intento (bien o mal) hace poco: al reiniciar el backend seguido (IntelliJ) no se vuelve a intentar,
	 * para no gastar consultas ni chocar con el límite por minuto de las fuentes.
	 */
	public boolean intentadaHacePoco(java.time.Duration margen) {
		Instant limite = Instant.now().minus(margen);
		return ultima(DIARIA).map(s -> s.getEjecutadoEn().isAfter(limite)).orElse(false);
	}

	private boolean hechaHoy(String clave) {
		LocalDate hoy = LocalDate.now(props.zona());
		return ultima(clave).filter(SyncEstado::isOk)
				.map(s -> LocalDate.ofInstant(s.getEjecutadoEn(), props.zona()).equals(hoy))
				.orElse(false);
	}

	/**
	 * Fuente 3 (TheSportsDB), una vez al día: historial de goles de los equipos que van a jugar y casi no tienen
	 * partidos guardados. Después une por nombre a los equipos nuevos con los de las otras fuentes.
	 */
	public void historialExtra() {
		if (!historialExtra.disponible() || hechaHoy(HISTORIAL_EXTRA) || !extraEnCurso.compareAndSet(false, true)) {
			return;
		}
		try {
			String r = historialExtra.completar();
			log.info("Historial TheSportsDB: {}", r);
			int unidos = tx.execute(s -> historial.vincularPorNombre());
			registrar(HISTORIAL_EXTRA, !r.contains("con error"), r + ", " + unidos + " equipos vinculados");
			recalcularAnalisis();
		} catch (RuntimeException e) {
			log.warn("Historial TheSportsDB falló: {}", e.getMessage());
			registrar(HISTORIAL_EXTRA, false, String.valueOf(e.getMessage()));
		} finally {
			extraEnCurso.set(false);
		}
	}

	private void registrar(String clave, boolean ok, String detalle) {
		SyncEstado s = syncEstadoRepository.findById(clave).orElseGet(SyncEstado::new);
		s.setClave(clave);
		s.setEjecutadoEn(Instant.now());
		s.setOk(ok);
		s.setDetalle(detalle.length() > 500 ? detalle.substring(0, 500) : detalle);
		syncEstadoRepository.save(s);
	}
}
