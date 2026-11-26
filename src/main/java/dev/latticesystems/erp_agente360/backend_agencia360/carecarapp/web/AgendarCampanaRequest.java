package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * La franja y el día en que el cliente quiere atender una campaña (V2-M10.03).
 */
public record AgendarCampanaRequest(@NotNull Long franjaId, @NotNull LocalDate fecha) {
}
