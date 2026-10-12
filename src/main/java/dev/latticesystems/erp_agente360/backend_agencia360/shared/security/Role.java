package dev.latticesystems.erp_agente360.backend_agencia360.shared.security;

/**
 * Roles para el control de acceso. Los del personal de la agencia deben
 * coincidir con la columna "nombre" de la tabla rol. CLIENTE_CARECAR es para
 * los clientes de la app, que tienen su propia cuenta.
 */
public enum Role {
	ADMINISTRADOR, GERENTE_MARKETING, VENTAS, FACTURACION, INVENTARIO, SERVICIO, REFACCIONES, CAJA, CONTABILIDAD, CLIENTE_CARECAR
}
