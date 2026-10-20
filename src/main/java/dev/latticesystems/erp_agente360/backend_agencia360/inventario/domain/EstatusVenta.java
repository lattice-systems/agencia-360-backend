package dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain;

/**
 * Estatus de una venta de unidad (ERP-1.0).
 */
public enum EstatusVenta {
	/** Facturación ya eligió la unidad; falta que el vendedor la entregue. */
	POR_ENTREGAR,
	/**
	 * El vendedor llevó el prospecto hasta Finalizado: es venta (decisión del PO).
	 */
	REGISTRADA
}
