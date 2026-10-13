package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.MovimientoInventario;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Refaccion;

/**
 * Resultado de una recepción de refacciones (ERP-2.0). En JSON los datos de la
 * recepción van al mismo nivel que los de la refacción.
 *
 * @param existencia
 *            existencia de la refacción después de sumar la recepción
 */
public record RecepcionRegistrada(@JsonUnwrapped RecepcionVista recepcion, Long refaccionId, String codigo,
		int existencia) {

	static RecepcionRegistrada de(MovimientoInventario movimiento, Refaccion refaccion) {
		return new RecepcionRegistrada(RecepcionVista.de(movimiento), refaccion.getId(), refaccion.getCodigo(),
				refaccion.getExistencia());
	}
}
