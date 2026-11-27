package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

/**
 * Estado de un documento según su vencimiento (V2-M09.01). Se calcula, no se
 * guarda: depende del día en que se pregunta.
 */
public enum EstadoDocumento {
	/** Sin fecha de vencimiento (p. ej. la factura de compra). */
	SIN_FECHA,
	/** Vence en más de {@value #DIAS_POR_VENCER} días. */
	VIGENTE,
	/** Vence hoy o dentro de los siguientes {@value #DIAS_POR_VENCER} días. */
	POR_VENCER,
	/** Su fecha de vencimiento ya pasó. */
	VENCIDO;

	public static final int DIAS_POR_VENCER = 30;
}
