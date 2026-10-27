package dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos;

import java.time.Instant;

/**
 * Facturación emitió la factura de un prospecto (ERP-1.3): la unidad ya quedó
 * Vendida y la venta registrada. Lo publica facturacion en la misma transacción
 * de la emisión.
 *
 * <p>
 * Vive en shared para que facturacion y ventascrm no dependan entre sí
 * (contrato acordado con Coper en el Sprint 3). Lo escucha ventascrm (ERP-1.2:
 * la factura es la autorización de entrega y el prospecto pasa de Facturación a
 * Entrega) con {@code @ApplicationModuleListener}.
 *
 * @param vehiculoId
 *            la unidad que se vendió con la factura
 * @param folio
 *            folio interno de la factura, sin timbrado (decisión 6 del modelo
 *            de datos)
 * @param emitidaEn
 *            el momento de la emisión
 * @param emitidaPorId
 *            quién de Facturación emitió la factura; queda en el historial del
 *            prospecto al pasar a Entrega. Se agregó al final para no mover los
 *            demás campos
 */
public record FacturaEmitida(Long prospectoId, Long vehiculoId, String folio, Instant emitidaEn, Long emitidaPorId) {
}
