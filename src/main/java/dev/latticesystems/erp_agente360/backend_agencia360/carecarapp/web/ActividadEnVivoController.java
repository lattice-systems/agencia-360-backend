package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ActividadesEnVivoAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Live Activity de la orden en la app (V2-M25.05), según el contrato en
 * {@code docs/api/agencia360-api-app-v2.yaml}.
 */
@RestController
@RequestMapping("/api/v1/app/ordenes/{ordenId}/actividad-en-vivo")
@Tag(name = "App CareCar - Taller en vivo", description = "La orden en el taller y sus trabajos adicionales")
public class ActividadEnVivoController {

	private final ActividadesEnVivoAppService actividades;

	public ActividadEnVivoController(ActividadesEnVivoAppService actividades) {
		this.actividades = actividades;
	}

	@PutMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Registrar la Live Activity de una orden", description = "Guarda el push token de la "
			+ "actividad (hexadecimal) para actualizarla desde el servidor. Repetirlo es seguro. Responde 404 si la "
			+ "orden no existe o es ajena y 409 ORDEN_CERRADA si ya se entregó.")
	public void registrar(@PathVariable Long ordenId, @Valid @RequestBody ActividadEnVivoRequest request,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		actividades.registrar(cliente.id(), ordenId, request.pushToken());
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Dar de baja la Live Activity de una orden", description = "Con pushToken quita solo "
			+ "esa actividad; sin él, todas las del cliente en la orden. Es idempotente. Responde 404 si la orden "
			+ "no existe o es ajena.")
	public void quitar(@PathVariable Long ordenId, @RequestParam(required = false) String pushToken,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		actividades.quitar(cliente.id(), ordenId, pushToken);
	}
}
