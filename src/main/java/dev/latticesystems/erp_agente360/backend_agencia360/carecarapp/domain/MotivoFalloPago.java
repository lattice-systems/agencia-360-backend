package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

/** Por qué no se cobró un pago (V2-M08.02). */
public enum MotivoFalloPago {

	/** El banco simulado rechazó la tarjeta por saldo. */
	FONDOS_INSUFICIENTES,

	/** El banco simulado no confirmó el 3-D Secure, o la persona lo canceló. */
	AUTENTICACION_FALLIDA,

	/** Nadie respondió el 3-D Secure a tiempo. */
	AUTENTICACION_VENCIDA,

	/** El personal marcó la orden pagada en caja mientras el intento seguía abierto. */
	PAGADA_EN_CAJA
}
