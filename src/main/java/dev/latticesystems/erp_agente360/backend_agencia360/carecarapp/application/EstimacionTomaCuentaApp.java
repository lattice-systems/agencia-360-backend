package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoVehiculo;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Una estimación no vinculante ({@code EstimacionTomaCuenta} del contrato de la
 * app). {@code minimo} y {@code maximo} son pesos con IVA, redondeados a 100.
 *
 * @param vehiculoId
 *            el vehículo del cliente, o null si capturó el auto a mano
 * @param modeloCatalogoId
 *            el modelo nuevo contra el que estimó, o null
 * @param modeloDestino
 *            nombre de ese modelo al momento de estimar, o null
 * @param precioDestino
 *            precio de lista de ese modelo en ese momento, o null
 */
public record EstimacionTomaCuentaApp(Long id, Instant creadaEn, Long vehiculoId, String marca, String modelo, int anio,
		int km, EstadoVehiculo estado, Long modeloCatalogoId, String modeloDestino, BigDecimal precioDestino,
		BigDecimal minimo, BigDecimal maximo, SupuestosEstimacionApp supuestos) {
}
