package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.EstatusVehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Vehiculo;
import java.time.Instant;

/**
 * Datos de una unidad que se muestran en el ERP.
 *
 * @param creadoEn
 *            fecha y hora del alta
 */
public record VehiculoVista(Long id, String vin, String modelo, String version, int anio, String color,
		EstatusVehiculo estatus, Instant creadoEn) {

	static VehiculoVista de(Vehiculo vehiculo) {
		return new VehiculoVista(vehiculo.getId(), vehiculo.getVin(), vehiculo.getModelo(), vehiculo.getVersion(),
				vehiculo.getAnio(), vehiculo.getColor(), vehiculo.getEstatus(), vehiculo.getCreatedAt());
	}
}
