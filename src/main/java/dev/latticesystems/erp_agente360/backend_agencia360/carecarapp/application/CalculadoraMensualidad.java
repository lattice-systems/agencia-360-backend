package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Mensualidad estimada de un auto nuevo (V2-M16.02) con pago fijo (sistema
 * francés): {@code M = P·r / (1 − (1 + r)^−n)}, con {@code r} la tasa anual
 * entre 12 y {@code P} el precio menos el enganche. Redondea a centavos.
 */
public final class CalculadoraMensualidad {

	/** Siempre acompaña a la mensualidad: no es una oferta. */
	public static final String LEYENDA = "Estimación no vinculante. No incluye seguro, comisión por apertura ni IVA "
			+ "de intereses; tu asesor te da la cotización formal.";

	private static final MathContext PRECISION = MathContext.DECIMAL128;
	private static final BigDecimal DOCE = BigDecimal.valueOf(12);

	private CalculadoraMensualidad() {
	}

	/** Lo que resulta del cálculo, en pesos. */
	public record Cotizacion(BigDecimal precio, BigDecimal enganche, BigDecimal montoFinanciado, BigDecimal tasaAnual,
			int plazoMeses, BigDecimal mensualidad, String leyenda) {
	}

	public static Cotizacion calcular(BigDecimal precio, BigDecimal enganche, int plazoMeses, BigDecimal tasaAnual) {
		BigDecimal financiado = precio.subtract(enganche).setScale(2, RoundingMode.HALF_UP);
		BigDecimal r = tasaAnual.divide(DOCE, PRECISION);
		BigDecimal factor = BigDecimal.ONE.add(r).pow(-plazoMeses, PRECISION);
		BigDecimal mensualidad = financiado.signum() == 0
				? BigDecimal.ZERO
				: financiado.multiply(r, PRECISION).divide(BigDecimal.ONE.subtract(factor, PRECISION), PRECISION);
		return new Cotizacion(precio.setScale(2, RoundingMode.HALF_UP), enganche.setScale(2, RoundingMode.HALF_UP),
				financiado, tasaAnual, plazoMeses, mensualidad.setScale(2, RoundingMode.HALF_UP), LEYENDA);
	}
}
