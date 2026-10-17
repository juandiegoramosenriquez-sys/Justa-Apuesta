package pe.justaapuesta.partido;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import pe.justaapuesta.config.JustaProperties;

class EquiposFamososTest {

	private final EquiposFamosos famosos = new EquiposFamosos(new JustaProperties(null, "America/Lima", null, null, null,
			null, null, null), null);

	private static Partido partido(long liga, long local, long visita) {
		Liga l = new Liga();
		l.setProveedorId(liga);
		Equipo a = new Equipo();
		a.setId(local);
		Equipo b = new Equipo();
		b.setId(visita);
		Partido p = new Partido();
		p.setLiga(l);
		p.setLocal(a);
		p.setVisita(b);
		return p;
	}

	@Test
	void muestraPartidosDeEquiposQueJueganCopasInternacionales() {
		Set<Long> deCopas = Set.of(1L);
		// Champions con un equipo famoso
		assertThat(famosos.esPartidoFamoso(partido(2, 1, 99), deCopas)).isTrue();
		// Libertadores, el famoso de visita
		assertThat(famosos.esPartidoFamoso(partido(13, 99, 1), deCopas)).isTrue();
	}

	@Test
	void dejaLosDemasParaElBuscador() {
		Set<Long> deCopas = Set.of(1L);
		// Ningún equipo jugó copas internacionales
		assertThat(famosos.esPartidoFamoso(partido(2, 98, 99), deCopas)).isFalse();
		// Amistoso o liga juvenil (no es liga principal), aunque juegue un famoso
		assertThat(famosos.esPartidoFamoso(partido(667, 1, 99), deCopas)).isFalse();
	}
}
