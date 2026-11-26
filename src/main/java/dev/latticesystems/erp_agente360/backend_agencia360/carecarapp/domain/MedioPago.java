package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

/**
 * Con qué se pagó una orden (columna {@code metodo} de {@code pago_app}). Las
 * billeteras son simuladas (V2-M08.03) y {@code CAJA} es la orden que el
 * personal marca pagada en el mostrador.
 */
public enum MedioPago {
	TARJETA, APPLE_PAY, GOOGLE_PAY, CAJA
}
