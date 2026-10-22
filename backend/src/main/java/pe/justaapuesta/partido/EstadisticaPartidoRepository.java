package pe.justaapuesta.partido;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EstadisticaPartidoRepository extends JpaRepository<EstadisticaPartido, EstadisticaPartido.Clave> {

	@Query("select e from EstadisticaPartido e where e.clave.partidoId in :ids")
	List<EstadisticaPartido> dePartidos(@Param("ids") Collection<Long> partidoIds);

	/** Partidos que ya tienen corners guardados (de cualquier fuente). */
	@Query("select distinct e.clave.partidoId from EstadisticaPartido e where e.corners is not null")
	List<Long> partidosConCorners();

	@Modifying
	@Query("delete from EstadisticaPartido e where e.clave.partidoId = :id")
	void borrarDePartido(@Param("id") Long partidoId);
}
