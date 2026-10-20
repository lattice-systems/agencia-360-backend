package dev.latticesystems.erp_agente360.backend_agencia360.inventario.web;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.AsignacionUnidadService;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.ConsultaVentasService;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.VentaVista;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Venta de unidades (ERP-1.0). Facturación elige la unidad de cada prospecto
 * que llegó a Facturación; la venta queda registrada cuando el prospecto llega
 * a Finalizado.
 */
@RestController
@RequestMapping("/api/v1/ventas")
@Tag(name = "Ventas", description = "Venta de unidades: la unidad que Facturación elige para cada prospecto")
public class VentaController {

	private final AsignacionUnidadService asignacionUnidadService;
	private final ConsultaVentasService consultaVentasService;

	public VentaController(AsignacionUnidadService asignacionUnidadService,
			ConsultaVentasService consultaVentasService) {
		this.asignacionUnidadService = asignacionUnidadService;
		this.consultaVentasService = consultaVentasService;
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('FACTURACION', 'ADMINISTRADOR')")
	@Operation(summary = "Listar las ventas", description = "De la más reciente a la más antigua, con su unidad.")
	public List<VentaVista> listar() {
		return consultaVentasService.listar();
	}

	@PutMapping("/{prospectoId}/unidad")
	@PreAuthorize("hasRole('FACTURACION')")
	@Operation(summary = "Elegir la unidad de la venta de un prospecto", description = "La unidad pasa de "
			+ "Disponible a Vendido. Si el prospecto ya tenía unidad y la venta no se ha registrado, se cambia y la "
			+ "anterior regresa a Disponible. Responde 404 si no existe la unidad o el prospecto y 409 si la unidad "
			+ "ya no está disponible o la venta ya se registró.")
	public VentaVista asignar(@PathVariable Long prospectoId, @Valid @RequestBody AsignarUnidadRequest request,
			@AuthenticationPrincipal UsuarioAutenticado actor) {
		return asignacionUnidadService.asignar(prospectoId, request.vehiculoId(), actor);
	}
}
