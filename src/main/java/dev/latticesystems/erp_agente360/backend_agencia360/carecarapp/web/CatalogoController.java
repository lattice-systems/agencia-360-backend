package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CatalogoService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ModeloCatalogoApp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catálogo de vehículos nuevos (APP-4.1): la app lee los modelos activos y el
 * Administrador o el Gerente de marketing lo mantienen desde el ERP o el CRM.
 */
@RestController
@Tag(name = "Catálogo", description = "Modelos nuevos que muestra la app CareCar")
public class CatalogoController {

	private final CatalogoService catalogoService;

	public CatalogoController(CatalogoService catalogoService) {
		this.catalogoService = catalogoService;
	}

	@GetMapping("/api/v1/app/catalogo")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Modelos actuales del catálogo", description = "Solo los activos, por modelo y del año más "
			+ "nuevo al más viejo.")
	public List<ModeloCatalogoApp> vigentes() {
		return catalogoService.vigentes();
	}

	@GetMapping("/api/v1/catalogo")
	@PreAuthorize("hasAnyRole('ADMINISTRADOR', 'GERENTE_MARKETING')")
	@Operation(summary = "Todo el catálogo", description = "Incluye los modelos retirados.")
	public List<ModeloCatalogoApp> todos() {
		return catalogoService.todos();
	}

	@PostMapping("/api/v1/catalogo")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAnyRole('ADMINISTRADOR', 'GERENTE_MARKETING')")
	@Operation(summary = "Agregar un modelo al catálogo", description = "Responde 409 si ya existe la misma marca, "
			+ "modelo, año y versión.")
	public ModeloCatalogoApp agregar(@Valid @RequestBody NuevoModeloCatalogoRequest request) {
		return catalogoService.agregar(request.marca(), request.modelo(), request.anio(), request.version(),
				request.precioDesde(), request.imagenUrl(), request.descripcion());
	}

	@PostMapping("/api/v1/catalogo/{id}/retirar")
	@PreAuthorize("hasAnyRole('ADMINISTRADOR', 'GERENTE_MARKETING')")
	@Operation(summary = "Retirar un modelo", description = "Deja de mostrarse en la app; no se borra.")
	public ModeloCatalogoApp retirar(@PathVariable Long id) {
		return catalogoService.retirar(id);
	}
}
