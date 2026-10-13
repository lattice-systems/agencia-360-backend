package dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain;

import java.math.BigDecimal;

/** Validación de los precios del inventario. */
final class Montos {

	private Montos() {
	}

	/**
	 * @throws IllegalArgumentException
	 *             si el precio falta o no es mayor a cero
	 */
	static BigDecimal mayorACero(BigDecimal precio) {
		if (precio == null || precio.signum() <= 0) {
			throw new IllegalArgumentException("El precio debe ser mayor a cero");
		}
		return precio;
	}
}
