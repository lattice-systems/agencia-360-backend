package dev.latticesystems.erp_agente360.backend_agencia360.acceso.web;

import jakarta.validation.constraints.NotBlank;

public record CambiarRolRequest(@NotBlank String rol) {
}
