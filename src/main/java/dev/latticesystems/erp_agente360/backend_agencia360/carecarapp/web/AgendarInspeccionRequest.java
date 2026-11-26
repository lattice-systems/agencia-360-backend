package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** La franja y el día de la inspección física (V2-M26.03). */
public record AgendarInspeccionRequest(@NotNull Long franjaId, @NotNull LocalDate fecha) {
}
