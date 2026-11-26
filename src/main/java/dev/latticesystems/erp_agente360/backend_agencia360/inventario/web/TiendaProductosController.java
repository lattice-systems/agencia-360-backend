package dev.latticesystems.erp_agente360.backend_agencia360.inventario.web;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.PublicacionVista;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.TiendaInventarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Qué refacciones salen en la tienda de la app (V2-M14.01), mientras el ERP
 * siga congelado. Se opera con Swagger UI en local o con un cliente HTTP.
 */
@RestController
@RequestMapping("/api/v1/tienda/productos")
@PreAuthorize("hasAnyRole('INVENTARIO', 'REFACCIONES', 'ADMINISTRADOR')")
@Tag(name = "Tienda - Productos", description = "Publicación de refacciones en la tienda de la app")
public class TiendaProductosController {

	private final TiendaInventarioService tienda;

	public TiendaProductosController(TiendaInventarioService tienda) {
		this.tienda = tienda;
	}

	@GetMapping("/{refaccionId}")
	@Operation(summary = "Ver la publicación de una refacción", description = "Responde 404 si no existe.")
	public PublicacionVista ver(@PathVariable Long refaccionId) {
		return tienda.publicacion(refaccionId);
	}

	@PutMapping("/{refaccionId}")
	@Operation(summary = "Publicar o retirar una refacción", description = "Reemplaza categoría, descripción, "
			+ "foto y compatibilidad. Responde 400 si al publicar falta la categoría o no es universal ni trae "
			+ "modelos, y 404 si no existe.")
	public PublicacionVista publicar(@PathVariable Long refaccionId,
			@Valid @RequestBody PublicarEnTiendaRequest request) {
		return tienda.publicar(refaccionId, request.comoPublicacion());
	}
}
