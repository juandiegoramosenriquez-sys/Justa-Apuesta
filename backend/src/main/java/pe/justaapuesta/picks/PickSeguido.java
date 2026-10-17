package pe.justaapuesta.picks;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "picks_seguidos")
@Getter
@Setter
@NoArgsConstructor
public class PickSeguido {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "usuario_id", nullable = false)
	private Long usuarioId;

	@Column(name = "partido_id", nullable = false)
	private Long partidoId;

	@Column(name = "mercado_clave", nullable = false, length = 60)
	private String mercadoClave;

	@Column(nullable = false, length = 150)
	private String texto;

	@Column(nullable = false)
	private int probabilidad;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 12)
	private EstadoPick estado = EstadoPick.PENDIENTE;

	@Column(name = "creado_en", nullable = false)
	private Instant creadoEn;
}
