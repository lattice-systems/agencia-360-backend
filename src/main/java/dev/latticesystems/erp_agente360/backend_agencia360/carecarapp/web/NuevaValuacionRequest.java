package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import jakarta.validation.constraints.NotNull;

/** La estimación desde la que el cliente pide la valuación (V2-M26.01). */
public record NuevaValuacionRequest(@NotNull Long estimacionTomaCuentaId) {
}
