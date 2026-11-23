package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import com.fasterxml.jackson.annotation.JsonFormat;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Cita;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoCita;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MotivoCita;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Cita de servicio vista por el cliente (APP-3.3); horas en {@code HH:mm}.
 *
 * @param vehiculo
 *            descripción corta del vehículo para mostrarla sin pedir la lista
 *            de vehículos (marca, modelo, año y versión que haya); puede faltar
 *            si el vehículo ya no es del cliente
 * @param motivo
 *            por qué agendó (V2-M02.08); nulo si no lo dijo
 */
public record CitaApp(Long id, Long vehiculoId, String vehiculo, Long franjaId, LocalDate fecha,
		@JsonFormat(pattern = "HH:mm") LocalTime horaInicio, @JsonFormat(pattern = "HH:mm") LocalTime horaFin,
		EstadoCita estado, MotivoCita motivo) {

	static CitaApp de(Cita cita, String vehiculo) {
		return new CitaApp(cita.getId(), cita.getVehiculoClienteId(), vehiculo, cita.getFranjaId(), cita.getFecha(),
				cita.getHoraInicio(), cita.getHoraFin(), cita.getEstado(), cita.getMotivo());
	}

	/** «Nissan March 2026 Advance»; omite lo que falte. */
	static String describir(VehiculoDeCliente vehiculo) {
		return Stream.of(vehiculo.marca(), vehiculo.modelo(), String.valueOf(vehiculo.anio()), vehiculo.version())
				.filter(parte -> parte != null && !parte.isBlank()).collect(Collectors.joining(" "));
	}
}
