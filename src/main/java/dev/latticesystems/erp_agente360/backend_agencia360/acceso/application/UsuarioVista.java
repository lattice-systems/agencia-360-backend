package dev.latticesystems.erp_agente360.backend_agencia360.acceso.application;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;

/** Datos de un usuario que se pueden mostrar. Nunca incluye la contraseña. */
public record UsuarioVista(Long id, String usuario, String nombre, String rol, boolean activo) {

	static UsuarioVista de(Usuario usuario) {
		return new UsuarioVista(usuario.getId(), usuario.getUsuario(), usuario.getNombre(),
				usuario.getRol().getNombre(), usuario.isActivo());
	}
}
