package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

/** Por qué se movieron los puntos de un cliente (V2-M12). */
public enum MotivoPuntos {
	/** Un servicio entregado o pagado (V2-M12.02). */
	SERVICIO,
	/** Un canje de recompensa (V2-M12.03); resta. */
	CANJE,
	/** Un canje que venció sin usarse regresa sus puntos (V2-M12.03). */
	DEVOLUCION_CANJE,
	/** La venta de un referido se finalizó (V2-M12.04). */
	REFERIDO,
	/** Ajuste manual de la agencia. */
	AJUSTE;

	/** Los que cuentan como puntos ganados para el nivel. */
	public boolean ganado() {
		return this == SERVICIO || this == REFERIDO || this == AJUSTE;
	}
}
