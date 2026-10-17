package pe.justaapuesta.usuario;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import pe.justaapuesta.comun.NoAutorizadoException;
import pe.justaapuesta.usuario.UsuarioDtos.ActualizarMeRequest;
import pe.justaapuesta.usuario.UsuarioDtos.EstadisticasPicks;
import pe.justaapuesta.usuario.UsuarioDtos.MeDto;

@Service
@RequiredArgsConstructor
@Transactional
public class UsuarioService {

	private final UsuarioRepository usuarioRepository;
	private final JdbcTemplate jdbc;

	@Transactional(readOnly = true)
	public MeDto me(Long id) {
		return aDto(buscar(id));
	}

	public MeDto actualizar(Long id, ActualizarMeRequest req) {
		Usuario u = buscar(id);
		if (req.modo() != null) {
			u.setModo(req.modo());
		}
		if (req.tema() != null) {
			u.setTema(req.tema());
		}
		if (req.acento() != null) {
			u.setAcento(req.acento());
		}
		if (req.tipVisto() != null) {
			u.setTipVisto(req.tipVisto());
		}
		if (req.ligasFavoritas() != null) {
			u.getLigasFavoritas().clear();
			u.getLigasFavoritas().addAll(new LinkedHashSet<>(req.ligasFavoritas().stream().map(String::trim).toList()));
		}
		return aDto(u);
	}

	MeDto aDto(Usuario u) {
		return new MeDto(u.getId(), u.getNombre(), u.getEmail(), u.getModo(), u.isTipVisto(),
				new ArrayList<>(u.getLigasFavoritas()), estadisticas(u.getId()), u.getTema(), u.getAcento());
	}

	/** Estadísticas reales de los picks que sigue el usuario. */
	private EstadisticasPicks estadisticas(Long usuarioId) {
		Map<String, Object> fila = jdbc.queryForMap("""
				select count(*) as total,
				       coalesce(sum(case when estado = 'ACERTADO' then 1 else 0 end), 0) as acertados,
				       coalesce(sum(case when estado = 'FALLADO' then 1 else 0 end), 0) as fallados
				from picks_seguidos where usuario_id = ?""", usuarioId);
		long total = ((Number) fila.get("total")).longValue();
		long acertados = ((Number) fila.get("acertados")).longValue();
		long fallados = ((Number) fila.get("fallados")).longValue();
		long resueltos = acertados + fallados;
		int porcentaje = resueltos == 0 ? 0 : Math.round(acertados * 100f / resueltos);
		return new EstadisticasPicks(total, acertados, porcentaje);
	}

	/**
	 * Si el token es válido pero la cuenta ya no existe (por ejemplo, se borró), responde 401:
	 * así el frontend cierra la sesión y manda al login en vez de quedarse atascado.
	 */
	private Usuario buscar(Long id) {
		return usuarioRepository.findById(id)
				.orElseThrow(() -> new NoAutorizadoException("Tu sesión expiró. Vuelve a iniciar sesión."));
	}
}
