package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ChecklistTrasladoApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ChecklistTrasladoService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.SeguimientoTrasladoApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.TrasladosAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Seguimiento y checklist del traslado a domicilio por el cliente (V2-M17.03 y
 * M17.04).
 */
@RestController
@RequestMapping("/api/v1/app/traslados")
@Tag(name = "App CareCar - Traslados", description = "Estado y ubicación del chofer de mis traslados")
public class TrasladosAppController {

	private final TrasladosAppService trasladosAppService;
	private final ChecklistTrasladoService checklistService;

	public TrasladosAppController(TrasladosAppService trasladosAppService, ChecklistTrasladoService checklistService) {
		this.trasladosAppService = trasladosAppService;
		this.checklistService = checklistService;
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Estado de un traslado mío", description = "Con la ubicación del chofer solo mientras va "
			+ "en camino o de regreso y ya reportó. Responde 404 si no existe o su cita es de otro cliente. Los "
			+ "cambios llegan en vivo por GET /app/eventos (traslado.estado y traslado.ubicacion).")
	public SeguimientoTrasladoApp obtener(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado cliente) {
		return trasladosAppService.obtener(cliente.id(), id);
	}

	@GetMapping("/{id}/checklist")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Checklist de un traslado mío", description = "Kilometraje, gasolina, notas, fotos y firma "
			+ "que anotó el chofer. Las fotos y la firma son URL firmadas que vencen a los 15 minutos. Responde 404 "
			+ "si el traslado es de otro cliente o todavía no tiene checklist.")
	public ChecklistTrasladoApp checklist(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado cliente) {
		return checklistService.obtener(cliente.id(), id);
	}
}
