package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.CatalogoDeTienda.ModeloCompatible;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.CategoriaTienda;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Refaccion;
import java.util.List;

/** Cómo está publicada una refacción, para el personal. */
public record PublicacionVista(Long refaccionId, String codigo, String nombre, boolean publicada,
		CategoriaTienda categoria, String descripcion, String fotoUrl, boolean universal,
		List<ModeloCompatible> compatibilidad) {

	static PublicacionVista de(Refaccion refaccion) {
		return new PublicacionVista(refaccion.getId(), refaccion.getCodigo(), refaccion.getDescripcion(),
				refaccion.isPublicadaEnApp(), refaccion.getCategoriaApp(), refaccion.getDescripcionApp(),
				refaccion.getFotoUrl(), refaccion.isUniversalApp(), TiendaInventarioService.compatibles(refaccion));
	}
}
