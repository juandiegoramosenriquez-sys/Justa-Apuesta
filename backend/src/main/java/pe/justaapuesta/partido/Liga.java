package pe.justaapuesta.partido;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ligas")
@Getter
@Setter
@NoArgsConstructor
public class Liga {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "proveedor_id", nullable = false, unique = true)
	private Long proveedorId;

	@Column(nullable = false, length = 100)
	private String nombre;

	@Column(name = "nombre_normalizado", nullable = false, length = 100)
	private String nombreNormalizado;

	@Column(length = 80)
	private String pais;

	@Column(name = "logo_url", length = 255)
	private String logoUrl;
}
