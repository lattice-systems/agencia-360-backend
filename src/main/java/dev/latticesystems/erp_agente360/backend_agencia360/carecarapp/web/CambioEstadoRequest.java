package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Nuevo estado que el personal le pone a una asistencia o a un traslado, en
 * minúsculas (por ejemplo {@code en_camino}).
 */
public record CambioEstadoRequest(@NotBlank String estado) {
}
