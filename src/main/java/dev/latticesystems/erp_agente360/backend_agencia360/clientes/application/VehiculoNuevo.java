package dev.latticesystems.erp_agente360.backend_agencia360.clientes.application;

/**
 * Vehículo que se asigna a un cliente.
 *
 * @param vehiculoId
 *            unidad del inventario, o {@code null} si no la vendió la agencia
 * @param marca
 *            puede faltar
 * @param version
 *            puede faltar
 */
public record VehiculoNuevo(Long vehiculoId, String vin, String marca, String modelo, int anio, String version) {

	/** Vehículo sin marca ni versión. */
	public VehiculoNuevo(Long vehiculoId, String vin, String modelo, int anio) {
		this(vehiculoId, vin, null, modelo, anio, null);
	}
}
