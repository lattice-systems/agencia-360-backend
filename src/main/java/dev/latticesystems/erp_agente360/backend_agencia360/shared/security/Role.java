package dev.latticesystems.erp_agente360.backend_agencia360.shared.security;

/**
 * Roles propuestos para el control de acceso, a partir de los departamentos de la
 * agencia (sección 1 del documento de arquitectura) más el rol de cliente de la app
 * CareCar. Definición inicial: la asignación fina de permisos por endpoint queda
 * pendiente junto con el modelo de datos (sección 9).
 */
public enum Role {
	ADMIN,
	VENTAS,
	FACTURACION,
	INVENTARIO,
	SERVICIO,
	REFACCIONES,
	CAJA,
	CONTABILIDAD,
	CLIENTE_CARECAR
}
