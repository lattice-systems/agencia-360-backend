package dev.latticesystems.erp_agente360.backend_agencia360.clientes.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta manual de un cliente por el Gerente de marketing (CRM-1.3).
 *
 * @param correo
 *            con el que entra a la app; vacío si no la quiere
 */
public record RegistrarClienteRequest(@NotBlank @Size(max = 120) String nombre, @Email @Size(max = 120) String correo,
		@Size(max = 20) String telefono) {
}
