package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.EstatusVehiculo;
import java.math.BigDecimal;

/**
 * Datos corregidos de una unidad.
 *
 * @param estatus
 *            Disponible o No disponible; {@code null} para conservar el actual
 */
public record VehiculoEditado(String vin, String modelo, String version, int anio, String color, BigDecimal precio,
		EstatusVehiculo estatus) {
}
