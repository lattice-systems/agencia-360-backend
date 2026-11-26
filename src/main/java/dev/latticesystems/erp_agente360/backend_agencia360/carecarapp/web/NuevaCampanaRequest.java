package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.CampanaSeguridad;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.GravedadCampana;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Campaña de seguridad que publica el personal (V2-M10.04). El rango de VIN es
 * opcional y lleva los dos extremos.
 */
public record NuevaCampanaRequest(@NotBlank String codigo, @NotBlank String titulo, @NotBlank String descripcion,
		@NotBlank String queHacer, @NotNull GravedadCampana gravedad, @NotBlank String modelo,
		@NotNull Integer anioDesde, @NotNull Integer anioHasta, String vinDesde, String vinHasta) {

	CampanaSeguridad.Datos datos() {
		return new CampanaSeguridad.Datos(codigo, titulo, descripcion, queHacer, gravedad, modelo, anioDesde, anioHasta,
				vinDesde, vinHasta);
	}
}
