package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.web;

import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application.NuevoProspecto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta de un prospecto. La regla "teléfono o correo" la valida el dominio. El
 * formato del teléfono todavía no está definido; solo se limita su longitud.
 */
public record RegistrarProspectoRequest(@NotBlank @Size(max = 120) String nombre, @Size(max = 20) String telefono,
		@Email @Size(max = 120) String correo) {

	NuevoProspecto comoNuevoProspecto() {
		return new NuevoProspecto(nombre, telefono, correo);
	}
}
