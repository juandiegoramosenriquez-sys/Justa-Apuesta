package pe.justaapuesta.partido;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "equipos")
@Getter
@Setter
@NoArgsConstructor
public class Equipo {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "proveedor_id", nullable = false, unique = true)
	private Long proveedorId;

	@Column(nullable = false, length = 100)
	private String nombre;

	@Column(name = "nombre_normalizado", nullable = false, length = 100)
	private String nombreNormalizado;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "liga_id")
	private Liga liga;

	@Column(length = 120)
	private String estadio;

	@Column(name = "escudo_url", length = 255)
	private String escudoUrl;

	@Column(name = "actualizado_en", nullable = false)
	private Instant actualizadoEn;

	@Column(name = "partidos_sync_en")
	private Instant partidosSyncEn;

	/** Id en football-data.org (si el equipo ya se vinculó entre las dos fuentes). */
	@Column(name = "fd_id", unique = true)
	private Long fdId;

	/** País de su liga (null si no se sabe). */
	public String pais() {
		return liga == null ? null : liga.getPais();
	}

	/** true si el equipo viene solo de una fuente de historial (football-data.org o TheSportsDB), aún sin vincular. */
	public boolean soloFootballData() {
		return proveedorId != null && proveedorId < 0;
	}
}
