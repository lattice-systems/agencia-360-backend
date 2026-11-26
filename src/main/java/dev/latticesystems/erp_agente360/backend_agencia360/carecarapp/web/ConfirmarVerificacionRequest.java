package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** El codigo de 6 digitos que llego al correo nuevo (V2-M01.07). */
public record ConfirmarVerificacionRequest(@NotBlank @Size(min = 6, max = 6) String codigo) {
}
