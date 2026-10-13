package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.web;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application.ProspectoVista;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application.SeguimientoProspectosService;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Etapa;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Seguimiento de prospectos por etapa (CRM-1.1). La visibilidad de CRM-0.1 se
 * aplica en {@link SeguimientoProspectosService}.
 */
@RestController
@RequestMapping("/api/v1/prospectos")
@Tag(name = "Prospectos", description = "Registro y seguimiento de prospectos del CRM")
public class SeguimientoProspectoController {

	private final SeguimientoProspectosService seguimientoProspectosService;

	public SeguimientoProspectoController(SeguimientoProspectosService seguimientoProspectosService) {
		this.seguimientoProspectosService = seguimientoProspectosService;
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('VENTAS', 'GERENTE_MARKETING')")
	@Operation(summary = "Listar prospectos, filtrable por etapa", description = "Ventas recibe solo sus "
			+ "prospectos y el Gerente de marketing recibe todos. Del más reciente al más antiguo.")
	public List<ProspectoVista> listar(@RequestParam(required = false) String etapa,
			@AuthenticationPrincipal UsuarioAutenticado actor) {
		return seguimientoProspectosService.listar(etapa == null ? null : Etapa.desde(etapa), actor);
	}

	@PatchMapping("/{id}/etapa")
	@PreAuthorize("hasRole('VENTAS')")
	@Operation(summary = "Cambiar la etapa de un prospecto", description = "Registra la fecha y hora en el "
			+ "historial. Responde 404 si el prospecto no existe o es de otro vendedor.")
	public ProspectoVista cambiarEtapa(@PathVariable Long id, @Valid @RequestBody CambiarEtapaRequest request,
			@AuthenticationPrincipal UsuarioAutenticado actor) {
		return seguimientoProspectosService.cambiarEtapa(id, request.etapa(), actor);
	}
}
