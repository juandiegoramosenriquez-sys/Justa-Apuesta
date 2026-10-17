package pe.justaapuesta.picks;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import pe.justaapuesta.analisis.AnalisisService;
import pe.justaapuesta.analisis.Entradas;
import pe.justaapuesta.analisis.Motor;
import pe.justaapuesta.partido.Partido;
import pe.justaapuesta.partido.PartidoRepository;

/**
 * Cada hora revisa los picks pendientes cuyos partidos ya terminaron y los marca ACERTADO o FALLADO
 * con la misma comprobación de cada mercado. Si falta el dato (por ejemplo, corners), queda pendiente.
 */
@Component
@RequiredArgsConstructor
public class ResolucionPicks {

	private static final Logger log = LoggerFactory.getLogger(ResolucionPicks.class);

	private final PickSeguidoRepository pickRepository;
	private final PartidoRepository partidoRepository;
	private final AnalisisService analisis;

	@Scheduled(cron = "0 15 * * * *", zone = "America/Lima")
	public void programado() {
		resolver();
	}

	public int resolver() {
		List<PickSeguido> pendientes = pickRepository.findByEstado(EstadoPick.PENDIENTE);
		if (pendientes.isEmpty()) {
			return 0;
		}
		Map<Long, Partido> partidos = partidoRepository
				.conEquipos(pendientes.stream().map(PickSeguido::getPartidoId).distinct().toList()).stream()
				.filter(Partido::jugado)
				.collect(Collectors.toMap(Partido::getId, Function.identity()));
		if (partidos.isEmpty()) {
			return 0;
		}
		Map<Long, Motor.Final> finales = analisis.finales(partidos.values());
		Map<Long, Entradas> entradas = analisis.entradas(partidos.values());
		int resueltos = 0;
		for (PickSeguido pick : pendientes) {
			Partido p = partidos.get(pick.getPartidoId());
			Entradas e = p == null ? null : entradas.get(p.getId());
			Motor.Final f = p == null ? null : finales.get(p.getId());
			if (e == null || f == null) {
				continue;
			}
			Boolean salio = Motor.allPicks(e).stream()
					.filter(m -> m.clave().equals(pick.getMercadoClave()))
					.map(m -> m.check().apply(f))
					.filter(Objects::nonNull)
					.findFirst().orElse(null);
			if (salio != null) {
				pick.setEstado(salio ? EstadoPick.ACERTADO : EstadoPick.FALLADO);
				pickRepository.save(pick);
				resueltos++;
			}
		}
		if (resueltos > 0) {
			log.info("Picks resueltos: {}", resueltos);
		}
		return resueltos;
	}
}
