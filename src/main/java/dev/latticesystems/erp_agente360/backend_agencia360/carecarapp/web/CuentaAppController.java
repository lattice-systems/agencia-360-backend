package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EstadoCuentaApp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cuenta de la app de un cliente, administrada por el Gerente de marketing
 * desde el CRM (CRM-1.3).
 */
@RestController
@RequestMapping("/api/v1/clientes/{clienteId}/cuenta-app")
@PreAuthorize("hasRole('GERENTE_MARKETING')")
@Tag(name = "Clientes - Cuenta de la app", description = "Alta, desactivación y estado de la cuenta de la app")
public class CuentaAppController {

	private final CuentasAppService cuentasAppService;

	public CuentaAppController(CuentasAppService cuentasAppService) {
		this.cuentasAppService = cuentasAppService;
	}

	@GetMapping
	@Operation(summary = "Estado de la cuenta de la app", description = "SIN_CUENTA, ACTIVA o DESACTIVADA.")
	public EstadoCuentaApp estado(@PathVariable Long clienteId) {
		return cuentasAppService.estado(clienteId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Abrir la cuenta de la app", description = "Genera una contraseña temporal y se la manda al "
			+ "cliente por correo; la cambia en su primer ingreso. Responde 404 si el cliente no existe y 409 si no "
			+ "tiene correo o ya tiene cuenta.")
	public EstadoCuentaApp abrir(@PathVariable Long clienteId) {
		return cuentasAppService.abrir(clienteId);
	}

	@PostMapping("/desactivar")
	@Operation(summary = "Desactivar la cuenta", description = "Cierra sus sesiones; la app recibe 403 "
			+ "(CUENTA_DESACTIVADA) en la siguiente petición. Responde 404 si el cliente no tiene cuenta.")
	public EstadoCuentaApp desactivar(@PathVariable Long clienteId) {
		return cuentasAppService.desactivar(clienteId);
	}

	@PostMapping("/activar")
	@Operation(summary = "Reactivar la cuenta", description = "Responde 404 si el cliente no tiene cuenta.")
	public EstadoCuentaApp activar(@PathVariable Long clienteId) {
		return cuentasAppService.activar(clienteId);
	}
}
