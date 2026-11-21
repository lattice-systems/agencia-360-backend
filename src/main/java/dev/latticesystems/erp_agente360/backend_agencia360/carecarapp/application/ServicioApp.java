package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.servicio.HistorialDeServicios.ServicioDelVehiculo;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Orden de servicio vista por el cliente (APP-3.2).
 *
 * @param estatus
 *            uno de {@link EstatusServicio}; hoy todo servicio registrado en el
 *            expediente ya está hecho, así que es {@code TERMINADO}
 * @param total
 *            mano de obra más refacciones, con IVA incluido
 */
public record ServicioApp(Long id, LocalDate fecha, EstatusServicio estatus, BigDecimal total) {

	static ServicioApp de(ServicioDelVehiculo servicio) {
		return new ServicioApp(servicio.id(), servicio.fecha(), EstatusServicio.TERMINADO, servicio.total());
	}
}
