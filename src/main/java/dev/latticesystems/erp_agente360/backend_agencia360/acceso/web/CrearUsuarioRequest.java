package dev.latticesystems.erp_agente360.backend_agencia360.acceso.web;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.application.NuevoUsuario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta de un usuario. La contraseña tiene un máximo de 72 caracteres porque
 * BCrypt no admite más.
 */
public record CrearUsuarioRequest(@NotBlank @Size(max = 50) String usuario, @NotBlank @Size(max = 72) String contrasena,
		@NotBlank @Size(max = 120) String nombre, @NotBlank String rol) {

	NuevoUsuario comoNuevoUsuario() {
		return new NuevoUsuario(usuario, contrasena, nombre, rol);
	}
}
