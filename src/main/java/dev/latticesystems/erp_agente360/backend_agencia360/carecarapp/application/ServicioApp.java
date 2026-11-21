package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.servicio.HistorialDeServicios.ServicioDelVehiculo;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Orden de servicio vista por el cliente (APP-3.2).
 *
 * @param estatus
 *            los valores siguen pendientes de ERP-3.1; hoy todo servicio
 *            registrado en el expediente ya está hecho, así que es
 *            {@value #TERMINADO}
 * @param total
 *            mano de obra más refacciones, con IVA incluido
 */
public record ServicioApp(Long id, LocalDate fecha, String estatus, BigDecimal total) {

	public static final String TERMINADO = "TERMINADO";

	static ServicioApp de(ServicioDelVehiculo servicio) {
		return new ServicioApp(servicio.id(), servicio.fecha(), TERMINADO, servicio.total());
	}
}
