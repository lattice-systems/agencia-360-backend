package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalTime;

/** Franja con cupo para agendar (APP-3.3); horas en {@code HH:mm}. */
public record DisponibilidadApp(LocalDate fecha, Long franjaId, @JsonFormat(pattern = "HH:mm") LocalTime horaInicio,
		@JsonFormat(pattern = "HH:mm") LocalTime horaFin, int cupoDisponible) {
}
