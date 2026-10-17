package dev.latticesystems.erp_agente360.backend_agencia360.clientes.application;

import dev.latticesystems.erp_agente360.backend_agencia360.clientes.domain.VehiculoCliente;

/** Vehículo de un cliente como se muestra en el CRM. */
public record VehiculoClienteVista(Long id, String vin, String modelo, int anio, String placas, Integer kilometraje) {

	static VehiculoClienteVista de(VehiculoCliente vehiculo) {
		return new VehiculoClienteVista(vehiculo.getId(), vehiculo.getVin(), vehiculo.getModelo(), vehiculo.getAnio(),
				vehiculo.getPlacas(), vehiculo.getKilometraje());
	}
}
