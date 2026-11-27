package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

/**
 * Qué se facturó desde la app (columna {@code tipo} de {@code factura},
 * V2-M08.06): una orden de servicio o un pedido de la tienda. Las facturas de
 * la venta de una unidad ({@code VENTA_UNIDAD}) son del ERP.
 */
public enum ObjetoFacturado {
	SERVICIO, PEDIDO_APP
}
