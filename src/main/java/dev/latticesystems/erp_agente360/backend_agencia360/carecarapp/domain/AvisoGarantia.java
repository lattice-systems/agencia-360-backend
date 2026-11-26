package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

/**
 * Avisos de garantía por vencer (V2-M10.05): 60 y 15 días antes de la fecha
 * límite y 1 000 km antes del km límite. Cada uno se manda una sola vez.
 */
public enum AvisoGarantia {
	DIAS_60, DIAS_15, KM_1000
}
