package dev.latticesystems.erp_agente360.backend_agencia360.inventario.web;

import jakarta.validation.constraints.NotNull;

/** Unidad que Facturación elige para la venta del prospecto. */
public record AsignarUnidadRequest(@NotNull Long vehiculoId) {
}
