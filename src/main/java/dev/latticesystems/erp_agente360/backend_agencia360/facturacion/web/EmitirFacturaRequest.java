package dev.latticesystems.erp_agente360.backend_agencia360.facturacion.web;

import jakarta.validation.constraints.NotNull;

/** Prospecto a facturar y la unidad Disponible que se le vende. */
public record EmitirFacturaRequest(@NotNull Long prospectoId, @NotNull Long vehiculoId) {
}
