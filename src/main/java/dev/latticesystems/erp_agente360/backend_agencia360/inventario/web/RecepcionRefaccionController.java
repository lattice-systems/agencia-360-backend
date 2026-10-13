package dev.latticesystems.erp_agente360.backend_agencia360.inventario.web;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.ConsultaRecepcionesService;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.RecepcionRefaccionService;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.RecepcionRegistrada;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.RecepcionVista;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recepción de refacciones (ERP-2.0). Solo Inventario registra; Refacciones y
 * Administrador consultan el historial.
 */
@RestController
@RequestMapping("/api/v1/refacciones/{id}/recepciones")
@Tag(name = "Refacciones", description = "Alta y consulta de las refacciones del inventario")
public class RecepcionRefaccionController {

	private final RecepcionRefaccionService recepcionRefaccionService;
	private final ConsultaRecepcionesService consultaRecepcionesService;

	public RecepcionRefaccionController(RecepcionRefaccionService recepcionRefaccionService,
			ConsultaRecepcionesService consultaRecepcionesService) {
		this.recepcionRefaccionService = recepcionRefaccionService;
		this.consultaRecepcionesService = consultaRecepcionesService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('INVENTARIO')")
	@Operation(summary = "Registrar una entrega de piezas", description = "Suma la cantidad a la existencia y "
			+ "guarda el proveedor, las notas, la fecha de llegada (si no viene, la del registro), la fecha de "
			+ "registro y el usuario en sesión como responsable. Responde 400 si la cantidad no es mayor a cero, "
			+ "falta el proveedor o la llegada es posterior al registro, y 404 si la refacción no existe.")
	public RecepcionRegistrada recibir(@PathVariable Long id, @Valid @RequestBody RegistrarRecepcionRequest request,
			@AuthenticationPrincipal UsuarioAutenticado actor) {
		return recepcionRefaccionService.recibir(id, request.comoRecepcion(), actor);
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('INVENTARIO', 'REFACCIONES', 'ADMINISTRADOR')")
	@Operation(summary = "Historial de recepciones de una refacción", description = "De la más reciente a la más "
			+ "antigua, con la fecha de llegada y la de registro. Responde 404 si la refacción no existe.")
	public List<RecepcionVista> listar(@PathVariable Long id) {
		return consultaRecepcionesService.listar(id);
	}
}
