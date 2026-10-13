package dev.latticesystems.erp_agente360.backend_agencia360.inventario.web;

import jakarta.validation.constraints.NotNull;

/**
 * Piezas recibidas. El dominio valida que la cantidad sea mayor a cero; la
 * fecha y el responsable los pone el servidor.
 */
public record RegistrarRecepcionRequest(@NotNull Integer cantidad) {
}
