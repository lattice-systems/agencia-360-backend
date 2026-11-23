package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MotivoCita;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Cita que el cliente pide agendar (APP-3.3); el motivo es opcional. */
public record NuevaCitaRequest(@NotNull Long vehiculoId, @NotNull Long franjaId, @NotNull LocalDate fecha,
		MotivoCita motivo) {
}
