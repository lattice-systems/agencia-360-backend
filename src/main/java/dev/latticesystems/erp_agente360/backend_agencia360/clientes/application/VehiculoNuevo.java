package dev.latticesystems.erp_agente360.backend_agencia360.clientes.application;

/**
 * Vehículo que se asigna a un cliente.
 *
 * @param vehiculoId
 *            unidad del inventario, o {@code null} si no la vendió la agencia
 */
public record VehiculoNuevo(Long vehiculoId, String vin, String modelo, int anio) {
}
