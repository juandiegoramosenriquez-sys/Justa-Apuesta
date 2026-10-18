package pe.justaapuesta.analisis;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalisisCacheRepository extends JpaRepository<AnalisisCache, AnalisisCache.Clave> {

	@Query("select a from AnalisisCache a where a.clave.modo = 'BASE' and a.clave.partidoId in :ids")
	List<AnalisisCache> base(@Param("ids") Collection<Long> partidoIds);

	/**
	 * Borra el análisis de los partidos ya empezados que se analizaron por primera vez después de empezar, para que
	 * se recalculen con los datos nuevos (el cálculo solo usa partidos anteriores al inicio, así que sigue siendo
	 * honesto). Los de partidos por jugar no se borran: se recalculan en segundo plano.
	 */
	@Modifying
	@Query("""
			delete from AnalisisCache a where exists (select 1 from Partido p where p.id = a.clave.partidoId
			  and p.fechaHoraUtc <= :ahora and p.fechaHoraUtc < a.calculadoEn)""")
	int borrarRecalculables(@Param("ahora") Instant ahora);
}
