package dev.latticesystems.erp_agente360.backend_agencia360.inventario.web;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.NuevoVehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.EstatusVehiculo;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Alta de una unidad. El dominio valida el formato del VIN, el año, que el
 * precio sea mayor a cero y el estatus; aquí solo se exigen los datos y se
 * limita su largo. El precio admite hasta 10 enteros y 2 decimales, como la
 * columna.
 *
 * @param estatus
 *            opcional: DISPONIBLE (por omisión) o NO_DISPONIBLE
 */
public record RegistrarVehiculoRequest(@NotBlank @Size(max = 30) String vin, @NotBlank @Size(max = 80) String modelo,
		@NotBlank @Size(max = 80) String version, @NotNull Integer anio, @NotBlank @Size(max = 40) String color,
		@NotNull @Digits(integer = 10, fraction = 2) BigDecimal precio, EstatusVehiculo estatus) {

	NuevoVehiculo comoNuevoVehiculo() {
		return new NuevoVehiculo(vin, modelo, version, anio, color, precio, estatus);
	}
}
