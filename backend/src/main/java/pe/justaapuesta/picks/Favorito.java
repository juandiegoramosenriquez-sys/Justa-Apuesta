package pe.justaapuesta.picks;

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

@Entity
@Table(name = "favoritos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Favorito {

	@EmbeddedId
	private Clave clave;

	@Column(name = "creado_en", nullable = false)
	private Instant creadoEn;

	@Embeddable
	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	@EqualsAndHashCode
	public static class Clave implements Serializable {

		@Column(name = "usuario_id")
		private Long usuarioId;

		@Column(name = "partido_id")
		private Long partidoId;
	}
}
