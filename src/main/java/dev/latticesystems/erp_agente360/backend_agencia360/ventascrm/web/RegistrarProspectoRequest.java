package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.web;

import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application.NuevoProspecto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta de un prospecto. El dominio valida que venga teléfono o correo y que el
 * teléfono tenga 10 dígitos; aquí solo se limita el largo de lo capturado, que
 * puede traer espacios o guiones.
 */
public record RegistrarProspectoRequest(@NotBlank @Size(max = 120) String nombre, @Size(max = 20) String telefono,
		@Email @Size(max = 120) String correo) {

	NuevoProspecto comoNuevoProspecto() {
		return new NuevoProspecto(nombre, telefono, correo);
	}
}
