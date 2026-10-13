package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.MovimientoInventario;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Refaccion;
import java.time.Instant;

/**
 * Resultado de una recepción de refacciones (ERP-2.0).
 *
 * @param movimientoId
 *            id del movimiento de entrada que respalda la recepción
 * @param existencia
 *            existencia de la refacción después de sumar la recepción
 */
public record RecepcionRegistrada(Long movimientoId, Long refaccionId, String codigo, int cantidad, Long responsableId,
		Instant fecha, int existencia) {

	static RecepcionRegistrada de(MovimientoInventario movimiento, Refaccion refaccion) {
		return new RecepcionRegistrada(movimiento.getId(), refaccion.getId(), refaccion.getCodigo(),
				movimiento.getCantidad(), movimiento.getResponsableId(), movimiento.getFecha(),
				refaccion.getExistencia());
	}
}
