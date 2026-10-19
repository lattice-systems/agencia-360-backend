package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import jakarta.validation.constraints.NotBlank;

/** Las reglas de la contraseña nueva las valida el servicio. */
public record CambiarContrasenaAppRequest(@NotBlank String contrasenaActual, @NotBlank String contrasenaNueva) {
}
