package dev.latticesystems.erp_agente360.backend_agencia360.acceso.web;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * Obtiene los roles del usuario autenticado a partir de las autoridades del
 * JWT.
 */
final class RolesDelActor {

	private static final String PREFIJO = "ROLE_";
	private static final Set<String> NOMBRES_VALIDOS = Arrays.stream(Role.values()).map(Enum::name)
			.collect(Collectors.toSet());

	private RolesDelActor() {
	}

	static Set<Role> de(Authentication authentication) {
		return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
				.filter(autoridad -> autoridad.startsWith(PREFIJO))
				.map(autoridad -> autoridad.substring(PREFIJO.length())).filter(NOMBRES_VALIDOS::contains)
				.map(Role::valueOf).collect(Collectors.toSet());
	}
}
