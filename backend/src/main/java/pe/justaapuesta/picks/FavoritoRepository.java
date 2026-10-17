package pe.justaapuesta.picks;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FavoritoRepository extends JpaRepository<Favorito, Favorito.Clave> {

	@Query("select f.clave.partidoId from Favorito f where f.clave.usuarioId = :usuario")
	List<Long> partidosDe(@Param("usuario") Long usuarioId);
}
