package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EstimacionTomaCuentaApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.Pagina;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.TomaCuentaService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Toma a cuenta de la app (V2-M03.04, V2-M03.06 y V2-M03.07), según el contrato
 * en {@code docs/api/agencia360-api-app-v2.yaml}. Es una estimación no
 * vinculante, nunca una oferta.
 */
@RestController
@RequestMapping("/api/v1/app/toma-a-cuenta")
@Tag(name = "App CareCar - Toma a cuenta", description = "Estimación no vinculante de lo que darían por el auto")
public class TomaCuentaController {

	private final TomaCuentaService tomaCuentaService;

	public TomaCuentaController(TomaCuentaService tomaCuentaService) {
		this.tomaCuentaService = tomaCuentaService;
	}

	@PostMapping("/estimaciones")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Estimar cuánto darían por el auto", description = "Calcula un rango con los parámetros de "
			+ "la agencia y lo guarda en el historial del cliente. Responde 404 si el vehículo no es del cliente o el "
			+ "modelo está retirado, y 422 si la agencia aún no tiene parámetros para ese auto.")
	public EstimacionTomaCuentaApp estimar(@AuthenticationPrincipal UsuarioAutenticado cliente,
			@Valid @RequestBody NuevaEstimacionTomaCuentaRequest request) {
		return tomaCuentaService.estimar(cliente.id(), request.comoSolicitud());
	}

	@GetMapping("/estimaciones")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Listar las estimaciones del cliente", description = "De la más reciente a la más antigua. "
			+ "Paginado.")
	public Pagina<EstimacionTomaCuentaApp> estimaciones(@AuthenticationPrincipal UsuarioAutenticado cliente,
			@RequestParam(required = false) Integer pagina, @RequestParam(required = false) Integer tamano) {
		return tomaCuentaService.listar(cliente.id(), pagina, tamano);
	}
}
