package pe.justaapuesta.analisis;

import java.io.Serializable;
import java.time.Instant;

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

/**
 * Entradas del modelo calculadas antes del partido (JSON). Se guardan con modo "BASE" porque el modo
 * solo cambia el orden de los consejos, no las probabilidades.
 */
@Entity
@Table(name = "analisis_cache")
@Getter
@Setter
@NoArgsConstructor
public class AnalisisCache {

	public static final String BASE = "BASE";

	@EmbeddedId
	private Clave clave;

	@Column(name = "json_resultado", nullable = false, columnDefinition = "LONGTEXT")
	private String json;

	@Column(name = "calculado_en", nullable = false)
	private Instant calculadoEn;

	@Embeddable
	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	@EqualsAndHashCode
	public static class Clave implements Serializable {

		@Column(name = "partido_id")
		private Long partidoId;

		@Column(length = 15)
		private String modo;
	}
}
