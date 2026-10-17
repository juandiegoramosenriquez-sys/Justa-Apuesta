package pe.justaapuesta.partido;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LigaRepository extends JpaRepository<Liga, Long> {

	List<Liga> findByProveedorIdIn(Collection<Long> proveedorIds);

	@Query("select l from Liga l where l.nombreNormalizado like concat('%', :q, '%')")
	List<Liga> buscar(@Param("q") String normalizado);
}
