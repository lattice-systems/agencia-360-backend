package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * El cliente entra con su correo (contrato v1.1). Se ignoran mayúsculas y
 * espacios al inicio y al final; un correo sin formato responde como credencial
 * inválida.
 */
public record LoginAppRequest(@NotBlank @Size(max = 120) String correo, @NotBlank @Size(max = 72) String contrasena) {
}
