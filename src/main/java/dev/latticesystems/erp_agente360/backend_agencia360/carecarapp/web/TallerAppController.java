package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.OrdenEnCursoApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.OrdenEnVivoApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.TallerAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Taller en vivo en la app (V2-M04.01, V2-M04.02, V2-M04.04 y V2-M04.05), según
 * el contrato en {@code docs/api/agencia360-api-app-v2.yaml}.
 */
@RestController
@RequestMapping("/api/v1/app/ordenes")
@Tag(name = "App CareCar - Taller en vivo", description = "La orden en el taller y sus trabajos adicionales")
public class TallerAppController {

	private final TallerAppService tallerAppService;

	public TallerAppController(TallerAppService tallerAppService) {
		this.tallerAppService = tallerAppService;
	}

	@GetMapping("/activas")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Órdenes abiertas del cliente", description = "Las de sus vehículos que siguen en el "
			+ "taller o listas para entrega, la más reciente primero; al entregarse dejan de aparecer.")
	public List<OrdenEnCursoApp> activas(@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return tallerAppService.enCurso(cliente.id());
	}

	@GetMapping("/{ordenId}")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Detalle de una orden", description = "Etapas con su hora, entrega estimada, inspección "
			+ "multipunto con fotos y videos, y trabajos adicionales con los totales. Responde 404 si la orden no "
			+ "existe o es de un vehículo ajeno.")
	public OrdenEnVivoApp orden(@PathVariable Long ordenId, @AuthenticationPrincipal UsuarioAutenticado cliente) {
		return tallerAppService.orden(cliente.id(), ordenId);
	}

	@PostMapping("/{ordenId}/adicionales/{adicionalId}/respuesta")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Aprobar o rechazar un trabajo adicional", description = "Con la firma en PNG en Base64. "
			+ "Devuelve la orden con los totales recalculados. Responde 400 si la firma no es válida, 404 si la "
			+ "orden o el trabajo no existen o son ajenos, y 409 ADICIONAL_YA_RESPONDIDO u ORDEN_CERRADA.")
	public OrdenEnVivoApp responder(@PathVariable Long ordenId, @PathVariable Long adicionalId,
			@Valid @RequestBody ResponderAdicionalRequest request,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return tallerAppService.responder(cliente.id(), ordenId, adicionalId, request.aprobado(), request.firma());
	}
}
