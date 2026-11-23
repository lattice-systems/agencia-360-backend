package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.SolicitudEstimacion;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoVehiculo;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/v1/app/toma-a-cuenta/estimaciones}
 * ({@code NuevaEstimacionTomaCuenta} del contrato). El auto se indica con
 * {@code vehiculoId} o con {@code marca}, {@code modelo} y {@code anio}, nunca
 * con las dos formas ni con ninguna.
 */
public record NuevaEstimacionTomaCuentaRequest(Long vehiculoId, @Size(max = 40) String marca,
		@Size(max = 80) String modelo, @Min(1900) Integer anio, @NotNull @Min(0) @Max(999_999) Integer km,
		@NotNull EstadoVehiculo estado, Long modeloCatalogoId) {

	SolicitudEstimacion comoSolicitud() {
		return new SolicitudEstimacion(vehiculoId, marca, modelo, anio, km, estado, modeloCatalogoId);
	}
}
