package dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Montos de un servicio. Los precios siempre traen el IVA incluido: el total es
 * la suma de los renglones y de ahí se desglosan el subtotal y el IVA del 16 %,
 * como en la factura (ERP-1.3). La suma siempre da el total.
 */
public record Montos(BigDecimal subtotal, BigDecimal iva, BigDecimal total) {

	/** Tasa general del IVA. */
	public static final BigDecimal TASA_IVA = new BigDecimal("0.16");

	/**
	 * @throws IllegalArgumentException
	 *             si el total es negativo
	 */
	public static Montos desglosar(BigDecimal total) {
		Objects.requireNonNull(total, "total");
		if (total.signum() < 0) {
			throw new IllegalArgumentException("El total del servicio no puede ser negativo");
		}
		BigDecimal conCentavos = total.setScale(2, RoundingMode.HALF_EVEN);
		BigDecimal subtotal = conCentavos.divide(BigDecimal.ONE.add(TASA_IVA), 2, RoundingMode.HALF_EVEN);
		return new Montos(subtotal, conCentavos.subtract(subtotal), conCentavos);
	}
}
