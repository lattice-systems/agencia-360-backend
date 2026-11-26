package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Un amigo que el cliente refiere (V2-M12.04). Necesita teléfono o correo y la
 * confirmación de que el amigo aceptó que la agencia lo contacte.
 */
public record ReferirRequest(@NotBlank @Size(max = 120) String nombre, @Size(max = 20) String telefono,
		@Email @Size(max = 254) String correo, @AssertTrue boolean consentimiento) {

	@AssertTrue(message = "Escribe el teléfono o el correo de tu amigo")
	public boolean isConContacto() {
		return (telefono != null && !telefono.isBlank()) || (correo != null && !correo.isBlank());
	}
}
