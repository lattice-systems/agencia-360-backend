package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain;

/**
 * En qué va la verificación de los pagos de un prospecto en Pago (ERP-1.1).
 * <ul>
 * <li>{@link #ABIERTA}: Caja registra o corrige los pagos.</li>
 * <li>{@link #ENVIADA}: Caja los envió y Contabilidad los revisa.</li>
 * <li>{@link #VALIDADA}: Contabilidad los aprobó y el prospecto pasó a
 * Facturación.</li>
 * <li>{@link #RECHAZADA}: Contabilidad los regresó con un motivo; Caja corrige
 * y vuelve a enviar.</li>
 * </ul>
 */
public enum EstadoLiquidacion {
	ABIERTA, ENVIADA, VALIDADA, RECHAZADA
}
