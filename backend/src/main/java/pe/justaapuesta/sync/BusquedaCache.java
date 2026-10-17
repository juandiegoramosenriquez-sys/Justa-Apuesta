package pe.justaapuesta.sync;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Una búsqueda ya enviada al proveedor; no se repite durante 7 días. */
@Entity
@Table(name = "busquedas_cache")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BusquedaCache {

	@Id
	@Column(length = 100)
	private String termino;

	@Column(name = "buscado_en", nullable = false)
	private Instant buscadoEn;
}
