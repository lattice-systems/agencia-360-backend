package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.AccesoAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Revisa en cada petición de la app que la cuenta del cliente siga activa: si
 * la agencia la desactiva, el token vigente deja de servir y la API responde
 * 403 con {@code CUENTA_DESACTIVADA} (contrato v1.1, RIC-3).
 */
class CuentaActivaInterceptor implements HandlerInterceptor {

	private final AccesoAppService accesoAppService;

	CuentaActivaInterceptor(AccesoAppService accesoAppService) {
		this.accesoAppService = accesoAppService;
	}

	@Override
	public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
			@NonNull Object handler) {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado cliente
				&& cliente.tieneRol(Role.CLIENTE_CARECAR)) {
			accesoAppService.exigirCuentaActiva(cliente.id());
		}
		return true;
	}
}
