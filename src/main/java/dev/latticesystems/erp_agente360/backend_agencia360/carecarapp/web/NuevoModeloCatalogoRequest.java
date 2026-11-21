package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Modelo que se agrega al catálogo de la app (APP-4.1). */
public record NuevoModeloCatalogoRequest(@NotBlank String marca, @NotBlank String modelo, @NotNull Integer anio,
		String version, BigDecimal precioDesde, String imagenUrl, String descripcion) {
}
