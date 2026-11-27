package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.util.List;

/**
 * Sucursal ({@code Sucursal} del contrato de la app). {@code lat} y {@code lng}
 * de la tabla salen como {@code latitud} y {@code longitud}.
 *
 * @param abiertaAhora
 *            algún departamento está abierto en este momento
 */
public record SucursalApp(Long id, String nombre, String direccion, double latitud, double longitud, String telefono,
		String correo, boolean abiertaAhora, List<HorarioSucursalApp> horarios) {
}
