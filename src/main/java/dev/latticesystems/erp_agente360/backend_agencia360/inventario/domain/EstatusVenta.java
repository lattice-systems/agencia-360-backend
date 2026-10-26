package dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain;

/**
 * Estatus de una venta de unidad (ERP-1.3).
 */
public enum EstatusVenta {
	/**
	 * Facturación emitió la factura: es venta y la unidad está Vendida; falta que
	 * el vendedor la entregue.
	 */
	REGISTRADA,
	/** El vendedor llevó el prospecto hasta Finalizado: la unidad ya salió. */
	ENTREGADA
}
