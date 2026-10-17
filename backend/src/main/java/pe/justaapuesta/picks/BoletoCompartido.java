package pe.justaapuesta.picks;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Boleto compartido por link: guarda la foto de los picks tal como estaban al compartir. */
@Entity
@Table(name = "boletos_compartidos")
@Getter
@Setter
@NoArgsConstructor
public class BoletoCompartido {

	@Id
	@Column(length = 12)
	private String codigo;

	@Column(name = "usuario_id", nullable = false)
	private Long usuarioId;

	@Column(name = "json_boleto", nullable = false, columnDefinition = "LONGTEXT")
	private String json;

	@Column(name = "creado_en", nullable = false)
	private Instant creadoEn;
}
