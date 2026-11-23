package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MotivoCita;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Cita que el cliente pide agendar (APP-3.3); el motivo y la recolección a
 * domicilio (V2-M17.02) son opcionales.
 */
public record NuevaCitaRequest(@NotNull Long vehiculoId, @NotNull Long franjaId, @NotNull LocalDate fecha,
		MotivoCita motivo, @Valid RecoleccionRequest recoleccion) {

	/** Pide que recojan y entreguen el vehículo en una dirección guardada. */
	public record RecoleccionRequest(@NotNull Long direccionId) {
	}
}
