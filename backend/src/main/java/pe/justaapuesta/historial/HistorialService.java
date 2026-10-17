package pe.justaapuesta.historial;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.RequiredArgsConstructor;
import pe.justaapuesta.comun.Normalizador;
import pe.justaapuesta.config.JustaProperties;
import pe.justaapuesta.historial.HistorialDtos.FdEquipo;
import pe.justaapuesta.historial.HistorialDtos.FdPartido;
import pe.justaapuesta.partido.Equipo;
import pe.justaapuesta.partido.EquipoRepository;
import pe.justaapuesta.partido.EstadoPartido;
import pe.justaapuesta.partido.Liga;
import pe.justaapuesta.partido.LigaRepository;
import pe.justaapuesta.partido.Partido;
import pe.justaapuesta.partido.PartidoRepository;
import pe.justaapuesta.proveedor.ProveedorException;
import pe.justaapuesta.sync.BusquedaCache;
import pe.justaapuesta.sync.BusquedaCacheRepository;

/**
 * Historial de la temporada desde football-data.org (gratis, sin estadísticas), unido a lo de API-Football:
 * <ul>
 * <li>Los partidos de football-data.org se guardan con id negativo (-id) y sus equipos también, hasta saber
 * a qué equipo de API-Football corresponden.</li>
 * <li><b>Vinculación:</b> cuando el mismo partido aparece en las dos fuentes (misma liga, misma hora y nombres
 * parecidos), los equipos se unen: todos sus partidos pasan al equipo de API-Football y el partido repetido
 * se borra (se queda el de API-Football, que trae corners, tarjetas y remates).</li>
 * <li>Si nunca coinciden en un partido, se unen por nombre cuando no hay ninguna duda.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class HistorialService {

	private static final Logger log = LoggerFactory.getLogger(HistorialService.class);
	/** Diferencia máxima de hora de inicio para considerar que es el mismo partido. */
	private static final Duration MISMA_HORA = Duration.ofHours(2);
	/** Ventana donde se buscan partidos repetidos entre fuentes (API-Football solo tiene ayer, hoy y mañana). */
	private static final Duration VENTANA = Duration.ofDays(3);

	private final HistorialProvider fuente;
	private final LigaRepository ligaRepository;
	private final EquipoRepository equipoRepository;
	private final PartidoRepository partidoRepository;
	private final TransactionTemplate tx;
	private final JustaProperties props;
	private final BusquedaCacheRepository busquedas;

	public boolean disponible() {
		return fuente.disponible();
	}

	/** Baja la temporada de cada competición configurada y vincula equipos. @return resumen para el log. */
	public String sincronizar() {
		int partidos = 0, vinculados = 0, errores = 0;
		String ultimoError = null;
		for (JustaProperties.Competicion c : props.historial().competiciones()) {
			try {
				List<FdPartido> lista = fuente.partidosDeTemporada(c.codigo());
				partidos += tx.execute(s -> guardar(c, lista));
				vinculados += tx.execute(s -> vincular(c.ligaApiFootball()));
				partidos += temporadaAnterior(c, lista);
			} catch (ProveedorException e) {
				errores++;
				ultimoError = e.getMessage();
				log.warn("Historial {} falló: {}", c.codigo(), e.getMessage());
			}
		}
		try {
			vinculados += tx.execute(s -> vincularPorNombre());
		} catch (RuntimeException e) {
			log.warn("Vinculación por nombre falló: {}", e.getMessage());
		}
		return "%d partidos de temporada, %d equipos vinculados%s".formatted(partidos, vinculados,
				errores == 0 ? "" : ", " + errores + " competiciones con error (" + ultimoError + ")");
	}

	/**
	 * La temporada pasada completa, una sola vez por competición (ya terminó: no cambia). Así los partidos del
	 * inicio de temporada también tienen los 10 anteriores de cada equipo para analizar.
	 */
	private int temporadaAnterior(JustaProperties.Competicion c, List<FdPartido> actual) {
		Integer temporada = actual.stream().map(FdPartido::temporada).filter(java.util.Objects::nonNull).findFirst()
				.orElse(null);
		if (temporada == null) {
			return 0;
		}
		String clave = "fd-temporada:" + c.codigo() + ":" + (temporada - 1);
		if (busquedas.existsById(clave)) {
			return 0;
		}
		List<FdPartido> pasada = fuente.partidosDeTemporada(c.codigo(), temporada - 1);
		int n = pasada.isEmpty() ? 0 : tx.execute(s -> guardar(c, pasada));
		busquedas.save(new BusquedaCache(clave, Instant.now()));
		return n;
	}

	// ---------- Guardar ----------

	int guardar(JustaProperties.Competicion c, List<FdPartido> lista) {
		if (lista.isEmpty()) {
			return 0;
		}
		Instant ahora = Instant.now();
		LocalDate hoy = LocalDate.now(props.zona());
		Liga liga = liga(c.ligaApiFootball(), lista.get(0));

		// Equipos: primero los ya vinculados o ya guardados (por id de football-data), luego los nuevos
		Map<Long, FdEquipo> fdEquipos = new LinkedHashMap<>();
		lista.forEach(m -> {
			fdEquipos.putIfAbsent(m.local().id(), m.local());
			fdEquipos.putIfAbsent(m.visita().id(), m.visita());
		});
		Map<Long, Equipo> equipos = equipoRepository.findByFdIdIn(fdEquipos.keySet()).stream()
				.collect(Collectors.toMap(Equipo::getFdId, Function.identity()));
		for (FdEquipo fe : fdEquipos.values()) {
			Equipo e = equipos.get(fe.id());
			if (e == null) {
				e = new Equipo();
				e.setProveedorId(-fe.id());
				e.setFdId(fe.id());
				e.setLiga(liga);
				e.setActualizadoEn(ahora);
			}
			if (e.soloFootballData()) {
				e.setNombre(recortar(fe.nombre(), 100));
				// Todos sus nombres, para que "barcelona" encuentre a "Barça" (y para vincular)
				e.setNombreNormalizado(recortar(NombresEquipos.variantes(fe.nombre(), fe.otrosNombres()), 100));
				if (fe.escudo() != null) {
					e.setEscudoUrl(recortar(fe.escudo(), 255));
				}
				e.setActualizadoEn(ahora);
			} else {
				// Ya vinculado con API-Football: se queda su nombre, pero se suman los de football-data.org
				List<String> nombres = new ArrayList<>(NombresEquipos.variantesDe(e.getNombreNormalizado()));
				nombres.add(fe.nombre());
				nombres.addAll(fe.otrosNombres());
				e.setNombreNormalizado(recortar(NombresEquipos.variantes(e.getNombre(), nombres), 100));
			}
			equipos.put(fe.id(), equipoRepository.save(e));
		}

		// Partidos de API-Football de esa liga cerca de hoy: para no repetir el mismo partido
		List<Partido> deApiFootball = partidoRepository.deLigas(List.of(liga.getId()), ahora.minus(VENTANA),
				ahora.plus(VENTANA)).stream().filter(p -> p.getProveedorId() > 0).toList();
		Map<Long, Partido> existentes = partidoRepository
				.findByProveedorIdIn(lista.stream().map(m -> -m.id()).toList()).stream()
				.collect(Collectors.toMap(Partido::getProveedorId, Function.identity()));

		int n = 0;
		for (FdPartido m : lista) {
			Equipo local = equipos.get(m.local().id());
			Equipo visita = equipos.get(m.visita().id());
			Partido p = existentes.get(-m.id());
			if (repetido(local, visita, m.fecha(), deApiFootball)) {
				if (p != null) {
					partidoRepository.delete(p);
				}
				continue;
			}
			if (p == null) {
				p = new Partido();
				p.setProveedorId(-m.id());
			}
			p.setLiga(liga);
			p.setLocal(local);
			p.setVisita(visita);
			p.setFechaHoraUtc(m.fecha());
			p.setEstadio(recortar(m.estadio(), 120));
			p.setEstado(EstadoPartido.desdeFootballData(m.estado(), m.fecha(), props.zona(), hoy));
			p.setEstadoProveedor(recortar(m.estado(), 10));
			p.setGolesLocal(m.golesLocal());
			p.setGolesVisita(m.golesVisita());
			p.setTemporada(m.temporada());
			p.setActualizadoEn(ahora);
			// football-data.org gratis no trae corners/tarjetas/remates: no hay nada más que pedir
			p.setDetalleSync(true);
			partidoRepository.save(p);
			n++;
		}
		return n;
	}

	/** El mismo partido ya está con los datos de API-Football (mismos equipos y casi la misma hora). */
	private static boolean repetido(Equipo local, Equipo visita, Instant fecha, List<Partido> deApiFootball) {
		if (local.soloFootballData() || visita.soloFootballData()) {
			return false;
		}
		return deApiFootball.stream().anyMatch(p -> p.getLocal().getId().equals(local.getId())
				&& p.getVisita().getId().equals(visita.getId())
				&& Duration.between(p.getFechaHoraUtc(), fecha).abs().compareTo(MISMA_HORA) <= 0);
	}

	private Liga liga(long idApiFootball, FdPartido ejemplo) {
		Liga l = ligaRepository.findByProveedorIdIn(List.of(idApiFootball)).stream().findFirst().orElseGet(Liga::new);
		if (l.getId() == null) {
			String nombre = props.nombreLiga(idApiFootball).orElse(ejemplo.competicion() == null ? "Liga" : ejemplo.competicion());
			l.setProveedorId(idApiFootball);
			l.setNombre(recortar(nombre, 100));
			l.setNombreNormalizado(recortar(Normalizador.normalizar(nombre), 100));
			l.setPais(recortar(ejemplo.pais(), 80));
			l = ligaRepository.save(l);
		}
		return l;
	}

	// ---------- Vincular ----------

	/**
	 * Busca partidos que estén en las dos fuentes (misma liga, ±2 horas, nombres parecidos del local y del
	 * visitante) y une los equipos. @return cuántos equipos se vincularon.
	 */
	int vincular(long idLigaApiFootball) {
		Liga liga = ligaRepository.findByProveedorIdIn(List.of(idLigaApiFootball)).stream().findFirst().orElse(null);
		if (liga == null) {
			return 0;
		}
		Instant ahora = Instant.now();
		List<Partido> cerca = partidoRepository.deLigas(List.of(liga.getId()), ahora.minus(VENTANA), ahora.plus(VENTANA));
		List<Partido> deApiFootball = cerca.stream().filter(p -> p.getProveedorId() > 0).toList();
		List<Partido> deFd = cerca.stream().filter(p -> p.getProveedorId() < 0).toList();

		Map<Long, Long> uniones = new HashMap<>(); // id equipo football-data -> id equipo API-Football
		List<Long> repetidos = new ArrayList<>();
		for (Partido f : deFd) {
			// Misma liga y misma hora (±2 h). Se puntúa cada partido de API-Football por lo que se parecen el local
			// y el visitante; se une solo si el mejor tiene al menos un equipo claramente igual (4 puntos) y le gana
			// al segundo. Así "Real" Madrid no se confunde con "Real" Betis.
			Partido a = null;
			int mejor = 0, segundo = 0;
			for (Partido c : deApiFootball) {
				if (Duration.between(c.getFechaHoraUtc(), f.getFechaHoraUtc()).abs().compareTo(MISMA_HORA) > 0) {
					continue;
				}
				int puntos = similitud(c.getLocal(), f.getLocal()) + similitud(c.getVisita(), f.getVisita());
				if (puntos > mejor) {
					segundo = mejor;
					mejor = puntos;
					a = c;
				} else if (puntos > segundo) {
					segundo = puntos;
				}
			}
			if (a == null || mejor < NombresEquipos.IGUAL || mejor == segundo) {
				continue; // ninguno o ambiguo: mejor no unir
			}
			if (f.getLocal().soloFootballData()) {
				uniones.put(f.getLocal().getId(), a.getLocal().getId());
			}
			if (f.getVisita().soloFootballData()) {
				uniones.put(f.getVisita().getId(), a.getVisita().getId());
			}
			repetidos.add(f.getId());
		}
		repetidos.forEach(partidoRepository::deleteById);
		partidoRepository.flush();

		int n = 0;
		for (Map.Entry<Long, Long> u : uniones.entrySet()) {
			if (unir(u.getKey(), u.getValue())) {
				n++;
			}
		}
		if (n > 0) {
			log.info("Liga {}: {} equipos vinculados entre API-Football y football-data.org", liga.getNombre(), n);
		}
		return n;
	}

	/**
	 * Equipos que nunca coincidieron en un mismo partido (por ejemplo Juventus en la Europa League, que
	 * football-data.org no tiene) y los que llegan de TheSportsDB: se unen por nombre solo si es claramente el mismo (mismo nombre, mismas palabras
	 * o sigla), sin juveniles ni femeninos, y con un único candidato de cada lado. @return cuántos se vincularon.
	 */
	public int vincularPorNombre() {
		List<Equipo> todos = equipoRepository.findAll();
		List<Equipo> sinVincular = todos.stream()
				.filter(e -> !e.soloFootballData() && e.getFdId() == null && !NombresEquipos.esJuvenilOFemenino(e.getNombre()))
				.toList();
		Map<Long, List<Long>> propuestas = new HashMap<>(); // id equipo API-Football -> ids de las otras fuentes
		for (Equipo f : todos.stream().filter(Equipo::soloFootballData).toList()) {
			List<Equipo> iguales = sinVincular.stream()
					.filter(a -> NombresEquipos.similitudConVariantes(a.getNombre(), f.getNombreNormalizado()) >= NombresEquipos.IGUAL)
					// Mismo nombre pero de otro país no es el mismo equipo ("NEW" Newcastle / "New York City")
					.filter(a -> NombresEquipos.mismoPais(a.pais(), f.pais()))
					.toList();
			if (iguales.size() == 1) {
				propuestas.computeIfAbsent(iguales.get(0).getId(), k -> new ArrayList<>()).add(f.getId());
			}
		}
		int n = 0;
		Map<Long, Equipo> porId = todos.stream().collect(Collectors.toMap(Equipo::getId, Function.identity()));
		for (Map.Entry<Long, List<Long>> p : propuestas.entrySet()) {
			// Como mucho uno de football-data.org (si hay dos, es ambiguo); los de TheSportsDB se suman
			if (p.getValue().stream().filter(id -> porId.get(id).getFdId() != null).count() > 1) {
				continue;
			}
			for (Long sale : p.getValue()) {
				if (unir(sale, p.getKey())) {
					n++;
				}
			}
		}
		if (n > 0) {
			log.info("{} equipos vinculados por nombre entre API-Football y football-data.org", n);
		}
		return n;
	}

	private static int similitud(Equipo deApiFootball, Equipo deFd) {
		if (deApiFootball.getId().equals(deFd.getId())) {
			return NombresEquipos.IGUAL + 1; // ya vinculados
		}
		return NombresEquipos.similitudConVariantes(deApiFootball.getNombre(), deFd.getNombreNormalizado());
	}

	/** El equipo de football-data.org pasa a ser el de API-Football (sus partidos y su id de football-data). */
	private boolean unir(Long idSale, Long idQueda) {
		Equipo sale = equipoRepository.findById(idSale).orElse(null);
		Equipo queda = equipoRepository.findById(idQueda).orElse(null);
		if (sale == null || queda == null || !sale.soloFootballData()
				|| (queda.getFdId() != null && sale.getFdId() != null && !queda.getFdId().equals(sale.getFdId()))) {
			return false;
		}
		Long fdId = sale.getFdId() != null ? sale.getFdId() : queda.getFdId();
		// Se guardan los nombres de las dos fuentes, para que "psg" encuentre a "Paris Saint Germain"
		List<String> nombres = new ArrayList<>(NombresEquipos.variantesDe(queda.getNombreNormalizado()));
		nombres.addAll(NombresEquipos.variantesDe(sale.getNombreNormalizado()));
		String variantes = recortar(NombresEquipos.variantes(queda.getNombre(), nombres), 100);
		partidoRepository.moverLocal(sale, queda);
		partidoRepository.moverVisita(sale, queda);
		equipoRepository.deleteById(idSale);
		equipoRepository.flush();
		Equipo q = equipoRepository.findById(idQueda).orElseThrow();
		q.setFdId(fdId);
		q.setNombreNormalizado(variantes);
		equipoRepository.save(q);
		return true;
	}

	private static String recortar(String s, int max) {
		return s == null ? null : s.length() <= max ? s : s.substring(0, max);
	}
}
