package pe.justaapuesta.sync;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Un día cuyos partidos ya se descargaron del proveedor. */
@Entity
@Table(name = "dias_sincronizados")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DiaSincronizado {

	@Id
	private LocalDate fecha;

	@Column(name = "sincronizado_en", nullable = false)
	private Instant sincronizadoEn;

	@Column(nullable = false)
	private int partidos;
}
