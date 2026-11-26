package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * La oferta que captura ventas (V2-M26.04). Sin {@code vigenciaHasta} se usa la
 * vigencia por omisión.
 */
public record OfertaValuacionRequest(@NotNull BigDecimal monto, LocalDate vigenciaHasta, String nota) {
}
