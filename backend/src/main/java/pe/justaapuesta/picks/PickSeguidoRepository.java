package pe.justaapuesta.picks;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PickSeguidoRepository extends JpaRepository<PickSeguido, Long> {

	List<PickSeguido> findByUsuarioIdOrderByCreadoEnDesc(Long usuarioId);

	Optional<PickSeguido> findByUsuarioIdAndPartidoIdAndMercadoClave(Long usuarioId, Long partidoId, String mercadoClave);

	boolean existsByUsuarioIdAndPartidoIdAndMercadoClave(Long usuarioId, Long partidoId, String mercadoClave);

	List<PickSeguido> findByEstado(EstadoPick estado);
}
