package pe.justaapuesta.usuario;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 80)
	private String nombre;

	@Column(nullable = false, length = 120, unique = true)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 15)
	private Modo modo = Modo.SEGURO;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Tema tema = Tema.OSCURO;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Acento acento = Acento.LIMON;

	@Column(name = "tip_visto", nullable = false)
	private boolean tipVisto;

	@Column(name = "creado_en", nullable = false)
	private Instant creadoEn;

	@ElementCollection
	@CollectionTable(name = "ligas_favoritas", joinColumns = @JoinColumn(name = "usuario_id"))
	@Column(name = "nombre_liga", length = 100)
	private Set<String> ligasFavoritas = new LinkedHashSet<>();
}
