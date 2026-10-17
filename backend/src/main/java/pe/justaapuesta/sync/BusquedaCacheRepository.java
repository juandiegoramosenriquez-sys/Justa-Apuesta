package pe.justaapuesta.sync;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BusquedaCacheRepository extends JpaRepository<BusquedaCache, String> {
}
