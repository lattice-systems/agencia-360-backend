package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.math.BigDecimal;

/**
 * Una orden de servicio quedó pagada, en la app o en caja (V2-M08.02). Lo
 * escuchan los puntos de lealtad (V2-M12.02) y lo escuchará el timbrado de la
 * factura (V2-M08.06). Se publica dentro de la transacción que lo causa.
 */
public record PagoConfirmado(Long pagoId, Long ordenId, Long clienteId, BigDecimal monto) {
}
