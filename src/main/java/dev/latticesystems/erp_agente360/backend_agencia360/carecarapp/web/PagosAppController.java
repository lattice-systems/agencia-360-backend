package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.MetodoPagoVista;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.Pagina;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.PagoVista;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.PagosAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pagar una orden o un pedido de la tienda, mis pagos y mis tarjetas guardadas
 * (V2-M08.02, V2-M08.04, V2-M08.07 y V2-M14.04). El procesador es simulado (ADR
 * 0009): no se cobra nada.
 */
@RestController
@RequestMapping("/api/v1/app")
@PreAuthorize("hasRole('CLIENTE_CARECAR')")
@Tag(name = "App CareCar - Pagos y facturas", description = "Datos fiscales, pagos y facturas simulados (ADR 0009)")
public class PagosAppController {

	private final PagosAppService pagos;

	public PagosAppController(PagosAppService pagos) {
		this.pagos = pagos;
	}

	@PostMapping("/ordenes/{ordenId}/pagos")
	@Operation(summary = "Pagar una orden", description = "201 con el intento, o 200 si esa Idempotency-Key ya lo "
			+ "había creado. Queda PAGADO, FALLIDO con su motivo o PENDIENTE con requiereAutenticacion. 409 "
			+ "ORDEN_NO_PAGABLE o PAGO_EN_CURSO.")
	public ResponseEntity<PagoVista> pagar(@PathVariable Long ordenId,
			@RequestHeader(name = "Idempotency-Key", required = false) String llave,
			@Valid @RequestBody NuevoPagoRequest request, @AuthenticationPrincipal UsuarioAutenticado cliente) {
		PagosAppService.Cobro cobro = pagos.pagar(cliente.id(), ordenId, llave, request.comoPago());
		return ResponseEntity.status(cobro.creado() ? HttpStatus.CREATED : HttpStatus.OK).body(cobro.pago());
	}

	@PostMapping("/pedidos/{pedidoId}/pagos")
	@Operation(summary = "Pagar un pedido de la tienda", description = "Igual que pagar una orden: 201 con el "
			+ "intento, o 200 si esa Idempotency-Key ya lo había creado. 409 PEDIDO_NO_PAGABLE o PAGO_EN_CURSO.")
	public ResponseEntity<PagoVista> pagarPedido(@PathVariable Long pedidoId,
			@RequestHeader(name = "Idempotency-Key", required = false) String llave,
			@Valid @RequestBody NuevoPagoRequest request, @AuthenticationPrincipal UsuarioAutenticado cliente) {
		PagosAppService.Cobro cobro = pagos.pagarPedido(cliente.id(), pedidoId, llave, request.comoPago());
		return ResponseEntity.status(cobro.creado() ? HttpStatus.CREATED : HttpStatus.OK).body(cobro.pago());
	}

	@PostMapping("/pagos/{pagoId}/autenticacion")
	@Operation(summary = "Responder el 3-D Secure", description = "Queda PAGADO o FALLIDO. 409 PAGO_NO_PENDIENTE.")
	public PagoVista autenticar(@PathVariable Long pagoId, @Valid @RequestBody AutenticacionPagoRequest request,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return pagos.autenticar(cliente.id(), pagoId, request.confirmada());
	}

	@GetMapping("/pagos")
	@Operation(summary = "Mis pagos", description = "Del más nuevo al más viejo. Paginado.")
	public Pagina<PagoVista> pagos(@AuthenticationPrincipal UsuarioAutenticado cliente,
			@RequestParam(required = false) Integer pagina, @RequestParam(required = false) Integer tamano) {
		return pagos.pagos(cliente.id(), pagina, tamano);
	}

	@GetMapping("/pagos/{pagoId}")
	@Operation(summary = "Ver un pago")
	public PagoVista pago(@PathVariable Long pagoId, @AuthenticationPrincipal UsuarioAutenticado cliente) {
		return pagos.pago(cliente.id(), pagoId);
	}

	@GetMapping("/metodos-pago")
	@Operation(summary = "Mis tarjetas guardadas", description = "La predeterminada primero. Sin número ni token.")
	public List<MetodoPagoVista> metodos(@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return pagos.metodos(cliente.id());
	}

	@PutMapping("/metodos-pago/{metodoPagoId}/predeterminado")
	@Operation(summary = "Marcar la tarjeta predeterminada")
	public MetodoPagoVista predeterminar(@PathVariable Long metodoPagoId,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return pagos.predeterminar(cliente.id(), metodoPagoId);
	}

	@DeleteMapping("/metodos-pago/{metodoPagoId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Borrar una tarjeta guardada", description = "Los pagos hechos con ella se conservan.")
	public void borrar(@PathVariable Long metodoPagoId, @AuthenticationPrincipal UsuarioAutenticado cliente) {
		pagos.borrarMetodo(cliente.id(), metodoPagoId);
	}
}
