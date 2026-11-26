package dev.latticesystems.erp_agente360.backend_agencia360.inventario.web;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.CatalogoDeTienda.ModeloCompatible;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.CategoriaTienda;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.PublicacionTienda;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PublicarEnTiendaRequest(@NotNull Boolean publicada, CategoriaTienda categoria,
		@Size(max = 1000) String descripcion, @Size(max = 500) String fotoUrl, boolean universal,
		@Size(max = 40) List<@Valid @NotNull Modelo> compatibilidad) {

	public record Modelo(@NotBlank @Size(max = 60) String modelo, @NotNull Integer anioDesde,
			@NotNull Integer anioHasta) {
	}

	PublicacionTienda comoPublicacion() {
		List<ModeloCompatible> modelos = compatibilidad == null
				? List.of()
				: compatibilidad.stream()
						.map(uno -> new ModeloCompatible(uno.modelo(), uno.anioDesde(), uno.anioHasta())).toList();
		return new PublicacionTienda(publicada, categoria, descripcion, fotoUrl, universal, modelos);
	}
}
