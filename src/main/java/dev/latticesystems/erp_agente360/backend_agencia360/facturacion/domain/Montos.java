package dev.latticesystems.erp_agente360.backend_agencia360.facturacion.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Montos de la factura. El precio total que captura el vendedor (ERP-1.1) ya
 * trae el IVA, como el precio de lista de un auto: de ahí se desglosan el
 * subtotal y el IVA del 16 %, y la suma siempre da el total.
 */
public record Montos(BigDecimal subtotal, BigDecimal iva, BigDecimal total) {

	/** Tasa general del IVA. */
	public static final BigDecimal TASA_IVA = new BigDecimal("0.16");

	/**
	 * @throws IllegalArgumentException
	 *             si el total no es mayor a cero
	 */
	public static Montos desglosar(BigDecimal total) {
		Objects.requireNonNull(total, "total");
		if (total.signum() <= 0) {
			throw new IllegalArgumentException("El total de la factura debe ser mayor a cero");
		}
		BigDecimal conCentavos = total.setScale(2, RoundingMode.HALF_EVEN);
		BigDecimal subtotal = conCentavos.divide(BigDecimal.ONE.add(TASA_IVA), 2, RoundingMode.HALF_EVEN);
		return new Montos(subtotal, conCentavos.subtract(subtotal), conCentavos);
	}
}
