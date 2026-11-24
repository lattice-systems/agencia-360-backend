package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * Tasa, enganche mínimo y plazos para la mensualidad estimada (V2-M16.02), de
 * {@code parametro_financiamiento}.
 */
public record ParametrosFinanciamiento(BigDecimal tasaAnual, BigDecimal engancheMinimoPct, List<Integer> plazos) {

	/** Lee la única fila de parámetros. */
	public interface Fuente {
		ParametrosFinanciamiento actuales();
	}
}
