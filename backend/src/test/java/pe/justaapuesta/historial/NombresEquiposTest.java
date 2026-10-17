package pe.justaapuesta.historial;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Nombres del mismo equipo en API-Football y football-data.org. */
class NombresEquiposTest {

	@Test
	void mismosEquiposConNombresDistintos() {
		assertThat(NombresEquipos.parecidos("Paris Saint Germain", "PSG")).isTrue();
		assertThat(NombresEquipos.parecidos("PSG", "Paris Saint-Germain FC")).isTrue();
		assertThat(NombresEquipos.parecidos("Manchester City", "Man City")).isTrue();
		assertThat(NombresEquipos.parecidos("Inter", "FC Internazionale Milano")).isTrue();
		assertThat(NombresEquipos.parecidos("Atletico Madrid", "Atleti")).isTrue();
		assertThat(NombresEquipos.parecidos("1. FC Köln", "1. FC Koln")).isTrue();
	}

	@Test
	void equiposDistintosNoSeUnen() {
		assertThat(NombresEquipos.parecidos("AS Roma", "SS Lazio")).isFalse();
		assertThat(NombresEquipos.parecidos("Liverpool", "Everton")).isFalse();
		assertThat(NombresEquipos.parecidos("PSV", "Paris Saint Germain")).isFalse();
		assertThat(NombresEquipos.parecidos("Real Betis", "Villarreal")).isFalse();
	}

	@Test
	void siglaComunNoUneEquiposDistintos() {
		// football-data.org le da a Newcastle la sigla "NEW": no es "New York City"
		assertThat(NombresEquipos.similitudConVariantes("New York City", "newcastle|newcastle united fc|new"))
				.isLessThan(NombresEquipos.IGUAL);
		// Una sigla real sí: "PSV" / "PSV Eindhoven"
		assertThat(NombresEquipos.similitud("PSV", "PSV Eindhoven")).isEqualTo(NombresEquipos.IGUAL);
	}

	@Test
	void mismoPais() {
		assertThat(NombresEquipos.mismoPais("England", "USA")).isFalse();
		assertThat(NombresEquipos.mismoPais("USA", "United States")).isTrue();
		assertThat(NombresEquipos.mismoPais("Spain", "España")).isTrue();
		assertThat(NombresEquipos.mismoPais("World", "Peru")).isTrue(); // copa internacional: país desconocido
		assertThat(NombresEquipos.mismoPais(null, "Argentina")).isTrue();
	}

	@Test
	void relevanciaDeLaBusqueda() {
		// "barcelona" es exactamente FC Barcelona; para Espanyol solo es una palabra más
		assertThat(NombresEquipos.relevancia("barcelona", "barca|fc barcelona|fcb")).isEqualTo(4);
		assertThat(NombresEquipos.relevancia("barcelona", "espanyol|rcd espanyol de barcelona|esp")).isEqualTo(2);
		// "milan": AC Milan antes que el Inter de Milano
		assertThat(NombresEquipos.relevancia("milan", "milan|ac milan|mil")).isEqualTo(4);
		assertThat(NombresEquipos.relevancia("milan", "inter|fc internazionale milano|int")).isEqualTo(2);
		// "boca": Boca Juniors empieza así
		assertThat(NombresEquipos.relevancia("boca", "boca juniors")).isEqualTo(3);
		assertThat(NombresEquipos.relevancia("universitario", "juventud unida universitario")).isEqualTo(2);
		assertThat(NombresEquipos.relevancia("universitario", "universitario")).isEqualTo(4);
	}
}
