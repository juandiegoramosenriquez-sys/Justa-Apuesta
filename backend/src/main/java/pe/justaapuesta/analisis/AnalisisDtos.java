package pe.justaapuesta.analisis;

import java.time.Instant;
import java.util.List;

import pe.justaapuesta.partido.EstadoPartido;

/**
 * Lo que recibe el frontend. Los colores se deciden en el frontend con "tono" (VERDE, ROJO, MEDIO).
 */
public final class AnalisisDtos {

	private AnalisisDtos() {
	}

	public record EquipoDto(Long id, String nombre, String escudo) {
	}

	public record LigaDto(String nombre, String pais, String logo) {
	}

	/** Consejo principal de un partido según el modo. acerto = null si aún no se jugó o falta el dato. */
	public record ConsejoDto(String clave, String categoria, String texto, int pct, String confianza, Boolean acerto,
			double cuota, boolean blindado) {
	}

	/** Tarjeta de partido (listas, búsqueda, favoritos). */
	public record PartidoResumenDto(
			Long id,
			Instant fecha,
			LigaDto liga,
			String estadio,
			EstadoPartido estado,
			EquipoDto local,
			EquipoDto visita,
			Integer golesLocal,
			Integer golesVisita,
			boolean favorito,
			/** null si no hay datos suficientes para analizar */
			ConsejoDto consejo) {

		public PartidoResumenDto conFavorito(boolean f) {
			return f == favorito ? this
					: new PartidoResumenDto(id, fecha, liga, estadio, estado, local, visita, golesLocal, golesVisita, f, consejo);
		}
	}

	/** Lista con la hora de la última actualización y un aviso suave si el proveedor falló. */
	/** @param buscandoMas la búsqueda sigue en otra fuente: la web vuelve a pedirla en unos segundos */
	public record ListaPartidosDto(List<PartidoResumenDto> partidos, Instant actualizadoEn, String aviso,
			boolean buscandoMas) {
		public ListaPartidosDto(List<PartidoResumenDto> partidos, Instant actualizadoEn, String aviso) {
			this(partidos, actualizadoEn, aviso, false);
		}
	}

	public record GanadorDto(int h, int d, int a, String veredicto) {
	}

	/** @param modelo lo que dice solo el modelo; pct es la certeza final con las pruebas */
	public record MercadoDto(String clave, String texto, int pct, String tono, Boolean salio, double cuota, int modelo,
			boolean blindado, List<Motor.Prueba> pruebas) {
	}

	public record CategoriaDto(String clave, String nombre, String promedio, List<MercadoDto> mercados) {
	}

	public record FinalDto(int golesLocal, int golesVisita, Integer corners, Integer tarjetas, Integer rematesAPuerta,
			String resumen) {
	}

	public record ComparaDto(String etiqueta, double local, double visita) {
	}

	public record PickDto(String clave, String texto, int pct, String categoria, double cuota, boolean blindado) {
	}

	/** Detalle completo del partido (todas las secciones del prototipo, en orden). */
	public record AnalisisDto(
			PartidoResumenDto partido,
			boolean datosSuficientes,
			String mensaje,
			String rachaLocal,
			String rachaVisita,
			GanadorDto ganador,
			ConsejoDto consejo,
			String porQue,
			boolean siguiendo,
			List<PickDto> tambien,
			List<PickDto> evita,
			Entradas.H2h h2h,
			List<CategoriaDto> categorias,
			FinalDto resultadoFinal,
			List<ComparaDto> caraACara,
			List<Motor.JugadorClave> jugadores,
			String aviso) {
	}

	/** Un partido de la racha de un equipo. */
	public record UltimoPartidoDto(Long id, Instant fecha, String rival, boolean enCasa, int golesFavor,
			int golesContra, String resultado) {
	}

	public record UltimosDto(String equipo, String resumen, List<UltimoPartidoDto> partidos) {
	}
}
