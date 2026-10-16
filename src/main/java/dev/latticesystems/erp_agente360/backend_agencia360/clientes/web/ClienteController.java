package dev.latticesystems.erp_agente360.backend_agencia360.clientes.web;

import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.ClienteDetalle;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.ClienteVista;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.ConsultaClientesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consulta de los clientes de la agencia desde el CRM (CRM-1.5). */
@RestController
@RequestMapping("/api/v1/clientes")
@Tag(name = "Clientes", description = "Clientes de la agencia, separados de los usuarios internos")
public class ClienteController {

	private final ConsultaClientesService consultaClientesService;

	public ClienteController(ConsultaClientesService consultaClientesService) {
		this.consultaClientesService = consultaClientesService;
	}

	@GetMapping
	@PreAuthorize("hasRole('GERENTE_MARKETING')")
	@Operation(summary = "Listar clientes", description = "En orden alfabético, con cuántos vehículos tiene cada "
			+ "uno. Con buscar, filtra por nombre o correo sin distinguir mayúsculas.")
	public List<ClienteVista> listar(@RequestParam(required = false) String buscar) {
		return consultaClientesService.listar(buscar);
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasRole('GERENTE_MARKETING')")
	@Operation(summary = "Consultar un cliente con sus vehículos", description = "Responde 404 si no existe.")
	public ClienteDetalle obtener(@PathVariable Long id) {
		return consultaClientesService.obtener(id);
	}
}
