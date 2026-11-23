package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoVehiculo;

/**
 * Lo que el cliente pide estimar. El auto es uno de sus vehículos
 * ({@code vehiculoId}) o uno capturado a mano ({@code marca}, {@code modelo} y
 * {@code anio}), nunca los dos ni ninguno. {@code modelo} es el Tipo, como
 * Versa. {@code modeloCatalogoId} es opcional.
 */
public record SolicitudEstimacion(Long vehiculoId, String marca, String modelo, Integer anio, Integer km,
		EstadoVehiculo estado, Long modeloCatalogoId) {
}
