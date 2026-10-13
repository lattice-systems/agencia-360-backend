package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.EstatusVehiculo;

/**
 * Datos capturados para dar de alta una unidad (ERP-2.0).
 *
 * @param estatus
 *            Disponible o No disponible; {@code null} para Disponible
 */
public record NuevoVehiculo(String vin, String modelo, String version, int anio, String color,
		EstatusVehiculo estatus) {
}
