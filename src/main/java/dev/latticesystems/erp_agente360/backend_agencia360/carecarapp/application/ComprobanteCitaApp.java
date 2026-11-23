package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Datos del comprobante de una cita para la app (V2-M02.11). {@code codigo} es
 * el mismo texto del QR del PDF; {@code vehiculoNombre} puede faltar.
 */
public record ComprobanteCitaApp(Long citaId, String codigo, LocalDate fecha,
		@JsonFormat(pattern = "HH:mm") LocalTime horaInicio, @JsonFormat(pattern = "HH:mm") LocalTime horaFin,
		String vehiculoNombre) {
}
