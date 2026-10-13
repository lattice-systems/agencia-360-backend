package dev.latticesystems.erp_agente360.backend_agencia360.inventario.web;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.RecepcionRefaccionService;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.RecepcionRegistrada;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Recepción de refacciones (ERP-2.0). Solo Inventario. */
@RestController
@RequestMapping("/api/v1/refacciones")
@Tag(name = "Refacciones", description = "Alta y consulta de las refacciones del inventario")
public class RecepcionRefaccionController {

	private final RecepcionRefaccionService recepcionRefaccionService;

	public RecepcionRefaccionController(RecepcionRefaccionService recepcionRefaccionService) {
		this.recepcionRefaccionService = recepcionRefaccionService;
	}

	@PostMapping("/{id}/recepciones")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('INVENTARIO')")
	@Operation(summary = "Registrar una recepción de piezas", description = "Suma la cantidad a la existencia y "
			+ "guarda la fecha, la cantidad y el usuario en sesión como responsable. Responde 400 si la cantidad no "
			+ "es mayor a cero y 404 si la refacción no existe.")
	public RecepcionRegistrada recibir(@PathVariable Long id, @Valid @RequestBody RegistrarRecepcionRequest request,
			@AuthenticationPrincipal UsuarioAutenticado actor) {
		return recepcionRefaccionService.recibir(id, request.cantidad(), actor);
	}
}
