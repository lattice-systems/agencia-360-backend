package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.SeguimientoTrasladoApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.TrasladosAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operación del chofer sobre los traslados (V2-M17.03). Usa el rol de marketing
 * mientras el ERP siga congelado.
 */
@RestController
@RequestMapping("/api/v1/traslados")
@Tag(name = "Traslados (personal)", description = "El chofer mueve el traslado y reporta su ubicación")
public class TrasladosPersonalController {

	private final TrasladosAppService trasladosAppService;

	public TrasladosPersonalController(TrasladosAppService trasladosAppService) {
		this.trasladosAppService = trasladosAppService;
	}

	@PatchMapping("/{id}/estado")
	@PreAuthorize("hasRole('GERENTE_MARKETING')")
	@Operation(summary = "Cambiar el estado de un traslado", description = "Recoger: en_camino, recogido, en_taller. "
			+ "Entregar: de_regreso, entregado. Avisa al cliente (traslado.estado). Responde 400 si el estado no "
			+ "existe, 404 si el traslado no existe y 409 con TRANSICION_INVALIDA si no sigue al estado actual.")
	public SeguimientoTrasladoApp cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstadoRequest request,
			@AuthenticationPrincipal UsuarioAutenticado chofer) {
		return trasladosAppService.cambiarEstado(chofer.id(), id, request.estado());
	}

	@PutMapping("/{id}/ubicacion")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasRole('GERENTE_MARKETING')")
	@Operation(summary = "Reportar la ubicación del chofer", description = "Solo en camino o de regreso; avisa al "
			+ "cliente (traslado.ubicacion). Responde 400 si está fuera de rango, 404 si no existe y 409 con "
			+ "TRANSICION_INVALIDA si el traslado no va en ruta.")
	public void reportarUbicacion(@PathVariable Long id, @Valid @RequestBody UbicacionChoferRequest request) {
		trasladosAppService.reportarUbicacion(id, request.lat(), request.lng());
	}
}
