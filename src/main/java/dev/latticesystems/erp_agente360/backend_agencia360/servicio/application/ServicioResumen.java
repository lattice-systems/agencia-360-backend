package dev.latticesystems.erp_agente360.backend_agencia360.servicio.application;

import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.Servicio;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un servicio en el listado del expediente.
 *
 * @param atendidoPor
 *            nombre del usuario de Servicio que lo registró
 * @param total
 *            con IVA incluido
 */
public record ServicioResumen(Long id, LocalDate fecha, int kilometraje, String atendidoPor, BigDecimal total) {

	static ServicioResumen de(Servicio servicio, String atendidoPor) {
		return new ServicioResumen(servicio.getId(), servicio.getFecha(), servicio.getKilometraje(), atendidoPor,
				servicio.getTotal());
	}
}
