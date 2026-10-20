package dev.latticesystems.erp_agente360.backend_agencia360.inventario.web;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.ConsultaVehiculosService;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.EdicionVehiculoService;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.RegistroVehiculoService;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.VehiculoVista;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.EstatusVehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Alta y consulta de unidades del inventario (ERP-2.0). Solo Inventario da de
 * alta; Administrador consulta todas y Ventas solo las disponibles.
 */
@RestController
@RequestMapping("/api/v1/vehiculos")
@Tag(name = "Vehículos", description = "Alta y consulta de las unidades del inventario")
public class VehiculoController {

	private final RegistroVehiculoService registroVehiculoService;
	private final ConsultaVehiculosService consultaVehiculosService;
	private final EdicionVehiculoService edicionVehiculoService;
	private final AvisosDeVehiculos avisos;

	public VehiculoController(RegistroVehiculoService registroVehiculoService,
			ConsultaVehiculosService consultaVehiculosService, EdicionVehiculoService edicionVehiculoService,
			AvisosDeVehiculos avisos) {
		this.registroVehiculoService = registroVehiculoService;
		this.consultaVehiculosService = consultaVehiculosService;
		this.edicionVehiculoService = edicionVehiculoService;
		this.avisos = avisos;
	}

	@PostMapping
	@PreAuthorize("hasRole('INVENTARIO')")
	@Operation(summary = "Dar de alta una unidad", description = "Entra como Disponible, o como No disponible si "
			+ "se indica (daños u otros motivos), y deja su entrada al inventario a cargo del usuario en sesión. Responde "
			+ "400 si faltan datos, el VIN no tiene 17 caracteres (sin I, O ni Q), el año es menor a 1900, el precio no es "
			+ "mayor a cero o el estatus no es de entrada, y 409 si el VIN ya existe.")
	public ResponseEntity<VehiculoVista> registrar(@Valid @RequestBody RegistrarVehiculoRequest request,
			@AuthenticationPrincipal UsuarioAutenticado actor) {
		VehiculoVista creado = registroVehiculoService.registrar(request.comoNuevoVehiculo(), actor);
		return ResponseEntity.created(URI.create("/api/v1/vehiculos/" + creado.id())).body(creado);
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('INVENTARIO', 'ADMINISTRADOR', 'VENTAS', 'FACTURACION')")
	@Operation(summary = "Listar unidades, filtrable por estatus", description = "De la más reciente a la más "
			+ "antigua. Estatus: DISPONIBLE, VENDIDO, ENTREGADO o NO_DISPONIBLE. Ventas y Facturación solo "
			+ "reciben las disponibles.")
	public List<VehiculoVista> listar(@RequestParam(required = false) String estatus,
			@AuthenticationPrincipal UsuarioAutenticado actor) {
		return consultaVehiculosService.listar(estatus == null ? null : EstatusVehiculo.desde(estatus), actor);
	}

	@GetMapping(path = "/eventos", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@PreAuthorize("hasAnyRole('INVENTARIO', 'ADMINISTRADOR', 'VENTAS', 'FACTURACION')")
	@Operation(summary = "Recibir los cambios del inventario en tiempo real", description = "Server-Sent Events "
			+ "(ERP-2.1). Manda el evento \"vehiculos\" cada vez que se da de alta o se corrige una unidad; el cliente "
			+ "vuelve a consultar la lista. Envía un comentario cada 25 s para mantener viva la conexión y la cierra a "
			+ "los 30 min para que el cliente se reconecte con su token.")
	public SseEmitter eventos() {
		return avisos.suscribir();
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAnyRole('INVENTARIO', 'ADMINISTRADOR', 'VENTAS', 'FACTURACION')")
	@Operation(summary = "Consultar una unidad", description = "Responde 404 si no existe o, para Ventas, si "
			+ "no está disponible.")
	public VehiculoVista obtener(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado actor) {
		return consultaVehiculosService.obtener(id, actor);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('INVENTARIO')")
	@Operation(summary = "Corregir los datos de una unidad", description = "Las unidades se editan, no se eliminan. "
			+ "Inventario solo cambia el estatus entre DISPONIBLE y NO_DISPONIBLE; sin estatus se conserva. Responde "
			+ "400 si un dato no es válido, 404 si no existe y 409 si otra unidad ya tiene ese VIN.")
	public VehiculoVista editar(@PathVariable Long id, @Valid @RequestBody EditarVehiculoRequest request) {
		return edicionVehiculoService.editar(id, request.comoVehiculoEditado());
	}
}
