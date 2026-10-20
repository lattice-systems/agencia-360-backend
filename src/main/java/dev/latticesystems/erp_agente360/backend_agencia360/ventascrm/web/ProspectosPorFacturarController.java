package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.web;

import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application.ProspectoPorFacturar;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application.ProspectosPorFacturarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prospectos que Facturación atiende desde el ERP (ERP-1.0). Facturación no
 * entra al CRM; solo ve los prospectos que ya pasaron Pago.
 */
@RestController
@RequestMapping("/api/v1/prospectos/por-facturar")
@Tag(name = "Prospectos")
public class ProspectosPorFacturarController {

	private final ProspectosPorFacturarService prospectosPorFacturarService;

	public ProspectosPorFacturarController(ProspectosPorFacturarService prospectosPorFacturarService) {
		this.prospectosPorFacturarService = prospectosPorFacturarService;
	}

	@GetMapping
	@PreAuthorize("hasRole('FACTURACION')")
	@Operation(summary = "Listar los prospectos en Facturación", description = "Del más reciente al más antiguo, "
			+ "con el nombre de su vendedor. Facturación les elige la unidad (PUT /api/v1/ventas/{id}/unidad) y "
			+ "después los pasa a Entrega.")
	public List<ProspectoPorFacturar> listar() {
		return prospectosPorFacturarService.listar();
	}
}
