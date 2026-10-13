package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.web;

import jakarta.validation.constraints.NotNull;

/** Vendedor que queda a cargo del prospecto. */
public record ReasignarProspectoRequest(@NotNull Long vendedorId) {
}
