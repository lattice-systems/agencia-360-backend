package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.CatalogoDeTienda.ModeloCompatible;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.CatalogoDeTienda.ProductoDeTienda;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/** Un producto de la tienda como lo pide el contrato (V2-M14.01). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductoApp(Long id, String codigo, String nombre, String descripcion, String categoria,
		BigDecimal precio, String fotoUrl, int existencia, boolean universal, List<ModeloCompatible> compatibilidad,
		Boolean compatible) {

	static ProductoApp de(ProductoDeTienda producto, Boolean compatible) {
		return new ProductoApp(producto.id(), producto.codigo(), producto.nombre(), producto.descripcion(),
				producto.categoria().name().toLowerCase(Locale.ROOT), producto.precio(), producto.fotoUrl(),
				producto.existencia(), producto.universal(), producto.compatibilidad(), compatible);
	}
}
