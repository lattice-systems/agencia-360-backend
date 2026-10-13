/**
 * Inventario.
 *
 * <p>
 * Alta de vehículos y refacciones y recepción de inventario (ERP HU 2.0), e
 * inventario en tiempo real (ERP HU 2.1). Los vehículos se registran uno por
 * uno por su VIN y las refacciones por existencia. Se registra cada movimiento
 * de entrada y salida, con bloqueo de fila para evitar vender la misma pieza
 * dos veces. Refleja automáticamente la entrada o salida de un vehículo y las
 * existencias de refacciones tras una venta o un servicio de taller.
 *
 * <p>
 * La división final de módulos está pendiente de definir (ver sección 9 del
 * documento de arquitectura); este límite es una propuesta inicial basada en
 * las áreas funcionales del backlog.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Inventario")
package dev.latticesystems.erp_agente360.backend_agencia360.inventario;
