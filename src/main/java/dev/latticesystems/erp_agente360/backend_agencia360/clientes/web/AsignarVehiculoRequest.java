package dev.latticesystems.erp_agente360.backend_agencia360.clientes.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Vehículo que el Gerente de marketing asigna a un cliente (CRM-1.3). */
public record AsignarVehiculoRequest(@NotBlank @Size(min = 17, max = 17) String vin,
		@NotBlank @Size(max = 80) String modelo, @NotNull Integer anio) {
}
