package pe.justaapuesta.partido;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "partidos")
@Getter
@Setter
@NoArgsConstructor
public class Partido {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "proveedor_id", nullable = false, unique = true)
	private Long proveedorId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "liga_id")
	private Liga liga;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "local_id")
	private Equipo local;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "visita_id")
	private Equipo visita;

	@Column(name = "fecha_hora_utc", nullable = false)
	private Instant fechaHoraUtc;

	@Column(length = 120)
	private String estadio;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 12)
	private EstadoPartido estado;

	@Column(name = "goles_local")
	private Integer golesLocal;

	@Column(name = "goles_visita")
	private Integer golesVisita;

	@Column(name = "actualizado_en", nullable = false)
	private Instant actualizadoEn;

	private Integer temporada;

	@Column(name = "estado_proveedor", length = 10)
	private String estadoProveedor;

	@Column(name = "detalle_sync", nullable = false)
	private boolean detalleSync;

	public boolean jugado() {
		return estado == EstadoPartido.JUGADO && golesLocal != null && golesVisita != null;
	}
}
