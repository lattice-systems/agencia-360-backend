package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.web;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application.ProspectoVista;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application.ReasignacionProspectosService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reasignación de prospectos entre vendedores (CRM-0.1). */
@RestController
@RequestMapping("/api/v1/prospectos")
@Tag(name = "Prospectos", description = "Registro y seguimiento de prospectos del CRM")
public class ReasignacionProspectoController {

	private final ReasignacionProspectosService reasignacionProspectosService;

	public ReasignacionProspectoController(ReasignacionProspectosService reasignacionProspectosService) {
		this.reasignacionProspectosService = reasignacionProspectosService;
	}

	@PutMapping("/{id}/vendedor")
	@PreAuthorize("hasRole('GERENTE_MARKETING')")
	@Operation(summary = "Reasignar un prospecto a otro vendedor", description = "Solo el Gerente de marketing. "
			+ "Responde 404 si el prospecto no existe y 400 si el usuario no es un vendedor activo.")
	public ProspectoVista reasignar(@PathVariable Long id, @Valid @RequestBody ReasignarProspectoRequest request,
			@AuthenticationPrincipal UsuarioAutenticado actor) {
		return reasignacionProspectosService.reasignar(id, request.vendedorId(), actor);
	}

	@PostMapping("/reasignaciones")
	@PreAuthorize("hasRole('GERENTE_MARKETING')")
	@Operation(summary = "Reasignar varios prospectos a la vez", description = "Solo el Gerente de marketing "
			+ "(CRM-UX-1). Todos o ninguno: responde 404 si alguno no existe, 400 si la lista está vacía, pasa "
			+ "de 100 o el usuario no es un vendedor activo.")
	public List<ProspectoVista> reasignarVarios(@Valid @RequestBody ReasignarVariosRequest request,
			@AuthenticationPrincipal UsuarioAutenticado actor) {
		return reasignacionProspectosService.reasignarVarios(request.prospectoIds(), request.vendedorId(), actor);
	}

	/** Prospectos que pasan al mismo vendedor. */
	public record ReasignarVariosRequest(@NotNull List<Long> prospectoIds, @NotNull Long vendedorId) {
	}
}
