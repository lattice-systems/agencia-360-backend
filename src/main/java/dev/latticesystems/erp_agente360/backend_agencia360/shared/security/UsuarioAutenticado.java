package dev.latticesystems.erp_agente360.backend_agencia360.shared.security;

import java.security.Principal;
import java.util.Set;

/**
 * Usuario de la petición en curso, armado a partir del JWT sin consultar la
 * base de datos. Es el principal del {@code Authentication}: en un controlador
 * se recibe con {@code @AuthenticationPrincipal UsuarioAutenticado actor}.
 *
 * @param id
 *            id del usuario en la tabla {@code usuario} (claim {@code uid})
 * @param usuario
 *            nombre de usuario con el que inició sesión (subject del token)
 * @param roles
 *            roles del usuario (claim {@code roles})
 */
public record UsuarioAutenticado(Long id, String usuario, Set<Role> roles) implements Principal {

	public UsuarioAutenticado {
		roles = Set.copyOf(roles);
	}

	public boolean tieneRol(Role rol) {
		return roles.contains(rol);
	}

	@Override
	public String getName() {
		return usuario;
	}
}
