package pe.justaapuesta.picks;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class PicksDtos {

	private PicksDtos() {
	}

	/** Un pick de un partido (combinada o ranking). */
	public record PickPartidoDto(Long partidoId, String partido, Instant fecha, String clave, String texto, int pct,
			String categoria, double cuota, boolean blindado) {
	}

	/** @param nivel MUY_SEGURA, SEGURA o RETO; pct = probabilidad de que salgan todos */
	public record CombinadaDto(String nivel, String nombre, List<PickPartidoDto> picks, int pct, double cuota) {
	}

	public record PicksDelDiaDto(List<CombinadaDto> combinadas, List<PickPartidoDto> mejores) {
	}

	public record SeguirPickRequest(
			@NotNull(message = "Falta el partido.") Long partidoId,
			@NotBlank(message = "Falta el mercado.") @Size(max = 60) String mercadoClave) {
	}

	public record PickSeguidoDto(Long id, Long partidoId, String partido, Instant fecha, String mercadoClave,
			String texto, int probabilidad, double cuota, EstadoPick estado, Instant creadoEn) {
	}

	/** Boleto compartido: una foto de los picks en el momento de compartir (no cambia después). */
	public record BoletoDto(String codigo, String autor, Instant creadoEn, List<BoletoPickDto> picks, int pct,
			double cuota) {
	}

	public record BoletoPickDto(String partido, Instant fecha, String texto, int pct, double cuota) {
	}
}
