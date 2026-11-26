package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

/**
 * Estado de un pago de la app (V2-M08.02). Nace {@code PENDIENTE}; queda
 * {@code PAGADO} o {@code FALLIDO} según el procesador. {@code REEMBOLSADO} lo
 * pone la agencia; la app solo lo muestra.
 */
public enum EstadoPago {
	PENDIENTE, PAGADO, FALLIDO, REEMBOLSADO
}
