package dev.latticesystems.erp_agente360.backend_agencia360.servicio.web;

import dev.latticesystems.erp_agente360.backend_agencia360.servicio.application.Expediente;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.application.ExpedienteService;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.application.NuevoServicio;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.application.ServicioDetalle;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.RenglonNuevo;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Expediente del vehículo del cliente (ERP-3.0). Servicio y el Administrador lo
 * consultan; solo Servicio corrige el vehículo y registra servicios. Servicio
 * no crea clientes: si necesita uno, lo registra el Gerente de marketing en el
 * CRM.
 */
@RestController
@RequestMapping("/api/v1/expedientes")
@Tag(name = "Expediente del vehículo", description = "Vehículos de los clientes y sus servicios")
public class ExpedienteController {

	private final ExpedienteService expedienteService;

	public ExpedienteController(ExpedienteService expedienteService) {
		this.expedienteService = expedienteService;
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('SERVICIO', 'ADMINISTRADOR')")
	@Operation(summary = "Buscar vehículos por VIN", description = "Con el VIN completo o una parte de al menos 5 "
			+ "caracteres, sin distinguir mayúsculas. Regresa hasta 20 vehículos ordenados por VIN, con el nombre de "
			+ "su dueño; con menos de 5 caracteres regresa una lista vacía.")
	public List<Expediente.Encontrado> buscar(@RequestParam(required = false) String vin) {
		return expedienteService.buscar(vin);
	}

	@GetMapping("/{vehiculoId}")
	@PreAuthorize("hasAnyRole('SERVICIO', 'ADMINISTRADOR')")
	@Operation(summary = "Consultar el expediente de un vehículo", description = "El vehículo, su dueño y sus "
			+ "servicios del más reciente al más antiguo. Responde 404 si el vehículo no existe.")
	public Expediente expediente(@PathVariable Long vehiculoId) {
		return expedienteService.expediente(vehiculoId);
	}

	@PatchMapping("/{vehiculoId}")
	@PreAuthorize("hasRole('SERVICIO')")
	@Operation(summary = "Corregir las placas y el kilometraje", description = "Un valor vacío lo deja sin "
			+ "capturar. Responde 400 si las placas pasan de 15 caracteres o el kilometraje es negativo, y 404 si "
			+ "el vehículo no existe.")
	public Expediente.Vehiculo corregir(@PathVariable Long vehiculoId,
			@Valid @RequestBody CorregirVehiculoRequest request) {
		return expedienteService.corregir(vehiculoId, request.placas(), request.kilometraje());
	}

	@PostMapping("/{vehiculoId}/servicios")
	@PreAuthorize("hasRole('SERVICIO')")
	@Operation(summary = "Registrar un servicio", description = "Queda a nombre de quien lo registra. Los precios "
			+ "traen el IVA incluido: el total es la suma de los renglones y de ahí se desglosan el subtotal y el "
			+ "IVA. No se edita ni se borra. Responde 400 si la fecha es posterior a hoy o un dato no es válido, y "
			+ "404 si el vehículo no existe.")
	public ResponseEntity<ServicioDetalle> registrar(@PathVariable Long vehiculoId,
			@Valid @RequestBody RegistrarServicioRequest request, @AuthenticationPrincipal UsuarioAutenticado actor) {
		List<RenglonNuevo> renglones = request.renglones().stream().map(renglon -> new RenglonNuevo(renglon.tipo(),
				renglon.concepto(), renglon.cantidad(), renglon.precioUnitario())).toList();
		ServicioDetalle creado = expedienteService.registrar(vehiculoId,
				new NuevoServicio(request.fecha(), request.kilometraje(), renglones), actor);
		return ResponseEntity.created(URI.create("/api/v1/expedientes/" + vehiculoId + "/servicios/" + creado.id()))
				.body(creado);
	}

	@GetMapping("/{vehiculoId}/servicios/{servicioId}")
	@PreAuthorize("hasAnyRole('SERVICIO', 'ADMINISTRADOR')")
	@Operation(summary = "Ver los detalles de un servicio", description = "Sus renglones con cantidad, precio "
			+ "unitario e importe, y el subtotal, el IVA y el total. Responde 404 si no existe o no es de ese "
			+ "vehículo.")
	public ServicioDetalle detalle(@PathVariable Long vehiculoId, @PathVariable Long servicioId) {
		return expedienteService.detalle(vehiculoId, servicioId);
	}
}
