package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import jakarta.validation.constraints.NotNull;

/** Con qué datos fiscales se factura un pago (V2-M08.06). */
public record NuevaFacturaRequest(@NotNull Long perfilFiscalId) {
}
