package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

/**
 * Cobertura de garantía de un componente que registra el personal (V2-M10.01).
 */
public record NuevaGarantiaRequest(@NotNull Long vehiculoId, @NotBlank String componente, String descripcion,
		@NotNull LocalDate iniciaEn, @NotNull LocalDate venceEn, @Positive Integer kmLimite) {
}
