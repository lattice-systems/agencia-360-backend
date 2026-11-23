package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.math.BigDecimal;

/**
 * Lo que usó la fórmula ({@code SupuestosEstimacion} del contrato de la app),
 * para mostrarlo al cliente y auditar el resultado. Se guarda tal cual con la
 * estimación.
 *
 * @param ajusteKm
 *            multiplicador por kilometraje, entre 0.5 y 1.2
 * @param margen
 *            fracción que se resta y se suma al valor estimado
 */
public record SupuestosEstimacionApp(BigDecimal valorBase, int edadAnios, long kmEsperado, BigDecimal ajusteKm,
		BigDecimal ajusteEstado, BigDecimal margen) {
}
