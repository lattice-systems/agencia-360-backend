package dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain;

/**
 * Estatus de una unidad del inventario (decisión del PO del 9 de octubre). Una
 * unidad recién recibida queda {@link #DISPONIBLE}.
 */
public enum EstatusVehiculo {
	DISPONIBLE, APARTADA, VENDIDO, ENTREGADO, NO_DISPONIBLE
}
