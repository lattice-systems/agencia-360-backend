package dev.latticesystems.erp_agente360.backend_agencia360.acceso.web;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Aplicacion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Credenciales y aplicación (ERP o CRM) a la que se quiere entrar. */
public record LoginRequest(@NotBlank String usuario, @NotBlank String contrasena, @NotNull Aplicacion aplicacion) {
}
