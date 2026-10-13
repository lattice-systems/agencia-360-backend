package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import java.math.BigDecimal;

/** Datos capturados para dar de alta una unidad (ERP-2.0). */
public record NuevoVehiculo(String vin, String modelo, String version, int anio, String color, BigDecimal precio) {
}
