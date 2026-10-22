package pe.justaapuesta.historial;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pe.justaapuesta.partido.Equipo;
import pe.justaapuesta.partido.Partido;

/** Lectura de ESPN y de football-data.co.uk (corners, tarjetas y remates). */
class FuentesEstadisticasTest {

	@Test
	void leeUnPartidoJugadoDeEspnConSusEstadisticas() {
		String json = """
				{"leagues":[{"id":"775","logos":[{"href":"https://x/l.png"}]}],
				 "events":[{"id":"401","date":"2026-10-22T19:00Z",
				  "status":{"type":{"name":"STATUS_FULL_TIME","state":"post","completed":true}},
				  "competitions":[{"venue":{"fullName":"Anfield"},
				   "competitors":[
				    {"homeAway":"home","score":"2","team":{"id":"364","displayName":"Liverpool","logo":"https://x/364.png"},
				     "statistics":[{"name":"wonCorners","displayValue":"7"},{"name":"shotsOnTarget","displayValue":"6"},
				                   {"name":"totalShots","displayValue":"15"},{"name":"possessionPct","displayValue":"61.5"}]},
				    {"homeAway":"away","score":"1","team":{"id":"337","displayName":"Brentford"},
				     "statistics":[{"name":"wonCorners","displayValue":"3"},{"name":"shotsOnTarget","displayValue":"2"},
				                   {"name":"totalShots","displayValue":"8"}]}],
				   "details":[{"type":{"text":"Yellow Card"},"team":{"id":"337"}},{"type":{"text":"Yellow Card"},"team":{"id":"337"}},
				              {"type":{"text":"Red Card"},"team":{"id":"364"}},{"type":{"text":"Goal"},"team":{"id":"364"}}]}]}]}
				""";
		List<EspnService.EventoEspn> eventos = EspnService.leer(json);

		assertThat(eventos).hasSize(1);
		EspnService.EventoEspn e = eventos.get(0);
		assertThat(e.fecha()).isEqualTo(Instant.parse("2026-10-22T19:00:00Z"));
		assertThat(e.estado()).isEqualTo("post");
		assertThat(e.local()).isEqualTo("Liverpool");
		assertThat(e.golesLocal()).isEqualTo(2);
		assertThat(e.golesVisita()).isEqualTo(1);
		assertThat(e.statsLocal().corners()).isEqualTo(7);
		assertThat(e.statsLocal().rematesTotales()).isEqualTo(15);
		assertThat(e.statsLocal().rojas()).isEqualTo(1);
		assertThat(e.statsVisita().amarillas()).isEqualTo(2);
		assertThat(e.statsVisita().rematesAPuerta()).isEqualTo(2);
	}

	@Test
	void unPartidoPorJugarDeEspnNoTraeMarcador() {
		String json = """
				{"leagues":[{"id":"760"}],"events":[{"id":"9","date":"2026-10-23T01:06Z",
				 "status":{"type":{"name":"STATUS_SCHEDULED","state":"pre","completed":false}},
				 "competitions":[{"competitors":[
				  {"homeAway":"home","score":"0","team":{"id":"1","displayName":"Atlas"}},
				  {"homeAway":"away","score":"0","team":{"id":"2","displayName":"León"}}]}]}]}
				""";
		EspnService.EventoEspn e = EspnService.leer(json).get(0);
		assertThat(e.estado()).isEqualTo("pre");
		assertThat(e.golesLocal()).isNull();
		assertThat(e.statsLocal()).isNull();
	}

	@Test
	void leeElCsvDeFootballDataCoUk() {
		String csv = "﻿Div,Date,Time,HomeTeam,AwayTeam,FTHG,FTAG,FTR,HS,AS,HST,AST,HF,AF,HC,AC,HY,AY,HR,AR\n"
				+ "E0,19/10/2026,20:00,Tottenham,Coventry,3,1,H,16,12,5,1,15,14,4,4,1,3,0,0\n"
				+ "E0,25/10/2026,15:00,Arsenal,Chelsea,,,,,,,,,,,,,,,\n";
		List<EstadisticasCsvService.Fila> filas = EstadisticasCsvService.leer(csv);

		assertThat(filas).hasSize(1);
		EstadisticasCsvService.Fila f = filas.get(0);
		assertThat(f.local()).isEqualTo("Tottenham");
		assertThat(f.inicio()).isEqualTo(Instant.parse("2026-10-19T19:00:00Z")); // 20:00 en Londres (horario de verano)
		assertThat(f.statsLocal().rematesTotales()).isEqualTo(16);
		assertThat(f.statsVisita().amarillas()).isEqualTo(3);
		assertThat(f.statsLocal().corners()).isEqualTo(4);
	}

	@Test
	void unaFilaDelCsvSeUneConElPartidoDelMismoDiaYMarcador() {
		EstadisticasCsvService.Fila fila = EstadisticasCsvService.leer(
				"Date,Time,HomeTeam,AwayTeam,FTHG,FTAG,HC,AC\n19/10/2026,20:00,Man United,Nott'm Forest,2,0,6,2\n").get(0);
		Partido bueno = partido(1L, "Manchester United FC", "Nottingham Forest FC", "2026-10-19T19:00:00Z", 2, 0);
		Partido otroMarcador = partido(2L, "Manchester United FC", "Nottingham Forest FC", "2026-10-19T19:00:00Z", 1, 0);
		Partido otroEquipo = partido(3L, "Manchester City FC", "Fulham FC", "2026-10-19T19:00:00Z", 2, 0);

		Partido elegido = EstadisticasCsvService.buscar(fila,
				Map.of(LocalDate.parse("2026-10-19"), List.of(otroMarcador, otroEquipo, bueno)));

		assertThat(elegido).isSameAs(bueno);
	}

	@Test
	void unPartidoDeEspnSeReconoceAunqueElEquipoEsteGuardadoConOtroNombre() {
		EspnService.EventoEspn ev = new EspnService.EventoEspn(1, 775, null, Instant.parse("2026-10-20T19:00:00Z"), "post",
				null, 364, "Liverpool", null, 9, "Eintracht Frankfurt", null, 4, 3, null, null);
		Partido otro = partido(10L, "Liverpool FC", "Eintracht Frankfurt", "2026-10-20T19:00:00Z", 4, 3);
		Partido distinto = partido(11L, "Everton FC", "Eintracht Frankfurt", "2026-10-20T19:00:00Z", 4, 3);

		Partido elegido = EspnService.mismoPartido(ev, EspnService.porDia(List.of(distinto, otro)));

		assertThat(elegido).isSameAs(otro);
	}

	@Test
	void temporadaDelCsv() {
		assertThat(EstadisticasCsvService.temporada(LocalDate.parse("2026-10-22"))).isEqualTo("2627");
		assertThat(EstadisticasCsvService.temporada(LocalDate.parse("2026-03-01"))).isEqualTo("2526");
	}

	private static Partido partido(Long id, String local, String visita, String fecha, int gl, int gv) {
		Partido p = new Partido();
		p.setId(id);
		p.setLocal(equipo(local));
		p.setVisita(equipo(visita));
		p.setFechaHoraUtc(Instant.parse(fecha));
		p.setGolesLocal(gl);
		p.setGolesVisita(gv);
		return p;
	}

	private static Equipo equipo(String nombre) {
		Equipo e = new Equipo();
		e.setNombre(nombre);
		e.setNombreNormalizado(pe.justaapuesta.comun.Normalizador.normalizar(nombre));
		return e;
	}
}
