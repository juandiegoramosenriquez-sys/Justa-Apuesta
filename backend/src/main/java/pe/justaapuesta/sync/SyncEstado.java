package pe.justaapuesta.sync;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Última ejecución de cada tarea de sincronización. */
@Entity
@Table(name = "sync_estado")
@Getter
@Setter
@NoArgsConstructor
public class SyncEstado {

	@Id
	@Column(length = 40)
	private String clave;

	@Column(name = "ejecutado_en", nullable = false)
	private Instant ejecutadoEn;

	@Column(nullable = false)
	private boolean ok;

	@Column(length = 500)
	private String detalle;
}
