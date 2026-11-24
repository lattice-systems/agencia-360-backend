package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.DireccionApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.DireccionesAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Direcciones guardadas del cliente para la recolección (V2-M17.01). */
@RestController
@RequestMapping("/api/v1/app/direcciones")
@Tag(name = "App CareCar - Recolección", description = "Direcciones, cotización y traslados a domicilio")
public class DireccionesAppController {

	private final DireccionesAppService direccionesAppService;

	public DireccionesAppController(DireccionesAppService direccionesAppService) {
		this.direccionesAppService = direccionesAppService;
	}

	@GetMapping
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Listar mis direcciones")
	public List<DireccionApp> listar(@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return direccionesAppService.listar(cliente.id());
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Guardar una dirección", description = "Responde 422 con FUERA_DE_COBERTURA si el pin "
			+ "queda fuera de la zona de cobertura.")
	public DireccionApp crear(@Valid @RequestBody DireccionRequest request,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return direccionesAppService.crear(cliente.id(), request.alias(), request.calle(), request.colonia(),
				request.cp(), request.ciudad(), request.lat(), request.lng());
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Editar una dirección", description = "Responde 404 si no es del cliente y 422 con "
			+ "FUERA_DE_COBERTURA si el nuevo pin queda fuera de la zona.")
	public DireccionApp editar(@PathVariable Long id, @Valid @RequestBody DireccionRequest request,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return direccionesAppService.editar(cliente.id(), id, request.alias(), request.calle(), request.colonia(),
				request.cp(), request.ciudad(), request.lat(), request.lng());
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Borrar una dirección", description = "Responde 404 si no es del cliente y 409 con "
			+ "DIRECCION_EN_USO si ya tiene traslados.")
	public void eliminar(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado cliente) {
		direccionesAppService.eliminar(cliente.id(), id);
	}
}
