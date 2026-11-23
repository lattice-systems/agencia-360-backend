package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.FranjaHorario;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.FranjaHorarioRepository;
import java.time.DayOfWeek;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HorarioTaller {

	private final FranjaHorarioRepository franjaHorarioRepository;
	private final Duration plazoParaCancelar;
	private final int diasDeAnticipacion;

	public HorarioTaller(FranjaHorarioRepository franjaHorarioRepository,
			@Value("${app.agenda.horas-para-cancelar:24}") int horasParaCancelar,
			@Value("${app.agenda.dias-de-anticipacion:60}") int diasDeAnticipacion) {
		if (horasParaCancelar < 0 || diasDeAnticipacion <= 0) {
			throw new IllegalStateException("La configuración app.agenda.* no es válida");
		}
		this.franjaHorarioRepository = franjaHorarioRepository;
		this.plazoParaCancelar = Duration.ofHours(horasParaCancelar);
		this.diasDeAnticipacion = diasDeAnticipacion;
	}

	public Map<DayOfWeek, List<FranjaHorario>> franjasPorDia() {
		return franjaHorarioRepository.findByActivaTrueOrderByDiaSemanaAscHoraInicioAsc().stream().collect(Collectors
				.groupingBy(FranjaHorario::getDiaSemana, () -> new EnumMap<>(DayOfWeek.class), Collectors.toList()));
	}

	public Optional<FranjaHorario> franja(Long id) {
		return id == null ? Optional.empty() : franjaHorarioRepository.findByIdAndActivaTrue(id);
	}

	public Duration plazoParaCancelar() {
		return plazoParaCancelar;
	}

	public int diasDeAnticipacion() {
		return diasDeAnticipacion;
	}
}
