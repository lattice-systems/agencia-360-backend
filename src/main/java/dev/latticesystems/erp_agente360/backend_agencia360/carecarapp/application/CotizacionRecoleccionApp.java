package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Costo y franjas de recolección y entrega para una dirección y un día
 * (V2-M17.02). El total es el de los dos traslados.
 */
public record CotizacionRecoleccionApp(Long direccionId, LocalDate fecha, BigDecimal costoPorTraslado,
		BigDecimal costoTotal, Franja recoleccion, Franja entrega) {

	/** Ventana de horas del traslado; horas en {@code HH:mm}. */
	public record Franja(@JsonFormat(pattern = "HH:mm") LocalTime inicio,
			@JsonFormat(pattern = "HH:mm") LocalTime fin) {
	}
}
