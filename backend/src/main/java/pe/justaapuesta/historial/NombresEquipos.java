package pe.justaapuesta.historial;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import pe.justaapuesta.comun.Normalizador;

/**
 * Compara nombres de equipos entre fuentes ("Paris Saint-Germain FC" vs "Paris Saint Germain", "Barça" vs
 * "FC Barcelona"). Se usa junto con la misma liga y la misma hora de inicio.
 */
public final class NombresEquipos {

	/** Palabras que no identifican a un equipo. */
	private static final Set<String> COMUNES = Set.of("fc", "cf", "afc", "ac", "sc", "ssc", "cd", "ud", "sd", "rc",
			"rcd", "ca", "club", "de", "del", "la", "el", "le", "les", "the", "fk", "sk", "sv", "bv", "us", "as",
			"ogc", "vfb", "vfl", "tsg", "1", "04", "05", "1899", "1909", "1846", "calcio", "futbol", "football", "cp", "sl");

	/** Palabras que comparten muchos equipos distintos: coincidir en ellas vale poco ("Real" Madrid / "Real" Betis). */
	private static final Set<String> DEBILES = Set.of("real", "city", "united", "utd", "athletic", "atletico",
			"sporting", "inter", "internacional", "union", "racing", "stade", "olympique", "dynamo", "dinamo",
			"borussia", "deportivo", "independiente", "san", "santa", "saint", "st", "new", "town", "county", "rovers",
			"wanderers", "albion", "nacional", "universidad", "juniors", "sport", "sports", "clube", "esporte",
			"atletica", "academy", "olimpia", "estudiantes", "central", "montevideo", "rio");

	/** Prefijos típicos de club: "Lyon" y "Olympique Lyon" son el mismo; "Rangers" y "Queens Park Rangers" no. */
	private static final Set<String> PREFIJOS_DE_CLUB = Set.of("olympique", "stade", "racing", "real", "athletic",
			"atletico", "sporting", "deportivo", "union", "dynamo", "dinamo", "borussia", "sport", "clube", "esporte");

	/** Palabras de equipos juveniles, filiales o femeninos ("PSG U19", "Barcelona B", "SC Freiburg W"). */
	private static final Set<String> JUVENILES = Set.of("u17", "u18", "u19", "u20", "u21", "u23", "ii", "b", "w",
			"women", "femenino", "feminino", "femenil", "fem", "reserves", "reserve", "res", "youth", "juvenil", "sub",
			"sub20");

	/** Puntaje que indica "es el mismo nombre". */
	public static final int IGUAL = 4;

	/** Separador entre los nombres de un equipo dentro de nombre_normalizado: "barca|fc barcelona|fcb". */
	public static final String SEPARADOR = "|";

	private NombresEquipos() {
	}

	/** "Barça" + ["FC Barcelona", "FCB"] -> "barca|fc barcelona|fcb" (sin repetidos). */
	public static String variantes(String nombre, List<String> otros) {
		LinkedHashSet<String> v = new LinkedHashSet<>();
		v.add(Normalizador.normalizar(nombre));
		if (otros != null) {
			otros.forEach(o -> v.add(Normalizador.normalizar(o)));
		}
		v.remove("");
		return String.join(SEPARADOR, v);
	}

	public static List<String> variantesDe(String nombreNormalizado) {
		return nombreNormalizado == null ? List.of()
				: Arrays.stream(nombreNormalizado.split("\\" + SEPARADOR)).filter(s -> !s.isBlank()).toList();
	}

	public static Set<String> palabras(String nombre) {
		return Arrays.stream(limpiar(nombre).split(" ")).filter(p -> p.length() > 1 && !COMUNES.contains(p))
				.collect(Collectors.toSet());
	}

	/**
	 * Qué tanto se parecen dos nombres: 4 = el mismo (igual, mismas palabras o sigla), 3 = comparten una palabra
	 * distintiva, 2 = una palabra empieza como la otra ("Man" / "Manchester", "Atleti" / "Atlético"),
	 * 1 = solo comparten una palabra común ("Real", "City"), 0 = nada.
	 */
	public static int similitud(String a, String b) {
		String la = limpiar(a), lb = limpiar(b);
		if (la.isEmpty() || lb.isEmpty()) {
			return 0;
		}
		Set<String> pa = palabras(a), pb = palabras(b);
		if (la.equals(lb) || (!pa.isEmpty() && pa.equals(pb)) || esSigla(a, b) || esSigla(b, a)
				|| soloSumaPrefijos(pa, pb) || soloSumaPrefijos(pb, pa) || siglaInicial(la, lb) || siglaInicial(lb, la)) {
			return IGUAL;
		}
		int mejor = 0;
		for (String x : pa) {
			for (String y : pb) {
				if (x.equals(y)) {
					mejor = Math.max(mejor, DEBILES.contains(x) ? 1 : 3);
				} else if ((x.length() >= 3 && y.length() > x.length() && y.startsWith(x))
						|| (y.length() >= 3 && x.length() > y.length() && x.startsWith(y))) {
					mejor = Math.max(mejor, 2);
				}
			}
		}
		return mejor;
	}

	/** Similitud contra el mejor de los nombres de un equipo ("barca|fc barcelona|fcb"). */
	public static int similitudConVariantes(String nombre, String nombreNormalizadoConVariantes) {
		return variantesDe(nombreNormalizadoConVariantes).stream().mapToInt(v -> similitud(nombre, v)).max().orElse(0);
	}

	/** Se parecen lo suficiente para ser el mismo equipo (dentro del mismo partido). */
	public static boolean parecidos(String a, String b) {
		return similitud(a, b) >= 2;
	}

	/** Regiones de copas internacionales: un equipo de una copa no tiene país conocido. */
	private static final Set<String> INTERNACIONAL = Set.of("", "world", "worldwide", "international", "europe",
			"south america", "sudamerica", "north america", "central america", "africa", "asia", "oceania", "conmebol",
			"uefa", "concacaf", "mundo");
	private static final java.util.Map<String, String> PAISES = java.util.Map.ofEntries(
			java.util.Map.entry("united states", "usa"), java.util.Map.entry("united states of america", "usa"),
			java.util.Map.entry("estados unidos", "usa"), java.util.Map.entry("inglaterra", "england"),
			java.util.Map.entry("espana", "spain"), java.util.Map.entry("italia", "italy"),
			java.util.Map.entry("alemania", "germany"), java.util.Map.entry("francia", "france"),
			java.util.Map.entry("peru", "peru"), java.util.Map.entry("brasil", "brazil"),
			java.util.Map.entry("paises bajos", "netherlands"), java.util.Map.entry("holland", "netherlands"));

	/**
	 * Dos equipos pueden ser el mismo solo si son del mismo país (o si alguno no tiene país conocido, por ejemplo
	 * porque solo lo vimos en una copa internacional).
	 */
	public static boolean mismoPais(String a, String b) {
		String pa = pais(a), pb = pais(b);
		return INTERNACIONAL.contains(pa) || INTERNACIONAL.contains(pb) || pa.equals(pb);
	}

	private static String pais(String p) {
		String n = limpiar(p == null ? "" : p);
		return PAISES.getOrDefault(n, n);
	}

	/** Nombre sin tildes ni signos y sin palabras comunes, en orden: "FC Barcelona" -> "barcelona". */
	public static String clave(String nombre) {
		return Arrays.stream(limpiar(nombre).split(" ")).filter(p -> !p.isEmpty() && !COMUNES.contains(p))
				.collect(Collectors.joining(" "));
	}

	/**
	 * Qué tan bien un equipo responde a lo que el usuario escribió (contra cada uno de sus nombres):
	 * 4 = es exactamente ese nombre ("barcelona" / "FC Barcelona"), 3 = el nombre empieza así ("real" / "Real Madrid"),
	 * 2 = alguna palabra empieza así ("madrid" / "Real Madrid"), 1 = solo lo contiene.
	 */
	public static int relevancia(String busqueda, String nombreNormalizadoConVariantes) {
		String q = clave(busqueda);
		String qLimpio = limpiar(busqueda);
		int mejor = 0;
		for (String v : variantesDe(nombreNormalizadoConVariantes)) {
			String c = clave(v);
			String l = limpiar(v);
			int p;
			if ((!q.isEmpty() && c.equals(q)) || l.equals(qLimpio)) {
				p = 4;
			} else if ((!q.isEmpty() && c.startsWith(q)) || l.startsWith(qLimpio)) {
				p = 3;
			} else if (Arrays.stream(l.split(" ")).anyMatch(w -> w.startsWith(qLimpio))) {
				p = 2;
			} else {
				p = l.contains(qLimpio) ? 1 : 0;
			}
			mejor = Math.max(mejor, p);
		}
		return mejor;
	}

	public static boolean esJuvenilOFemenino(String nombre) {
		return Arrays.stream(limpiar(nombre).split(" ")).anyMatch(JUVENILES::contains);
	}

	/** El nombre largo tiene las mismas palabras que el corto más solo prefijos de club ("Olympique Lyon" / "Lyon"). */
	private static boolean soloSumaPrefijos(Set<String> corto, Set<String> largo) {
		return !corto.isEmpty() && largo.size() > corto.size() && largo.containsAll(corto)
				&& largo.stream().filter(p -> !corto.contains(p)).allMatch(PREFIJOS_DE_CLUB::contains);
	}

	/** Sigla de 2 o 3 letras que es la primera palabra del otro nombre ("AZ" / "AZ Alkmaar", "PSV" / "PSV Eindhoven"). */
	private static boolean siglaInicial(String corto, String largo) {
		// "NEW" (Newcastle) no es "New York City": las palabras comunes no cuentan como sigla
		return !corto.contains(" ") && corto.length() >= 2 && corto.length() <= 3 && !COMUNES.contains(corto)
				&& !DEBILES.contains(corto) && largo.startsWith(corto + " ");
	}

	/** "psg" es la sigla de "Paris Saint-Germain FC" (iniciales de sus palabras, sin contar FC, CF...). */
	static boolean esSigla(String corto, String largo) {
		String sigla = limpiar(corto).replace(" ", "");
		if (sigla.length() < 2 || sigla.length() > 5 || limpiar(corto).contains(" ")) {
			return false;
		}
		String iniciales = Arrays.stream(limpiar(largo).split(" "))
				.filter(p -> !p.isEmpty() && !COMUNES.contains(p))
				.map(p -> p.substring(0, 1))
				.collect(Collectors.joining());
		return iniciales.length() >= 2 && iniciales.equals(sigla);
	}

	/** Sin tildes, minúsculas, sin signos y con espacios simples. */
	private static String limpiar(String s) {
		return Normalizador.normalizar(s).replaceAll("[^a-z0-9 ]", " ").trim().replaceAll("\\s+", " ");
	}
}
