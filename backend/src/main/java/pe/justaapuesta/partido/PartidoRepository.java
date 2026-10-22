package pe.justaapuesta.partido;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartidoRepository extends JpaRepository<Partido, Long> {

	String CON_EQUIPOS = "select p from Partido p join fetch p.local join fetch p.visita join fetch p.liga ";

	List<Partido> findByProveedorIdIn(Collection<Long> proveedorIds);

	@Query(CON_EQUIPOS + "where p.id = :id")
	Optional<Partido> conEquipos(@Param("id") Long id);

	@Query(CON_EQUIPOS + "where p.id in :ids")
	List<Partido> conEquipos(@Param("ids") Collection<Long> ids);

	@Query(CON_EQUIPOS + "where p.fechaHoraUtc >= :desde and p.fechaHoraUtc < :hasta order by p.fechaHoraUtc")
	List<Partido> entre(@Param("desde") Instant desde, @Param("hasta") Instant hasta);

	/** Si ya hay un partido de esos equipos cerca de esa fecha (de cualquier fuente). */
	boolean existsByLocal_IdAndVisita_IdAndFechaHoraUtcBetween(Long localId, Long visitaId, Instant desde, Instant hasta);

	List<Partido> findByLocal_IdAndVisita_IdAndFechaHoraUtcBetween(Long localId, Long visitaId, Instant desde,
			Instant hasta);

	/** Pares (local, visita) de los partidos de esas ligas desde una fecha. */
	@Query("select p.local.id, p.visita.id from Partido p where p.liga.proveedorId in :ligas and p.fechaHoraUtc >= :desde")
	List<Object[]> equiposDeLigas(@Param("ligas") Collection<Long> ligasProveedor, @Param("desde") Instant desde);

	@Query(CON_EQUIPOS + "where p.local.id in :ids or p.visita.id in :ids order by p.fechaHoraUtc")
	List<Partido> deEquipos(@Param("ids") Collection<Long> equipoIds);

	@Query(CON_EQUIPOS + """
			where p.liga.id in :ligas and p.fechaHoraUtc >= :desde and p.fechaHoraUtc < :hasta
			order by p.fechaHoraUtc""")
	List<Partido> deLigas(@Param("ligas") Collection<Long> ligaIds, @Param("desde") Instant desde,
			@Param("hasta") Instant hasta);

	/** Últimos partidos jugados de un equipo antes de una fecha (para el modelo y las rachas). */
	@Query(CON_EQUIPOS + """
			where p.estado = pe.justaapuesta.partido.EstadoPartido.JUGADO
			  and (p.local.id = :equipo or p.visita.id = :equipo) and p.fechaHoraUtc < :antes
			order by p.fechaHoraUtc desc""")
	List<Partido> jugadosDeEquipo(@Param("equipo") Long equipoId, @Param("antes") Instant antes, Pageable pagina);

	/** Últimos partidos jugados de una liga antes de una fecha (promedio de la liga). */
	@Query(CON_EQUIPOS + """
			where p.estado = pe.justaapuesta.partido.EstadoPartido.JUGADO
			  and p.liga.id = :liga and p.fechaHoraUtc < :antes
			order by p.fechaHoraUtc desc""")
	List<Partido> jugadosDeLiga(@Param("liga") Long ligaId, @Param("antes") Instant antes, Pageable pagina);

	@Query("select count(p) from Partido p where p.local.id = :equipo or p.visita.id = :equipo")
	long contarDeEquipo(@Param("equipo") Long equipoId);

	/** Próximos partidos de un equipo (incluye los que se están jugando ahora). */
	@Query(CON_EQUIPOS + """
			where p.estado <> pe.justaapuesta.partido.EstadoPartido.JUGADO
			  and p.estado <> pe.justaapuesta.partido.EstadoPartido.SUSPENDIDO
			  and (p.local.id = :equipo or p.visita.id = :equipo) and p.fechaHoraUtc >= :desde
			order by p.fechaHoraUtc""")
	List<Partido> proximosDeEquipo(@Param("equipo") Long equipoId, @Param("desde") Instant desde, Pageable pagina);

	@Query(CON_EQUIPOS + """
			where p.estado = pe.justaapuesta.partido.EstadoPartido.JUGADO and p.fechaHoraUtc < :antes
			  and ((p.local.id = :a and p.visita.id = :b) or (p.local.id = :b and p.visita.id = :a))
			order by p.fechaHoraUtc desc""")
	List<Partido> enfrentamientos(@Param("a") Long equipoA, @Param("b") Long equipoB, @Param("antes") Instant antes,
			Pageable pagina);

	/** Jugados de esas ligas (id del proveedor) a los que aún no se les pidieron estadísticas, los más recientes primero. */
	@Query("""
			select p from Partido p where p.estado = pe.justaapuesta.partido.EstadoPartido.JUGADO
			  and p.detalleSync = false and p.proveedorId > 0 and p.liga.proveedorId in :ligas
			order by p.fechaHoraUtc desc""")
	List<Partido> jugadosSinDetalle(@Param("ligas") Collection<Long> ligasProveedor, Pageable pagina);

	/** Partidos sin terminar cuya hora ya pasó (hay que refrescarlos). */
	@Query("""
			select p from Partido p where p.estado in :estados and p.fechaHoraUtc < :antes
			order by p.fechaHoraUtc desc""")
	List<Partido> pendientesDeResultado(@Param("estados") Collection<EstadoPartido> estados,
			@Param("antes") Instant antes, Pageable pagina);

	@Modifying
	@Query("delete from Partido p where p.fechaHoraUtc < :antes")
	int borrarAnterioresA(@Param("antes") Instant antes);

	/** Une dos equipos que son el mismo (uno de cada fuente): sus partidos pasan al equipo que queda. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Partido p set p.local = :queda where p.local = :sale")
	int moverLocal(@Param("sale") Equipo sale, @Param("queda") Equipo queda);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Partido p set p.visita = :queda where p.visita = :sale")
	int moverVisita(@Param("sale") Equipo sale, @Param("queda") Equipo queda);
}
