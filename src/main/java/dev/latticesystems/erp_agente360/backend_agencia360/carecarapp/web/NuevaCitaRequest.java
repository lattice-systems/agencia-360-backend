package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Cita que el cliente pide agendar (APP-3.3). */
public record NuevaCitaRequest(@NotNull Long vehiculoId, @NotNull Long franjaId, @NotNull LocalDate fecha) {
}
