/**
 * Caja.
 *
 * <p>
 * Recepción y registro de pagos, vinculados a una orden de servicio, un ticket
 * de refacciones o una venta de auto (ERP HU 4.1). Los movimientos de dinero no
 * se borran: una cancelación se registra como movimiento inverso para conservar
 * la trazabilidad, y requiere un mecanismo de autorización.
 *
 * <p>
 * La división final de módulos está pendiente de definir (ver sección 9 del
 * documento de arquitectura); este límite es una propuesta inicial basada en
 * las áreas funcionales del backlog.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Caja")
package dev.latticesystems.erp_agente360.backend_agencia360.caja;
