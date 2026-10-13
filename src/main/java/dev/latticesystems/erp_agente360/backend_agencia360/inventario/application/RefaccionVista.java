package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Refaccion;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Datos de una refacción que se muestran en el ERP.
 *
 * @param bajoMinimo
 *            {@code true} si la existencia llegó al mínimo o quedó por debajo;
 *            es lo que dispara el aviso a Refacciones y al Administrador
 * @param creadoEn
 *            fecha y hora del alta
 */
public record RefaccionVista(Long id, String codigo, String descripcion, BigDecimal costo, BigDecimal precio,
		int existencia, int minimo, boolean bajoMinimo, Instant creadoEn) {

	static RefaccionVista de(Refaccion refaccion) {
		return new RefaccionVista(refaccion.getId(), refaccion.getCodigo(), refaccion.getDescripcion(),
				refaccion.getCosto(), refaccion.getPrecio(), refaccion.getExistencia(), refaccion.getMinimo(),
				refaccion.estaBajoMinimo(), refaccion.getCreatedAt());
	}
}
