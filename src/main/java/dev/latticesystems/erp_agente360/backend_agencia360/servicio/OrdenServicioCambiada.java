package dev.latticesystems.erp_agente360.backend_agencia360.servicio;

/**
 * Cambió una orden de servicio de un vehículo del cliente (V2-T05.08 y
 * V2-M04.03). La app lo recibe en vivo por {@code GET /api/v1/app/eventos} y
 * vuelve a consultar la orden; las notificaciones (V2-M06.07) lo escuchan para
 * mandar el push.
 *
 * @param etapa
 *            la etapa en que quedó; nula para un servicio registrado ya hecho,
 *            que no tiene línea de tiempo
 */
public record OrdenServicioCambiada(Long ordenId, Long vehiculoId, Long clienteId, EstatusOrden estatus,
		EtapaTaller etapa, MotivoCambio motivo) {
}
