package dev.latticesystems.erp_agente360.backend_agencia360.clientes.application;

import dev.latticesystems.erp_agente360.backend_agencia360.clientes.domain.VehiculoCliente;

/**
 * Vehículo de un cliente como se muestra en el CRM. {@code modelo} es el Tipo
 * (Sentra) y {@code anio} el Modelo (2026).
 */
public record VehiculoClienteVista(Long id, String vin, String marca, String modelo, int anio, String version,
		String placas, Integer kilometraje) {

	static VehiculoClienteVista de(VehiculoCliente vehiculo) {
		return new VehiculoClienteVista(vehiculo.getId(), vehiculo.getVin(), vehiculo.getMarca(), vehiculo.getModelo(),
				vehiculo.getAnio(), vehiculo.getVersion(), vehiculo.getPlacas(), vehiculo.getKilometraje());
	}
}
