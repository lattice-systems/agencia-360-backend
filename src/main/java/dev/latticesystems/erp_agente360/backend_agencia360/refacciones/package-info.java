/**
 * Refacciones.
 *
 * <p>Ventas directas de refacciones y búsqueda rápida por código o descripción
 * (ERP HU 3.2). Toda venta descuenta existencias en Inventario mediante bloqueo de
 * fila para evitar vender la misma pieza dos veces.
 *
 * <p>La división final de módulos está pendiente de definir (ver sección 9 del
 * documento de arquitectura); este límite es una propuesta inicial basada en las
 * áreas funcionales del backlog.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Refacciones")
package dev.latticesystems.erp_agente360.backend_agencia360.refacciones;
