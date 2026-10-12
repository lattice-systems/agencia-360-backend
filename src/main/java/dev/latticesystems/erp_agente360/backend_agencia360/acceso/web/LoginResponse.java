package dev.latticesystems.erp_agente360.backend_agencia360.acceso.web;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.application.SesionIniciada;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Aplicacion;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;

/**
 * Token JWT y datos básicos del usuario. El frontend usa el rol para mostrar
 * solo los módulos que le corresponden.
 */
public record LoginResponse(String accessToken, String tipo, String usuario, String nombre, Role rol,
		Aplicacion aplicacion) {

	static LoginResponse de(SesionIniciada sesion) {
		return new LoginResponse(sesion.accessToken(), "Bearer", sesion.usuario(), sesion.nombre(), sesion.rol(),
				sesion.aplicacion());
	}
}
