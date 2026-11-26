package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.Pagina;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.PedidoVista;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ProductoApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.TiendaAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tienda de la app (M14), según el contrato en
 * {@code docs/api/agencia360-api-app-v2.yaml}. El cliente sale del token y lo
 * ajeno responde 404.
 */
@RestController
@RequestMapping("/api/v1/app/tienda")
@PreAuthorize("hasRole('CLIENTE_CARECAR')")
@Tag(name = "App CareCar - Tienda", description = "Productos compatibles, apartados y pedidos")
public class TiendaAppController {

	private final TiendaAppService tienda;

	public TiendaAppController(TiendaAppService tienda) {
		this.tienda = tienda;
	}

	@GetMapping("/productos")
	@Operation(summary = "Ver los productos de la tienda", description = "Con vehiculoId, solo los que le quedan.")
	public Pagina<ProductoApp> productos(@AuthenticationPrincipal UsuarioAutenticado cliente,
			@RequestParam(required = false) Long vehiculoId, @RequestParam(required = false) String categoria,
			@RequestParam(name = "q", required = false) String texto, @RequestParam(required = false) Integer pagina,
			@RequestParam(required = false) Integer tamano) {
		return tienda.productos(cliente.id(), vehiculoId, categoria, texto, pagina, tamano);
	}

	@GetMapping("/productos/{productoId}")
	@Operation(summary = "Ver un producto")
	public ProductoApp producto(@PathVariable Long productoId, @RequestParam(required = false) Long vehiculoId,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return tienda.producto(cliente.id(), productoId, vehiculoId);
	}

	@PostMapping("/pedidos")
	@Operation(summary = "Apartar productos", description = "201 con el pedido, o 200 si esa Idempotency-Key ya "
			+ "lo había creado. 409 SIN_EXISTENCIA, CITA_NO_DISPONIBLE o LIMITE_APARTADOS y 422 "
			+ "PRODUCTO_INCOMPATIBLE.")
	public ResponseEntity<PedidoVista> apartar(@RequestHeader(name = "Idempotency-Key", required = false) String llave,
			@Valid @RequestBody NuevoPedidoRequest request, @AuthenticationPrincipal UsuarioAutenticado cliente) {
		TiendaAppService.Apartado apartado = tienda.apartar(cliente.id(), llave, request.comoPedido());
		return ResponseEntity.status(apartado.creado() ? HttpStatus.CREATED : HttpStatus.OK).body(apartado.pedido());
	}

	@GetMapping("/pedidos")
	@Operation(summary = "Listar mis pedidos", description = "El más reciente primero. Paginado.")
	public Pagina<PedidoVista> pedidos(@AuthenticationPrincipal UsuarioAutenticado cliente,
			@RequestParam(required = false) Integer pagina, @RequestParam(required = false) Integer tamano) {
		return tienda.pedidos(cliente.id(), pagina, tamano);
	}

	@GetMapping("/pedidos/{pedidoId}")
	@Operation(summary = "Ver un pedido")
	public PedidoVista ver(@PathVariable Long pedidoId, @AuthenticationPrincipal UsuarioAutenticado cliente) {
		return tienda.ver(cliente.id(), pedidoId);
	}

	@PostMapping("/pedidos/{pedidoId}/cancelar")
	@Operation(summary = "Cancelar un pedido", description = "Libera las piezas. 409 PEDIDO_NO_CANCELABLE.")
	public PedidoVista cancelar(@PathVariable Long pedidoId, @AuthenticationPrincipal UsuarioAutenticado cliente) {
		return tienda.cancelar(cliente.id(), pedidoId);
	}

	@PutMapping("/pedidos/{pedidoId}/instalacion")
	@Operation(summary = "Elegir la cita donde se instala", description = "Con citaId nulo vuelve a mostrador.")
	public PedidoVista instalar(@PathVariable Long pedidoId, @RequestBody InstalacionPedidoRequest request,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return tienda.instalar(cliente.id(), pedidoId, request.citaId());
	}
}
