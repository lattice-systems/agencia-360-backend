package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Nueva franja de una cita (V2-M02.13). Sin {@code fecha}, la cita se queda en
 * su mismo día.
 */
public record ReprogramarCitaRequest(@NotNull Long franjaId, LocalDate fecha) {
}
