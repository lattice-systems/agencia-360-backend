package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.web;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application.LiquidacionVista;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application.LiquidacionesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verificación de los pagos de la venta (ERP-1.1): Caja registra y envía los
 * pagos de un prospecto en Pago; Contabilidad los valida o los rechaza.
 */
@RestController
@RequestMapping("/api/v1/liquidaciones")
@Tag(name = "Pagos de la venta", description = "Caja registra los pagos y Contabilidad los valida (ERP-1.1)")
public class LiquidacionController {

	private final LiquidacionesService liquidacionesService;

	public LiquidacionController(LiquidacionesService liquidacionesService) {
		this.liquidacionesService = liquidacionesService;
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('CAJA', 'CONTABILIDAD')")
	@Operation(summary = "Prospectos en Pago con sus pagos", description = "Incluye los que aún no tienen pagos.")
	public List<LiquidacionVista> listar(@AuthenticationPrincipal UsuarioAutenticado actor) {
		return liquidacionesService.listar(actor);
	}

	@GetMapping("/{prospectoId}")
	@PreAuthorize("hasAnyRole('CAJA', 'CONTABILIDAD')")
	@Operation(summary = "Pagos de un prospecto", description = "Responde 404 si el prospecto no existe.")
	public LiquidacionVista detalle(@PathVariable Long prospectoId, @AuthenticationPrincipal UsuarioAutenticado actor) {
		return liquidacionesService.detalle(prospectoId, actor);
	}

	@PostMapping("/{prospectoId}/pagos")
	@PreAuthorize("hasRole('CAJA')")
	@Operation(summary = "Registrar un pago", description = "Solo con el prospecto en Pago y antes de enviar los "
			+ "pagos. Responde 400 si el monto o la fecha no son válidos y 409 si ya se enviaron.")
	public ResponseEntity<LiquidacionVista> registrarPago(@PathVariable Long prospectoId,
			@Valid @RequestBody RegistrarPagoRequest request, @AuthenticationPrincipal UsuarioAutenticado actor) {
		return ResponseEntity.status(HttpStatus.CREATED).body(liquidacionesService.registrarPago(prospectoId,
				request.monto(), request.fechaPago(), request.referencia(), actor));
	}

	@PostMapping("/{prospectoId}/pagos/{pagoId}/anular")
	@PreAuthorize("hasRole('CAJA')")
	@Operation(summary = "Anular un pago mal capturado", description = "Solo antes de enviar los pagos. El pago "
			+ "no se borra: queda registrado como anulado y deja de contar en el total.")
	public LiquidacionVista anularPago(@PathVariable Long prospectoId, @PathVariable Long pagoId,
			@AuthenticationPrincipal UsuarioAutenticado actor) {
		return liquidacionesService.anularPago(prospectoId, pagoId, actor);
	}

	@PostMapping("/{prospectoId}/enviar")
	@PreAuthorize("hasRole('CAJA')")
	@Operation(summary = "Enviar los pagos a Contabilidad", description = "Responde 409 si no hay pagos.")
	public LiquidacionVista enviar(@PathVariable Long prospectoId, @AuthenticationPrincipal UsuarioAutenticado actor) {
		return liquidacionesService.enviar(prospectoId, actor);
	}

	@PostMapping("/{prospectoId}/validar")
	@PreAuthorize("hasRole('CONTABILIDAD')")
	@Operation(summary = "Validar los pagos", description = "El prospecto pasa de Pago a Facturación. Responde 409 "
			+ "si Caja aún no los envía.")
	public LiquidacionVista validar(@PathVariable Long prospectoId, @AuthenticationPrincipal UsuarioAutenticado actor) {
		return liquidacionesService.validar(prospectoId, actor);
	}

	@PostMapping("/{prospectoId}/rechazar")
	@PreAuthorize("hasRole('CONTABILIDAD')")
	@Operation(summary = "Rechazar los pagos", description = "Regresan a Caja con el motivo; el prospecto sigue en "
			+ "Pago. Responde 400 sin motivo.")
	public LiquidacionVista rechazar(@PathVariable Long prospectoId, @RequestBody RechazarPagosRequest request,
			@AuthenticationPrincipal UsuarioAutenticado actor) {
		return liquidacionesService.rechazar(prospectoId, request.motivo(), actor);
	}

	/** El monto, la fecha y el largo de la referencia los valida el dominio. */
	public record RegistrarPagoRequest(BigDecimal monto, LocalDate fechaPago, String referencia) {
	}

	/** El motivo es obligatorio; esa regla la valida el dominio. */
	public record RechazarPagosRequest(String motivo) {
	}
}
