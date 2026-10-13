package dev.latticesystems.erp_agente360.backend_agencia360.inventario.web;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.VehiculoEditado;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.EstatusVehiculo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corrección de una unidad. Se mandan todos los datos, como en el alta.
 *
 * @param estatus
 *            opcional: DISPONIBLE o NO_DISPONIBLE; sin valor se conserva
 */
public record EditarVehiculoRequest(@NotBlank @Size(max = 30) String vin, @NotBlank @Size(max = 80) String modelo,
		@NotBlank @Size(max = 80) String version, @NotNull Integer anio, @NotBlank @Size(max = 40) String color,
		EstatusVehiculo estatus) {

	VehiculoEditado comoVehiculoEditado() {
		return new VehiculoEditado(vin, modelo, version, anio, color, estatus);
	}
}
