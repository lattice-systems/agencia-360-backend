package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RestablecerContrasenaRequest(@Size(max = 100) String token, @Email @Size(max = 120) String correo,
		@Pattern(regexp = "\\d{6}", message = "debe tener 6 dígitos") String codigo, @NotBlank String contrasenaNueva) {

	boolean conEnlace() {
		return token != null && !token.isBlank();
	}

	boolean conCodigo() {
		return correo != null && !correo.isBlank() && codigo != null && !codigo.isBlank();
	}
}
