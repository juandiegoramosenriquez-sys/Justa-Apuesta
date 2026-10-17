package pe.justaapuesta.sync;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiaSincronizadoRepository extends JpaRepository<DiaSincronizado, LocalDate> {

	@Query("select d.fecha from DiaSincronizado d where d.fecha in :fechas")
	List<LocalDate> yaDescargados(@Param("fechas") Collection<LocalDate> fechas);
}
