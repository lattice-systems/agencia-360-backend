package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import com.fasterxml.jackson.annotation.JsonFormat;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Cita;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoCita;
import java.time.LocalDate;
import java.time.LocalTime;

/** Cita de servicio vista por el cliente (APP-3.3); horas en {@code HH:mm}. */
public record CitaApp(Long id, Long vehiculoId, Long franjaId, LocalDate fecha,
		@JsonFormat(pattern = "HH:mm") LocalTime horaInicio, @JsonFormat(pattern = "HH:mm") LocalTime horaFin,
		EstadoCita estado) {

	static CitaApp de(Cita cita) {
		return new CitaApp(cita.getId(), cita.getVehiculoClienteId(), cita.getFranjaId(), cita.getFecha(),
				cita.getHoraInicio(), cita.getHoraFin(), cita.getEstado());
	}
}
