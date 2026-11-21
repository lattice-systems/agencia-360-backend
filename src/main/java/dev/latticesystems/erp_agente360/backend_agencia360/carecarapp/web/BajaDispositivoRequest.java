package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import jakarta.validation.constraints.NotBlank;

/** Token que la app deja de usar al cerrar sesión (APP-6). */
public record BajaDispositivoRequest(@NotBlank String token) {
}
