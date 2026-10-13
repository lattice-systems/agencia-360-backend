package dev.latticesystems.erp_agente360.backend_agencia360.inventario.web;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.ConsultaRefaccionesService;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.EdicionRefaccionService;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.RefaccionVista;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.RegistroRefaccionService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Alta y consulta de refacciones del inventario (ERP-2.0). Solo Inventario da
 * de alta; Refacciones y Administrador solo consultan.
 */
@RestController
@RequestMapping("/api/v1/refacciones")
@Tag(name = "Refacciones", description = "Alta y consulta de las refacciones del inventario")
public class RefaccionController {

	private final RegistroRefaccionService registroRefaccionService;
	private final ConsultaRefaccionesService consultaRefaccionesService;
	private final EdicionRefaccionService edicionRefaccionService;

	public RefaccionController(RegistroRefaccionService registroRefaccionService,
			ConsultaRefaccionesService consultaRefaccionesService, EdicionRefaccionService edicionRefaccionService) {
		this.registroRefaccionService = registroRefaccionService;
		this.consultaRefaccionesService = consultaRefaccionesService;
		this.edicionRefaccionService = edicionRefaccionService;
	}

	@PostMapping
	@PreAuthorize("hasRole('INVENTARIO')")
	@Operation(summary = "Dar de alta una refacción", description = "Entra con existencia cero; la existencia "
			+ "crece con las recepciones. Responde 400 si faltan datos, el costo es negativo, el precio no es mayor a "
			+ "cero o el mínimo es negativo, y 409 si el código ya existe.")
	public ResponseEntity<RefaccionVista> registrar(@Valid @RequestBody RegistrarRefaccionRequest request) {
		RefaccionVista creada = registroRefaccionService.registrar(request.comoNuevaRefaccion());
		return ResponseEntity.created(URI.create("/api/v1/refacciones/" + creada.id())).body(creada);
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('INVENTARIO', 'REFACCIONES', 'ADMINISTRADOR')")
	@Operation(summary = "Listar refacciones", description = "Ordenadas por código.")
	public List<RefaccionVista> listar() {
		return consultaRefaccionesService.listar();
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAnyRole('INVENTARIO', 'REFACCIONES', 'ADMINISTRADOR')")
	@Operation(summary = "Consultar una refacción", description = "Responde 404 si no existe.")
	public RefaccionVista obtener(@PathVariable Long id) {
		return consultaRefaccionesService.obtener(id);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('INVENTARIO')")
	@Operation(summary = "Corregir los datos de una refacción", description = "Las refacciones se editan, no se "
			+ "eliminan. La existencia no se edita: solo cambia con movimientos. Responde 400 si un dato no es "
			+ "válido, 404 si no existe y 409 si otra refacción ya tiene ese código.")
	public RefaccionVista editar(@PathVariable Long id, @Valid @RequestBody EditarRefaccionRequest request) {
		return edicionRefaccionService.editar(id, request.comoRefaccionEditada());
	}
}
