package dev.latticesystems.erp_agente360.backend_agencia360.inventario.web;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.ConsultaRefaccionesService;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.RefaccionVista;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.RegistroRefaccionService;
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
import org.springframework.web.bind.annotation.RestController;

/** Alta y consulta de refacciones del inventario (ERP-2.0). Solo Inventario. */
@RestController
@RequestMapping("/api/v1/refacciones")
@PreAuthorize("hasRole('INVENTARIO')")
@Tag(name = "Refacciones", description = "Alta y consulta de las refacciones del inventario")
public class RefaccionController {

	private final RegistroRefaccionService registroRefaccionService;
	private final ConsultaRefaccionesService consultaRefaccionesService;

	public RefaccionController(RegistroRefaccionService registroRefaccionService,
			ConsultaRefaccionesService consultaRefaccionesService) {
		this.registroRefaccionService = registroRefaccionService;
		this.consultaRefaccionesService = consultaRefaccionesService;
	}

	@PostMapping
	@Operation(summary = "Dar de alta una refacción", description = "La existencia es opcional: si viene, entra "
			+ "como la primera recepción a cargo del usuario en sesión. Responde 400 si faltan datos, el costo o la "
			+ "existencia son negativos, el precio no es mayor a cero o el mínimo es negativo, y 409 si el código ya "
			+ "existe.")
	public ResponseEntity<RefaccionVista> registrar(@Valid @RequestBody RegistrarRefaccionRequest request,
			@AuthenticationPrincipal UsuarioAutenticado actor) {
		RefaccionVista creada = registroRefaccionService.registrar(request.comoNuevaRefaccion(), actor);
		return ResponseEntity.created(URI.create("/api/v1/refacciones/" + creada.id())).body(creada);
	}

	@GetMapping
	@Operation(summary = "Listar refacciones", description = "Ordenadas por código.")
	public List<RefaccionVista> listar() {
		return consultaRefaccionesService.listar();
	}

	@GetMapping("/{id}")
	@Operation(summary = "Consultar una refacción", description = "Responde 404 si no existe.")
	public RefaccionVista obtener(@PathVariable Long id) {
		return consultaRefaccionesService.obtener(id);
	}
}
