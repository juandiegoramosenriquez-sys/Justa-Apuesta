package pe.justaapuesta.partido;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Errores de escritura y apodos en la búsqueda. */
class BusquedaTest {

	@Test
	void unaOdosLetrasMalEscritasCuentanComoParecidas() {
		assertThat(PartidosService.distancia("liverpol", "liverpool")).isEqualTo(1);
		assertThat(PartidosService.distancia("barcelno", "barcelona")).isEqualTo(2);
		assertThat(PartidosService.distancia("madrid", "madrid")).isZero();
		assertThat(PartidosService.distancia("ajax", "manchester")).isGreaterThan(2);
	}

	@Test
	void losApodosSeTraducen() {
		assertThat(PartidosService.APODOS).containsEntry("psg", "paris saint germain").containsEntry("la u", "universitario");
	}
}
