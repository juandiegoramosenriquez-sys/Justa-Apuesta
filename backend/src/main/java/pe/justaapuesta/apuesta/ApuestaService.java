package pe.justaapuesta.apuesta;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import pe.justaapuesta.analisis.AnalisisService;
import pe.justaapuesta.analisis.Entradas;
import pe.justaapuesta.analisis.Motor;
import pe.justaapuesta.comun.Normalizador;
import pe.justaapuesta.comun.SolicitudInvalidaException;
import pe.justaapuesta.historial.NombresEquipos;
import pe.justaapuesta.partido.EstadoPartido;
import pe.justaapuesta.partido.Partido;
import pe.justaapuesta.partido.PartidoRepository;
import pe.justaapuesta.partido.PartidosService;

/**
 * "Analizar mi apuesta": recibe el texto de un boleto de una casa de apuestas (leído de una foto en el celular o
 * escrito a mano), reconoce cada partido y lo que se apostó, y calcula la probabilidad de cada selección y de la
 * combinada con el mismo modelo de la app.
 */
@Service
@RequiredArgsConstructor
public class ApuestaService {

	private static final int MAX_TEXTO = 5000;
	/** Partidos que se buscan en el boleto: desde ayer hasta dentro de 2 semanas. */
	private static final Duration ANTES = Duration.ofDays(1);
	private static final Duration DESPUES = Duration.ofDays(14);

	private final PartidoRepository partidoRepository;
	private final AnalisisService analisis;

	public record SeleccionDto(String leido, Long partidoId, String partido, Instant fecha, String liga, String mercado,
			Integer pct, Double cuota, String nota) {
	}

	/**
	 * @param pctTotal   probabilidad de que salgan todas (null si no se pudo calcular ninguna)
	 * @param cuotaJusta la cuota que pagaría una casa justa por esa probabilidad
	 * @param consejo    la selección más riesgosa y cuánto sube la combinada sin ella
	 */
	public record AnalisisApuestaDto(List<SeleccionDto> selecciones, Integer pctTotal, Double cuotaJusta,
			String consejo) {
	}

	public record PedidoDto(String texto) {
	}

	public AnalisisApuestaDto analizar(String texto) {
		if (texto == null || texto.isBlank()) {
			throw new SolicitudInvalidaException("No encontramos texto en la apuesta.");
		}
		List<String> lineas = lineas(texto.length() > MAX_TEXTO ? texto.substring(0, MAX_TEXTO) : texto);
		Instant ahora = Instant.now();
		List<Partido> candidatos = PartidosService.sinRepetidos(partidoRepository.entre(ahora.minus(ANTES), ahora.plus(DESPUES))
				.stream().filter(p -> p.getEstado() != EstadoPartido.SUSPENDIDO).toList());
		List<Mencion> menciones = partidosEnTexto(lineas, candidatos);
		if (menciones.isEmpty()) {
			return new AnalisisApuestaDto(List.of(), null, null,
					"No reconocimos ningún partido. Revisa que se lean los nombres de los equipos, o escríbelos así: "
							+ "\"Liverpool vs Brentford - Más de 2.5 goles\".");
		}
		Map<Mencion, List<Seleccion>> porPartido = asignar(lineas, menciones);
		Map<Long, Entradas> entradas = analisis.entradas(menciones.stream().map(Mencion::partido).toList());

		List<SeleccionDto> out = new ArrayList<>();
		for (Mencion m : menciones) {
			Partido p = m.partido();
			String nombre = p.getLocal().getNombre() + " vs " + p.getVisita().getNombre();
			List<Seleccion> sels = porPartido.getOrDefault(m, List.of());
			if (sels.isEmpty()) {
				out.add(new SeleccionDto(lineas.get(m.linea()), p.getId(), nombre, p.getFechaHoraUtc(), p.getLiga().getNombre(),
						null, null, null, "No reconocimos qué apostaste en este partido. Corrige el texto y vuelve a calcular."));
				continue;
			}
			Entradas e = entradas.get(p.getId());
			Map<String, Motor.Mercado> mercados = e == null || !e.suficientes() ? Map.of()
					: analisis.mercados(p.getId(), e).stream()
							.collect(Collectors.toMap(Motor.Mercado::clave, x -> x, (a, b) -> a, LinkedHashMap::new));
			for (Seleccion s : sels) {
				String texto1 = s.texto(p);
				if (e == null || !e.suficientes()) {
					out.add(new SeleccionDto(s.leido(), p.getId(), nombre, p.getFechaHoraUtc(), p.getLiga().getNombre(),
							texto1, null, null, "Faltan partidos de estos equipos para calcularlo."));
					continue;
				}
				Motor.Mercado mk = s.clave() == null ? null : mercados.get(s.clave());
				Integer pct = mk != null ? Integer.valueOf(mk.pct())
						: s.tipo() == null ? null : Motor.linea(e, s.tipo(), s.linea(), s.mas(), s.local());
				Double cuota = mk != null ? mk.cuota() : pct == null ? null : Motor.cuota(pct);
				out.add(new SeleccionDto(s.leido(), p.getId(), nombre, p.getFechaHoraUtc(), p.getLiga().getNombre(),
						mk != null ? mk.texto() : texto1, pct, cuota,
						pct == null ? "No tenemos datos de ese mercado para estos equipos." : null));
			}
		}
		List<SeleccionDto> calculadas = out.stream().filter(s -> s.pct() != null).toList();
		if (calculadas.isEmpty()) {
			return new AnalisisApuestaDto(out, null, null, null);
		}
		double prob = calculadas.stream().mapToDouble(s -> s.pct() / 100.0).reduce(1, (a, b) -> a * b);
		int pctTotal = (int) Math.round(prob * 100);
		String consejo = null;
		if (calculadas.size() > 1) {
			SeleccionDto peor = calculadas.stream().min(Comparator.comparingInt(SeleccionDto::pct)).orElseThrow();
			int sin = (int) Math.round(prob / (peor.pct() / 100.0) * 100);
			consejo = "La más riesgosa es \"%s\" (%d%%). Sin ella, la combinada sube a %d%%."
					.formatted(peor.mercado(), peor.pct(), sin);
		}
		if (calculadas.size() < out.size()) {
			String falta = "Ojo: %d de %d selecciones no se pudieron calcular y no cuentan en el total."
					.formatted(out.size() - calculadas.size(), out.size());
			consejo = consejo == null ? falta : consejo + " " + falta;
		}
		double cuotaJusta = Math.round(1 / (Math.max(prob, 0.001) * 1.06) * 100) / 100.0;
		return new AnalisisApuestaDto(out, pctTotal, Math.max(1.01, cuotaJusta), consejo);
	}

	// ---------- Lectura del texto ----------

	/** Líneas en minúsculas, sin tildes ni signos raros (se dejan números, punto, coma, + y /). */
	static List<String> lineas(String texto) {
		return Arrays.stream(texto.split("\\r?\\n"))
				.map(l -> Normalizador.normalizar(l).replaceAll("[^a-z0-9.,+/: -]", " ").replaceAll("\\s+", " ").trim())
				.filter(l -> !l.isEmpty())
				.toList();
	}

	private static List<String> palabras(String linea) {
		return Arrays.stream(linea.split("[^a-z0-9]+")).filter(w -> !w.isEmpty()).toList();
	}

	/** Un partido nombrado en el boleto: en qué líneas están sus equipos. */
	record Mencion(Partido partido, int linea, int fin, int puntos) {
	}

	/** Qué tanto aparece un equipo en una línea (0 = no aparece). */
	static int aparece(String nombreEquipo, String linea) {
		Set<String> clave = NombresEquipos.palabrasDistintivas(nombreEquipo);
		boolean debil = clave.isEmpty();
		if (debil) {
			clave = NombresEquipos.palabras(nombreEquipo);
		}
		List<String> tokens = palabras(linea);
		int n = 0;
		for (String w : clave) {
			// Las casas abrevian: "Man. United", "Atl. Nacional"
			if (tokens.stream().anyMatch(t -> t.equals(w) || (t.length() >= 3 && w.startsWith(t))
					|| (w.length() >= 4 && t.startsWith(w)))) {
				n++;
			}
		}
		// Un nombre de puras palabras comunes ("Atlético Nacional") tiene que aparecer completo
		return debil && n < clave.size() ? 0 : n;
	}

	/** Los partidos del boleto: los dos equipos en la misma línea o en líneas seguidas. */
	static List<Mencion> partidosEnTexto(List<String> lineas, List<Partido> candidatos) {
		List<Mencion> todas = new ArrayList<>();
		for (Partido p : candidatos) {
			Mencion mejor = null;
			for (int i = 0; i < lineas.size(); i++) {
				int l = aparece(p.getLocal().getNombre(), lineas.get(i));
				if (l == 0) {
					continue;
				}
				for (int j = Math.max(0, i - 1); j <= Math.min(lineas.size() - 1, i + 1); j++) {
					int v = aparece(p.getVisita().getNombre(), lineas.get(j));
					if (v > 0 && (mejor == null || l + v > mejor.puntos())) {
						mejor = new Mencion(p, Math.min(i, j), Math.max(i, j), l + v);
					}
				}
			}
			if (mejor != null) {
				todas.add(mejor);
			}
		}
		// Si dos partidos se pelean las mismas líneas, se queda el que más coincide
		todas.sort(Comparator.comparingInt((Mencion m) -> -m.puntos()));
		List<Mencion> elegidas = new ArrayList<>();
		for (Mencion m : todas) {
			if (elegidas.stream().noneMatch(x -> x.linea() <= m.fin() && m.linea() <= x.fin())) {
				elegidas.add(m);
			}
		}
		elegidas.sort(Comparator.comparingInt(Mencion::linea));
		return elegidas;
	}

	/**
	 * Lo apostado en una línea. clave = mercado de la app si existe; si no, tipo/linea/mas/local para calcular
	 * una línea cualquiera.
	 */
	record Seleccion(String leido, String clave, String tipo, double linea, boolean mas, Boolean local, String etiqueta) {
		String texto(Partido p) {
			if (etiqueta != null) {
				return etiqueta.replace("{L}", p.getLocal().getNombre()).replace("{V}", p.getVisita().getNombre());
			}
			String quien = local == null ? "" : (local ? p.getLocal().getNombre() : p.getVisita().getNombre()) + " ";
			String que = switch (tipo) {
				case "corners" -> "corners";
				case "tarjetas" -> "tarjetas";
				case "remates" -> "remates a puerta";
				case "tiros" -> "remates";
				default -> "goles";
			};
			return quien + (mas ? "más de " : "menos de ") + String.valueOf(linea).replace(".0", "") + " " + que;
		}
	}

	private static final Pattern LINEA = Pattern.compile(
			"(?:^|[^a-z0-9])(mas|menos|over|under|encima|debajo|sobre|bajo|o|u|\\+)\\s*(?:de\\s*|:\\s*)?(\\d{1,2})(?:[.,](\\d))?(?![0-9])");

	/**
	 * Todas las selecciones, cada una con su partido. Cada casa ordena distinto: en unas lo apostado va encima de
	 * los equipos (Betano) y en otras debajo; se mira qué hay antes del primer partido para saberlo.
	 */
	static Map<Mencion, List<Seleccion>> asignar(List<String> lineas, List<Mencion> menciones) {
		Mencion primera = menciones.get(0);
		boolean arriba = false;
		for (int i = Math.max(0, primera.linea() - 3); i < primera.linea(); i++) {
			if (detectar(lineas.get(i), contexto(lineas, i), primera.partido(), false) != null) {
				arriba = true;
			}
		}
		Map<Mencion, List<Seleccion>> out = new LinkedHashMap<>();
		for (int i = 0; i < lineas.size(); i++) {
			Mencion m = suPartido(i, menciones, arriba);
			if (m == null) {
				continue;
			}
			Seleccion s = detectar(lineas.get(i), contexto(lineas, i), m.partido(), i >= m.linea() && i <= m.fin());
			if (s != null) {
				List<Seleccion> lista = out.computeIfAbsent(m, k -> new ArrayList<>());
				boolean repetida = lista.stream().anyMatch(x -> x.texto(m.partido()).equals(s.texto(m.partido())));
				if (!repetida) {
					lista.add(s);
				}
			}
		}
		return out;
	}

	/** El partido de una línea: el suyo si es la línea de los equipos; si no, el de abajo (o arriba) más cercano. */
	private static Mencion suPartido(int fila, List<Mencion> menciones, boolean arriba) {
		Mencion propia = menciones.stream().filter(m -> fila >= m.linea() && fila <= m.fin()).findFirst().orElse(null);
		if (propia != null) {
			return propia;
		}
		Mencion abajo = menciones.stream().filter(m -> m.linea() > fila).min(Comparator.comparingInt(Mencion::linea))
				.orElse(null);
		Mencion encima = menciones.stream().filter(m -> m.fin() < fila).max(Comparator.comparingInt(Mencion::fin))
				.orElse(null);
		Mencion primero = arriba ? abajo : encima, segundo = arriba ? encima : abajo;
		if (primero != null && distancia(fila, primero) <= 4) {
			return primero;
		}
		return segundo != null && distancia(fila, segundo) <= 2 ? segundo : null;
	}

	private static String contexto(List<String> lineas, int i) {
		return (i > 0 ? lineas.get(i - 1) + " " : "") + lineas.get(i) + (i + 1 < lineas.size() ? " " + lineas.get(i + 1) : "");
	}

	private static int distancia(int fila, Mencion m) {
		return fila < m.linea() ? m.linea() - fila : fila > m.fin() ? fila - m.fin() : 0;
	}

	/** @param esLineaDelPartido la línea donde están los nombres de los dos equipos */
	static Seleccion detectar(String l, String contexto, Partido p, boolean esLineaDelPartido) {
		String L = p.getLocal().getNombre(), V = p.getVisita().getNombre();
		boolean hayL = aparece(L, l) > 0, hayV = aparece(V, l) > 0;
		Boolean equipo = hayL && !hayV ? Boolean.TRUE : hayV && !hayL ? Boolean.FALSE : null;
		if (esLineaDelPartido && hayL && hayV) {
			// "Liverpool - Brentford: más de 2.5": se mira solo lo que viene después de los nombres
			int dos = Math.max(l.lastIndexOf(':'), -1);
			if (dos < 0) {
				return null;
			}
			l = l.substring(dos + 1);
			equipo = null;
		}
		List<String> t = palabras(l);

		// Ambos marcan (Sí / No)
		if (l.matches(".*(ambos (equipos )?(marcan|anotan)|btts|both teams).*")) {
			boolean no = t.contains("no");
			return new Seleccion(l, no ? "goles.btts_no" : "goles.btts", null, 0, true, null,
					no ? "Ambos marcan: No" : "Ambos marcan");
		}
		// Doble oportunidad
		String sin = l.replace(" ", "");
		if (sin.equals("1x") || l.matches(".*(local|" + regex(L) + ")\\s*(o|/)\\s*empate.*")
				|| l.matches(".*empate\\s*(o|/)\\s*(local|" + regex(L) + ").*")) {
			return new Seleccion(l, "resultado.local_empate", null, 0, true, null, "{L} o empate");
		}
		if (sin.equals("x2") || l.matches(".*(visita|visitante|" + regex(V) + ")\\s*(o|/)\\s*empate.*")
				|| l.matches(".*empate\\s*(o|/)\\s*(visita|visitante|" + regex(V) + ").*")) {
			return new Seleccion(l, "resultado.visita_empate", null, 0, true, null, "{V} o empate");
		}
		if (sin.equals("12") || l.matches(".*(no hay empate|sin empate|no empate).*") || (hayL && hayV && l.contains(" o "))) {
			return new Seleccion(l, "resultado.sin_empate", null, 0, true, null, "No hay empate");
		}
		// Más / menos de una línea
		Matcher m = LINEA.matcher(l);
		if (m.find() && m.group(3) != null) {
			String dir = m.group(1);
			boolean mas = !(dir.equals("menos") || dir.equals("under") || dir.equals("debajo") || dir.equals("bajo")
					|| dir.equals("u"));
			if ((dir.equals("o") || dir.equals("u")) && !contexto.matches(".*(gol|total|corner|tarjeta|remate|tiro).*")) {
				return null; // "o 2.5" solo cuenta si se habla de goles, corners...
			}
			double linea = Integer.parseInt(m.group(2)) + Integer.parseInt(m.group(3)) / 10.0;
			String tipo = tipo(l, contexto);
			String clave = clave(tipo, linea, mas, equipo);
			// "Gana Liverpool y más de 1.5"
			if (equipo != null && l.matches(".*\\by\\b.*") && "goles".equals(tipo) && mas && linea == 1.5
					&& !l.contains("marca")) {
				return new Seleccion(l, equipo ? "combo.local_o15" : "combo.visita_o15", null, 0, true, null,
						"Gana " + (equipo ? "{L}" : "{V}") + " y más de 1.5 goles");
			}
			return new Seleccion(l, clave, tipo, linea, mas, equipo, null);
		}
		// Un solo equipo en la línea (sin números): marca, no recibe gol o gana
		if (equipo != null && !esLineaDelPartido) {
			if (l.matches(".*(no recibe|valla invicta|porteria a cero|arco en cero|clean sheet).*")) {
				return new Seleccion(l, equipo ? "equipos.local_cero" : "equipos.visita_cero", null, 0, true, null,
						(equipo ? "{L}" : "{V}") + " no recibe gol");
			}
			if (l.matches(".*(marca|anota|gol).*")) {
				return new Seleccion(l, equipo ? "goles.local" : "goles.visita", null, 0, true, null,
						(equipo ? "{L}" : "{V}") + " marca");
			}
			if (l.matches(".*(tarjeta|amonest).*") && !equipo) {
				return new Seleccion(l, "tarjetas.visita", null, 0, true, null, "{V} recibe tarjeta");
			}
			return new Seleccion(l, equipo ? "resultado.local" : "resultado.visita", null, 0, true, null,
					"Gana " + (equipo ? "{L}" : "{V}"));
		}
		if (l.equals("x") || l.equals("empate")) {
			return new Seleccion(l, "resultado.empate", null, 0, true, null, "Empate");
		}
		return null;
	}

	private static String tipo(String linea, String contexto) {
		for (String s : List.of(linea, contexto)) {
			if (s.matches(".*(corner|esquina).*")) {
				return "corners";
			}
			if (s.matches(".*(tarjeta|amonest|card|booking).*")) {
				return "tarjetas";
			}
			if (s.matches(".*(a puerta|al arco|a porteria|on target|entre los tres palos).*")) {
				return "remates";
			}
			if (s.matches(".*(remate|tiro|disparo|shot).*")) {
				return "tiros";
			}
			if (s.matches(".*\\bgol.*")) {
				return "goles";
			}
		}
		return "goles";
	}

	/** El mercado de la app para esa línea, si existe ("goles.o25", "corners.o85", "goles.local"...). */
	static String clave(String tipo, double linea, boolean mas, Boolean local) {
		String n = String.valueOf((int) Math.round(linea * 10));
		if (local == null) {
			return tipo + (mas ? ".o" : ".u") + n;
		}
		if ("goles".equals(tipo) && mas && linea == 0.5) {
			return local ? "goles.local" : "goles.visita";
		}
		if ("goles".equals(tipo) && mas && linea == 1.5) {
			return local ? "equipos.local15" : "equipos.visita15";
		}
		if ("corners".equals(tipo) && mas && linea == 3.5) {
			return local ? "corners.local35" : "corners.visita35";
		}
		return null;
	}

	private static String regex(String nombre) {
		Set<String> w = NombresEquipos.palabrasDistintivas(nombre);
		if (w.isEmpty()) {
			w = NombresEquipos.palabras(nombre);
		}
		return w.isEmpty() ? "zzzz" : w.stream().map(Pattern::quote).collect(Collectors.joining("|"));
	}
}
