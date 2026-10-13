package dev.latticesystems.erp_agente360.backend_agencia360.inventario.web;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.ConsultaVehiculosService;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.RegistroVehiculoService;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.VehiculoVista;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.EstatusVehiculo;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Alta y consulta de unidades del inventario (ERP-2.0). Solo Inventario. */
@RestController
@RequestMapping("/api/v1/vehiculos")
@PreAuthorize("hasRole('INVENTARIO')")
@Tag(name = "Vehículos", description = "Alta y consulta de las unidades del inventario")
public class VehiculoController {

	private final RegistroVehiculoService registroVehiculoService;
	private final ConsultaVehiculosService consultaVehiculosService;

	public VehiculoController(RegistroVehiculoService registroVehiculoService,
			ConsultaVehiculosService consultaVehiculosService) {
		this.registroVehiculoService = registroVehiculoService;
		this.consultaVehiculosService = consultaVehiculosService;
	}

	@PostMapping
	@Operation(summary = "Dar de alta una unidad", description = "Entra como Disponible, o como No disponible si "
			+ "se indica (daños u otros motivos), y deja su entrada al inventario a cargo del usuario en sesión. Responde "
			+ "400 si faltan datos, el VIN no tiene 17 caracteres (sin I, O ni Q), el año es menor a 1900 o el estatus no "
			+ "es de entrada, y 409 si el VIN ya existe.")
	public ResponseEntity<VehiculoVista> registrar(@Valid @RequestBody RegistrarVehiculoRequest request,
			@AuthenticationPrincipal UsuarioAutenticado actor) {
		VehiculoVista creado = registroVehiculoService.registrar(request.comoNuevoVehiculo(), actor);
		return ResponseEntity.created(URI.create("/api/v1/vehiculos/" + creado.id())).body(creado);
	}

	@GetMapping
	@Operation(summary = "Listar unidades, filtrable por estatus", description = "De la más reciente a la más "
			+ "antigua. Estatus: DISPONIBLE, APARTADA, VENDIDO, ENTREGADO o NO_DISPONIBLE.")
	public List<VehiculoVista> listar(@RequestParam(required = false) String estatus) {
		return consultaVehiculosService.listar(estatus == null ? null : EstatusVehiculo.desde(estatus));
	}

	@GetMapping("/{id}")
	@Operation(summary = "Consultar una unidad", description = "Responde 404 si no existe.")
	public VehiculoVista obtener(@PathVariable Long id) {
		return consultaVehiculosService.obtener(id);
	}
}
