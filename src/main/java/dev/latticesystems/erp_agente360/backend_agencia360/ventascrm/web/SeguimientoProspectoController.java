package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.web;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application.MovimientoVista;
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

	@GetMapping("/{id}/historial")
	@PreAuthorize("hasAnyRole('VENTAS', 'GERENTE_MARKETING')")
	@Operation(summary = "Historial de etapas de un prospecto", description = "Del más antiguo al más reciente, "
			+ "con quién hizo cada cambio. Responde 404 si el prospecto no existe o es de otro vendedor.")
	public List<MovimientoVista> historial(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado actor) {
		return seguimientoProspectosService.historial(id, actor);
	}

	@PatchMapping("/{id}/etapa")
	@PreAuthorize("hasAnyRole('VENTAS', 'FACTURACION')")
	@Operation(summary = "Cambiar la etapa de un prospecto", description = "El vendedor dueño mueve Primer contacto, "
			+ "Visita y Cotización entre sí, a Pago o a Cerrado (con motivo), y de Entrega a Finalizado. "
			+ "Al pasar a Pago el vendedor manda precioTotal (obligatorio). "
			+ "Facturación pasa de Facturación a Entrega. De Pago a Facturación solo se pasa validando los pagos "
			+ "(/api/v1/liquidaciones). Para finalizar hace falta el correo del cliente (correoCliente si "
			+ "faltaba), salvo con clienteRechazaApp=true. Un cambio que no corresponde responde 409; un rol que "
			+ "no puede hacerlo, 403; un prospecto ajeno, 404.")
	public ProspectoVista cambiarEtapa(@PathVariable Long id, @Valid @RequestBody CambiarEtapaRequest request,
			@AuthenticationPrincipal UsuarioAutenticado actor) {
		return seguimientoProspectosService.cambiarEtapa(id, request.etapa(), request.motivoCierre(),
				Boolean.TRUE.equals(request.clienteRechazaApp()), request.correoCliente(), request.precioTotal(),
				actor);
	}
}
