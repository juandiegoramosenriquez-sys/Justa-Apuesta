package pe.justaapuesta.usuario;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	Optional<Usuario> findByEmail(String email);

	boolean existsByEmail(String email);

	@Query("select l from Usuario u join u.ligasFavoritas l where u.id = :id")
	List<String> ligasFavoritas(@Param("id") Long usuarioId);
}
