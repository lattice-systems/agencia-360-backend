package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;

/**
 * Vehículo del cliente en la app ({@code Vehiculo} del contrato de la app).
 * Como en la factura, {@code modelo} es el Tipo (Sentra) y {@code anio} el
 * Modelo (2026). La marca y la versión pueden faltar en vehículos registrados
 * antes.
 */
public record VehiculoApp(Long id, String vin, String marca, String modelo, int anio, String version, String placas,
		Integer kilometraje) {

	static VehiculoApp de(VehiculoDeCliente vehiculo) {
		return new VehiculoApp(vehiculo.id(), vehiculo.vin(), vehiculo.marca(), vehiculo.modelo(), vehiculo.anio(),
				vehiculo.version(), vehiculo.placas(), vehiculo.kilometraje());
	}
}
