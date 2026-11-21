package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.ModeloCatalogo;
import java.math.BigDecimal;

/**
 * Modelo del catálogo ({@code ModeloCatalogo} del contrato de la app). Todo lo
 * que no es id, marca, modelo y año puede venir en null.
 *
 * @param precioDesde
 *            precio de lista más bajo, con IVA
 * @param activo
 *            en la app siempre es true; el ERP ve también los retirados
 */
public record ModeloCatalogoApp(Long id, String marca, String modelo, int anio, String version, BigDecimal precioDesde,
		String imagenUrl, String descripcion, boolean activo) {

	static ModeloCatalogoApp de(ModeloCatalogo modelo) {
		return new ModeloCatalogoApp(modelo.getId(), modelo.getMarca(), modelo.getModelo(), modelo.getAnio(),
				modelo.getVersion(), modelo.getPrecioDesde(), modelo.getImagenUrl(), modelo.getDescripcion(),
				modelo.isActivo());
	}
}
