package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import com.fasterxml.jackson.annotation.JsonFormat;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.HistorialDeServicios.ServicioDelVehiculo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Orden de servicio vista por el cliente (APP-3.2, V2-M02.05).
 *
 * @param estatus
 *            uno de {@link EstatusServicio}, el mismo de la orden de servicio
 * @param fechaCierre
 *            cuándo se cerró la orden, en hora de la agencia; {@code null} si
 *            sigue abierta
 * @param total
 *            mano de obra más refacciones, con IVA incluido
 */
public record ServicioApp(Long id, LocalDate fecha, EstatusServicio estatus,
		@JsonFormat(pattern = ServicioApp.FORMATO_FECHA_HORA) LocalDateTime fechaCierre, BigDecimal total) {

	static final String FORMATO_FECHA_HORA = "yyyy-MM-dd'T'HH:mm:ss";
	static final ZoneId ZONA_AGENCIA = ZoneId.of("America/Mexico_City");

	static ServicioApp de(ServicioDelVehiculo servicio) {
		return new ServicioApp(servicio.id(), servicio.fecha(), EstatusServicio.valueOf(servicio.estatus().name()),
				enLaAgencia(servicio.fechaCierre()), servicio.total());
	}

	static LocalDateTime enLaAgencia(Instant momento) {
		return momento == null ? null : LocalDateTime.ofInstant(momento, ZONA_AGENCIA).truncatedTo(ChronoUnit.SECONDS);
	}
}
