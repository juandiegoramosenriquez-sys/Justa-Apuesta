package pe.justaapuesta.comun;

import java.text.Normalizer;
import java.util.Locale;

/** "Atlético  Madrid " -> "atletico madrid": para buscar sin importar tildes ni mayúsculas. */
public final class Normalizador {

	private Normalizador() {
	}

	public static String normalizar(String texto) {
		if (texto == null) {
			return "";
		}
		String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return sinTildes.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
	}
}
