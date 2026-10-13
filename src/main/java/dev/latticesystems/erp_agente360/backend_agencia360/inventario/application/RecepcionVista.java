package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.MovimientoInventario;
import java.time.Instant;

/**
 * Una recepción de refacciones como se muestra en el ERP.
 *
 * @param movimientoId
 *            id del movimiento de entrada que respalda la recepción
 * @param fechaLlegada
 *            cuándo llegó la mercancía
 * @param fechaRegistro
 *            cuándo se registró; si difiere de la llegada, el Administrador lo
 *            ve aquí
 */
public record RecepcionVista(Long movimientoId, int cantidad, String proveedor, String notas, Instant fechaLlegada,
		Instant fechaRegistro, Long responsableId) {

	static RecepcionVista de(MovimientoInventario movimiento) {
		return new RecepcionVista(movimiento.getId(), movimiento.getCantidad(), movimiento.getProveedor(),
				movimiento.getNotas(), movimiento.getFechaLlegada(), movimiento.getFechaRegistro(),
				movimiento.getResponsableId());
	}
}
