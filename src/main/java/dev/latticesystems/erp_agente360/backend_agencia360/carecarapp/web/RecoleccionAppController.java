package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CotizacionRecoleccionApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.RecoleccionService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Cotización de la recolección a domicilio (V2-M17.02). */
@RestController
@RequestMapping("/api/v1/app/recoleccion")
@Tag(name = "App CareCar - Recolección", description = "Direcciones, cotización y traslados a domicilio")
public class RecoleccionAppController {

	private final RecoleccionService recoleccionService;

	public RecoleccionAppController(RecoleccionService recoleccionService) {
		this.recoleccionService = recoleccionService;
	}

	@GetMapping("/cotizacion")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Costo y franja de la recolección", description = "Para una dirección propia y un día "
			+ "en que el taller abre. Responde 404 si la dirección no es del cliente y 422 con FUERA_DE_COBERTURA "
			+ "si ya no está en la zona.")
	public CotizacionRecoleccionApp cotizacion(@RequestParam Long direccionId, @RequestParam LocalDate fecha,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return recoleccionService.cotizar(cliente.id(), direccionId, fecha);
	}
}
