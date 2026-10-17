package pe.justaapuesta.partido;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JugadorPartidoRepository extends JpaRepository<JugadorPartido, JugadorPartido.Clave> {

	@Query("select j from JugadorPartido j where j.equipoId = :equipo and j.clave.partidoId in :partidos")
	List<JugadorPartido> deEquipoEnPartidos(@Param("equipo") Long equipoId, @Param("partidos") Collection<Long> partidoIds);

	@Modifying
	@Query("delete from JugadorPartido j where j.clave.partidoId = :id")
	void borrarDePartido(@Param("id") Long partidoId);
}
