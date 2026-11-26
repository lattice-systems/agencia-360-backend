package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import jakarta.validation.constraints.NotNull;

/** Lo que respondió la persona en la pantalla simulada de su banco. */
public record AutenticacionPagoRequest(@NotNull Boolean confirmada) {
}
