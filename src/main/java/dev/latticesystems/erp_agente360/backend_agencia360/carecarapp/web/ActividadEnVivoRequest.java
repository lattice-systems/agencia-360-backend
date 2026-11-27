package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Token de la Live Activity de una orden (V2-M25.05): el push token que da
 * ActivityKit, en hexadecimal.
 */
public record ActividadEnVivoRequest(
		@NotBlank @Size(max = 300) @Pattern(regexp = "^[0-9a-fA-F]*$", message = "debe ser hexadecimal") String pushToken) {
}
