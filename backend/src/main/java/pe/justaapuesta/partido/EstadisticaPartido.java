package pe.justaapuesta.partido;

import java.io.Serializable;
import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Estadísticas finales de un equipo en un partido. Cada valor es null si el proveedor no lo tiene. */
@Entity
@Table(name = "estadisticas_partido")
@Getter
@Setter
@NoArgsConstructor
public class EstadisticaPartido {

	@EmbeddedId
	private Clave clave;

	private Integer corners;

	@Column(name = "tarjetas_amarillas")
	private Integer tarjetasAmarillas;

	@Column(name = "tarjetas_rojas")
	private Integer tarjetasRojas;

	@Column(name = "remates_a_puerta")
	private Integer rematesAPuerta;

	@Column(name = "remates_totales")
	private Integer rematesTotales;

	@Column(precision = 5, scale = 2)
	private BigDecimal posesion;

	/** Amarillas + rojas, o null si no hay ninguno de los dos datos. */
	public Integer tarjetas() {
		if (tarjetasAmarillas == null && tarjetasRojas == null) {
			return null;
		}
		return (tarjetasAmarillas == null ? 0 : tarjetasAmarillas) + (tarjetasRojas == null ? 0 : tarjetasRojas);
	}

	@Embeddable
	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	@EqualsAndHashCode
	public static class Clave implements Serializable {

		@Column(name = "partido_id")
		private Long partidoId;

		@Column(name = "equipo_id")
		private Long equipoId;
	}
}
