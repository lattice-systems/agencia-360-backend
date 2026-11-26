package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * La respuesta del cliente (V2-M26.04). {@code monto} es el que vio en pantalla.
 */
public record RespuestaOfertaRequest(@NotNull Boolean aceptada, @NotNull BigDecimal monto) {
}
