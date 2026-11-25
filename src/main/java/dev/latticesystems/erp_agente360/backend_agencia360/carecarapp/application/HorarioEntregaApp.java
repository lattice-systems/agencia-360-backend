package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import com.fasterxml.jackson.annotation.JsonFormat;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.FranjaHorario;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cuándo puede el cliente recoger su auto listo (V2-M04.06): el primer día
 * desde hoy con horario del taller ({@code franja_horario}), de la primera hora
 * a la última. Hoy cuenta solo si aún no cierra.
 */
public record HorarioEntregaApp(LocalDate fecha, @JsonFormat(pattern = "HH:mm") LocalTime desde,
		@JsonFormat(pattern = "HH:mm") LocalTime hasta) {

	private static final int DIAS_A_REVISAR = 8;

	static Optional<HorarioEntregaApp> siguiente(Map<DayOfWeek, List<FranjaHorario>> franjas, LocalDateTime ahora) {
		for (int dias = 0; dias < DIAS_A_REVISAR; dias++) {
			LocalDate dia = ahora.toLocalDate().plusDays(dias);
			List<FranjaHorario> delDia = franjas.getOrDefault(dia.getDayOfWeek(), List.of());
			if (delDia.isEmpty()) {
				continue;
			}
			LocalTime desde = delDia.stream().map(FranjaHorario::getHoraInicio).min(Comparator.naturalOrder())
					.orElseThrow();
			LocalTime hasta = delDia.stream().map(FranjaHorario::getHoraFin).max(Comparator.naturalOrder())
					.orElseThrow();
			if (dias == 0 && !ahora.toLocalTime().isBefore(hasta)) {
				continue;
			}
			return Optional.of(new HorarioEntregaApp(dia, desde, hasta));
		}
		return Optional.empty();
	}
}
