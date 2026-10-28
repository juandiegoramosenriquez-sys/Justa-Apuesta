package pe.justaapuesta.partido;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EquipoRepository extends JpaRepository<Equipo, Long> {

	List<Equipo> findByProveedorIdIn(Collection<Long> proveedorIds);

	List<Equipo> findByFdIdIn(Collection<Long> fdIds);

	/** Los que empiezan con el texto salen primero; luego los que lo contienen. Con su liga (para saber el país). */
	@Query("""
			select e from Equipo e left join fetch e.liga
			where e.nombreNormalizado like concat('%', :q, '%')
			order by case when e.nombreNormalizado like concat(:q, '%') then 0 else 1 end, length(e.nombreNormalizado)""")
	List<Equipo> buscar(@Param("q") String normalizado, Pageable pagina);
}
