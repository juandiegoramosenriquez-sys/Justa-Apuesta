package pe.justaapuesta.partido;

import java.io.Serializable;

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

/** Lo que hizo un jugador en un partido jugado. */
@Entity
@Table(name = "jugadores_partido")
@Getter
@Setter
@NoArgsConstructor
public class JugadorPartido {

	@EmbeddedId
	private Clave clave;

	@Column(name = "equipo_id", nullable = false)
	private Long equipoId;

	@Column(nullable = false, length = 100)
	private String nombre;

	@Column(length = 10)
	private String posicion;

	private Integer dorsal;

	@Column(nullable = false)
	private int minutos;

	@Column(nullable = false)
	private int goles;

	@Column(name = "remates_a_puerta", nullable = false)
	private int rematesAPuerta;

	@Column(nullable = false)
	private int amarillas;

	@Column(nullable = false)
	private int rojas;

	@Embeddable
	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	@EqualsAndHashCode
	public static class Clave implements Serializable {

		@Column(name = "partido_id")
		private Long partidoId;

		@Column(name = "jugador_id")
		private Long jugadorId;
	}
}
