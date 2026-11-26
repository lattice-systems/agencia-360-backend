package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.Pagina;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.PedidoVista;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.TiendaAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoPedido;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pedidos que surte Refacciones mientras el ERP siga congelado (V2-M14.05). Se
 * operan con Swagger UI en local o con un cliente HTTP.
 */
@RestController
@RequestMapping("/api/v1/tienda/pedidos")
@PreAuthorize("hasAnyRole('REFACCIONES', 'ADMINISTRADOR')")
@Tag(name = "Tienda - Pedidos", description = "Surtido de los pedidos de la app")
public class TiendaPersonalController {

	private final TiendaAppService tienda;

	public TiendaPersonalController(TiendaAppService tienda) {
		this.tienda = tienda;
	}

	@GetMapping
	@Operation(summary = "Listar pedidos", description = "Sin estado, los abiertos. Los más antiguos primero.")
	public Pagina<PedidoVista> listar(@RequestParam(required = false) EstadoPedido estado,
			@RequestParam(required = false) Integer pagina, @RequestParam(required = false) Integer tamano) {
		return tienda.delPersonal(estado, pagina, tamano);
	}

	@PostMapping("/{pedidoId}/listo")
	@Operation(summary = "Marcar un pedido listo", description = "409 TRANSICION_INVALIDA si no está apartado.")
	public PedidoVista listo(@PathVariable Long pedidoId) {
		return tienda.marcarListo(pedidoId);
	}

	@PostMapping("/{pedidoId}/entregado")
	@Operation(summary = "Marcar un pedido entregado", description = "409 TRANSICION_INVALIDA si no está listo.")
	public PedidoVista entregado(@PathVariable Long pedidoId) {
		return tienda.entregar(pedidoId);
	}

	@PostMapping("/{pedidoId}/cancelar")
	@Operation(summary = "Cancelar un pedido", description = "Libera las piezas. 409 PEDIDO_NO_CANCELABLE.")
	public PedidoVista cancelar(@PathVariable Long pedidoId) {
		return tienda.cancelarPorLaAgencia(pedidoId);
	}
}
