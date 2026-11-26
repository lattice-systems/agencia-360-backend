package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CampanaApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CampanasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CitaApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.GarantiaApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.GarantiaAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Garantía y campañas de seguridad de la app (V2-M10.01 a V2-M10.03). */
@RestController
@RequestMapping("/api/v1/app/vehiculos/{vehiculoId}")
@Tag(name = "App CareCar - Garantía y campañas", description = "Cobertura de garantía y campañas de seguridad")
public class GarantiaAppController {

	private final GarantiaAppService garantiaAppService;
	private final CampanasAppService campanasAppService;

	public GarantiaAppController(GarantiaAppService garantiaAppService, CampanasAppService campanasAppService) {
		this.garantiaAppService = garantiaAppService;
		this.campanasAppService = campanasAppService;
	}

	@GetMapping("/garantia")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Garantía del vehículo", description = "Cada componente cubierto con su fecha y km límite "
			+ "contra el km actual, su consumo y si está vigente, por vencer o vencida. Responde 404 si el vehículo "
			+ "no es del cliente.")
	public GarantiaApp garantia(@PathVariable Long vehiculoId, @AuthenticationPrincipal UsuarioAutenticado cliente) {
		return garantiaAppService.garantia(cliente.id(), vehiculoId);
	}

	@GetMapping("/campanas")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Campañas de seguridad del vehículo", description = "Las que aplican por modelo, año y "
			+ "rango de VIN, con su gravedad, qué hacer y su estado (abierta, agendada o atendida). Responde 404 si "
			+ "el vehículo no es del cliente.")
	public List<CampanaApp> campanas(@PathVariable Long vehiculoId,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return campanasAppService.campanas(cliente.id(), vehiculoId);
	}

	@PostMapping("/campanas/{campanaId}/cita")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Agendar la atención de una campaña", description = "Agenda una cita con motivo campana "
			+ "y la campaña pasa a agendada. Responde 404 si el vehículo no es del cliente o la campaña no le "
			+ "aplica, 409 CAMPANA_NO_AGENDABLE si ya está agendada o atendida y los errores de la agenda.")
	public CitaApp agendar(@PathVariable Long vehiculoId, @PathVariable Long campanaId,
			@Valid @RequestBody AgendarCampanaRequest request, @AuthenticationPrincipal UsuarioAutenticado cliente) {
		return campanasAppService.agendar(cliente.id(), vehiculoId, campanaId, request.franjaId(), request.fecha());
	}
}
