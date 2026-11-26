package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.Pagina;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ValuacionPersonalApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ValuacionService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoValuacion;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Valuaciones que atiende ventas mientras el CRM siga congelado (V2-M26.04). Se
 * operan con Swagger UI en local o con un cliente HTTP.
 */
@RestController
@RequestMapping("/api/v1/valuaciones")
@PreAuthorize("hasAnyRole('VENTAS', 'ADMINISTRADOR')")
@Tag(name = "Ventas - Valuaciones", description = "Consulta de valuaciones y captura de la oferta")
public class ValuacionPersonalController {

	private final ValuacionService valuaciones;

	public ValuacionPersonalController(ValuacionService valuaciones) {
		this.valuaciones = valuaciones;
	}

	@GetMapping
	@Operation(summary = "Listar valuaciones", description = "Las más antiguas primero. Ventas ve solo las de sus "
			+ "prospectos. Responde 400 si el estado o la página no son válidos.")
	public Pagina<ValuacionPersonalApp> listar(@AuthenticationPrincipal UsuarioAutenticado actor,
			@RequestParam(required = false) EstadoValuacion estado, @RequestParam(required = false) Integer pagina,
			@RequestParam(required = false) Integer tamano) {
		return valuaciones.delPersonal(actor, estado, pagina, tamano);
	}

	@GetMapping("/{id}")
	@Operation(summary = "Ver una valuación", description = "Cliente, auto, rango estimado, cita y fotos. Responde "
			+ "404 si no existe o ventas no atiende su prospecto.")
	public ValuacionPersonalApp ver(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado actor) {
		return valuaciones.verDelPersonal(actor, id);
	}

	@PostMapping("/{id}/oferta")
	@Operation(summary = "Capturar o renovar la oferta", description = "Tras la inspección, o para renovar una "
			+ "vencida. Una oferta vigente no se edita. Responde 400, 404 o 409 TRANSICION_INVALIDA.")
	public ValuacionPersonalApp ofertar(@PathVariable Long id, @Valid @RequestBody OfertaValuacionRequest request,
			@AuthenticationPrincipal UsuarioAutenticado actor) {
		return valuaciones.ofertar(actor, id, request.monto(), request.vigenciaHasta(), request.nota());
	}
}
