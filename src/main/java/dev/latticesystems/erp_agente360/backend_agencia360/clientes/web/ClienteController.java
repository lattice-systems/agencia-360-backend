package dev.latticesystems.erp_agente360.backend_agencia360.clientes.web;

import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.ClienteDetalle;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.ClienteVista;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.ConsultaClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.VehiculoNuevo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Clientes de la agencia desde el CRM: consulta (CRM-1.5) y alta manual con sus
 * vehículos (CRM-1.3). Solo el Gerente de marketing.
 */
@RestController
@RequestMapping("/api/v1/clientes")
@Tag(name = "Clientes", description = "Clientes de la agencia, separados de los usuarios internos")
public class ClienteController {

	private final ConsultaClientesService consultaClientesService;
	private final RegistroClientesService registroClientesService;

	public ClienteController(ConsultaClientesService consultaClientesService,
			RegistroClientesService registroClientesService) {
		this.consultaClientesService = consultaClientesService;
		this.registroClientesService = registroClientesService;
	}

	@PostMapping
	@PreAuthorize("hasRole('GERENTE_MARKETING')")
	@Operation(summary = "Dar de alta un cliente a mano", description = "Para casos especiales; normalmente el "
			+ "cliente se crea al finalizar su prospecto. Responde 400 si faltan datos y 409 si ya hay un cliente con "
			+ "ese correo.")
	public ResponseEntity<ClienteDetalle> registrar(@Valid @RequestBody RegistrarClienteRequest request) {
		ClienteDetalle creado = registroClientesService
				.registrar(new NuevoCliente(request.nombre(), request.correo(), request.telefono(), null));
		return ResponseEntity.created(URI.create("/api/v1/clientes/" + creado.id())).body(creado);
	}

	@PostMapping("/{id}/vehiculos")
	@PreAuthorize("hasRole('GERENTE_MARKETING')")
	@Operation(summary = "Asignar un vehículo a un cliente", description = "Responde 400 si el VIN no tiene 17 "
			+ "caracteres o el año es menor a 1900, 404 si el cliente no existe y 409 si el VIN ya es de otro "
			+ "cliente.")
	public ClienteDetalle asignarVehiculo(@PathVariable Long id, @Valid @RequestBody AsignarVehiculoRequest request) {
		return registroClientesService.asignarVehiculo(id, new VehiculoNuevo(null, request.vin(), request.marca(),
				request.modelo(), request.anio(), request.version()));
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
