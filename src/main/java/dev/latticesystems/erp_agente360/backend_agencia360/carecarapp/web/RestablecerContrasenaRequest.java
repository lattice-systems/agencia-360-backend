package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RestablecerContrasenaRequest(@NotBlank @Email @Size(max = 120) String correo,
		@NotBlank @Pattern(regexp = "\\d{6}", message = "debe tener 6 dígitos") String codigo,
		@NotBlank String contrasenaNueva) {
}
