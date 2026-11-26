package dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.CatalogoDeTienda.ModeloCompatible;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.CategoriaTienda;
import java.util.List;

/** Cómo se muestra una refacción en la tienda de la app. */
public record PublicacionTienda(boolean publicada, CategoriaTienda categoria, String descripcion, String fotoUrl,
		boolean universal, List<ModeloCompatible> compatibilidad) {

	public PublicacionTienda {
		compatibilidad = compatibilidad == null ? List.of() : List.copyOf(compatibilidad);
	}
}
