package dev.latticesystems.erp_agente360.backend_agencia360.inventario.web;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.ConsultaVentasService;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.VentaVista;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ventas de unidades. Desde ERP-1.3 la venta se registra al emitir la factura
 * ({@code POST /api/v1/facturas}); aquí solo se consultan.
 */
@RestController
@RequestMapping("/api/v1/ventas")
@Tag(name = "Ventas", description = "Venta de unidades: la unidad que se vendió con la factura de cada prospecto")
public class VentaController {

	private final ConsultaVentasService consultaVentasService;

	public VentaController(ConsultaVentasService consultaVentasService) {
		this.consultaVentasService = consultaVentasService;
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('FACTURACION', 'ADMINISTRADOR')")
	@Operation(summary = "Listar las ventas", description = "De la más reciente a la más antigua, con su unidad.")
	public List<VentaVista> listar() {
		return consultaVentasService.listar();
	}
}
