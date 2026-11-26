package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CampanaApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CampanaPublicadaApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CampanasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CoberturaApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.GarantiaAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Garantías y campañas que captura el personal de servicio mientras el ERP siga
 * congelado (V2-M10.01 y V2-M10.04).
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Postventa - Garantía y campañas", description = "Registro de garantías y publicación de campañas")
public class GarantiaPersonalController {

	private final GarantiaAppService garantiaAppService;
	private final CampanasAppService campanasAppService;

	public GarantiaPersonalController(GarantiaAppService garantiaAppService, CampanasAppService campanasAppService) {
		this.garantiaAppService = garantiaAppService;
		this.campanasAppService = campanasAppService;
	}

	@PostMapping("/garantias")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAnyRole('SERVICIO', 'ADMINISTRADOR')")
	@Operation(summary = "Registrar la garantía de un componente", description = "Responde 400 si un dato no es "
			+ "válido y 404 si el vehículo no existe.")
	public CoberturaApp registrarGarantia(@Valid @RequestBody NuevaGarantiaRequest request) {
		return garantiaAppService.registrar(request.vehiculoId(), request.componente(), request.descripcion(),
				request.iniciaEn(), request.venceEn(), request.kmLimite());
	}

	@PostMapping("/campanas")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAnyRole('SERVICIO', 'ADMINISTRADOR')")
	@Operation(summary = "Publicar una campaña de seguridad", description = "La cruza con los vehículos del "
			+ "modelo, año y rango de VIN y avisa en vivo a cada dueño (campana.nueva). Responde 400 si un dato no "
			+ "es válido o el código ya existe.")
	public CampanaPublicadaApp publicar(@Valid @RequestBody NuevaCampanaRequest request) {
		return campanasAppService.publicar(request.datos());
	}

	@PostMapping("/campanas/{campanaId}/vehiculos/{vehiculoId}/atendida")
	@PreAuthorize("hasAnyRole('SERVICIO', 'ADMINISTRADOR')")
	@Operation(summary = "Marcar una campaña como atendida en un vehículo", description = "Responde 404 si la "
			+ "campaña no existe o no aplica al vehículo.")
	public CampanaApp atender(@PathVariable Long campanaId, @PathVariable Long vehiculoId) {
		return campanasAppService.atender(campanaId, vehiculoId);
	}
}
