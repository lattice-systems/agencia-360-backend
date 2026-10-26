package dev.latticesystems.erp_agente360.backend_agencia360.servicio.application;

import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.Propietario;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
import java.util.List;

/**
 * Expediente del vehículo (ERP-3.0): arriba el vehículo y su dueño, abajo sus
 * servicios del más reciente al más antiguo.
 */
public record Expediente(Vehiculo vehiculo, Propietario cliente, List<ServicioResumen> servicios) {

	/**
	 * Datos del vehículo. Como en el inventario y la factura, {@code modelo} es el
	 * Tipo (Sentra) y {@code anio} el Modelo (2026).
	 */
	public record Vehiculo(Long id, String vin, String marca, String modelo, int anio, String version, String placas,
			Integer kilometraje) {

		static Vehiculo de(VehiculoDeCliente vehiculo) {
			return new Vehiculo(vehiculo.id(), vehiculo.vin(), vehiculo.marca(), vehiculo.modelo(), vehiculo.anio(),
					vehiculo.version(), vehiculo.placas(), vehiculo.kilometraje());
		}
	}

	/** Un vehículo encontrado al buscar por VIN, con el nombre de su dueño. */
	public record Encontrado(Long id, String vin, String marca, String modelo, int anio, String version, String placas,
			String cliente) {

		static Encontrado de(VehiculoDeCliente vehiculo) {
			return new Encontrado(vehiculo.id(), vehiculo.vin(), vehiculo.marca(), vehiculo.modelo(), vehiculo.anio(),
					vehiculo.version(), vehiculo.placas(), vehiculo.propietario().nombre());
		}
	}
}
