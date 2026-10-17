package pe.justaapuesta.picks;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import pe.justaapuesta.analisis.Motor;
import pe.justaapuesta.comun.ConflictoException;
import pe.justaapuesta.comun.NoEncontradoException;
import pe.justaapuesta.picks.PicksDtos.BoletoDto;
import pe.justaapuesta.picks.PicksDtos.BoletoPickDto;
import pe.justaapuesta.usuario.Usuario;
import pe.justaapuesta.usuario.UsuarioRepository;
import tools.jackson.databind.ObjectMapper;

/** Comparte el boleto (los picks pendientes del usuario) con un link público. */
@Service
@RequiredArgsConstructor
public class BoletoService {

	private static final String LETRAS = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final int LARGO = 8;
	private static final SecureRandom AZAR = new SecureRandom();

	private final PicksService picks;
	private final BoletoCompartidoRepository repo;
	private final UsuarioRepository usuarios;
	private final ObjectMapper json;

	@Transactional
	public BoletoDto compartir(Long usuarioId) {
		List<BoletoPickDto> lista = picks.seguidos(usuarioId).stream()
				.filter(x -> x.estado() == EstadoPick.PENDIENTE)
				.map(x -> new BoletoPickDto(x.partido(), x.fecha(), x.texto(), x.probabilidad(), x.cuota()))
				.toList();
		if (lista.isEmpty()) {
			throw new ConflictoException("Tu boleto está vacío.");
		}
		String autor = usuarios.findById(usuarioId).map(Usuario::getNombre).orElse("");
		double producto = lista.stream().mapToDouble(x -> x.pct() / 100.0).reduce(1, (a, b) -> a * b);
		String codigo;
		do {
			codigo = codigo();
		} while (repo.existsById(codigo));
		BoletoDto b = new BoletoDto(codigo, autor, Instant.now(), lista, (int) Math.round(producto * 100),
				Motor.cuotaTotal(lista.stream().map(BoletoPickDto::pct).toList()));
		BoletoCompartido e = new BoletoCompartido();
		e.setCodigo(codigo);
		e.setUsuarioId(usuarioId);
		e.setJson(json.writeValueAsString(b));
		e.setCreadoEn(b.creadoEn());
		repo.save(e);
		return b;
	}

	@Transactional(readOnly = true)
	public BoletoDto ver(String codigo) {
		return repo.findById(codigo).map(b -> json.readValue(b.getJson(), BoletoDto.class))
				.orElseThrow(() -> new NoEncontradoException("Este boleto no existe."));
	}

	private static String codigo() {
		StringBuilder sb = new StringBuilder(LARGO);
		for (int i = 0; i < LARGO; i++) {
			sb.append(LETRAS.charAt(AZAR.nextInt(LETRAS.length())));
		}
		return sb.toString();
	}
}
